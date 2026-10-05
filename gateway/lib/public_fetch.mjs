import {lookup} from 'node:dns/promises';
import net from 'node:net';
import http from 'node:http';
import https from 'node:https';

const failure=(message,status=400)=>Object.assign(new Error(message),{status});
export function isPrivateAddress(input) {
  const address=String(input||'').replace(/^\[|\]$/g,'').toLowerCase();
  if(net.isIP(address)===4) {
    const [a,b]=address.split('.').map(Number);
    return a===0||a===10||a===127||a>=224||(a===100&&b>=64&&b<=127)||
      (a===169&&b===254)||(a===172&&b>=16&&b<=31)||(a===192&&(b===0||b===168))||(a===198&&(b===18||b===19));
  }
  if(net.isIP(address)===6) {
    // URL canonicalization normalizes dotted IPv4-mapped forms into hex words.
    const canonical=new URL('http://['+address+']/').hostname.slice(1,-1);
    const mapped=/^::ffff:([0-9a-f]+):([0-9a-f]+)$/.exec(canonical);
    if(mapped) {
      const hi=parseInt(mapped[1],16),lo=parseInt(mapped[2],16);
      return isPrivateAddress([hi>>8,hi&255,lo>>8,lo&255].join('.'));
    }
    // Only globally routable unicast space; rejects local, multicast, NAT64 and unspecified.
    return !/^[23][0-9a-f]{3}:/.test(canonical);
  }
  return false;
}

export async function publicTarget(url,{allowPrivate=false,resolveHost=host=>lookup(host,{all:true})}={}) {
  const u=new URL(url);
  if(!['http:','https:'].includes(u.protocol)) throw failure('URL must use http or https');
  if(u.username||u.password) throw failure('URL credentials are not allowed');
  const host=u.hostname.replace(/^\[|\]$/g,'').replace(/\.$/,'');
  if(!allowPrivate && (host==='localhost'||host.endsWith('.localhost')||isPrivateAddress(host))) throw failure('private address blocked');
  const resolved=net.isIP(host)?[{address:host,family:net.isIP(host)}]:await resolveHost(host);
  const addresses=(resolved||[]).map(a=>typeof a==='string'?{address:a,family:net.isIP(a)}:{address:a.address,family:net.isIP(a.address)});
  if(!addresses.length||addresses.some(a=>!a.family||(!allowPrivate&&isPrivateAddress(a.address)))) throw failure('private address or invalid DNS result blocked');
  return {url:u,addresses};
}

async function readLimited(response,maxBytes) {
  const len=Number(response.headers.get('content-length'));
  if(Number.isFinite(len)&&len>maxBytes) {await response.body?.cancel?.();throw failure('response too large',413);}
  const encoding=response.headers.get('content-encoding');
  if(encoding&&encoding!=='identity') {await response.body?.cancel?.();throw failure('compressed response is not supported',415);}
  if(!response.body) return Buffer.alloc(0);
  const reader=response.body.getReader();const parts=[];let size=0;
  try {
    for(;;) {
      const {done,value}=await reader.read();if(done)break;
      size+=value.byteLength;if(size>maxBytes)throw failure('response too large',413);
      parts.push(Buffer.from(value));
    }
    return Buffer.concat(parts,size);
  } catch(e) {await reader.cancel().catch(()=>{});throw e;}
  finally {reader.releaseLock();}
}

export async function fetchPublicResource(url,{maxBytes=2000000,signal,allowPrivate=false,resolveHost,headers={},fetchFn,requestImpl}={}) {
  if(!Number.isSafeInteger(maxBytes)||maxBytes<1||maxBytes>50*1024*1024)throw failure('invalid response byte limit');
  const target=await publicTarget(url,{allowPrivate,resolveHost});
  const deadline=AbortSignal.timeout(15000);
  const effectiveSignal=signal?AbortSignal.any([signal,deadline]):deadline;
  const requestHeaders={...headers,'Accept-Encoding':'identity'};
  if(fetchFn) { // trusted test transport; production always uses pinned native lookup below
    const r=await fetchFn(target.url.toString(),{signal:effectiveSignal,redirect:'manual',headers:requestHeaders});
    if(r.status>=300&&r.status<400) {await r.body?.cancel?.();return {status:r.status,headers:r.headers,buffer:Buffer.alloc(0)};}
    return {status:r.status,headers:r.headers,buffer:await readLimited(r,maxBytes)};
  }
  const makeRequest=requestImpl||((u,options,cb)=>(u.protocol==='https:'?https:http).request(u,options,cb));
  return new Promise((resolve,reject)=>{
    const req=makeRequest(target.url,{
      method:'GET',headers:requestHeaders,signal:effectiveSignal,
      lookup:(_host,options,callback)=>options?.all?callback(null,target.addresses):callback(null,target.addresses[0].address,target.addresses[0].family),
    },res=>{
      const headers=new Headers();
      for(const [key,value] of Object.entries(res.headers)) if(value!=null)headers.set(key,Array.isArray(value)?value.join(','):String(value));
      const status=res.statusCode;
      if(status>=300&&status<400){res.destroy();resolve({status,headers,buffer:Buffer.alloc(0)});return;}
      const len=Number(headers.get('content-length')),encoding=headers.get('content-encoding');
      if((Number.isFinite(len)&&len>maxBytes)||(encoding&&encoding!=='identity')) {
        res.destroy();reject(failure(encoding&&encoding!=='identity'?'compressed response is not supported':'response too large',encoding&&encoding!=='identity'?415:413));return;
      }
      (async()=>{
        let size=0;const parts=[];
        for await(const chunk of res){size+=chunk.length;if(size>maxBytes)throw failure('response too large',413);parts.push(chunk);}
        return {status,headers,buffer:Buffer.concat(parts,size)};
      })().then(resolve,reject);
    });
    req.once('error',reject);req.end();
  });
}
