import test from "node:test";
import assert from "node:assert/strict";
import {
  buildCatalog,
  createCatalogCache,
  fetchOllamaModels,
  humanSize,
  isAllowed,
  parseOllamaTags,
  CLOUD_MODELS,
  familySupportsTools,
  familySupportsThinking,
  parseShowCapabilities,
  parseShowCapabilitySet,
  probeOllamaCapabilities,
  thinkingModeForModel,
  annotateToolSupport,
} from "../lib/model_catalog.mjs";

const tags = {
  models: [
    {
      name: "qwen3:14b",
      size: 9_000_000_000,
      details: { parameter_size: "14B", quantization_level: "Q4_K_M", family: "qwen3" },
    },
    { name: "gemma4:e2b", size: 7_200_000_000, details: { parameter_size: "2B" } },
  ],
};

test("parseOllamaTags: normalizes, sorts, and survives junk", () => {
  const parsed = parseOllamaTags(tags);
  assert.deepEqual(parsed.map((m) => m.name), ["gemma4:e2b", "qwen3:14b"]);
  assert.equal(parsed[1].parameterSize, "14B");
  assert.equal(parsed[1].family, "qwen3");
  // Bozuk/eksik girdi asla fırlatmaz.
  assert.deepEqual(parseOllamaTags(null), []);
  assert.deepEqual(parseOllamaTags({}), []);
  assert.deepEqual(parseOllamaTags({ models: [{}, { name: "  " }, null] }), []);
});

test("humanSize: GB/MB eşikleri ve geçersiz girdi", () => {
  assert.equal(humanSize(9_000_000_000), "8.4 GB");
  assert.equal(humanSize(300_000_000), "286 MB");
  assert.equal(humanSize(0), null);
  assert.equal(humanSize(undefined), null);
});

test("isAllowed: boş allowlist serbest, provider/* joker", () => {
  assert.equal(isAllowed("ollama/x", []), true);
  assert.equal(isAllowed("ollama/x", ["ollama/*"]), true);
  assert.equal(isAllowed("openai/gpt-5.6", ["ollama/*"]), false);
  assert.equal(isAllowed("openai/gpt-5.6", ["openai/gpt-5.6"]), true);
});

test("buildCatalog: canlı Ollama modelleri listeye girer ve varsayılan olur", () => {
  const cat = buildCatalog({ ollamaModels: parseOllamaTags(tags), keys: {}, allow: [] });
  const ids = cat.data.map((m) => m.id);
  assert.ok(ids.includes("ollama/qwen3:14b"));
  assert.ok(ids.includes("ollama/gemma4:e2b"));
  // Varsayılan = ilk canlı yerel model (alfabetik sıralı listenin ilki).
  assert.equal(cat.defaultModel, "ollama/gemma4:e2b");
  assert.equal(cat.ollama.ok, true);
  assert.equal(cat.ollama.count, 2);
  // Açıklama satırı gerçek meta veriden kurulur.
  const q = cat.data.find((m) => m.id === "ollama/qwen3:14b");
  assert.equal(q.desc, "14B · Q4_K_M · 8.4 GB");
});

test("buildCatalog: anahtarsız bulut modelleri listede ama pasif ve gerekçeli", () => {
  const cat = buildCatalog({ ollamaModels: [], keys: { openai: "sk-x" }, allow: [] });
  const claude = cat.data.find((m) => m.id === "anthropic/claude-opus-4-8");
  const gpt = cat.data.find((m) => m.id === "openai/gpt-5.6");
  assert.equal(claude.available, false);
  assert.equal(claude.reason, "ANTHROPIC_API_KEY tanımlı değil");
  assert.equal(gpt.available, true);
  assert.equal(gpt.reason, undefined);
});

test("buildCatalog: yerel model yoksa varsayılan null — auto'ya sessizce düşmez", () => {
  const cat = buildCatalog({ ollamaModels: [], keys: {}, ollamaError: "Ollama'ya ulaşılamadı" });
  assert.equal(cat.defaultModel, null);
  assert.equal(cat.ollama.ok, false);
  assert.equal(cat.ollama.error, "Ollama'ya ulaşılamadı");
  // "auto" yine listede, ama kendiliğinden seçilmiyor.
  assert.ok(cat.data.some((m) => m.id === "auto"));
});

