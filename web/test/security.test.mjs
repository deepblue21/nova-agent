import {test} from 'node:test';
import assert from 'node:assert/strict';
import {redactStoredSecrets,STATE_KEY,AUTH_KEY,store} from '../src/lib/store.mjs';
import {configureOidc,loadOidc,OIDC} from '../src/lib/oidc.mjs';
import {streamChat} from '../src/lib/stream.mjs';
import {safeLinkHref} from '../src/lib/format.mjs';

test('credentials are removed from persisted settings, including legacy fallback copies',async t=>{
  const input=JSON.stringify({settings:{providers:{gateway:{apiKey:'gateway-secret',baseUrl:'https://gateway.example'},anthropic:{apiKey:'provider-secret'}},accent:'ocean'},convs:[{id:'history'}]});
  const copy=JSON.parse(redactStoredSecrets(STATE_KEY,input));
  assert.equal(copy.settings.providers.gateway.apiKey,'');assert.equal(copy.convs[0].id,'history');
  assert.equal(redactStoredSecrets(AUTH_KEY,'private-refresh-token'),'');
  const local=new Map([[STATE_KEY,input],[AUTH_KEY,'old-token']]),bridge=new Map(local);
  globalThis.localStorage={getItem:k=>local.get(k),setItem:(k,v)=>local.set(k,v)};
  globalThis.window={storage:{get:async k=>({value:bridge.get(k)}),set:async(k,v)=>bridge.set(k,v)}};
  t.after(()=>{delete globalThis.localStorage;delete globalThis.window;});
  assert.equal((await store.get(STATE_KEY)).includes('secret'),false);
  assert.equal(await store.get(AUTH_KEY),null);
  for(const copies of [local,bridge]){assert.equal(copies.get(STATE_KEY).includes('secret'),false);assert.equal(copies.get(AUTH_KEY),'');}
  await store.set(AUTH_KEY,'new-session');assert.equal(await store.get(AUTH_KEY),'new-session');assert.equal(local.get(AUTH_KEY),'');
});

test('OIDC uses public gateway configuration and refuses remote cleartext issuers',async t=>{
  assert.throws(()=>configureOidc({issuer:'http://auth.example/realms/nova',clientId:'nova-web'}),/HTTPS/);
  assert.throws(()=>configureOidc({issuer:'https://user:pass@auth.example/realms/nova',clientId:'nova-web'}));
  let url;
  t.mock.method(globalThis,'fetch',async u=>{url=u;return Response.json({oidc:{issuer:'https://auth.example/realms/nova',clientId:'nova-web'}});});
  await loadOidc('https://nova.example/v1');assert.equal(url,'https://nova.example/v1/config');assert.equal(OIDC.issuer,'https://auth.example/realms/nova');
});

test('keyless Anthropic fails before network access; source links reject executable schemes',async t=>{
  t.mock.method(globalThis,'fetch',()=>{throw Error('unexpected network call');});
  await assert.rejects(()=>streamChat({prov:{kind:'anthropic'},history:[],onToken:()=>{}}),/anahtar/);
  for(const href of ['javascript:alert(1)','java\nscript:alert(1)','data:text/html,<script>','vbscript:test'])assert.equal(safeLinkHref(href),'');
  assert.equal(safeLinkHref('https://example.com/source'),'https://example.com/source');
});
