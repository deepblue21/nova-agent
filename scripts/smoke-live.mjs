#!/usr/bin/env node
// NOVA live smoke test — exercises a RUNNING gateway end to end.
// Zero dependencies (uses global fetch, Node 20.19+).
//
// Required checks (affect exit code): /health, auth enforcement, /v1/models.
// Optional checks (informational unless SMOKE_STRICT=1): chat, agent, RAG.
//
// Env:
//   GATEWAY_URL    base url           (default http://localhost:8088)
//   GATEWAY_TOKEN  bearer token / API key (enables the auth-enforcement check)
//   SMOKE_MODEL    model id for chat/agent (default "auto")
//   SMOKE_AGENT=1  run the agent tool-calling check (calculator)
//   SMOKE_RAG=1    run the RAG upload + doc_search check (needs multi-user auth)
//   SMOKE_STRICT=1 optional checks also affect the exit code
//   SMOKE_TIMEOUT_MS per-request timeout (default 120000)
//
// Usage:  node scripts/smoke-live.mjs

const BASE = (process.env.GATEWAY_URL || "http://localhost:8088").replace(/\/$/, "");
const TOKEN = process.env.GATEWAY_TOKEN || "";
const MODEL = process.env.SMOKE_MODEL || "auto";
const STRICT = process.env.SMOKE_STRICT === "1";
const TIMEOUT = parseInt(process.env.SMOKE_TIMEOUT_MS || "120000", 10);

const results = [];
function record(name, status, detail = "") { results.push({ name, status, detail }); }
const C = { pass: "✓", fail: "✗", skip: "•", warn: "!" };

async function req(path, { method = "GET", token = TOKEN, body, headers = {} } = {}) {
  const ctrl = new AbortController();
  const t = setTimeout(() => ctrl.abort(), TIMEOUT);
  try {
    const h = { ...headers };
    if (token) h.Authorization = "Bearer " + token;
    if (body !== undefined) h["Content-Type"] = "application/json";
    const r = await fetch(BASE + path, { method, headers: h, body: body !== undefined ? JSON.stringify(body) : undefined, signal: ctrl.signal });
    const text = await r.text();
    let json = null; try { json = JSON.parse(text); } catch { /* not json */ }
    return { ok: r.ok, status: r.status, text, json };
  } finally { clearTimeout(t); }
}

function chatContent(json) {
  if (!json) return "";
  return json.choices?.[0]?.message?.content
    ?? json.choices?.[0]?.text
    ?? json.content
    ?? "";
}

// ── required checks ─────────────────────────────────────────────────────────
async function checkHealth() {
  try {
    const r = await req("/health", { token: "" });
    if (r.status === 200 && r.json && r.json.ok === true) record("health", "pass", `GET /health 200 ok:true`);
    else record("health", "fail", `status ${r.status} body ${r.text.slice(0, 120)}`);
  } catch (e) { record("health", "fail", String(e.message || e)); }
}

async function checkAuthEnforced() {
  if (!TOKEN) { record("auth-enforced", "skip", "no GATEWAY_TOKEN set"); return; }
  try {
    const r = await req("/v1/models", { token: "" });
    if (r.status === 401) record("auth-enforced", "pass", "GET /v1/models without token → 401");
    else record("auth-enforced", "fail", `expected 401, got ${r.status}`);
  } catch (e) { record("auth-enforced", "fail", String(e.message || e)); }
}

async function checkModels() {
  try {
    const r = await req("/v1/models");
    const list = r.json?.data || r.json?.models || (Array.isArray(r.json) ? r.json : null);
    if (r.status === 200 && Array.isArray(list) && list.length) record("models", "pass", `${list.length} models`);
    else record("models", "fail", `status ${r.status} body ${r.text.slice(0, 120)}`);
  } catch (e) { record("models", "fail", String(e.message || e)); }
}

// ── optional checks ─────────────────────────────────────────────────────────
async function checkChat() {
  try {
    const r = await req("/v1/chat/completions", {
      method: "POST",
      body: { model: MODEL, stream: false, messages: [{ role: "user", content: "Sadece 'tamam' yaz." }] },
    });
    const content = chatContent(r.json);
    if (r.status === 200 && content) record("chat", "pass", `route reply (${content.trim().slice(0, 40)})`);
    else record("chat", STRICT ? "fail" : "warn", `status ${r.status} ${r.text.slice(0, 120)} — needs a working provider/Ollama`);
  } catch (e) { record("chat", STRICT ? "fail" : "warn", String(e.message || e)); }
}

async function checkAgent() {
  if (process.env.SMOKE_AGENT !== "1") { record("agent", "skip", "set SMOKE_AGENT=1 to run"); return; }
  try {
    const r = await req("/v1/chat/completions", {
      method: "POST",
      body: { model: MODEL, stream: false, agent: true, messages: [{ role: "user", content: "27*4 kaç eder? calculator aracını kullan ve sadece sonucu yaz." }] },
    });
    const content = chatContent(r.json);
    if (r.status === 200 && /108/.test(content)) record("agent", "pass", "calculator tool produced 108");
    else record("agent", STRICT ? "fail" : "warn", `status ${r.status} content "${content.slice(0, 60)}" — needs tool-capable model (Qwen/Titus)`);
  } catch (e) { record("agent", STRICT ? "fail" : "warn", String(e.message || e)); }
}

async function checkRag() {
  if (process.env.SMOKE_RAG !== "1") { record("rag", "skip", "set SMOKE_RAG=1 to run"); return; }
  if (!TOKEN) { record("rag", "skip", "needs GATEWAY_TOKEN (multi-user)"); return; }
  const secret = "ZUMRUT-" + Math.floor(Math.random() * 9000 + 1000);
  try {
    const up = await req("/v1/knowledge", { method: "POST", body: { title: "smoke-doc", text: `Bu belgedeki gizli proje kodu ${secret} olarak tanımlanmıştır. Sadece bu belgede geçer.` } });
    if (up.status !== 201 && up.status !== 200) { record("rag", STRICT ? "fail" : "warn", `upload status ${up.status} ${up.text.slice(0, 100)}`); return; }
    const r = await req("/v1/chat/completions", {
      method: "POST",
      body: { model: MODEL, stream: false, agent: true, messages: [{ role: "user", content: "Belgedeki gizli proje kodu nedir? doc_search aracını kullan." }] },
    });
    const content = chatContent(r.json);
    if (r.status === 200 && content.includes(secret)) record("rag", "pass", `doc_search retrieved ${secret}`);
    else record("rag", STRICT ? "fail" : "warn", `secret not echoed (status ${r.status}) — needs embeddings + pgvector + tool model`);
  } catch (e) { record("rag", STRICT ? "fail" : "warn", String(e.message || e)); }
}

// ── run ─────────────────────────────────────────────────────────────────────
console.log(`NOVA live smoke → ${BASE}  (token ${TOKEN ? "set" : "none"}, model ${MODEL})\n`);
await checkHealth();
await checkAuthEnforced();
await checkModels();
await checkChat();
await checkAgent();
await checkRag();

let failed = 0;
for (const r of results) {
  if (r.status === "fail") failed++;
  const mark = C[r.status] || "?";
  console.log(`  ${mark} ${r.name.padEnd(16)} ${r.detail}`);
}
const required = results.filter(r => ["health", "auth-enforced", "models"].includes(r.name) && r.status === "fail").length;
console.log(`\n${failed ? C.fail : C.pass} ${results.length} checks, ${failed} failed${required ? ` (${required} required)` : ""}`);
process.exit(failed ? 1 : 0);
