import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { PGlite } from '@electric-sql/pglite';
import express from 'express';
import request from 'supertest';
import { pool } from '../lib/db.mjs';
import * as spaces from '../lib/workspace_store.mjs';
import { withMemory } from '../lib/memory_store.mjs';
import { listTasks } from '../lib/scheduled_store.mjs';
import { workspaces } from '../routes/workspaces.mjs';
import { resolveOidcUser } from '../lib/auth.mjs';
import { voiceJobAccess } from '../lib/voice_queue.mjs';
import { runScheduledTask } from '../lib/scheduled_runner.mjs';

test('pending and legacy memberships require recipient consent before any shared context', async t => {
  const db = new PGlite();
  t.after(() => db.close());
  const migrate = async name => db.exec(readFileSync(new URL('../migrations/' + name, import.meta.url), 'utf8'));
  await migrate('001_init.sql');
  // This test needs document ownership columns, not the optional vector extension.
  await db.exec('CREATE TABLE documents(id uuid PRIMARY KEY, user_id uuid REFERENCES users(id))');
  for (const name of ['004_scheduled_tasks.sql', '005_user_memory.sql', '006_workspaces.sql', '007_memory_workspace.sql']) await migrate(name);
  const a = (await db.query("INSERT INTO users(email) VALUES ('a@example.invalid') RETURNING id")).rows[0].id;
  const b = (await db.query("INSERT INTO users(email) VALUES ('b@example.invalid') RETURNING id")).rows[0].id;
  const ws = (await db.query("INSERT INTO workspaces(name,owner_id) VALUES ('untrusted',$1) RETURNING id", [a])).rows[0].id;
  await db.query("INSERT INTO workspace_members(workspace_id,user_id,role) VALUES ($1,$2,'admin'),($1,$3,'viewer')", [ws,a,b]);
  await db.query("INSERT INTO user_memory(user_id,content,workspace_id) VALUES ($1,'shared injection',$2)", [a,ws]);
  await db.query("INSERT INTO scheduled_tasks(user_id,title,prompt,schedule,workspace_id,last_result,next_run_at) VALUES ($1,'task','prompt','every:1h',$2,'old private result',now())", [a,ws]);
  await migrate('012_workspace_consent.sql');
  t.mock.method(pool, 'query', async (...args) => { const r = await db.query(...args); return { ...r, rowCount: r.affectedRows ?? r.rows.length }; });
  assert.equal(await spaces.getRole(ws,a), 'admin');
  assert.equal(await spaces.getRole(ws,b), null);
  assert.deepEqual(await spaces.listWorkspaceIds(b), []);
  assert.deepEqual(await spaces.listForUser(b), []);
  assert.deepEqual(await listTasks(b), []);
  assert.deepEqual(await withMemory([{ role:'user', content:'hi' }], b), [{ role:'user', content:'hi' }]);
  const app = express().use(express.json()).use((req,_res,next) => { req.principal = {userId:req.headers['x-test-user']}; next(); }).use(workspaces);
  const post = (path,user,body) => request(app).post(path).set('x-test-user',user).send(body);
  assert.equal((await post(`/v1/workspace-invitations/${ws}`,a,{accept:true,userId:b})).status,404);
  const unknown = await post(`/v1/workspaces/${ws}/members`,a,{email:'unknown@example.invalid'});
  const known = await post(`/v1/workspaces/${ws}/members`,a,{email:'b@example.invalid'});
  assert.equal(known.status,202); assert.equal(unknown.status,202); assert.deepEqual(known.body,unknown.body);
  assert.equal((await spaces.listMembers(ws)).length,1); // no email-existence leak through pending members
  const cancel=await request(app).delete(`/v1/workspaces/${ws}/invitations`).set('x-test-user',a).send({email:'b@example.invalid'});
  assert.equal(cancel.status,204);assert.deepEqual(await spaces.listInvitations(b),[]);
  assert.equal((await post(`/v1/workspace-invitations/${ws}`,b,{accept:true})).status,404);
  await spaces.inviteMember(ws,'b@example.invalid','viewer');
  assert.equal((await post(`/v1/workspace-invitations/${ws}`,b,{accept:true})).status,204);
  assert.equal(await spaces.getRole(ws,b),'viewer');
  assert.deepEqual(await spaces.listWorkspaceIds(b),[ws]);
  assert.equal((await listTasks(b))[0].last_result,null); // old unsafe output stays hidden
  await spaces.inviteMember(ws,'b@example.invalid','admin');
  assert.equal(await spaces.getRole(ws,b),'viewer'); // re-invite cannot change accepted role
  assert.deepEqual(await withMemory([{role:'user',content:'hi'}],b),[{role:'user',content:'hi'}]);
  await spaces.removeMember(ws,b);
  await spaces.inviteMember(ws,'b@example.invalid','viewer');
  assert.equal((await post(`/v1/workspace-invitations/${ws}`,b,{accept:false})).status,204);
  assert.deepEqual(await spaces.listInvitations(b),[]);
});

