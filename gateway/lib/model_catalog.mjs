// Model kataloğu — /v1/models'in canlı kaynağı.
//
// Amaç: istemcilerdeki (web + Android) sabit, bayatlayan model listelerini
// tek bir canlı kaynakla değiştirmek.
//
// - Yerel modeller Ollama'nın /api/tags ucundan **canlı** okunur.
// - Bulut modelleri küratörlü bir listedir; sağlayıcı anahtarı yoksa entry
//   listede kalır ama `available:false` + dürüst gerekçe ile döner
//   (istemci pasif gösterir — "desteklenmeyeni taklit etme" ilkesi).
// - Saf fonksiyonlar ayrıştırıldı: ağ olmadan test edilebilir.

/** Sağlayıcı anahtarı yokken bile gösterilecek küratörlü bulut modelleri. */
export const CLOUD_MODELS = {
  anthropic: [
    { id: "claude-opus-4-8", name: "Claude Opus 4.8", desc: "en yetenekli" },
    { id: "claude-sonnet-5", name: "Claude Sonnet 5", desc: "dengeli" },
    { id: "claude-haiku-4-5-20251001", name: "Claude Haiku 4.5", desc: "hızlı" },
  ],
  gemini: [
    { id: "gemini-3.5-flash", name: "Gemini 3.5 Flash", desc: "güncel kararlı" },
    { id: "gemini-3.1-pro-preview", name: "Gemini 3.1 Pro", desc: "önizleme" },
    { id: "gemini-3.1-flash-lite", name: "Gemini 3.1 Flash Lite", desc: "ekonomik" },
  ],
  openai: [
    { id: "gpt-5.6", name: "GPT-5.6 Sol", desc: "amiral gemisi" },
    { id: "gpt-5.6-terra", name: "GPT-5.6 Terra", desc: "dengeli" },
    { id: "gpt-5.6-luna", name: "GPT-5.6 Luna", desc: "ekonomik" },
  ],
};

export const PROVIDER_ENV = {
  anthropic: "ANTHROPIC_API_KEY",
  gemini: "GEMINI_API_KEY",
  openai: "OPENAI_API_KEY",
};

export const GROUPS = {
  auto: "Otomatik",
  ollama: "Yerel · Ollama",
  cloud: "Bulut · API Key",
  agent: "Ajan",
};

/**
 * ALLOW_MODELS allowlist denetimi. Boş liste = her şey serbest.
 * `provider/*` biçimini destekler (gateway.mjs ile aynı semantik).
 */
export function isAllowed(id, allow = []) {
  if (!allow.length) return true;
  return allow.some((rule) => {
    if (rule === id) return true;
    if (rule.endsWith("/*")) return id.startsWith(rule.slice(0, -1));
    return false;
  });
}

/** Ollama /api/tags yanıtını normalize eder. Bozuk/eksik veri → boş liste. */
export function parseOllamaTags(payload) {
  const list = payload && Array.isArray(payload.models) ? payload.models : [];
  return list
    .map((m) => {
      const name = typeof m?.name === "string" ? m.name.trim() : "";
      if (!name) return null;
      const d = m.details || {};
      return {
        name,
        sizeBytes: Number.isFinite(m.size) ? m.size : null,
        parameterSize: typeof d.parameter_size === "string" ? d.parameter_size : null,
        quantization: typeof d.quantization_level === "string" ? d.quantization_level : null,
        family: typeof d.family === "string" ? d.family : null,
      };
    })
    .filter(Boolean)
    .sort((a, b) => a.name.localeCompare(b.name));
}

/* ------------------------- araç (agentic) yeteneği ------------------------ */
//
// Ajan modu yalnız araç çağırabilen modellerde anlamlıdır; desteklemeyen bir
// modelde araç döngüsü ya boş döner ya da model uydurur. Bu yüzden yetenek
// katalogda açıkça taşınır ve istemciler pasif/rozetli gösterir.
//
// Tespit iki katmanlı: önce Ollama'ya sorulur (`/api/show` → capabilities),
// ulaşılamazsa bilinen aileye bakılır. Hangi yolun kullanıldığı `toolsSource`
// ile bildirilir — tahmin, ölçümle aynı şeymiş gibi sunulmaz.

/**
 * Araç çağırmayı desteklediği bilinen Ollama aileleri/etiket önekleri.
 * Probe başarısız olduğunda kullanılır; elle güncel tutulur.
 */