test("buildCatalog: ALLOW_MODELS listeyi kısar", () => {
  const cat = buildCatalog({
    ollamaModels: parseOllamaTags(tags),
    keys: { openai: "sk-x" },
    allow: ["ollama/*"],
  });
  assert.ok(cat.data.every((m) => m.id.startsWith("ollama/")));
  assert.equal(cat.defaultModel, "ollama/gemma4:e2b");
});

test("buildCatalog: openclaw yapılandırılmamışsa ajan girdisi yok", () => {
  const off = buildCatalog({ openclaw: false });
  assert.equal(off.data.some((m) => m.id === "openclaw/default"), false);
  const on = buildCatalog({ openclaw: true });
  assert.equal(on.data.some((m) => m.id === "openclaw/default"), true);
});

test("bulut kataloğu güncel 2026 model kimlikleri taşır", () => {
  // Bayat model adları bu projede tekrar eden bir hataydı; kilitliyoruz.
  assert.ok(CLOUD_MODELS.anthropic.some((m) => m.id === "claude-opus-4-8"));
  assert.ok(CLOUD_MODELS.gemini.some((m) => m.id === "gemini-3.5-flash"));
  assert.ok(CLOUD_MODELS.openai.some((m) => m.id === "gpt-5.6"));
  const all = Object.values(CLOUD_MODELS).flat().map((m) => m.id);
  // Emekliye ayrılmış kimlikler geri sızmasın.
  for (const eski of ["gpt-4o-mini", "gemini-2.5-flash", "claude-sonnet-4-20250514"]) {
    assert.equal(all.includes(eski), false, `bayat model: ${eski}`);
  }
});

test("fetchOllamaModels: hata durumunda fırlatmaz, dürüst mesaj döner", async () => {
  const ok = await fetchOllamaModels("http://x", {
    fetchImpl: async () => ({ ok: true, json: async () => tags }),
  });
  assert.equal(ok.error, null);
  assert.equal(ok.models.length, 2);

  const down = await fetchOllamaModels("http://x", {
    fetchImpl: async () => { throw new Error("ECONNREFUSED"); },
  });
  assert.deepEqual(down.models, []);
  assert.equal(down.error, "Ollama'ya ulaşılamadı");

  const bad = await fetchOllamaModels("http://x", {
    fetchImpl: async () => ({ ok: false, status: 500 }),
  });
  assert.equal(bad.error, "Ollama 500");

  const empty = await fetchOllamaModels("");
  assert.equal(empty.error, "OLLAMA_URL tanımlı değil");
});

test("createCatalogCache: TTL içinde tutar, sonra düşer", () => {
  const cache = createCatalogCache(1000);
  assert.equal(cache.get(0), null);
  cache.set({ v: 1 }, 0);
  assert.deepEqual(cache.get(500), { v: 1 });
  assert.equal(cache.get(1500), null);
  cache.set({ v: 2 }, 2000);
  assert.deepEqual(cache.get(2100), { v: 2 });
  cache.clear();
  assert.equal(cache.get(2100), null);
});

/* ---------------- araç (agentic) yeteneği ---------------- */

test("familySupportsTools: bilinen aileleri etiketten tanır", () => {
  assert.equal(familySupportsTools("qwen3.6:35b"), true);
  assert.equal(familySupportsTools("lfm2.5:8b"), true);
  assert.equal(familySupportsTools("deepseek-r1:8b"), true);
  assert.equal(familySupportsTools("phi4-mini:3.8b"), true);
  assert.equal(familySupportsTools("granite4:3b"), true);
  assert.equal(familySupportsTools("gpt-oss:20b"), true);
  assert.equal(familySupportsTools("llama3.1:8b"), true);
  assert.equal(familySupportsTools("mistral-nemo"), true);
  assert.equal(familySupportsTools("phi3:mini"), false);
  assert.equal(familySupportsTools(""), false);
  assert.equal(familySupportsTools(null), false);
});

test("parseShowCapabilities: tools yeteneğini okur, alan yoksa null döner", () => {
  assert.equal(parseShowCapabilities({ capabilities: ["completion", "tools"] }), true);
  assert.equal(parseShowCapabilities({ capabilities: ["completion"] }), false);
  assert.equal(parseShowCapabilities({}), null);
  assert.equal(parseShowCapabilities(null), null);
});

