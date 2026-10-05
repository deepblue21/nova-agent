// Run all schema migrations once, serialized across replicas.
import './lib/env.mjs';
import {readdirSync, readFileSync} from 'node:fs';
import {pool} from './lib/db.mjs';
import {applyMigrations} from './lib/migrations.mjs';

async function main() {
  const dir = new URL('./migrations/', import.meta.url);
  const files = readdirSync(dir).filter(n => n.endsWith('.sql')).sort().map(name => ({name, sql:readFileSync(new URL(name,dir),'utf8')}));
  const client = await pool.connect();
  try { await applyMigrations(client,files,name => console.log('applied', name)); }
  finally { client.release(); await pool.end(); }
  console.log('migrations done');
}
main().catch(e => {console.error('migration failed:',e.message);process.exitCode=1;});