test('OIDC requires verified email for linking; subject-only accounts remain separate', async t => {
  const db = new PGlite(); t.after(() => db.close());
  for (const name of ['001_init.sql','013_oidc_subject_accounts.sql']) await db.exec(readFileSync(new URL('../migrations/'+name,import.meta.url),'utf8'));
  const query = db.query.bind(db);
  const victim = (await query("INSERT INTO users(email) VALUES ('admin@example.invalid') RETURNING id")).rows[0].id;
  for (const email_verified of [false,undefined,'true']) {
    const user = await resolveOidcUser({sub:'attacker-'+String(email_verified),email:'admin@example.invalid',email_verified},query);
    assert.notEqual(user.userId,victim); assert.equal(user.email,null);
  }
  const linked = await resolveOidcUser({sub:'verified-owner',email:'admin@example.invalid',email_verified:true},query);
  assert.equal(linked.userId,victim);
  assert.equal((await resolveOidcUser({sub:'verified-owner',email:'elsewhere@example.invalid'},query)).userId,victim);
  const first = await resolveOidcUser({sub:'no-email-one'},query);
  const second = await resolveOidcUser({sub:'no-email-two'},query);
  assert.notEqual(first.userId,second.userId);
  const verified=await resolveOidcUser({sub:'no-email-one',email:'later@example.invalid',email_verified:true},query);
  assert.equal(verified.userId,first.userId);assert.equal(verified.email,'later@example.invalid');
  const conflicting=await resolveOidcUser({sub:'no-email-one',email:'admin@example.invalid',email_verified:true},query);
  assert.equal(conflicting.userId,first.userId);assert.equal(conflicting.email,'later@example.invalid');
});

test('voice ownership hides every foreign state, audio result and legacy ownerless job', async () => {
  const jobs = new Map();
  const api = voiceJobAccess({ add: async (name,data) => { const job={id:String(jobs.size+1),name,data,getState:async()=>job.state,state:'queued'}; jobs.set(job.id,job); return job; }, getJob:async id=>jobs.get(id) });
  const {id} = await api.add('tts',{owner:'user:forged',input:'private'},'user:a');
  const job=jobs.get(id);
  for (const state of ['queued','active','failed','completed']) {
    job.state=state; job.returnvalue={audio:'private'}; job.failedReason='private';
    assert.equal(await api.get(id,'user:b'),null);
    assert.ok(await api.get(id,'user:a'));
  }
  assert.equal(await api.get(id,'single-user'),null);
  delete job.data.owner;
  assert.equal(await api.get(id,'user:a'),null);
  assert.equal(await api.get('unknown','user:a'),null);
});

test('shared scheduled run propagates document scope; personal run retains its own context', async () => {
  let args;
  const runAgentImpl = async a => { args=a; return {content:'ok'}; };
  const options={runAgentImpl,checkQuotaImpl:async()=>({allowed:true}),recordUsageImpl:async()=>{},rateLimitImpl:async()=>({allowed:true})};
  await runScheduledTask({user_id:'creator',workspace_id:'task-workspace',prompt:'search'}, options);
  assert.deepEqual(args.documentScope,{workspaceId:'task-workspace'});
  await runScheduledTask({user_id:'creator',prompt:'search'}, options);
  assert.equal(args.documentScope,undefined);
});