export const TOOL_CAPABLE_FAMILIES = [
  "qwen3", "qwen2.5", "qwen2", "qwq",
  "llama3.3", "llama3.2", "llama3.1",
  "mistral-nemo", "mistral-large", "mistral-small", "mixtral",
  "firefunction", "command-r", "hermes3",
  "granite3", "granite4", "nemotron", "athene",
  "gemma4", "lfm2.5", "deepseek-r1", "phi4-mini", "gpt-oss",
];

/** Düşünme izi ürettiği bilinen Ollama aileleri; yalnız probe yoksa kullanılır. */
export const THINKING_CAPABLE_FAMILIES = [
  "qwen3", "qwq", "deepseek-r1", "gpt-oss",
  "nemotron", "lfm2.5", "gemma4",
];

/** Model etiketinden ("qwen3.6:35b") aile tahmini yapar. Saf. */
export function familySupportsTools(modelName, families = TOOL_CAPABLE_FAMILIES) {
  const name = String(modelName || "").toLowerCase();
  if (!name) return false;
  const bare = name.split(":")[0];
  return families.some((f) => bare === f || bare.startsWith(f));
}

/** Model etiketinden düşünme desteği için muhafazakâr aile tahmini yapar. */
export function familySupportsThinking(modelName, families = THINKING_CAPABLE_FAMILIES) {
  const name = String(modelName || "").toLowerCase();
  if (!name) return false;
  const bare = name.split(":")[0];
  return families.some((f) => bare === f || bare.startsWith(f));
}

/** Ollama'nın seviyeli düşünme kullanan modellerini aç-kapat modellerinden ayırır. */
export function thinkingModeForModel(modelName, thinking) {
  if (!thinking) return "none";
  return String(modelName || "").toLowerCase().startsWith("gpt-oss") ? "levels" : "toggle";
}

/** `/api/show` yanıtındaki capabilities dizisinde araç desteği var mı. Saf. */
export function parseShowCapabilities(payload) {
  const caps = payload && Array.isArray(payload.capabilities) ? payload.capabilities : null;
  if (!caps) return null;                       // alan yok → bilinmiyor (probe başarısız sayılır)
  return caps.some((c) => String(c).toLowerCase() === "tools");
}

/** `/api/show` capability dizisini tek seferde web kataloğunun iki alanına çevirir. */
export function parseShowCapabilitySet(payload) {
  const caps = payload && Array.isArray(payload.capabilities) ? payload.capabilities : null;
  if (!caps) return null;
  const names = new Set(caps.map((c) => String(c).toLowerCase()));
  return { tools: names.has("tools"), thinking: names.has("thinking") };
}

/** Tek model için araç ve düşünme yeteneklerini aynı `/api/show` çağrısıyla ölçer. */
export async function probeOllamaCapabilities(
  baseUrl,
  modelName,
  { timeoutMs = 2000, fetchImpl = fetch } = {},
) {
  const base = String(baseUrl || "").replace(/\/$/, "");
  if (!base || !modelName) return null;
  const ac = new AbortController();
  const timer = setTimeout(() => ac.abort(), timeoutMs);
  try {
    const r = await fetchImpl(`${base}/api/show`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ model: modelName }),
      signal: ac.signal,
    });
    if (!r.ok) return null;
    return parseShowCapabilitySet(await r.json());
  } catch {
    return null;
  } finally {
    clearTimeout(timer);
  }
}

/**
 * Tek bir model için araç yeteneğini Ollama'ya sorar.
 * @returns {Promise<boolean|null>} null = sorulamadı (çağıran aileye düşer)
 */
export async function probeOllamaTools(baseUrl, modelName, { timeoutMs = 2000, fetchImpl = fetch } = {}) {
  const result = await probeOllamaCapabilities(baseUrl, modelName, { timeoutMs, fetchImpl });
  return result ? result.tools : null;
}

/**
 * Ollama listesindeki her modele `tools` + `toolsSource` ekler.
 * Probe'lar paralel çalışır ve tek tek başarısız olabilir; hata fırlatmaz.
 */
