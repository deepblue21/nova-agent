import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {PGlite} from '@electric-sql/pglite';
import {pool} from '../lib/db.mjs';
import {claimDue,markRun} from '../lib/scheduled_store.mjs';
import {runScheduledTask} from '../lib/scheduled_runner.mjs';
import {createBilling} from '../lib/billing.mjs';

async function database(t,names) {
  const db=new PGlite(); t.after(()=>db.close());
  for (const n of names) await db.exec(readFileSync(new URL('../migrations/'+n,import.meta.url),'utf8'));
  const user=(await db.query("INSERT INTO users(email) VALUES('job@example.invalid') RETURNING id")).rows[0].id;
  return {db,user};
}

test('scheduler claims are exclusive and stale completions cannot overwrite recovered runs',async t=>{
  const {db,user}=await database(t,['001_init.sql','004_scheduled_tasks.sql','014_scheduler_claims.sql']);
  await db.exec('ALTER TABLE scheduled_tasks ADD COLUMN workspace_id uuid; ALTER TABLE scheduled_tasks ADD COLUMN result_scope_version integer DEFAULT 0');
  const id=(await db.query("INSERT INTO scheduled_tasks(user_id,title,prompt,schedule,next_run_at) VALUES($1,'t','p','every:1h',now()-interval '1 minute') RETURNING id",[user])).rows[0].id;
  t.mock.method(pool,'query',db.query.bind(db));
  const claims=await Promise.all([claimDue(Date.now()),claimDue(Date.now())]);
  assert.equal(claims.filter(Boolean).length,1);
  const first=claims.find(Boolean);
  await db.query("UPDATE scheduled_tasks SET claim_until=now()-interval '1 minute' WHERE id=$1",[id]);
  const recovered=await claimDue(Date.now());
  assert.notEqual(recovered.claim_token,first.claim_token);
  await markRun(id,{status:'ok',result:'stale',nextRunAt:Date.now()+3600000,claimToken:first.claim_token});
  assert.equal((await db.query('SELECT last_result FROM scheduled_tasks WHERE id=$1',[id])).rows[0].last_result,null);
  await markRun(id,{status:'ok',result:'current',nextRunAt:Date.now()+3600000,claimToken:recovered.claim_token});
  assert.equal((await db.query('SELECT last_result FROM scheduled_tasks WHERE id=$1',[id])).rows[0].last_result,'current');
  assert.equal(await claimDue(Date.now()),null);
});

test('scheduled policies run before inference; both execution modes are metered',async()=>{
  const task={user_id:'u',model:'ollama/x',prompt:'p'};
  let calls=0,charges=[];
  const options={env:{},checkQuotaImpl:async()=>({allowed:true}),rateLimitImpl:async()=>({allowed:true}),
    recordUsageImpl:async x=>charges.push(x),runAgentImpl:async()=>{calls++;return {content:'a',usage:{in:10,out:20}};},
    providerClient:{chat:async()=>{calls++;return 'answer';}}};
  assert.equal((await runScheduledTask(task,{...options,env:{ALLOW_MODELS:'ollama/other'}})).status,'error');
  assert.equal((await runScheduledTask(task,{...options,checkQuotaImpl:async()=>({allowed:false})})).status,'error');
  assert.equal((await runScheduledTask(task,{...options,rateLimitImpl:async()=>({allowed:false})})).status,'error');
  assert.equal(calls,0);
  await runScheduledTask(task,options);
  await runScheduledTask({...task,agent:false},options);
  assert.equal(charges.length,2); assert.equal(charges[0].tokensOut,20); assert.equal(charges[0].route,'ollama/x');
});

test('billing retries an immutable batch after Stripe succeeds but acknowledgement fails',async t=>{
  const {db,user}=await database(t,['001_init.sql','002_billing.sql','015_billing_batches.sql']);
  await db.query("INSERT INTO billing_accounts(user_id,stripe_item_id) VALUES($1,'test-item')",[user]);
  const add=()=>db.query("INSERT INTO usage_events(user_id,model,cost_micros) VALUES($1,'test',10000)",[user]);
  await add();
  let failAck=true; const deliveries=new Map(); const calls=[];
  const client={subscriptionItems:{createUsageRecord:async(item,payload,{idempotencyKey})=>{
    calls.push({item,payload,idempotencyKey});
    if(deliveries.has(idempotencyKey)) assert.deepEqual(deliveries.get(idempotencyKey),payload);
    else deliveries.set(idempotencyKey,payload);
  }}};
  const flush=createBilling({client,query:db.query.bind(db),transaction:fn=>db.transaction(tx=>fn({query:async(sql,args)=>{
    if (failAck && sql.startsWith("UPDATE billing_batches SET state='reported'")) {failAck=false;throw new Error('ack failed');}
    return tx.query(sql,args);
  }}))});
  await assert.rejects(flush(),/ack failed/);
  await add(); // must not change the retry batch's membership or payload
  await Promise.all([flush(),flush()]);
  assert.equal(deliveries.size,2);
  assert.equal(calls.filter(c=>c.idempotencyKey===calls[0].idempotencyKey).length>=2,true);
  assert.equal(Number((await db.query('SELECT count(*) AS n FROM usage_events WHERE reported_at IS NULL')).rows[0].n),0);
  await add();
  const batch=(await db.query("INSERT INTO billing_batches(user_id,item_id,quantity,usage_timestamp,first_attempt_at) VALUES($1,'test-item',1,1,now()-interval '25 hours') RETURNING id",[user])).rows[0].id;
  await db.query('UPDATE usage_events SET billing_batch_id=$1 WHERE reported_at IS NULL',[batch]);
  const count=deliveries.size;
  assert.equal((await flush()).manualReview,1); assert.equal(deliveries.size,count);
});
