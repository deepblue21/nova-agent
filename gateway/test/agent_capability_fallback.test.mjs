// Ajan döngüsünün yetenek hatalarına tepkisi.
//
// İki davranış sözleşme: (1) "does not support tools" hatası ÜST KATMANA
// TOOLS_UNSUPPORTED koduyla ulaşmalı ki gateway düz sohbete düşebilsin,
// (2) "does not support thinking" hatası döngü içinde düşünme geri çekilerek
// bir kez daha denenmeli ve bu sessizce olmamalı (notices'a yazılmalı).
import { test } from "node:test";
import assert from "node:assert/strict";
import { runAgent } from "../lib/agent.mjs";
import { classifyUpstream, UP } from "../lib/upstream_errors.mjs";

function stubOllama(handler) {
  const prev = globalThis.fetch;
  const calls = [];
  globalThis.fetch = async (url, opts = {}) => {
    const body = JSON.parse(opts.body || "{}");
    calls.push({ url: String(url), body });
    return handler(body, calls.length);
  };
  return { calls, restore() { globalThis.fetch = prev; } };
}

const jsonRes = (obj, status = 200) => ({
  ok: status >= 200 && status < 300,
  status,
  async json() { return obj; },
  async text() { return JSON.stringify(obj); },
});

test("runAgent: araç desteklemeyen model TOOLS_UNSUPPORTED koduyla yukarı fırlar", async () => {
  const stub = stubOllama(() =>
    jsonRes({ error: "registry.ollama.ai/library/gemma3 does not support tools" }, 400));
  try {
    await assert.rejects(
      () => runAgent({
        ollamaBase: "http://x:11434",
        model: "gemma3:4b",
        messages: [{ role: "user", content: "merhaba" }],
      }),
      (err) => {
        assert.equal(err.upstreamCode, UP.TOOLS_UNSUPPORTED);
        assert.equal(classifyUpstream(err), UP.TOOLS_UNSUPPORTED);
        return true;
      },
    );
    // Tek deneme: araç hatası döngü içinde geri çekilmez, üst katman karar verir.
    assert.equal(stub.calls.length, 1);
  } finally { stub.restore(); }
});

test("runAgent: düşünme desteklenmiyorsa think geri çekilip yeniden denenir", async () => {
  const stub = stubOllama((body, n) => {
    if (n === 1) {
      assert.equal(body.think, true);
      return jsonRes({ error: "llama3.1:8b does not support thinking" }, 400);
    }
    assert.equal(body.think, false);
    return jsonRes({ message: { role: "assistant", content: "selam" } });
  });
  const notices = [];
  try {
    const out = await runAgent({
      ollamaBase: "http://x:11434",
      model: "llama3.1:8b",
      messages: [{ role: "user", content: "merhaba" }],
      think: true,
      notices,
    });
    assert.equal(out.content, "selam");
    assert.equal(stub.calls.length, 2);
    // Sessiz düşürme yok: kullanıcıya bildirilecek not üretildi.
    assert.deepEqual(notices, [UP.THINK_UNSUPPORTED]);
  } finally { stub.restore(); }
});

test("runAgent: Ollama kapalıysa UNREACHABLE kodu taşınır", async () => {
  const prev = globalThis.fetch;
  globalThis.fetch = async () => { throw new Error("fetch failed"); };
  try {
    await assert.rejects(
      () => runAgent({
        ollamaBase: "http://x:11434",
        model: "qwen3:8b",
        messages: [{ role: "user", content: "merhaba" }],
      }),
      (err) => {
        assert.equal(err.upstreamCode, UP.UNREACHABLE);
        return true;
      },
    );
  } finally { globalThis.fetch = prev; }
});

test("runAgent: model yoksa MODEL_MISSING — think geri çekme denenmez", async () => {
  const stub = stubOllama(() =>
    jsonRes({ error: 'model "yok:1b" not found, try pulling it first' }, 404));
  try {
    await assert.rejects(
      () => runAgent({
        ollamaBase: "http://x:11434",
        model: "yok:1b",
        messages: [{ role: "user", content: "merhaba" }],
        think: true,
      }),
      (err) => err.upstreamCode === UP.MODEL_MISSING,
    );
    assert.equal(stub.calls.length, 1);
  } finally { stub.restore(); }
});