export async function annotateToolSupport(baseUrl, models, opts = {}) {
  return Promise.all(
    models.map(async (m) => {
      const probed = await probeOllamaCapabilities(baseUrl, m.name, opts);
      if (probed !== null) {
        return {
          ...m,
          tools: probed.tools,
          toolsSource: "probe",
          thinking: probed.thinking,
          thinkingSource: "probe",
          thinkingMode: thinkingModeForModel(m.name, probed.thinking),
        };
      }
      const tools = familySupportsTools(m.name, opts.families);
      const thinking = familySupportsThinking(m.name, opts.thinkingFamilies);
      return {
        ...m,
        tools,
        toolsSource: tools ? "family" : "unknown",
        thinking,
        thinkingSource: thinking ? "family" : "unknown",
        thinkingMode: thinkingModeForModel(m.name, thinking),
      };
    }),
  );
}

/** İnsan okunur boyut; bilinmiyorsa null. */
export function humanSize(bytes) {
  if (!Number.isFinite(bytes) || bytes <= 0) return null;
  const gb = bytes / 1_073_741_824;
  if (gb >= 1) return `${gb.toFixed(1)} GB`;
  return `${Math.round(bytes / 1_048_576)} MB`;
}

/**
 * Katalogu kurar. Tamamen saf: ağ çağrısı yapmaz, çağıran taraf besler.
 *
 * @param {object}   o
 * @param {Array}    o.ollamaModels  parseOllamaTags çıktısı
 * @param {object}   o.keys          { anthropic, gemini, openai } — dolu = anahtar var
 * @param {string[]} o.allow         ALLOW_MODELS
 * @param {string}   o.ollamaError   Ollama'ya ulaşılamadıysa dürüst mesaj
 * @param {boolean}  o.openclaw      ajan katmanı yapılandırılmış mı
 */
export function buildCatalog({
  ollamaModels = [],
  keys = {},
  allow = [],
  ollamaError = null,
  openclaw = true,
} = {}) {
  const data = [];

  data.push({
    id: "auto",
    name: "Dinamik Yönlendirme",
    desc: "gateway göreve göre model seçer",
    provider: "gateway",
    group: GROUPS.auto,
    available: true,
    // Dinamik rota buluta da düşebilir; seçilmeden önce ortak bir yetenek
    // garantisi yoktur. Çalışmayabilecek kontrolleri iyimser biçimde açmayız.
    tools: false,
    toolsSource: "gateway",
    thinking: false,
    thinkingSource: "gateway",
    thinkingMode: "none",
  });

  for (const m of ollamaModels) {
    const id = `ollama/${m.name}`;
    const parts = [m.parameterSize, m.quantization, humanSize(m.sizeBytes)].filter(Boolean);
    // annotateToolSupport çalıştıysa m.tools doludur; çalışmadıysa aileye bak.
    const tools = typeof m.tools === "boolean" ? m.tools : familySupportsTools(m.name);
    const thinking = typeof m.thinking === "boolean" ? m.thinking : familySupportsThinking(m.name);
    data.push({
      id,
      name: m.name,
      desc: parts.join(" · ") || "yerel",
      provider: "ollama",
      group: GROUPS.ollama,
      available: true,
      sizeBytes: m.sizeBytes,
      family: m.family,
      tools,
      toolsSource: m.toolsSource || (tools ? "family" : "unknown"),
      thinking,
      thinkingSource: m.thinkingSource || (thinking ? "family" : "unknown"),
      thinkingMode: m.thinkingMode || thinkingModeForModel(m.name, thinking),
    });
  }

  for (const [provider, models] of Object.entries(CLOUD_MODELS)) {
    const hasKey = Boolean(keys[provider]);
    for (const m of models) {
      data.push({
        id: `${provider}/${m.id}`,
        name: m.name,
        desc: m.desc,
        provider,
        group: GROUPS.cloud,
        available: hasKey,
        reason: hasKey ? undefined : `${PROVIDER_ENV[provider]} tanımlı değil`,
        // Sağlayıcı teorik olarak desteklese bile NOVA'nın mevcut gateway yolu
        // bu kontrolleri uygulamıyor. UI'da çalışmayan bir düğmeyi etkin göstermeyiz.
        tools: false,
        toolsSource: "gateway",
        thinking: false,
        thinkingSource: "gateway",
        thinkingMode: "none",
      });
    }
  }

  if (openclaw) {
    data.push({
      id: "openclaw/default",
      name: "OpenClaw Ajanı",
      desc: "ajan katmanı",
      provider: "openclaw",
      group: GROUPS.agent,
      available: true,
      // OpenClaw zaten ayrı bir ajan hedefidir; NOVA'nın Ollama araç/takım
      // katmanını bu hedefin üzerine yeniden bindirmeyiz.
      tools: false,
      toolsSource: "agent",
      thinking: false,
      thinkingSource: "agent",
      thinkingMode: "none",
    });
  }

  const filtered = data.filter((m) => isAllowed(m.id, allow));

  // İstemci varsayılanı: canlı Ollama listesinin ilki. Yoksa null —
  // istemci "auto"ya kendiliğinden düşmez, kullanıcı seçer.
  const firstLocal = filtered.find((m) => m.provider === "ollama" && m.available);

  return {
    data: filtered,
    defaultModel: firstLocal ? firstLocal.id : null,
    ollama: {
      ok: !ollamaError,
      count: ollamaModels.length,
      error: ollamaError || undefined,
    },
  };
}

