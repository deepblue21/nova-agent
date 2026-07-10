#!/usr/bin/env node
// Production-readiness env check — run before exposing NOVA beyond localhost.
// Reads process.env (source your prod .env first). Exit 1 if a hard check fails.
//   node scripts/prod-check.mjs
import { evaluate, hardFailures } from "../gateway/lib/prodcheck.mjs";

const rows = evaluate(process.env);
const hard = hardFailures(rows);

console.log("NOVA production-readiness:\n");
for (const c of rows) {
  console.log(`  ${c.pass ? "✓" : c.hard ? "✗" : "!"} ${c.name.padEnd(22)} ${c.detail}`);
}
console.log(hard
  ? `\n✗ ${hard} required check(s) failed — see SECURITY.md`
  : "\n✓ required checks passed (review ! warnings before going public)");
process.exit(hard ? 1 : 0);
