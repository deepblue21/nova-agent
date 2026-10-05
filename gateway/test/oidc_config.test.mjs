import {test} from 'node:test';
import assert from 'node:assert/strict';
import {configureNovaClient} from '../scripts/configure-oidc.mjs';

test('existing Keycloak client upgrade preserves unrelated attributes and updates the audience idempotently',async()=>{
  const writes=[];
  const fetchImpl=async(url,options)=>{
    assert.equal(options.headers.Authorization,'Bearer fixture-token');
    if(options.method!=='GET'){writes.push({url,body:JSON.parse(options.body)});return new Response(null,{status:204});}
    if(url.endsWith('?clientId=nova-web'))return Response.json([{id:'client-id',clientId:'nova-web'}]);
    if(url.endsWith('/protocol-mappers/models'))return Response.json([{id:'mapper-id',name:'nova-gateway-audience'}]);
    return Response.json({id:'client-id',clientId:'nova-web',attributes:{existing:'retained'},redirectUris:['http://localhost/*']});
  };
  await configureNovaClient({baseUrl:'https://auth.example',origin:'https://nova.example',token:'fixture-token',fetchImpl});
  assert.deepEqual(writes[0].body.redirectUris,['https://nova.example/']);assert.deepEqual(writes[0].body.webOrigins,['https://nova.example']);
  assert.equal(writes[0].body.attributes.existing,'retained');assert.equal(writes[0].body.attributes['pkce.code.challenge.method'],'S256');
  assert.match(writes[1].url,/mapper-id$/);assert.equal(writes[1].body.config['included.custom.audience'],'nova-gateway');
});