/**
 * Ollama'dan yüklü modelleri çeker. Hata durumunda ASLA fırlatmaz:
 * `{ models: [], error }` döner ki katalog yine de kurulabilsin.
 */
export async function fetchOllamaModels(baseUrl, { timeoutMs = 2500, fetchImpl = fetch } = {}) {
  const base = String(baseUrl || "").replace(/\/$/, "");
  if (!base) return { models: [], error: "OLLAMA_URL tanımlı değil" };
  const ac = new AbortController();
  const timer = setTimeout(() => ac.abort(), timeoutMs);
  try {
    const r = await fetchImpl(`${base}/api/tags`, { signal: ac.signal });
    if (!r.ok) return { models: [], error: `Ollama ${r.status}` };
    return { models: parseOllamaTags(await r.json()), error: null };
  } catch (e) {
    const msg = e?.name === "AbortError" ? "Ollama zaman aşımı" : "Ollama'ya ulaşılamadı";
    return { models: [], error: msg };
  } finally {
    clearTimeout(timer);
  }
}

/**
 * DEFAULT_MODEL / ROUTE_* değerleri kurulumdan kuruluma tutmaz: compose'da
 * yazan tag makinede yüklü olmayabilir. O durumda Ollama 404 döner ve
 * "Dinamik Yönlendirme" hiç çalışmaz. Bu fonksiyon yüklü modeller arasından
 * makul bir vekil seçer — ama SESSİZCE değil: substituted=true döner ki
 * çağıran taraf kullanıcıya hangi modele düştüğünü söyleyebilsin.
 *
 * Sıralama: aynı taban ad (qwen3:14b → qwen3:8b) → aynı aile (qwen3.5 → qwen3)
 * → araç destekleyen herhangi bir model → ilk yüklü model.
 *
 * @param {string} wanted    "qwen3:14b" (provider öneki OLMADAN)
 * @param {Array}  installed parseOllamaTags çıktısı ([{name, tools?}, …])
 * @returns {{model:string, substituted:boolean, reason:string}}
 */
export function pickInstalledModel(wanted, installed = []) {
  const list = (installed || []).filter((m) => m && typeof m.name === "string");
  const want = String(wanted || "").trim();
  if (!list.length) return { model: want, substituted: false, reason: "no-catalog" };
  if (list.some((m) => m.name === want)) return { model: want, substituted: false, reason: "exact" };

  const base = want.split(":")[0];
  // "qwen3.5" → "qwen3" → "qwen": sürüm son ekini kademeli kırp
  const family = base.replace(/[.\-_]?\d+(\.\d+)*$/, "") || base;
  const prefer = (arr) =>
    arr.find((m) => m.name.endsWith(":latest")) || arr[0];

  const sameBase = list.filter((m) => m.name.split(":")[0] === base);
  if (sameBase.length) return { model: prefer(sameBase).name, substituted: true, reason: "same-base" };

  const sameFamily = family
    ? list.filter((m) => m.name.split(":")[0].startsWith(family))
    : [];
  if (sameFamily.length) return { model: prefer(sameFamily).name, substituted: true, reason: "same-family" };

  const toolCapable = list.filter((m) => m.tools === true);
  if (toolCapable.length) return { model: prefer(toolCapable).name, substituted: true, reason: "tool-capable" };

  return { model: prefer(list).name, substituted: true, reason: "first-installed" };
}

/** Küçük TTL cache — model listesi her istekte Ollama'yı dürtmesin. */
export function createCatalogCache(ttlMs = 30_000) {
  let hit = null;
  return {
    get(now = Date.now()) {
      if (hit && now - hit.at < ttlMs) return hit.value;
      return null;
    },
    set(value, now = Date.now()) {
      hit = { at: now, value };
      return value;
    },
    clear() {
      hit = null;
    },
  };
}
