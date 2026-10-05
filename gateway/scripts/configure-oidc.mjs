// Idempotent nova-web client upgrade for existing Keycloak realms.
// Administrator token is supplied through the environment, never command args.
import {pathToFileURL} from 'node:url';

function trustedUrl(value) {
  const u=new URL(value);
  if (u.username||u.password||u.search||u.hash||!['https:','http:'].includes(u.protocol)) throw Error('invalid configuration URL');
  if(u.protocol==='http:'&&!['localhost','127.0.0.1','[::1]'].includes(u.hostname))throw Error('HTTPS required outside loopback');
  return u;
}

export async function configureNovaClient({baseUrl,origin,token,realm='nova',fetchImpl=fetch}) {
  const base=trustedUrl(baseUrl).href.replace(/\/$/,'');
  const publicUrl=trustedUrl(origin);
  if(publicUrl.pathname!=='/')throw Error('NOVA_PUBLIC_ORIGIN must be an origin without a path');
  if(!token)throw Error('KEYCLOAK_ADMIN_TOKEN required');
  const api=base+'/admin/realms/'+encodeURIComponent(realm);
  const req=async(path,method='GET',body)=>{
    const r=await fetchImpl(api+path,{method,redirect:'error',signal:AbortSignal.timeout(15000),headers:{Authorization:'Bearer '+token,'Content-Type':'application/json'},...(body?{body:JSON.stringify(body)}:{})});
    if(!r.ok)throw Error('Keycloak configuration request failed ('+r.status+')');
    return r.status===204||r.status===201?null:r.json();
  };
  const clients=await req('/clients?clientId=nova-web');
  const client=clients.find(c=>c.clientId==='nova-web');
  if(!client?.id)throw Error('nova-web client missing; import the nova realm first');
  const path='/clients/'+encodeURIComponent(client.id);
  const current=await req(path);
  await req(path,'PUT',{...current,redirectUris:[publicUrl.origin+'/'],webOrigins:[publicUrl.origin],attributes:{...current.attributes,'pkce.code.challenge.method':'S256'}});
  const mapper={name:'nova-gateway-audience',protocol:'openid-connect',protocolMapper:'oidc-audience-mapper',config:{'included.custom.audience':'nova-gateway','access.token.claim':'true','id.token.claim':'false'}};
  const existing=(await req(path+'/protocol-mappers/models')).find(m=>m.name===mapper.name);
  if(existing)await req(path+'/protocol-mappers/models/'+encodeURIComponent(existing.id),'PUT',{...mapper,id:existing.id});
  else await req(path+'/protocol-mappers/models','POST',mapper);
  return {origin:publicUrl.origin,audience:'nova-gateway'};
}

if(process.argv[1]&&import.meta.url===pathToFileURL(process.argv[1]).href) {
  configureNovaClient({baseUrl:process.env.KEYCLOAK_ADMIN_URL,origin:process.env.NOVA_PUBLIC_ORIGIN,token:process.env.KEYCLOAK_ADMIN_TOKEN,realm:process.env.OIDC_REALM||'nova'})
    .then(r=>console.log('nova-web configured:',r.origin,'audience:',r.audience))
    .catch(e=>{console.error(e.message);process.exitCode=1;});
}
