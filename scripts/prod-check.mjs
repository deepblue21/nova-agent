#!/usr/bin/env node
// Production-readiness env check — run before exposing NOVA beyond localhost.
// Reads process.env (source your prod .env first). Exit 1 if a hard check fails.
//   node scripts/prod-check.mjs

const env = process.env;
const rows = [];
const add = (name, pass, detail, hard = true) => rows.push({ name, pass: !!pass, detail, hard });

const multiUser = env.MULTI_USER === "on" || !!env.DATABASE_URL;
add("auth", multiUser || !!env.GATEWAY_TOKEN, "GATEWAY_TOKEN set or multi-user (DATABASE_URL)");
add("NODE_ENV", env.NODE_ENV === "production", "NODE_ENV=production");
add("CORS", env.ALLOW_ORIGINS && env.ALLOW_ORIGINS !== "*", "ALLOW_ORIGINS is a fixed allowlist (not *)");
add("ALLOW_MODELS", !!env.ALLOW_MODELS, "model allowlist set (cost/exposure control)", false);
add("TRUST_PROXY", env.TRUST_PROXY === "1", "TRUST_PROXY=1 (behind a TLS reverse proxy)", false);
add("HEALTH_DETAILS", env.HEALTH_DETAILS_ENABLED !== "1", "HEALTH_DETAILS_ENABLED != 1 in prod", false);
add("rate-limit", env.RATE_MAX !== "0", "rate limiting enabled (RATE_MAX != 0)", false);

let hardFail = 0;
console.log("NOVA production-readiness:\n");
for (const c of rows) {
  if (!c.pass && c.hard) hardFail++;
  console.log(`  ${c.pass ? "✓" : c.hard ? "✗" : "!"} ${c.name.padEnd(15)} ${c.detail}`);
}
console.log(hardFail
  ? `\n✗ ${hardFail} required check(s) failed — see SECURITY.md`
  : "\n✓ required checks passed (review ! warnings before going public)");
process.exit(hardFail ? 1 : 0);
