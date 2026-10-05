import { test } from 'node:test';
import assert from 'node:assert/strict';
import { fork } from 'node:child_process';
import { once } from 'node:events';
import { priceFor } from '../lib/pricing.mjs';
import { buildCatalog } from '../lib/model_catalog.mjs';

async function gateway(t, env = {}) {
  const child = fork(new URL('../test-support/request-probe.mjs', import.meta.url), [], {
    silent: true,
    env: { ...process.env, NODE_ENV: 'test', PORT: '0', GATEWAY_BIND: '127.0.0.1',
      DATABASE_URL: '', MULTI_USER: '0', GATEWAY_TOKEN: '', RATE_LIMIT_MAX: '0',
      VOICE_QUEUE_ENABLED: '0', STRIPE_SECRET_KEY: '', SCHEDULER_ENABLED: '0',
      MOBILE_WORKER_ENABLED: '0', OLLAMA_URL: 'http://127.0.0.1:1',
      OPENAI_API_KEY: 'test-only-not-a-secret', ALLOW_MODELS: '', AUTO_AGENT_ENABLED: '0', ...env },
  });
  child.stdout.resume();
  child.stderr.resume();
  t.after(async () => {
    if (child.exitCode === null && child.signalCode === null) {
      const exited = once(child, 'exit'); child.kill(); await exited;
    }
  });
  const [address] = await once(child, 'message', { signal: AbortSignal.timeout(15000) });
  const base = `http://127.0.0.1:${address.port}`;
  return {
    base,
    post: (body, path = '/v1/chat/completions') => fetch(base + path, {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body),
      signal: AbortSignal.timeout(5000),
    }),
  };
}

test('malformed chat returns 400 and the same process still serves valid requests', async t => {
  const g = await gateway(t);
  const valid = { model: 'openai/gpt-4o-mini', messages: [{ role: 'user', content: 'hello' }] };
  const attacks = [
    ...[1, {}, [], false].map(model => ({ ...valid, model })),
    ...[{}, [null], [{ type: 'text', text: {} }], [{ type: 'image_url', image_url: { url: 1 } }]]
      .map(content => ({ ...valid, messages: [{ role: 'user', content }] })),
  ];
  for (const body of attacks) {
    assert.equal((await g.post(body)).status, 400, JSON.stringify(body));
    assert.equal((await fetch(g.base + '/health')).status, 200);
  }
  const control = await g.post(valid);
  assert.equal(control.status, 200);
  assert.equal((await control.json()).choices[0].message.content, 'hello');
  const image = await g.post({ ...valid, messages: [{ role: 'user', content: [
    { type: 'text', text: 'describe' }, { type: 'image_url', image_url: { url: 'data:image/png;base64,AQID' } },
  ] }] });
  assert.equal(image.status, 200);
});

test('unknown paid models fail closed in chat and eval before dispatch', async t => {
  const g = await gateway(t);
  const res = await g.post({ model: 'openai/unpriced-model', messages: [{ role: 'user', content: 'hello' }] });
  assert.equal(res.status, 400);
  const ev = await g.post({ prompt: 'hello', models: ['openai/unpriced-model', 'openai/gpt-4o-mini'] }, '/v1/eval');
  const { results } = await ev.json();
  assert.equal(results[0].ok, false);
  assert.equal(results[1].ok, true);
  assert.ok(results[1].cost_micros > 0);
});

test('unknown route has no implicit zero-dollar price', () => {
  assert.throws(() => priceFor('openai/unpriced-model'), /price/i);
  assert.deepEqual(priceFor('ollama/qwen3:14b'), [0, 0]);
});

test('agent/team timeout produces an explicit JSON or streaming error, never an empty success',async t=>{
  const g=await gateway(t,{TEST_SLOW_OLLAMA:'1',AGENT_TIMEOUT_MS:'40',TEAM_TIMEOUT_MS:'40'});
  for(const team of [false,true])for(const stream of [false,true]){
    const r=await g.post({model:'ollama/test',messages:[{role:'user',content:'task'}],agent:true,team,stream});
    if(stream && r.status===200){const body=await r.text();assert.match(body,/zaman aşımına/);assert.match(body,/DONE/);}
    else {assert.equal(r.status,504);assert.match((await r.json()).error,/zaman aşımına/);}
  }
});

test('catalog availability agrees with pricing and configured prices remain usable', () => {
  const keys = { openai:'test', anthropic:'test', gemini:'test' };
  const env = { MODEL_PRICES_JSON: JSON.stringify({'openai/gpt-5.6':[1,2]}) };
  const catalog = buildCatalog({keys,env});
  for (const m of catalog.data.filter(m => keys[m.provider])) {
    if (m.available) assert.doesNotThrow(() => priceFor(m.id,env));
    else assert.match(m.reason,/fiyat/);
  }
  assert.equal(catalog.data.find(m=>m.id==='openai/gpt-5.6').available,true);
  assert.deepEqual(priceFor('openai/gpt-5.6',env),[1,2]);
  assert.throws(()=>priceFor('openai/gpt-5.6',{MODEL_PRICES_JSON:'{"openai/gpt-5.6":[0,0]}'}),/price/);
});
