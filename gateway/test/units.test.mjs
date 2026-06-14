// Unit tests for the pure surface of the gateway modules.
//   npm test        (node --test)
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

import { newApiKey, isApiKey, keyPrefix, sha256hex, hashEquals, parseBearer } from "../lib/keys.mjs";
import { priceFor, approxTokens, estimateCostMicros } from "../lib/pricing.mjs";
import { routeLabel } from "../lib/metrics.mjs";
import { mediaKey } from "../lib/storage.mjs";
import { microsToCents } from "../lib/billing.mjs";
import { hasImageContent, messageTextLength, pickDynamicModel } from "../lib/routing.mjs";
import { normalizeSttPayload, normalizeTtsPayload, synthesizeSpeech, transcribeAudio, voiceLimitsFromEnv } from "../lib/voice.mjs";
import { createVoiceQueue, isVoiceQueueEnabled, voiceQueueConfig } from "../lib/voice_queue.mjs";
import { imageInputConfig, isPrivateAddress, resolveImageInputs } from "../lib/image_inputs.mjs";
import { mediaInputConfig, normalizeMediaInput } from "../lib/media_input.mjs";
import { createProviderClient, messageText, normMsg, pickParams, routeModel } from "../lib/providers.mjs";
import {
  usageFromOpenAI, usageFromAnthropic, usageFromGemini, usageFromOllama, makeUsageAccumulator,
} from "../lib/tokens.mjs";
import { buildOtlpSpan, tracingConfig, createTracer } from "../lib/tracing.mjs";

