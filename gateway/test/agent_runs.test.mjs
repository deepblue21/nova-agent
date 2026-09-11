// Agent run history pure-helper tests — npm --prefix gateway test (node --test)
import { test } from "node:test";
import assert from "node:assert/strict";
import { formatRunTools, openclawRunFromCompletion } from "../lib/agent_runs_store.mjs";

test("formatRunTools: counts repeats and joins", () => {
  assert.equal(formatRunTools(["web_search", "web_search", "calculator"]), "web_search×2, calculator");
  assert.equal(formatRunTools(["doc_search"]), "doc_search");
  assert.equal(formatRunTools([]), "");
  assert.equal(formatRunTools(null), "");
  assert.equal(formatRunTools(["", "  ", "x"]), "x");
});

// Faz 11 — PC devri izlenebilir mi. Bu testler yazılmadan önce telefondan
// devredilen iş hiçbir yere kaydolmuyordu; Android tarafındaki yorum ise
// "otomatik kaydolur" diyordu.
test("openclawRunFromCompletion: PC devri koşum olarak kaydedilir", () => {
  const run = openclawRunFromCompletion({
    provider: "openclaw", model: "openclaw/default",
    prompt: "faturayı özetle", result: "özet",
  });
  assert.equal(run.mode, "openclaw");
  assert.equal(run.model, "openclaw/default");
  assert.equal(run.prompt, "faturayı özetle");
  assert.equal(run.result, "özet");
  assert.equal(run.tools, "");
  assert.equal(run.rounds, 0);
});

test("openclawRunFromCompletion: ajan bayrağı tek başına koşum üretmez", () => {
  // `agent: true` gönderilmiş bir bulut modeli ajan DÖNGÜSÜNE girmez
  // (o dal yalnız ollama). Onu geçmişe yazmak ikinci bir yalan olurdu.
  assert.equal(openclawRunFromCompletion({ provider: "anthropic", model: "anthropic/claude-opus-4-8" }), null);
  assert.equal(openclawRunFromCompletion({ provider: "ollama", model: "qwen3" }), null);
  assert.equal(openclawRunFromCompletion({}), null);
  assert.equal(openclawRunFromCompletion(), null);
});

test("openclawRunFromCompletion: boş yanıt kaydı düşürmez", () => {
  // Devir başarısız olsa bile iz kalmalı — kullanıcı "gönderdim, ne oldu?"
  // sorusunun yanıtını boş bir koşum satırında da görür.
  const run = openclawRunFromCompletion({ provider: "openclaw", model: "openclaw/default", prompt: "x" });
  assert.equal(run.result, "");
  assert.equal(run.prompt, "x");
});

// Faz 11B — OpenClaw akışındaki araç adımı geçişi. `viaOpenClaw` bugüne kadar
// akıştan yalnız metin çekip geri kalan her alanı atıyordu.
test("openclawToolStep: bilinen biçimler geçer", async () => {
  const { openclawToolStep } = await import("../lib/providers.mjs");
  assert.deepEqual(
    openclawToolStep({ type: "tool_call", name: "web_search", args: { q: "x" } }),
    { name: "web_search", args: { q: "x" } },
  );
  assert.deepEqual(
    openclawToolStep({ type: "tool_result", name: "web_search", sources: [{ n: 1 }] }),
    { name: "web_search", done: true, sources: [{ n: 1 }] },
  );
  // Gateway'in kendi şekli olduğu gibi geçer (iki yerleşim de).
  assert.deepEqual(openclawToolStep({ tool_step: { name: "calc" } }), { name: "calc" });
  assert.deepEqual(
    openclawToolStep({ choices: [{ delta: { tool_step: { name: "calc", done: true } } }] }),
    { name: "calc", done: true },
  );
});

test("openclawToolStep: tanınmayan hiçbir şey adım UYDURMAZ", async () => {
  const { openclawToolStep } = await import("../lib/providers.mjs");
  // Düz metin parçası bir araç adımı değildir.
  assert.equal(openclawToolStep({ choices: [{ delta: { content: "merhaba" } }] }), null);
  assert.equal(openclawToolStep({ type: "tool_call" }), null);      // adı yok
  assert.equal(openclawToolStep({ tool_step: { args: {} } }), null); // adı yok
  assert.equal(openclawToolStep({ type: "baska_olay", name: "x" }), null);
  assert.equal(openclawToolStep({}), null);
  assert.equal(openclawToolStep(null), null);
  assert.equal(openclawToolStep("metin"), null);
});
