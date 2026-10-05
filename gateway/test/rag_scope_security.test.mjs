import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, readdirSync } from 'node:fs';
import { PGlite } from '@electric-sql/pglite';
import { vector } from '@electric-sql/pglite-pgvector';
import { pool } from '../lib/db.mjs';
import { search } from '../lib/rag.mjs';
import { runAgent } from '../lib/agent.mjs';

test('shared agent doc_search cannot see personal or other workspace documents', async t => {
  const db = new PGlite({extensions:{vector}}); t.after(()=>db.close());
  const dir=new URL('../migrations/',import.meta.url);
  for (const name of readdirSync(dir).filter(n=>n.endsWith('.sql')).sort()) await db.exec(readFileSync(new URL(name,dir),'utf8'));
  t.mock.method(pool,'query', async (...args)=>{const r=await db.query(...args);return {...r,rowCount:r.affectedRows??r.rows.length};});
  const user=(await db.query("INSERT INTO users(email) VALUES('creator@example.invalid') RETURNING id")).rows[0].id;
  const outsider=(await db.query("INSERT INTO users(email) VALUES('outsider@example.invalid') RETURNING id")).rows[0].id;
  const ws=(await db.query("INSERT INTO workspaces(name,owner_id) VALUES('task-space',$1) RETURNING id",[user])).rows[0].id;
  const other=(await db.query("INSERT INTO workspaces(name,owner_id) VALUES('other-space',$1) RETURNING id",[user])).rows[0].id;
  await db.query("INSERT INTO workspace_members(workspace_id,user_id,role,accepted_at) VALUES($1,$3,'admin',now()),($2,$3,'admin',now())",[ws,other,user]);
  const embedding=Array(768).fill(0); embedding[0]=1;
  for (const [title,workspace,owner] of [['personal',null,user],['allowed',ws,user],['other',other,user],['foreign',null,outsider]]) {
    const doc=(await db.query('INSERT INTO documents(user_id,title,workspace_id) VALUES($1,$2,$3) RETURNING id',[owner,title,workspace])).rows[0].id;
    await db.query('INSERT INTO doc_chunks(document_id,user_id,idx,content,embedding) VALUES($1,$2,0,$3,$4)',[doc,owner,title,JSON.stringify(embedding)]);
  }
  let turns=0, toolsText='';
  t.mock.method(globalThis,'fetch',async (url,options)=>{
    if (String(url).endsWith('/api/embeddings')) return Response.json({embedding});
    const body=JSON.parse(options.body);
    if (turns++===0) return Response.json({message:{role:'assistant',content:'',tool_calls:[{function:{name:'doc_search',arguments:{query:'private'}}}]}});
    toolsText=body.messages.filter(m=>m.role==='tool').map(m=>m.content).join('');
    return Response.json({message:{role:'assistant',content:'done'}});
  });
  const rows=await search(user,'query',5,undefined,{workspaceId:ws});
  assert.deepEqual(rows.map(r=>r.title),['allowed']);
  assert.deepEqual(await search(outsider,'query',5,undefined,{workspaceId:ws}),[]);
  await runAgent({ollamaBase:'http://test.invalid',model:'test',messages:[{role:'user',content:'summarize'}],userId:user,documentScope:{workspaceId:ws}});
  assert.match(toolsText,/allowed/); assert.doesNotMatch(toolsText,/personal|other|foreign/);
  const personal=await search(user,'query');
  assert.deepEqual(personal.map(r=>r.title).sort(),['allowed','other','personal']);
  await db.query('UPDATE workspace_members SET accepted_at=NULL WHERE workspace_id=$1',[ws]);
  assert.deepEqual(await search(user,'query',5,undefined,{workspaceId:ws}),[]);
});
