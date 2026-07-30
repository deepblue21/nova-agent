import test from "node:test";
import assert from "node:assert/strict";
import {
  UP,
  classifyUpstream,
  upstreamMessage,
  describeUpstreamError,
  tagUpstream,
  isCapabilityError,
} from "../lib/upstream_errors.mjs";

// Gerçek Ollama / sağlayıcı gövdelerinden örnekler.
const CASES = [
  ['ollama 400 {"error":"llama3.1:8b does not support thinking"}', UP.THINK_UNSUPPORTED],
  ['ollama 400 {"error":"registry.ollama.ai/library/gemma3 does not support tools"}', UP.TOOLS_UNSUPPORTED],
  ['ollama 400 {"error":"model \\"qwen3:8b\\" not found, try pulling it first"}', UP.MODEL_MISSING],
  ['ollama 500 {"error":"model requires more system memory (9.2 GiB) than is available"}', UP.OUT_OF_MEMORY],
  ["ollama fetch failed", UP.UNREACHABLE],
  ["ollama connect ECONNREFUSED 127.0.0.1:11434", UP.UNREACHABLE],
  ["openai 429 rate limit reached", UP.RATE_LIMITED],
  ["anthropic 401 invalid api key", UP.AUTH_REJECTED],
  ["OPENAI_API_KEY not set", UP.KEY_MISSING],
  ["gemini 503 service unavailable", UP.UPSTREAM_DOWN],
  ['ollama 400 {"error":"this model does not support image input"}', UP.VISION_UNSUPPORTED],
];

test("classifyUpstream: gerçek upstream gövdelerini doğru kodlara indirger", () => {
  for (const [text, expected] of CASES) {
    assert.equal(classifyUpstream(new Error(text)), expected, text);
  }
});

test("classifyUpstream: AbortError ve bilinmeyenler", () => {
  const abort = new Error("The operation was aborted");
  abort.name = "AbortError";
  assert.equal(classifyUpstream(abort), UP.TIMEOUT);
  assert.equal(classifyUpstream(new Error("something odd happened")), UP.UNKNOWN);
  assert.equal(classifyUpstream(null), UP.UNKNOWN);
  assert.equal(classifyUpstream("plain string"), UP.UNKNOWN);
});

test("classifyUpstream: iliştirilmiş kod yeniden tahmine tercih edilir", () => {
  const e = tagUpstream(new Error("ollama 400 whatever"), UP.TOOLS_UNSUPPORTED);
  assert.equal(classifyUpstream(e), UP.TOOLS_UNSUPPORTED);
});

test("upstreamMessage: ham upstream metnini ASLA sızdırmaz", () => {
  const secret = 'ollama 500 {"error":"internal path /root/.ollama/models/blobs/sha256-deadbeef"}';
  const { message } = describeUpstreamError(new Error(secret), { provider: "ollama", model: "qwen3:8b" });
  assert.ok(!message.includes("deadbeef"));
  assert.ok(!message.includes("/root/"));
  assert.ok(!message.includes("500"));
});

test("upstreamMessage: eyleme dönüştürülebilir ve model adını içerir", () => {
  const missing = upstreamMessage(UP.MODEL_MISSING, { provider: "ollama", model: "qwen3:8b" });
  assert.ok(missing.includes("qwen3:8b"));
  assert.ok(missing.includes("ollama pull qwen3:8b"));

  const think = upstreamMessage(UP.THINK_UNSUPPORTED, { provider: "ollama", model: "gemma3:4b" });
  assert.ok(think.includes("gemma3:4b"));
  assert.ok(/Düşün/.test(think));

  const down = upstreamMessage(UP.UNREACHABLE, { provider: "ollama" });
  assert.ok(/OLLAMA_URL/.test(down));

  // Sağlayıcı etiketleri Türkçeleşmiş mesajda görünür
  assert.ok(upstreamMessage(UP.RATE_LIMITED, { provider: "openai" }).includes("OpenAI"));
  assert.ok(upstreamMessage(UP.AUTH_REJECTED, { provider: "anthropic" }).includes("Anthropic"));
});

test("upstreamMessage: her kod için boş olmayan mesaj döner", () => {
  for (const code of Object.values(UP)) {
    const msg = upstreamMessage(code, { provider: "ollama", model: "m" });
    assert.equal(typeof msg, "string");
    assert.ok(msg.length > 10, code);
  }
  // bilinmeyen kod da çökmez
  assert.ok(upstreamMessage("no_such_code", {}).length > 0);
});

test("isCapabilityError: yalnız geri çekilebilir yetenekler", () => {
  assert.equal(isCapabilityError(UP.THINK_UNSUPPORTED), true);
  assert.equal(isCapabilityError(UP.TOOLS_UNSUPPORTED), true);
  assert.equal(isCapabilityError(UP.VISION_UNSUPPORTED), true);
  assert.equal(isCapabilityError(UP.UNREACHABLE), false);
  assert.equal(isCapabilityError(UP.MODEL_MISSING), false);
});