test("parseShowCapabilitySet: tools ve thinking yeteneklerini tek yanıttan okur", () => {
  assert.deepEqual(
    parseShowCapabilitySet({ capabilities: ["completion", "tools", "thinking"] }),
    { tools: true, thinking: true },
  );
  assert.deepEqual(
    parseShowCapabilitySet({ capabilities: ["completion"] }),
    { tools: false, thinking: false },
  );
  assert.equal(parseShowCapabilitySet({}), null);
});

test("thinking aile ve mod politikası destekleneni seviyeli modelden ayırır", () => {
  assert.equal(familySupportsThinking("qwen3.5:4b"), true);
  assert.equal(familySupportsThinking("deepseek-r1:8b"), true);
  assert.equal(familySupportsThinking("hermes3:8b"), false);
  assert.equal(thinkingModeForModel("gpt-oss:20b", true), "levels");
  assert.equal(thinkingModeForModel("qwen3:8b", true), "toggle");
  assert.equal(thinkingModeForModel("hermes3:8b", false), "none");
});

test("probeOllamaCapabilities: tek /api/show çağrısında iki yeteneği döndürür", async () => {
  let calls = 0;
  const result = await probeOllamaCapabilities("http://o", "qwen3:8b", {
    fetchImpl: async () => {
      calls += 1;
      return { ok: true, json: async () => ({ capabilities: ["tools", "thinking"] }) };
    },
  });
  assert.equal(calls, 1);
  assert.deepEqual(result, { tools: true, thinking: true });
});

test("annotateToolSupport: probe önceliklidir, başarısızsa aileye düşer", async () => {
  const fetchImpl = async (url, init) => {
    const body = JSON.parse(init.body);
    if (body.model === "probed:latest") {
      return { ok: true, json: async () => ({ capabilities: ["completion", "tools"] }) };
    }
    return { ok: false };                       // probe başarısız → aile tahmini
  };
  const out = await annotateToolSupport("http://o", [
    { name: "probed:latest" },
    { name: "qwen3:8b" },
    { name: "phi3:mini" },
  ], { fetchImpl });

  assert.deepEqual(out.map((m) => [m.name, m.tools, m.toolsSource]), [
    ["probed:latest", true, "probe"],
    ["qwen3:8b", true, "family"],
    ["phi3:mini", false, "unknown"],
  ]);
  assert.deepEqual(out.map((m) => [m.name, m.thinking, m.thinkingSource, m.thinkingMode]), [
    ["probed:latest", false, "probe", "none"],
    ["qwen3:8b", true, "family", "toggle"],
    ["phi3:mini", false, "unknown", "none"],
  ]);
});

test("buildCatalog: yetenek alanını taşır; uygulanmayan bulut kontrollerini pasif tutar", () => {
  const cat = buildCatalog({
    ollamaModels: [
      { name: "qwen3:8b", tools: true, toolsSource: "probe" },
      { name: "phi3:mini" },                    // annotate çalışmamış → aileye bakılır
    ],
    keys: { anthropic: "k" },
    openclaw: true,
  });
  const by = (id) => cat.data.find((m) => m.id === id);
  assert.equal(by("ollama/qwen3:8b").tools, true);
  assert.equal(by("ollama/qwen3:8b").toolsSource, "probe");
  assert.equal(by("ollama/qwen3:8b").thinking, true);
  assert.equal(by("ollama/qwen3:8b").thinkingSource, "family");
  assert.equal(by("ollama/qwen3:8b").thinkingMode, "toggle");
  assert.equal(by("ollama/phi3:mini").tools, false);
  assert.equal(by("auto").tools, false);
  assert.equal(by("auto").thinking, false);
  assert.equal(by("auto").thinkingSource, "gateway");
  assert.equal(by("anthropic/claude-opus-4-8").tools, false);
  assert.equal(by("anthropic/claude-opus-4-8").toolsSource, "gateway");
  assert.equal(by("anthropic/claude-opus-4-8").thinking, false);
  assert.equal(by("anthropic/claude-opus-4-8").thinkingSource, "gateway");
  assert.equal(by("openclaw/default").tools, false);
  assert.equal(by("openclaw/default").toolsSource, "agent");
  assert.equal(by("openclaw/default").thinking, false);
});
