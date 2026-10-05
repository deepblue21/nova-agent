import { readFileSync } from 'node:fs';
import { parseEnv } from 'node:util';

export function applyEnv(text, target = process.env) {
  for (const [key,value] of Object.entries(parseEnv(text))) {
    if (!Object.hasOwn(target,key)) target[key]=value;
  }
}

// Imported before other gateway modules so their environment constants are correct.
try { applyEnv(readFileSync(new URL('../.env',import.meta.url),'utf8')); }
catch (error) { if (error.code !== 'ENOENT') throw error; }
