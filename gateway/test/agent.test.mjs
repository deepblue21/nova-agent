// Faz 3 ajan/araç/RAG saf yüzey testleri (ağ/DB gerektirmez).
//   npm test   (node --test)
import { test } from "node:test";
import assert from "node:assert/strict";
import JSZip from "jszip";

import { formatResults } from "../lib/search.mjs";
import { TOOL_SPECS, runTool } from "../lib/tools.mjs";
import { chunkText, toVectorLiteral } from "../lib/embed.mjs";
import { DOC_TYPES, normalizeKnowledgeInput } from "../lib/doc_extract.mjs";
import { runJavaScriptSandbox } from "../lib/code_sandbox.mjs";
import { runAgent } from "../lib/agent.mjs";
import { registry } from "../lib/metrics.mjs";

function xmlEscape(s) {
  return String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

async function minimalDocx(text) {
  const zip = new JSZip();
  zip.file("[Content_Types].xml", `<?xml version="1.0" encoding="UTF-8"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>`);
  zip.folder("_rels").file(".rels", `<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>`);
  zip.folder("word").file("document.xml", `<?xml version="1.0" encoding="UTF-8"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body><w:p><w:r><w:t>${xmlEscape(text)}</w:t></w:r></w:p></w:body>
</w:document>`);
  return zip.generateAsync({ type: "nodebuffer" });
}

test("search.formatResults: numaralı kaynak biçimi", () => {
  assert.equal(formatResults([]), "Arama sonucu bulunamadı.");
  const out = formatResults([
    { n: 1, title: "A", url: "http://a", snippet: "sn1" },
    { n: 2, title: "B", url: "http://b", snippet: "sn2" },
  ]);
  assert.match(out, /\[1\] A/);
  assert.match(out, /http:\/\/b/);
  assert.equal(out.split("\n\n").length, 2);
});

test("tools.TOOL_SPECS: beklenen araçlar + JSON şema", () => {
  const names = TOOL_SPECS.map(t => t.function.name);
  for (const n of ["web_search", "doc_search", "calculator", "current_time"])
    assert.ok(names.includes(n), "eksik araç: " + n);
  assert.ok(!names.includes("code_run"), "code_run varsayılan kapalı olmalı");
  for (const t of TOOL_SPECS) {
    assert.equal(t.type, "function");
    assert.equal(typeof t.function.description, "string");
    assert.equal(t.function.parameters.type, "object");
  }
});

test("tools.code_run: env açılmadan kapalı kalır", async () => {
  const r = await runTool("code_run", { code: "return 2 + 2;" });
  assert.ok(r.ok);
  assert.match(r.text, /kapalı/);
});

test("tools.calculator: doğru hesap + tehlikeli girişi blokla", async () => {
  const ok = await runTool("calculator", { expression: "(1234*7)/3" });
  assert.ok(ok.ok);
  assert.equal(Number(ok.text).toFixed(2), "2879.33");

  const m = await runTool("calculator", { expression: "sqrt(16)+1" });
  assert.equal(m.text, "5");

  // tehlikeli/izinsiz identifier'lar reddedilmeli (ok:false)
  for (const bad of ["constructor", "process.exit", "this", "globalThis", "require('fs')", "1;while(1){}"]) {
    const r = await runTool("calculator", { expression: bad });
    assert.equal(r.ok, false, "engellenemedi: " + bad);
  }
});

test("tools.current_time + bilinmeyen araç", async () => {
  const t = await runTool("current_time", {});
  assert.ok(t.ok && t.text.length > 0);
  const u = await runTool("bilinmeyen_arac", {});
  assert.equal(u.ok, false);
  assert.match(u.text, /Bilinmeyen araç/);
});

test("embed.chunkText: parçalama + overlap", () => {
  const para = "satır. ".repeat(60).trim();           // ~420 krktr tek paragraf
  const text = (para + "\n\n").repeat(6);              // 6 paragraf
  const chunks = chunkText(text, 500, 100);
  assert.ok(chunks.length >= 2, "çok az parça");
  for (const c of chunks) assert.ok(c.length <= 500 * 1.5 + 5);
  // tek kısa metin tek parça
  assert.deepEqual(chunkText("kısa bir not", 500), ["kısa bir not"]);
});

test("embed.toVectorLiteral: pgvector biçimi", () => {
  assert.equal(toVectorLiteral([0.1, 0.2, -1]), "[0.100000,0.200000,-1.000000]");
  assert.equal(toVectorLiteral([]), "[]");
});

test("doc_extract: düz metin dosyasını normalize eder", async () => {
  const out = await normalizeKnowledgeInput({
    file: {
      name: "runbook.md",
      mime: "text/markdown",
      b64: Buffer.from("NOVA local knowledge base test document content.", "utf8").toString("base64"),
    },
  });
  assert.equal(out.title, "runbook");
  assert.equal(out.kind, "text");
  assert.match(out.text, /knowledge base/);
});

test("doc_extract: DOCX metnini çıkarır", async () => {
  const buf = await minimalDocx("NOVA DOCX extraction smoke content.");
  const out = await normalizeKnowledgeInput({
    file: { name: "demo.docx", mime: DOC_TYPES.DOCX_MIME, b64: buf.toString("base64") },
  });
  assert.equal(out.title, "demo");
  assert.equal(out.kind, "docx");
  assert.match(out.text, /DOCX extraction smoke/);
});

test("doc_extract: desteklenmeyen dosya türünü reddeder", async () => {
  await assert.rejects(
    () => normalizeKnowledgeInput({
      file: { name: "archive.zip", mime: "application/zip", b64: Buffer.from("nope").toString("base64") },
    }),
    err => err.status === 415 && /desteklenmeyen/.test(err.message),
  );
});

test("doc_extract: eksik, boş, kısa ve büyük girdileri reddeder", async () => {
  await assert.rejects(
    () => normalizeKnowledgeInput({}),
    err => err.status === 400 && /text/.test(err.message),
  );
  await assert.rejects(
    () => normalizeKnowledgeInput({ text: "çok kısa" }),
    err => err.status === 400 && /min 20/.test(err.message),
  );
  await assert.rejects(
    () => normalizeKnowledgeInput({ text: "x".repeat(21) }, { maxTextBytes: 10 }),
    err => err.status === 413 && /belge çok büyük/.test(err.message),
  );
  await assert.rejects(
    () => normalizeKnowledgeInput({ file: { name: "empty.txt", mime: "text/plain", b64: "" } }),
    err => err.status === 400 && /file.b64/.test(err.message),
  );
  await assert.rejects(
    () => normalizeKnowledgeInput({
      file: { name: "big.txt", mime: "text/plain", b64: Buffer.from("x".repeat(20)).toString("base64") },
    }, { maxFileBytes: 5 }),
    err => err.status === 413 && /dosya çok büyük/.test(err.message),
  );
});

test("code_sandbox: input, console ve return çalışır", async () => {
  const r = await runJavaScriptSandbox({
    input: { xs: [1, 2, 3] },
    code: "const total = input.xs.reduce((a, b) => a + b, 0); console.log('toplam', total); return total * 2;",
  });
  assert.match(r.text, /stdout:/);
  assert.match(r.text, /toplam 6/);
  assert.equal(r.result, "12");
});

test("code_sandbox: host process/require yok", async () => {
  const r = await runJavaScriptSandbox({
    code: "return [typeof process, typeof require, typeof fetch].join('|');",
  });
  assert.equal(r.result, "undefined|undefined|undefined");
});

test("code_sandbox: sonsuz döngüyü keser", async () => {
  await assert.rejects(
    () => runJavaScriptSandbox({ code: "while (true) {}", timeout_ms: 50 }),
    /interrupted|sandbox/i,
  );
});

test("code_sandbox: boş ve fazla uzun kodu reddeder", async () => {
  await assert.rejects(
    () => runJavaScriptSandbox({ code: "   " }),
    /code gerekli/,
  );
  await assert.rejects(
    () => runJavaScriptSandbox({ code: "x".repeat(7000) }),
    /code çok uzun/,
  );
});

// ── runAgent orchestration (mocked Ollama + SearXNG, no live infra) ──────────
// Routes fetch by URL: /api/chat → queued Ollama messages, /search → SearXNG.
function mockFetch({ ollama = [], searx = [] } = {}) {
  const prev = globalThis.fetch;
  let oi = 0;
  const calls = [];
  globalThis.fetch = async (url, opts = {}) => {
    const u = String(url);
    let body = null;
    try { body = opts.body ? JSON.parse(opts.body) : null; } catch { /* ignore */ }
    calls.push({ url: u, body });
    if (u.includes("/api/chat")) {
      const message = ollama[oi++] ?? { content: "" };
      return { ok: true, status: 200, async json() { return { message }; }, async text() { return ""; } };
    }
    if (u.includes("/search")) {
      return { ok: true, status: 200, async json() { return { results: searx }; }, async text() { return ""; } };
    }
    return { ok: false, status: 404, async json() { return {}; }, async text() { return "not mocked: " + u; } };
  };
  return { calls, restore() { globalThis.fetch = prev; } };
}

test("runAgent: araç çağrısı olmadan doğrudan cevap döner", async () => {
  const fx = mockFetch({ ollama: [{ content: "doğrudan cevap" }] });
  try {
    const out = await runAgent({
      ollamaBase: "http://ollama-test:11434",
      model: "test-model",
      messages: [{ role: "user", content: "merhaba" }],
    });
    assert.equal(out.content, "doğrudan cevap");
    assert.deepEqual(out.toolsUsed, []);
    assert.equal(out.rounds, 0);
    assert.equal(fx.calls.length, 1);
  } finally { fx.restore(); }
});

test("runAgent: araç çağrısını çalıştırır ve sonucu modele geri besler", async () => {
  const steps = [];
  const fx = mockFetch({
    ollama: [
      { content: "", tool_calls: [{ function: { name: "calculator", arguments: { expression: "2+2" } } }] },
      { content: "Sonuç 4" },
    ],
  });
  try {
    const out = await runAgent({
      ollamaBase: "http://ollama-test:11434",
      model: "test-model",
      messages: [{ role: "user", content: "2+2 kaç?" }],
      onStep: (e) => steps.push(e),
    });
    assert.equal(out.content, "Sonuç 4");
    assert.ok(out.toolsUsed.includes("calculator"));
    // İkinci /api/chat çağrısının gövdesinde araç sonucu (tool mesajı "4") olmalı.
    assert.equal(fx.calls.length, 2);
    const fedBack = fx.calls[1].body.messages.find(m => m.role === "tool");
    assert.ok(fedBack && String(fedBack.content) === "4", "araç sonucu modele geri beslenmedi");
    // Akış event'leri tool_call + tool_result üretmeli.
    assert.ok(steps.some(s => s.type === "tool_call" && s.name === "calculator"));
    assert.ok(steps.some(s => s.type === "tool_result" && s.text === "4"));
  } finally { fx.restore(); }
});

test("runAgent: web_search kaynakları sonuca taşınır", async () => {
  const fx = mockFetch({
    ollama: [
      { content: "", tool_calls: [{ function: { name: "web_search", arguments: { query: "nova agent" } } }] },
      { content: "Özet cevap" },
    ],
    searx: [
      { title: "NOVA", url: "https://example.com/nova", content: "açıklama" },
    ],
  });
  try {
    const out = await runAgent({
      ollamaBase: "http://ollama-test:11434",
      model: "test-model",
      messages: [{ role: "user", content: "nova agent nedir?" }],
    });
    assert.equal(out.content, "Özet cevap");
    assert.ok(out.toolsUsed.includes("web_search"));
    assert.equal(out.sources.length, 1);
    assert.equal(out.sources[0].url, "https://example.com/nova");
  } finally { fx.restore(); }
});

test("metrics: agent runs + tool calls are counted in the registry", async () => {
  const fx = mockFetch({ ollama: [
    { content: "", tool_calls: [{ function: { name: "calculator", arguments: { expression: "2+2" } } }] },
    { content: "ok" },
  ] });
  try {
    await runAgent({ ollamaBase: "http://x:11434", model: "m", messages: [{ role: "user", content: "x" }] });
    const out = await registry.metrics();
    assert.match(out, /nova_agent_runs_total \d/);
    assert.match(out, /nova_agent_tool_calls_total\{tool="calculator",status="ok"\}/);
    assert.match(out, /nova_agent_tool_duration_seconds_count\{tool="calculator"\}/);
  } finally { fx.restore(); }
});