test("regression: gateway aborts upstream on res 'close', not req 'close'", () => {
  // Bug (fixed 13 Jun): req.on('close') fires as soon as the POST body is read —
  // before we respond — so it aborted every chat/vision upstream call instantly
  // (~1ms). The abort must be wired to res 'close' (response done or client drop).
  const src = readFileSync(new URL("../gateway.mjs", import.meta.url), "utf8");
  assert.ok(/res\.on\(\s*["']close["']/.test(src), "expected res.on('close') upstream-abort wiring in gateway.mjs");
  assert.ok(!/req\.on\(\s*["']close["']/.test(src), "req.on('close') reintroduced — aborts upstream prematurely (use res.on('close'))");
});

test("tracing: buildOtlpSpan structure + attribute typing + config gating", () => {
  const sp = buildOtlpSpan({ name: "chat", traceId: "a".repeat(32), spanId: "b".repeat(16), startTimeUnixNano: 1n, endTimeUnixNano: 2n, attributes: { "nova.route": "ollama/x", n: 5, ok: true }, statusCode: 1 });
  const s = sp.resourceSpans[0].scopeSpans[0].spans[0];
  assert.equal(s.name, "chat");
  assert.equal(s.startTimeUnixNano, "1");
  assert.equal(s.status.code, 1);
  const a = Object.fromEntries(s.attributes.map(x => [x.key, x.value]));
  assert.deepEqual(a["nova.route"], { stringValue: "ollama/x" });
  assert.deepEqual(a["n"], { intValue: "5" });
  assert.deepEqual(a["ok"], { boolValue: true });
  assert.equal(sp.resourceSpans[0].resource.attributes[0].value.stringValue, "nova-gateway");
  assert.equal(tracingConfig({}).enabled, false);
  assert.equal(tracingConfig({ OTEL_EXPORTER_OTLP_ENDPOINT: "http://c:4318" }).enabled, true);
  assert.match(tracingConfig({ OTEL_EXPORTER_OTLP_ENDPOINT: "http://c:4318/" }).endpoint, /\/v1\/traces$/);
});

test("tracing: exports span via OTLP when enabled, no-op when disabled", async () => {
  let posted = null;
  const tracer = createTracer({ OTEL_EXPORTER_OTLP_ENDPOINT: "http://collector:4318" }, async (url, opts) => { posted = { url, body: JSON.parse(opts.body) }; return { ok: true }; });
  tracer.startSpan("chat", { "nova.route": "ollama/x" }).end({ "http.status_code": 200 }, 1);
  await new Promise(r => setTimeout(r, 25));
  assert.ok(posted, "enabled tracer should POST");
  assert.match(posted.url, /\/v1\/traces$/);
  assert.equal(posted.body.resourceSpans[0].scopeSpans[0].spans[0].name, "chat");
  let p2 = false;
  createTracer({}, async () => { p2 = true; return { ok: true }; }).startSpan("x").end();
  await new Promise(r => setTimeout(r, 25));
  assert.equal(p2, false, "disabled tracer must not POST");
});

test("providers.pickParams: effort tiers drive local generation defaults", () => {
  assert.deepEqual(pickParams({}), {});                       // no effort → no defaults
  assert.equal(pickParams({ effort: "max" }).max_tokens, 4096);
  assert.equal(pickParams({ effort: "max" }).temperature, 0.3);
  assert.equal(pickParams({ effort: "fast" }).max_tokens, 512);
  assert.equal(pickParams({ effort: "deep" }).max_tokens, 2048);
  // explicit values always win over tier defaults
  const ex = pickParams({ effort: "max", max_tokens: 100, temperature: 0.9 });
  assert.equal(ex.max_tokens, 100);
  assert.equal(ex.temperature, 0.9);
});

test("routing.pickDynamicModel: ROUTE_<EFFORT> env overrides the model (auto)", () => {
  const env = { ROUTE_MAX: "ollama/qwen3.6:35b", ROUTE_FAST: "ollama/gemma4:e2b" };
  assert.equal(pickDynamicModel({ effort: "max", messages: [], keys: {}, defaultModel: "ollama/d", env }), "ollama/qwen3.6:35b");
  assert.equal(pickDynamicModel({ effort: "fast", messages: [], keys: {}, defaultModel: "ollama/d", env }), "ollama/gemma4:e2b");
  // no override + no cloud keys → default model
  assert.equal(pickDynamicModel({ effort: "max", messages: [], keys: {}, defaultModel: "ollama/d", env: {} }), "ollama/d");
});

test("voice.synthesizeSpeech: response_format follows config.ttsFormat (default wav)", async () => {
  let sent = null;
  const fetchFn = async (_url, opts) => {
    sent = JSON.parse(opts.body);
    return { ok: true, headers: { get: () => "audio/wav" }, arrayBuffer: async () => new ArrayBuffer(8) };
  };
  await synthesizeSpeech({ input: "merhaba" }, { ttsUrl: "http://tts/x", ttsFormat: "opus" }, { fetchFn });
  assert.equal(sent.response_format, "opus");
  await synthesizeSpeech({ input: "merhaba" }, { ttsUrl: "http://tts/x" }, { fetchFn });
  assert.equal(sent.response_format, "wav");   // default when unset
});

test("api keys: shape, hashing, parsing", () => {
  const k = newApiKey();
  assert.match(k.prefix, /^nv_[0-9a-f]{6}$/);
  assert.ok(k.full.startsWith(k.prefix + "_"));
  assert.equal(k.token_hash, sha256hex(k.full));
  assert.ok(isApiKey(k.full));
  assert.ok(!isApiKey("eyJhbGci.x.y"));
  assert.equal(keyPrefix(k.full), k.prefix);
  assert.ok(hashEquals(k.token_hash, sha256hex(k.full)));
  assert.ok(!hashEquals(k.token_hash, sha256hex("nope")));
  assert.equal(parseBearer("Bearer abc"), "abc");
  assert.equal(parseBearer("Basic abc"), "");
});

test("pricing: tiers + cost", () => {
  assert.deepEqual(priceFor("anthropic/claude-sonnet-4-20250514"), [3, 15]);
  assert.deepEqual(priceFor("ollama/qwen3:14b"), [0, 0]);
  assert.deepEqual(priceFor("unknown/x"), [0, 0]);
  assert.equal(approxTokens("12345678"), 2);
  assert.equal(estimateCostMicros("anthropic/claude-sonnet-4-20250514", 1000, 500), 10500);
  assert.equal(estimateCostMicros("ollama/qwen3:14b", 1000, 500), 0);
});

test("metrics.routeLabel: cardinality collapse", () => {
  assert.equal(routeLabel("/v1/conversations/2b1c9f4a-1111-2222-3333-444455556666"), "/v1/conversations/:id");
  assert.equal(routeLabel("/v1/items/123"), "/v1/items/:n");
  assert.equal(routeLabel("/v1/chat/completions"), "/v1/chat/completions");
});

test("routing: image requests choose vision model", () => {
  const messages = [
    { role: "user", content: [
      { type: "text", text: "Bu görseli açıkla." },
      { type: "image_url", image_url: { url: "data:image/png;base64,AAAA" } },
    ] },
  ];
  assert.equal(hasImageContent(messages), true);
  assert.equal(messageTextLength(messages[0]), "Bu görseli açıkla.".length);
  assert.equal(pickDynamicModel({
    effort: "deep",
    messages,
    keys: { anthropic: "x", gemini: "x" },
    defaultModel: "ollama/gemma4:latest",
    visionModel: "ollama/qwen3.5-omni:latest",
    env: { ROUTE_DEEP: "anthropic/claude-sonnet-4-20250514" },
  }), "ollama/qwen3.5-omni:latest");
  assert.equal(pickDynamicModel({
    effort: "balanced",
    messages,
    keys: {},
    defaultModel: "ollama/gemma4:latest",
    visionModel: "ollama/qwen3.5-omni:latest",
    env: { ROUTE_VISION: "ollama/qwen3.6-35b-a3b:latest" },
  }), "ollama/qwen3.6-35b-a3b:latest");
});

test("routing: text requests keep effort routing", () => {
  const messages = [{ role: "user", content: "Merhaba" }];
  assert.equal(hasImageContent(messages), false);
  assert.equal(pickDynamicModel({
    effort: "deep",
    messages,
    keys: { anthropic: "x", gemini: "x" },
    defaultModel: "ollama/gemma4:latest",
    visionModel: "ollama/qwen3.5-omni:latest",
    env: { ROUTE_DEEP: "anthropic/custom" },
  }), "anthropic/custom");
});

test("routing: edge cases fall back safely", () => {
  const longMessages = [{ role: "user", content: "x".repeat(9001) }];
  assert.equal(pickDynamicModel({
    effort: "balanced",
    messages: longMessages,
    keys: { gemini: "x" },
    defaultModel: "ollama/default",
    visionModel: "ollama/vision",
    env: {},
  }), "gemini/gemini-2.5-pro");
  assert.equal(pickDynamicModel({
    effort: "max",
    messages: [{ role: "user", content: [{ type: "image", source: { data: "abc" } }] }],
    keys: { gemini: "x" },
    defaultModel: "ollama/default",
    visionModel: "",
    env: {},
  }), "gemini/gemini-2.5-flash");
});

test("providers: route parsing, params and multimodal normalization", () => {
  assert.deepEqual(routeModel("ollama/gemma4:latest", "ollama/default"), { provider: "ollama", model: "gemma4:latest" });
  assert.deepEqual(routeModel("gemma4:e2b", "ollama/default"), { provider: "ollama", model: "gemma4:e2b" });
  assert.deepEqual(routeModel("", "ollama/default"), { provider: "ollama", model: "default" });
  assert.deepEqual(routeModel("auto", "ollama/default"), { provider: "ollama", model: "default" });
  assert.deepEqual(pickParams({ max_tokens: 12, temperature: 0.2, top_p: 0.9, ignored: true }), {
    max_tokens: 12,
    temperature: 0.2,
    top_p: 0.9,
  });

  const msg = { role: "user", content: [
    { type: "text", text: "Bak" },
    { type: "image_url", image_url: { url: "data:image/png;base64,AQID" } },
  ] };
  assert.equal(messageText(msg), "Bak");
  assert.deepEqual(normMsg(msg), { role: "user", text: "Bak", images: [{ mime: "image/png", b64: "AQID" }] });
});

test("providers: negative dispatch cases", async () => {
  const client = createProviderClient();
  await assert.rejects(
    () => client.chat({ provider: "unknown", model: "x", messages: [], stream: false, ctx: {}, res: {} }),
    /unknown provider/,
  );
  await assert.rejects(
    () => client.chat({ provider: "openai", model: "gpt-x", messages: [], stream: false, ctx: {}, res: {} }),
    /OPENAI_API_KEY not set/,
  );
});

test("voice: validates STT and TTS payloads", () => {
  const stt = normalizeSttPayload({
    audio: "data:audio/webm;base64," + Buffer.from("abc").toString("base64"),
    mime: "audio/webm",
    language: "tr",
  }, { maxAudioBytes: 10 });
  assert.equal(stt.buffer.toString(), "abc");
  assert.equal(stt.mime, "audio/webm");
  assert.equal(stt.language, "tr");
  assert.throws(() => normalizeSttPayload({ audio: "not base64!" }), /base64/);
  assert.throws(() => normalizeSttPayload({ audio: Buffer.from("toolong").toString("base64") }, { maxAudioBytes: 2 }), /too large/);

  assert.deepEqual(normalizeTtsPayload({ input: "  merhaba  ", voice: "alloy" }, { maxTtsChars: 20 }), {
    input: "merhaba",
    voice: "alloy",
    model: undefined,
  });
  assert.throws(() => normalizeTtsPayload({ input: "" }), /input required/);
  assert.throws(() => normalizeTtsPayload({ input: "abcdef" }, { maxTtsChars: 3 }), /too large/);
});

test("voice: upstream errors and malformed payloads are explicit", async () => {
  assert.throws(() => normalizeSttPayload({}), err => err.status === 400 && /required/.test(err.message));
  assert.throws(() => normalizeSttPayload({ audio: "" }), err => err.status === 400 && /required/.test(err.message));
  await assert.rejects(
    () => transcribeAudio({ audio: Buffer.from("abc").toString("base64") }, {}, {
      fetchFn: async () => new Response("bad whisper", { status: 500 }),
    }),
    err => err.status === 502 && /whisper 500/.test(err.message),
  );
  await assert.rejects(
    () => synthesizeSpeech({ input: "merhaba" }, {}, {
      fetchFn: async () => new Response("bad tts", { status: 503 }),
    }),
    err => err.status === 502 && /tts 503/.test(err.message),
  );
});

test("voice queue: env config is explicit opt-in", () => {
  assert.equal(isVoiceQueueEnabled({}), false);
  assert.equal(isVoiceQueueEnabled({ VOICE_QUEUE_ENABLED: "1" }), true);
  assert.deepEqual(voiceLimitsFromEnv({ VOICE_QUEUE_MAX_AUDIO_BYTES: "42", TTS_MAX_INPUT_CHARS: "99" }), {
    maxAudioBytes: 42,
    maxTtsChars: 99,
  });
  const cfg = voiceQueueConfig({
    VOICE_QUEUE_ENABLED: "1",
    REDIS_URL: "redis://redis:6379",
    VOICE_QUEUE_NAME: "nova_test",
    VOICE_QUEUE_CONCURRENCY: "3",
  });
  assert.equal(cfg.enabled, true);
  assert.equal(cfg.redisUrl, "redis://redis:6379");
  assert.equal(cfg.name, "nova_test");
  assert.equal(cfg.concurrency, 3);
});

test("voice queue: disabled queue rejects job operations without Redis", async () => {
  const q = createVoiceQueue({ env: {}, logger: { warn() {} } });
  assert.equal(q.enabled, false);
  await assert.rejects(() => q.add("stt", {}), /disabled/);
  assert.equal(await q.get("missing"), null);
});

test("image inputs: remote URLs are explicit opt-in and private targets are blocked", async () => {
  assert.equal(imageInputConfig({}).remoteEnabled, false);
  assert.equal(imageInputConfig({ REMOTE_IMAGE_URLS_ENABLED: "1" }).remoteEnabled, true);
  assert.equal(isPrivateAddress("127.0.0.1"), true);
  assert.equal(isPrivateAddress("192.168.1.10"), true);
  assert.equal(isPrivateAddress("93.184.216.34"), false);

  const messages = [{ role: "user", content: [
    { type: "text", text: "Açıkla" },
    { type: "image_url", image_url: { url: "https://example.com/a.png" } },
  ] }];
  await assert.rejects(
    () => resolveImageInputs(messages, { remoteEnabled: false, maxBytes: 100, maxRedirects: 1 }),
    /remote image URLs disabled/,
  );
  await assert.rejects(
    () => resolveImageInputs([{ role: "user", content: [{ type: "image_url", image_url: { url: "http://127.0.0.1/a.png" } }] }], {
      remoteEnabled: true, maxBytes: 100, maxRedirects: 1,
    }),
    /private address/,
  );
});

test("image inputs: rejects unsafe URL and content edge cases", async () => {
  const cfg = { remoteEnabled: true, maxBytes: 100, maxRedirects: 1 };
  await assert.rejects(
    () => resolveImageInputs([{ role: "user", content: [{ type: "image_url", image_url: { url: "data:text/plain;base64,AAAA" } }] }], cfg),
    err => err.status === 415 && /data URL must be an image/.test(err.message),
  );
  await assert.rejects(
    () => resolveImageInputs([{ role: "user", content: [{ type: "image_url", image_url: { url: "data:image/png;base64,not!base64" } }] }], cfg),
    err => err.status === 400 && /http or https|base64/.test(err.message),
  );
  await assert.rejects(
    () => resolveImageInputs([{ role: "user", content: [{ type: "image_url", image_url: { url: "data:image/png;base64," + Buffer.from("too-big").toString("base64") } }] }], {
      remoteEnabled: true, maxBytes: 2, maxRedirects: 1,
    }),
    err => err.status === 413 && /too large/.test(err.message),
  );
  await assert.rejects(
    () => resolveImageInputs([{ role: "user", content: [{ type: "image_url", image_url: { url: "ftp://example.com/a.png" } }] }], cfg),
    /http or https/,
  );
  await assert.rejects(
    () => resolveImageInputs([{ role: "user", content: [{ type: "image_url", image_url: { url: "https://u:p@example.com/a.png" } }] }], cfg),
    /credentials/,
  );
  await assert.rejects(
    () => resolveImageInputs([{ role: "user", content: [{ type: "image_url", image_url: { url: "https://example.com/a.png" } }] }], cfg, {
      resolveHost: async () => [{ address: "10.0.0.2" }],
    }),
    /private address/,
  );
});

test("image inputs: remote images convert to data URLs", async () => {
  const messages = [{ role: "user", content: [
    { type: "text", text: "Açıkla" },
    { type: "image_url", image_url: { url: "https://example.com/a.png" } },
  ] }];
  const out = await resolveImageInputs(messages, { remoteEnabled: true, maxBytes: 100, maxRedirects: 1 }, {
    resolveHost: async () => [{ address: "93.184.216.34" }],
    fetchFn: async () => new Response(Buffer.from([1, 2, 3]), {
      status: 200,
      headers: { "content-type": "image/png", "content-length": "3" },
    }),
  });
  assert.equal(out[0].content[0].text, "Açıkla");
  assert.equal(out[0].content[1].image_url.url, "data:image/png;base64,AQID");
  await assert.rejects(
    () => resolveImageInputs(messages, { remoteEnabled: true, maxBytes: 2, maxRedirects: 1 }, {
      resolveHost: async () => [{ address: "93.184.216.34" }],
      fetchFn: async () => new Response(Buffer.from([1, 2, 3]), {
        status: 200,
        headers: { "content-type": "image/png", "content-length": "3" },
      }),
    }),
    /too large/,
  );
});

test("image inputs: rejects non-image responses and redirect loops", async () => {
  const messages = [{ role: "user", content: [
    { type: "image_url", image_url: { url: "https://example.com/a.png" } },
  ] }];
  const resolveHost = async () => [{ address: "93.184.216.34" }];
  await assert.rejects(
    () => resolveImageInputs(messages, { remoteEnabled: true, maxBytes: 100, maxRedirects: 1 }, {
      resolveHost,
      fetchFn: async () => new Response("html", { status: 200, headers: { "content-type": "text/html" } }),
    }),
    err => err.status === 415 && /did not return an image/.test(err.message),
  );
  await assert.rejects(
    () => resolveImageInputs(messages, { remoteEnabled: true, maxBytes: 100, maxRedirects: 0 }, {
      resolveHost,
      fetchFn: async () => new Response("", { status: 302, headers: { location: "/b.png" } }),
    }),
    /redirected too many times/,
  );
});

test("media input: strict mime, base64 and size validation", () => {
  assert.equal(mediaInputConfig({ MAX_MEDIA_BYTES: "3" }).maxBytes, 3);
  const ok = normalizeMediaInput({
    data_url: "data:image/png;base64," + Buffer.from([1, 2, 3]).toString("base64"),
  }, mediaInputConfig({ MAX_MEDIA_BYTES: "10" }));
  assert.equal(ok.mime, "image/png");
  assert.deepEqual([...ok.buffer], [1, 2, 3]);
  assert.throws(
    () => normalizeMediaInput({ mime: "text/html", b64: Buffer.from("<h1>x</h1>").toString("base64") }),
    err => err.status === 415 && /not allowed/.test(err.message),
  );
  assert.throws(
    () => normalizeMediaInput({ mime: "image/png", b64: "not!base64" }),
    err => err.status === 400 && /base64/.test(err.message),
  );
  assert.throws(
    () => normalizeMediaInput({ mime: "image/png", b64: Buffer.from("abcd").toString("base64") }, mediaInputConfig({ MAX_MEDIA_BYTES: "2" })),
    err => err.status === 413 && /too large/.test(err.message),
  );
});

test("storage.mediaKey: scoped + ext", () => {
  const k = mediaKey("user-abc", "image/png");
  assert.ok(k.startsWith("u/user-abc/"));
  assert.ok(k.endsWith(".png"));
});

test("billing.microsToCents: ceil", () => {
  assert.equal(microsToCents(10000), 1);
  assert.equal(microsToCents(15000), 2);
  assert.equal(microsToCents(0), 0);
});

test("tokens: per-provider extractors", () => {
  assert.deepEqual(usageFromOpenAI({ usage: { prompt_tokens: 10, completion_tokens: 5 } }), { in: 10, out: 5 });
  assert.equal(usageFromOpenAI({}), null);
  assert.deepEqual(usageFromAnthropic({ type: "message_start", message: { usage: { input_tokens: 12, output_tokens: 1 } } }), { in: 12, out: 1 });
  assert.deepEqual(usageFromAnthropic({ type: "message_delta", usage: { output_tokens: 20 } }), { in: 0, out: 20 });
  assert.deepEqual(usageFromGemini({ usageMetadata: { promptTokenCount: 8, candidatesTokenCount: 30 } }), { in: 8, out: 30 });
  assert.deepEqual(usageFromOllama({ done: true, prompt_eval_count: 7, eval_count: 42 }), { in: 7, out: 42 });
  assert.equal(usageFromOllama({ done: false }), null);
});

test("tokens: accumulator keeps max across a stream", () => {
  const acc = makeUsageAccumulator("anthropic");
  acc.observe({ type: "message_start", message: { usage: { input_tokens: 12, output_tokens: 1 } } });
  acc.observe({ type: "message_delta", usage: { output_tokens: 8 } });
  acc.observe({ type: "message_delta", usage: { output_tokens: 25 } });
  assert.ok(acc.seen());
  assert.deepEqual(acc.get(), { in: 12, out: 25 });
});
