import {test} from 'node:test';
import assert from 'node:assert/strict';
import {EventEmitter} from 'node:events';
import {Readable} from 'node:stream';
import {fetchPublicResource,isPrivateAddress} from '../lib/public_fetch.mjs';
import {resolveImageInputs} from '../lib/image_inputs.mjs';
import {runComputeJob} from '../lib/compute_jobs.mjs';
import {runJavaScriptSandbox} from '../lib/code_sandbox.mjs';
import {normalizeKnowledgeInput} from '../lib/doc_extract.mjs';
import {needsLiveData,guessLocation,runAgent} from '../lib/agent.mjs';
import {needsLiveData as webIntent} from '../../web/src/lib/live_intent.mjs';
import {runTool} from '../lib/tools.mjs';
import {providerPathSegment} from '../lib/providers.mjs';
import {nextRunAt} from '../lib/scheduler.mjs';
import {runTeam} from '../lib/multiagent.mjs';
import JSZip from 'jszip';

test('public fetch pins the one validated DNS resolution into the transport',async()=>{
  let resolutions=0;
  const out=await fetchPublicResource('https://public.example/image',{
    resolveHost:async()=>{resolutions++;return [{address:resolutions===1?'93.184.216.34':'127.0.0.1'}];},
    requestImpl:(url,options,cb)=>{
      assert.equal(url.hostname,'public.example');
      options.lookup('public.example',{},(err,ip,family)=>{assert.equal(err,null);assert.equal(ip,'93.184.216.34');assert.equal(family,4);});
      const req=new EventEmitter();req.end=()=>{const res=Readable.from([Buffer.from('ok')]);res.statusCode=200;res.headers={};cb(res);};return req;
    },
  });
  assert.equal(resolutions,1);assert.equal(out.buffer.toString(),'ok');
  for(const ip of ['127.0.0.1','::1','::ffff:127.0.0.1','::ffff:7f00:1','fc00::1','fe80::1']) assert.equal(isPrivateAddress(ip),true,ip);
  assert.equal(isPrivateAddress('2606:4700:4700::1111'),false);
});

test('public fetch stops oversized bodies; image redirects revalidate private targets',async()=>{
  let cancelled=false;
  const options={resolveHost:async()=>['93.184.216.34'],maxBytes:3,fetchFn:async()=>new Response(new ReadableStream({pull(c){c.enqueue(new Uint8Array(4));},cancel(){cancelled=true;}}))};
  await assert.rejects(()=>fetchPublicResource('https://public.example',options),e=>e.status===413);
  assert.equal(cancelled,true);
  let calls=0;
  await assert.rejects(()=>resolveImageInputs([{content:[{type:'image_url',image_url:{url:'https://public.example/image'}}]}],{remoteEnabled:true,maxBytes:10,maxRedirects:2},{
    resolveHost:async()=>['93.184.216.34'],fetchFn:async()=>{calls++;return new Response(null,{status:302,headers:{location:'http://[::ffff:7f00:1]/secret'}});},
  }),/private/);
  assert.equal(calls,1);
});

test('worker execution leaves main-loop timers responsive and releases capacity after timeout',async()=>{
  let beats=0;const timer=setInterval(()=>beats++,5);
  try {await assert.rejects(()=>runJavaScriptSandbox({code:'while(true){}',timeout_ms:200}),/interrupt|sandbox/);}
  finally {clearInterval(timer);}
  assert.ok(beats>=3,'main event loop must run while QuickJS is busy');
  const a=runComputeJob('code',{code:'while(true){}'},30),b=runComputeJob('code',{code:'while(true){}'},30);
  const settled=Promise.allSettled([a,b]);
  await assert.rejects(()=>runComputeJob('code',{code:'return 1'}),e=>e.status===503);
  assert.ok((await settled).every(r=>r.status==='rejected'));
  assert.equal((await runJavaScriptSandbox({code:'return 6*7'})).result,'42');
});

test('DOCX decompression limits reject inflated and falsified archive sizes',async()=>{
  const zip=new JSZip();zip.file('word/document.xml','x'.repeat(33*1024*1024));
  const buf=await zip.generateAsync({type:'nodebuffer',compression:'DEFLATE'});
  const run=b=>normalizeKnowledgeInput({file:{name:'bomb.docx',b64:b.toString('base64')}});
  await assert.rejects(()=>run(buf),e=>e.status===422);
  const forged=Buffer.from(buf);
  for(let i=0;i<forged.length-46;i++)if(forged.readUInt32LE(i)===0x02014b50)forged.writeUInt32LE(1,i+24);
  await assert.rejects(()=>run(forged),e=>e.status===422);
});

