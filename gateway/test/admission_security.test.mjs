import {test} from 'node:test';
import assert from 'node:assert/strict';
import express from 'express';
import request from 'supertest';
import {PGlite} from '@electric-sql/pglite';
import {readFileSync} from 'node:fs';
import {scopeAllows,userAdmission} from '../lib/admission.mjs';
import {metricsMiddleware,registry} from '../lib/metrics.mjs';
import {getMcpTools,_resetCache} from '../lib/mcp.mjs';
import {rateLimit,RATE_SCRIPT} from '../lib/cache.mjs';
import {applyEnv} from '../lib/env.mjs';
import {createMediaStore} from '../lib/media_store.mjs';
import {cleanupExpiredMedia} from '../lib/storage.mjs';
import {applyMigrations} from '../lib/migrations.mjs';

test('scoped API keys cannot spend through sibling endpoints; admission runs before their handlers',async()=>{
  for (const path of ['/v1/knowledge','/v1/media','/stt','/tts','/v1/voice/jobs','/v1/mcp/tools','/v1/eval','/v1/admin/users/u/api-keys'])
    assert.equal(scopeAllows({via:'api_key',scopes:['read']},'POST',path),false,path);
  assert.equal(scopeAllows({via:'api_key',scopes:['chat']},'GET','/v1/models'),true);
  assert.equal(scopeAllows({via:'api_key',scopes:['unknown']},'POST','/v1/chat/completions'),false);
  assert.equal(scopeAllows({via:'api_key',scopes:[]},'POST','/v1/chat/completions'),true);
  let spent=0;
  const app=express().use((req,_res,next)=>{req.principal={userId:'u',via:'jwt'};next();})
    .use(userAdmission({max:1,windowMs:60000,limit:async()=>({allowed:false,retryAfterMs:1200})}))
    .use((_req,res)=>{spent++;res.json({ok:true});});
  for(const path of ['/v1/knowledge','/v1/media','/stt','/tts','/v1/voice/jobs','/v1/mcp/tools']){
    const r=await request(app).post(path);assert.equal(r.status,429);assert.equal(r.headers['retry-after'],'2');
  }
  assert.equal(spent,0);
});

test('unknown unauthenticated paths collapse into one metric label',async()=>{
  const app=express().use(metricsMiddleware()).use((_req,res)=>res.status(401).end());
  for(let i=0;i<40;i++) await request(app).get('/attacker-path-'+i);
  const metric=(await registry.getMetricsAsJSON()).find(m=>m.name==='nova_http_requests_total');
  const paths=new Set(metric.values.map(v=>v.labels.route));
  assert.deepEqual([...paths],['unmatched']);
});

test('MCP cached admin tools cannot be discovered or dispatched by another tenant',async t=>{
  const saved={...process.env};t.after(()=>{for(const key of ['DATABASE_URL','MULTI_USER','ADMIN_USER_IDS','MCP_SERVERS']) saved[key]===undefined?delete process.env[key]:process.env[key]=saved[key];_resetCache();});
  Object.assign(process.env,{DATABASE_URL:'postgres://fixture',MULTI_USER:'1',ADMIN_USER_IDS:'admin',MCP_SERVERS:'private=https://mcp.example'});
  _resetCache();let calls=0;
  t.mock.method(globalThis,'fetch',async(_url,opts)=>{
    calls++;const method=JSON.parse(opts.body).method;
    return Response.json({result:method==='tools/list'?{tools:[{name:'read',description:'private'}]}:{content:[{type:'text',text:'ok'}]}});
  });
  const admin=await getMcpTools(undefined,Date.now(),{userId:'admin'});assert.equal(admin.specs.length,1);
  const before=calls;
  assert.deepEqual((await getMcpTools(undefined,Date.now(),{userId:'other'})).specs,[]);
  assert.equal((await admin.dispatch('mcp__private__read',{}, {userId:'other'})).ok,false);assert.equal(calls,before);
  assert.equal((await admin.dispatch('mcp__private__read',{}, {userId:'admin'})).ok,true);
});

test('rate limiter uses one atomic script and remaining bucket TTL',async()=>{
  const result=await rateLimit('user',1,60000,{now:59000,client:{eval:async(script,keys,key,ttl)=>{
    assert.equal(script,RATE_SCRIPT);assert.equal(keys,1);assert.equal(ttl,1000);assert.match(script,/INCR/);assert.match(script,/PEXPIRE/);return [2,875];
  }}});
  assert.equal(result.allowed,false);assert.equal(result.retryAfterMs,875);
});

test('dotenv comments and quoted values preserve operator environment overrides',()=>{
  const target={ALREADY:'override'};
  applyEnv('MEMORY_ENABLED=0  # comment\nFETCH_TOOL_ENABLED=1 # on\nQUOTED="a # b"\nALREADY=file',target);
  assert.deepEqual(target,{ALREADY:'override',MEMORY_ENABLED:'0',FETCH_TOOL_ENABLED:'1',QUOTED:'a # b'});
});

test('media reservations bound storage, enforce ownership and only release after deletion',async t=>{
  const db=new PGlite();t.after(()=>db.close());
  for(const n of ['001_init.sql','016_media_retention.sql'])await db.exec(readFileSync(new URL('../migrations/'+n,import.meta.url),'utf8'));
  const user=(await db.query("INSERT INTO users(email) VALUES('media@example.invalid') RETURNING id")).rows[0].id;
  const foreign=(await db.query("INSERT INTO users(email) VALUES('foreign@example.invalid') RETURNING id")).rows[0].id;
  const store=createMediaStore({query:db.query.bind(db),transaction:fn=>db.transaction(tx=>fn({query:tx.query.bind(tx)}))});
  await store.reserve(user,'first',6,{quotaBytes:10,retentionDays:1});
  await assert.rejects(()=>store.reserve(user,'second',6,{quotaBytes:10,retentionDays:1}),e=>e.status===413);
  assert.equal(await store.get(foreign,'first'),undefined);
  await db.exec("UPDATE media_objects SET expires_at=now()-interval '1 day'");
  await assert.rejects(()=>cleanupExpiredMedia({store,client:{send:async()=>{throw Error('storage down');}}}),/storage down/);
  assert.ok(await store.get(user,'first'));
  assert.equal(await cleanupExpiredMedia({store,client:{send:async command=>assert.equal(command.input.Key,'first')}}),1);
  await store.reserve(user,'second',6,{quotaBytes:10,retentionDays:1});
});

test('migration lock covers schema creation and is released on success and failure',async t=>{
  const db=new PGlite();t.after(()=>db.close());const calls=[];
  const client={query:async(...args)=>{calls.push(args[0]);return db.query(...args);}};
  await applyMigrations(client,[{name:'001',sql:'CREATE TABLE example(id int)'}]);
  await applyMigrations(client,[{name:'001',sql:'CREATE TABLE example(id int)'}]);
  await assert.rejects(()=>applyMigrations(client,[{name:'002',sql:'INVALID SQL'}]));
  assert.match(calls[0],/pg_advisory_lock/);assert.match(calls.at(-1),/pg_advisory_unlock/);
  assert.equal((await db.query('SELECT count(*) FROM schema_migrations')).rows[0].count,1);
});
