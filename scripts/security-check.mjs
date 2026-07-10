#!/usr/bin/env node
// NOVA one-command security & quality gate. Run before opening a PR or a
// public release. Cross-platform (uses process.execPath for node; spawns npm
// via shell so npm.cmd resolves on Windows).
//
// Steps:  syntax (node --check) · gateway unit tests · secret scan ·
//         gateway npm audit · web npm audit · web build
//
// Env / flags:
//   SKIP_BUILD=1   skip the web production build (faster)
//   SKIP_AUDIT=1   skip npm audit steps
//   AUDIT_LEVEL    npm audit level (default "moderate")
//
// Usage:  node scripts/security-check.mjs   (or: npm run security)

import { spawnSync } from "node:child_process";
import { readdirSync, statSync, existsSync, readFileSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const NODE = process.execPath;
const AUDIT_LEVEL = process.env.AUDIT_LEVEL || "moderate";
const results = [];

function run(name, cmd, args, opts = {}) {
  process.stdout.write(`\n▶ ${name}\n`);
  const r = spawnSync(cmd, args, { cwd: ROOT, stdio: "inherit", encoding: "utf8", ...opts });
  const ok = !r.error && r.status === 0;
  results.push({ name, ok, code: r.error ? r.error.message : r.status });
  return ok;
}

// Collect gateway + scripts .mjs files for a syntax check.
function collectMjs(dir, acc = []) {
  if (!existsSync(dir)) return acc;
  for (const n of readdirSync(dir)) {
    if (n === "node_modules") continue;
    const p = join(dir, n);
    const st = statSync(p);
    if (st.isDirectory()) collectMjs(p, acc);
    else if (n.endsWith(".mjs")) acc.push(p);
  }
  return acc;
}

// 1) Syntax check every gateway + script .mjs
(() => {
  const files = [...collectMjs(join(ROOT, "gateway")), ...collectMjs(join(ROOT, "scripts"))];
  process.stdout.write(`\n▶ syntax (node --check, ${files.length} files)\n`);
  let bad = 0;
  for (const f of files) {
    const r = spawnSync(NODE, ["--check", f], { encoding: "utf8" });
    if (r.status !== 0) { bad++; process.stderr.write(`  ✗ ${f}\n${r.stderr || ""}\n`); }
  }
  results.push({ name: "syntax", ok: bad === 0, code: bad });
  process.stdout.write(bad === 0 ? "  ✓ all parse\n" : `  ✗ ${bad} file(s) failed\n`);
})();

// 2) Gateway unit tests
run("gateway unit tests", "npm", ["--prefix", "gateway", "test"], { shell: true });

// 3) Secret scan
run("secret scan", NODE, [join(ROOT, "scripts", "secret-scan.mjs")]);

// 4) Static security config checks
(() => {
  process.stdout.write("\n▶ static security config\n");
  let ok = true;
  try {
    const realmPath = join(ROOT, "keycloak", "nova-realm.json");
    const realm = JSON.parse(readFileSync(realmPath, "utf8"));
    const users = Array.isArray(realm.users) ? realm.users : [];
    const clients = Array.isArray(realm.clients) ? realm.clients : [];
    const directGrantClients = clients.filter((c) => c.publicClient && c.directAccessGrantsEnabled);
    if (users.length > 0) {
      ok = false;
      process.stderr.write("  ✗ keycloak/nova-realm.json must not import default users\n");
    }
    if (directGrantClients.length > 0) {
      ok = false;
      process.stderr.write("  ✗ public Keycloak clients must not enable directAccessGrantsEnabled\n");
    }
    if (ok) process.stdout.write("  ✓ Keycloak realm imports no users and public clients use PKCE flow only\n");
  } catch (e) {
    ok = false;
    process.stderr.write(`  ✗ failed to validate keycloak/nova-realm.json: ${e.message}\n`);
  }
  results.push({ name: "static security config", ok, code: ok ? 0 : 1 });
})();

// 5) Audits
if (process.env.SKIP_AUDIT === "1") {
  results.push({ name: "gateway audit", ok: true, code: "skipped" });
  results.push({ name: "web audit", ok: true, code: "skipped" });
} else {
  run("gateway audit", "npm", ["--prefix", "gateway", "audit", `--audit-level=${AUDIT_LEVEL}`], { shell: true });
  run("web audit", "npm", ["--prefix", "web", "audit", `--audit-level=${AUDIT_LEVEL}`], { shell: true });
}

// 6) Web build (catches breakage; optional)
if (process.env.SKIP_BUILD === "1") {
  results.push({ name: "web build", ok: true, code: "skipped" });
} else {
  run("web build", "npm", ["--prefix", "web", "run", "build"], { shell: true });
}

// ── summary ─────────────────────────────────────────────────────────────────
console.log("\n──────── security-check summary ────────");
let failed = 0;
for (const r of results) {
  if (!r.ok) failed++;
  console.log(`  ${r.ok ? "✓" : "✗"} ${r.name}${r.ok ? "" : `  (exit ${r.code})`}`);
}
console.log(failed ? `\n✗ ${failed} step(s) failed` : "\n✓ all security checks passed");
process.exit(failed ? 1 : 0);