test('generic time/link words never force egress; Turkish locations and web/gateway intent agree',async t=>{
  for(const q of ['Şimdi bana şiir yaz','Bugün bir plan yap','current değişkenini açıkla','link listesini sırala','Yağmur hakkında hikaye yaz']) {
    assert.equal(needsLiveData(q),false,q);assert.equal(webIntent(q),false,q);
  }
  for(const city of ['İstanbul','İzmir','Çanakkale','Çorum','Şanlıurfa','Ağrı','Elazığ','Tekirdağ','Muş']) {
    const q=city.toLocaleUpperCase('tr-TR')+' hava durumu';assert.equal(guessLocation(q),city+', Türkiye');
    assert.equal(needsLiveData(q),true);assert.equal(webIntent(q),true);
  }
  assert.equal(guessLocation('Hava nasıl?'),'');
  let calls=0;
  t.mock.method(globalThis,'fetch',async url=>{calls++;assert.match(String(url),/\/api\/chat$/);return Response.json({message:{content:'şiir'}});});
  const r=await runAgent({ollamaBase:'http://model.invalid',model:'x',messages:[{role:'user',content:'Şimdi bana şiir yaz'}]});
  assert.equal(r.content,'şiir');assert.equal(calls,1);
});

test('provider URL segments reject traversal and equivalent encodings',()=>{
  for(const s of ['../secrets','..','a/b','%2e%2e','a?key=x','a#x','a\\b']) assert.throws(()=>providerPathSegment(s));
  assert.equal(providerPathSegment('gemini-3.1-pro-preview'),'gemini-3.1-pro-preview');
});

test('weather uses the forecast date array and does not silently replace unsupported dates',async t=>{
  t.mock.method(globalThis,'fetch',async url=>String(url).includes('geocoding')?Response.json({results:[{name:'İstanbul',latitude:41,longitude:29}]}):Response.json({daily:{time:['2026-10-04','2026-10-05']}}));
  const result=await runTool('weather_forecast',{location:'İstanbul',date:'2099-01-01'},{});
  assert.match(result.text,/desteklenmiyor/);assert.doesNotMatch(result.text,/yarın/);
});

test('daily schedules use explicit zones and survive DST gaps and repeated hours',()=>{
  assert.equal(nextRunAt('daily:09:00@Etc/GMT-3',Date.parse('2026-10-04T00:00:00Z')),Date.parse('2026-10-04T06:00:00Z'));
  assert.equal(nextRunAt('daily:09:00@Europe/Istanbul',Date.parse('2026-10-04T00:00:00Z')),Date.parse('2026-10-04T06:00:00Z'));
  assert.equal(nextRunAt('daily:02:30@America/New_York',Date.parse('2026-03-08T06:00:00Z')),Date.parse('2026-03-09T06:30:00Z'));
  assert.equal(nextRunAt('daily:01:30@America/New_York',Date.parse('2026-11-01T05:45:00Z')),Date.parse('2026-11-01T06:30:00Z'));
});

test('team sources and citations are renumbered before synthesis',async()=>{
  const r=await runTeam({task:'x',subtasks:[{prompt:'a'},{prompt:'b'}],runOne:async p=>({content:p+' [1]',sources:[{n:1,url:'https://example/'+p}]}),
    synthesize:async prompt=>{assert.match(prompt,/a \[1\]/);assert.match(prompt,/b \[2\]/);return {content:'ok'};}});
  assert.deepEqual(r.sources.map(s=>s.n),[1,2]);
});

test('synthesis cannot open a new tool search or restart source numbering',async t=>{
  let calls=0;
  t.mock.method(globalThis,'fetch',async(_url,options)=>{
    calls++;const body=JSON.parse(options.body);assert.equal(body.tools,undefined);
    return Response.json({message:{content:'Birinci [1], ikinci [2]',tool_calls:[{function:{name:'web_search',arguments:{query:'new'}}}]}});
  });
  const result=await runAgent({ollamaBase:'http://model.invalid',model:'x',toolsEnabled:false,messages:[{role:'user',content:'Bugünün haberlerini sentezle [1] [2]'}]});
  assert.equal(calls,1);assert.deepEqual(result.sources,[]);assert.deepEqual(result.toolsUsed,[]);assert.match(result.content,/\[2\]/);
});
