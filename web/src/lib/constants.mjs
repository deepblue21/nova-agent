// Uygulama sabitleri: modeller, çaba kademeleri, personalar, sağlayıcılar,
// ajan katmanları ve giriş kartları. Metinler mobil sürümle aynı sözlüğü kullanır.
import { Icons } from "./icons.mjs";
import { NovaMark } from "../ui/NovaMark.jsx";
import { Zap, Activity, Cloud, Cpu, Link2, Flame, Code2, GitBranch, Brain, CircleDot, Waves } from "lucide-react";
import { normalizeCapabilityFields } from "./model-capabilities.mjs";

/**
 * Gateway'e bağlıyken model listesi CANLI gelir (GET /v1/models → Ollama'da
 * yüklü modeller + anahtarı olan bulut sağlayıcıları). Aşağıdaki liste yalnız
 * gateway'e ulaşılamadığında kullanılan yedektir.
 */
export const FALLBACK_MODELS = [
  {
    group: "Otomatik · Gateway",
    items: [
      {
        id: "auto", name: "Dinamik Yönlendirme", desc: "gateway göreve göre model seçer",
        icon: NovaMark, provider: "gateway", model: "auto",
        tools: false, toolsSource: "gateway", thinking: false, thinkingSource: "gateway", thinkingMode: "none",
      },
    ],
  },
  {
    group: "Bulut · API Key",
    items: [
      { id: "opus", name: "Claude Opus 4.8", desc: "anthropic", icon: Cloud, provider: "anthropic", model: "claude-opus-4-8", tools: false, toolsSource: "gateway", thinking: false, thinkingSource: "gateway", thinkingMode: "none" },
      { id: "sonnet", name: "Claude Sonnet 5", desc: "anthropic", icon: Cloud, provider: "anthropic", model: "claude-sonnet-5", tools: false, toolsSource: "gateway", thinking: false, thinkingSource: "gateway", thinkingMode: "none" },
      { id: "gem-flash", name: "Gemini 3.5 Flash", desc: "google", icon: Cloud, provider: "gemini", model: "gemini-3.5-flash", tools: false, toolsSource: "gateway", thinking: false, thinkingSource: "gateway", thinkingMode: "none" },
      { id: "gem-pro", name: "Gemini 3.1 Pro", desc: "google · önizleme", icon: Cloud, provider: "gemini", model: "gemini-3.1-pro-preview", tools: false, toolsSource: "gateway", thinking: false, thinkingSource: "gateway", thinkingMode: "none" },
      { id: "gpt", name: "GPT-5.6 Sol", desc: "openai uyumlu", icon: Cloud, provider: "openai", model: "gpt-5.6", tools: false, toolsSource: "gateway", thinking: false, thinkingSource: "gateway", thinkingMode: "none" },
    ],
  },
  {
    group: "Ajan · Gateway",
    items: [
      {
        id: "openclaw", name: "OpenClaw Ajanı", desc: "openclaw/default",
        icon: Link2, provider: "gateway", model: "openclaw/default",
        tools: false, toolsSource: "agent", thinking: false, thinkingSource: "agent", thinkingMode: "none",
      },
    ],
  },
];

/**
 * Canlı katalog girdisini seçicinin beklediği biçime çevirir. Tüm canlı
 * modeller gateway üzerinden çağrılır: sağlayıcı anahtarları sunucuda kalır.
 */
export function liveGroupsFrom(catalog) {
  const iconFor = (p) => (p === "ollama" ? Cpu : p === "gateway" ? NovaMark : p === "openclaw" ? Link2 : Cloud);
  const byGroup = new Map();
  for (const m of (catalog && catalog.data) || []) {
    const item = {
      id: "live:" + m.id,
      name: m.name || m.id,
      desc: m.desc || m.id,
      icon: iconFor(m.provider),
      provider: "gateway",
      model: m.id,
      available: m.available !== false,
      reason: m.reason || "",
      srcProvider: m.provider,
      ...normalizeCapabilityFields(m),
    };
    const g = m.group || "Diğer";
    if (!byGroup.has(g)) byGroup.set(g, []);
    byGroup.get(g).push(item);
  }
  return [...byGroup.entries()].map(([group, items]) => ({ group, items }));
}

export const EFFORTS = [
  { id: "fast", name: "Hızlı", icon: Zap, sys: "Çok kısa, net ve doğrudan yanıt ver. Gereksiz açıklamadan kaçın." },
  { id: "balanced", name: "Dengeli", icon: Activity, sys: "Dengeli, açık ve yeterli ölçüde yanıt ver." },
  { id: "deep", name: "Derin", icon: Brain, sys: "Konuyu derinlemesine ele al; gerektiğinde adım adım açıkla." },
  { id: "max", name: "Maks", icon: Flame, sys: "Kapsamlı, titiz ve detaylı bir analiz sun." },
];

export const PERSONAS = [
  {
    id: "nova", name: "Genel NOVA", short: "NOVA",
    desc: "Günlük, teknik ve pratik yardımcı", icon: NovaMark,
    sys: "Genel görevlerde dengeli davran; hedefi netleştir, uygulanabilir adımlar ver ve gereksiz uzatma.",
  },
  {
    id: "code-review", name: "Kod İnceleyici", short: "Kod",
    desc: "Bug, test, güvenlik ve sade düzeltme", icon: Code2,
    sys: "Kod üzerinde çalışırken önce hataları, güvenlik risklerini, davranış regresyonlarını ve test boşluklarını öne çıkar. Düzeltmelerde küçük, doğrulanabilir adımlar izle.",
  },
  {
    id: "soc", name: "SOC Analisti", short: "SOC",
    desc: "Triage, IOC, etki ve kanıt zinciri", icon: Activity,
    sys: "Güvenlik olaylarında SOC analisti gibi davran: IOC, etki, kapsam, hipotez, triage adımları, yanlış pozitif olasılığı ve kanıt zincirini belirt. Komut önerilerinde yıkıcı olmayan seçenekleri öncele.",
  },
  {
    id: "architect", name: "Mimari Planlayıcı", short: "Mimari",
    desc: "Faz, risk, bağımlılık ve rollout", icon: GitBranch,
    sys: "Mimari ve ürün kararlarında trade-off, bağımlılık, risk, kullanıcı etkisi ve rollout planını açık belirt. Büyük değişiklikleri fazlara ayır.",
  },
  {
    id: "local-llm", name: "Yerel LLM Koçu", short: "Yerel",
    desc: "Ollama, VRAM, model seçimi ve gizlilik", icon: Cpu,
    sys: "Yerel LLM ve Ollama akışlarında donanım, VRAM, model boyutu, gecikme, gizlilik ve offline çalışma kısıtlarını dikkate al. Önerileri local-first olacak şekilde ver.",
  },
  {
    id: "custom", name: "Özel Persona", short: "Özel",
    desc: "Kendi sistem yönergeni kaydet", icon: Brain, sys: "",
  },
];

export const PROV_ORDER = ["gateway", "ollama", "anthropic", "gemini", "openai"];

export const PROV_META = {
  gateway: {
    label: "Gateway", icon: Link2,
    hint: "Tüm sağlayıcıları tek noktadan yönlendirir (önerilen). Anahtarlar sunucuda kalır, CORS sorunu olmaz.",
    keyLabel: "Anahtar (opsiyonel)",
  },
  ollama: {
    label: "Ollama (Yerel)", icon: Cpu,
    hint: "Yerel modeller. Tarayıcıdan erişim için Ollama'yı OLLAMA_ORIGINS=* ile başlat.",
    keyLabel: null,
  },
  anthropic: {
    label: "Anthropic", icon: Cloud,
    hint: "Boş bırakırsan bu önizleme yerleşik bağlantıyı kullanır.",
    keyLabel: "x-api-key",
  },
  gemini: {
    label: "Google Gemini", icon: Cloud,
    hint: "API key ile tarayıcıdan çağrılabilir.",
    keyLabel: "API key",
  },
  openai: {
    label: "OpenAI / uyumlu", icon: Cloud,
    hint: "Tarayıcı CORS engelliyse Gateway üzerinden kullan.",
    keyLabel: "API key",
  },
};

export const AGENTS = [
  { id: "direct", name: "Doğrudan LLM", desc: "Ara katman yok", icon: CircleDot, ph: "" },
  { id: "openclaw", name: "OpenClaw", desc: "Self-hosted ajan · skills", icon: Link2, ph: "http://localhost:8787" },
  { id: "hermes", name: "Hermes Agent", desc: "Çok adımlı otomasyon", icon: Link2, ph: "http://localhost:4040" },
];

export const THINK_STEPS = [
  "Bağlam analiz ediliyor",
  "Niyet çözümleniyor",
  "Strateji oluşturuluyor",
  "Yanıt sentezleniyor",
];

export const SUGGESTIONS = [
  { icon: Cpu, cat: "Kod", t: "Spring Boot REST endpoint örneği yaz", d: "JWT auth + validation ile" },
  { icon: Waves, cat: "Güvenlik", t: "Bir log satırında IOC ara ve açıkla", d: "SOC/DFIR bakışıyla" },
  { icon: Activity, cat: "Analiz", t: "Qwen3 14B vs Gemma 4 karşılaştır", d: "yerel kullanım için" },
  { icon: Brain, cat: "Fikir", t: "RSS haber dedup mantığı öner", d: "embedding tabanlı" },
];

export const SCHEDULE_OPTIONS = [
  { value: "every:30m", label: "Her 30 dakika" },
  { value: "every:1h", label: "Her saat" },
  { value: "every:6h", label: "Her 6 saat" },
  { value: "every:1d", label: "Her gün (24s)" },
  { value: "daily:09:00", label: "Her gün 09:00" },
  { value: "daily:18:00", label: "Her gün 18:00" },
];

/**
 * Yürütme hedefi. Mobildeki `ExecutionPolicy` ile aynı kimlikler; web'de
 * cihaz-üstü model bulunmadığı için telefona özgü seçenekler pasif gösterilir
 * ve nedeni yazılır (desteklenmeyeni taklit etme ilkesi).
 */
export const EXECUTION_TARGETS = [
  {
    id: "GATEWAY_ONLY", label: "Yalnız Gateway", note: "her zaman PC",
    icon: Icons.gateway, webSupported: true,
    desc: "İstemler PC'deki gateway üzerinden çalışır; bulut anahtarları sunucuda kalır.",
  },
  {
    id: "LOCAL_FIRST", label: "Yerel öncelikli", note: "önce telefon",
    icon: Icons.local, webSupported: false,
    reason: "Cihaz-üstü model yalnız Android istemcisinde çalışır. Tarayıcıda yerel motor yok.",
    desc: "Telefon önce kendi modelini dener, gerekirse izin isteyip PC'ye devreder.",
  },
  {
    id: "LOCAL_ONLY", label: "Yalnız telefon", note: "çevrimdışı",
    icon: Icons.shield, webSupported: false,
    reason: "Tam çevrimdışı çalışma telefona özgüdür; web istemcisi gateway'e bağlıdır.",
    desc: "İstemler hiçbir koşulda cihaz dışına çıkmaz.",
  },
  {
    id: "HYBRID", label: "Hibrit", note: "akıllı seçim",
    icon: Icons.team, webSupported: false,
    reason: "Hibrit yönlendirme pil/ısı/uzunluk sinyallerini okur; bunlar tarayıcıda yok.",
    desc: "Kısa işler telefonda, uzun işler ve düşük pil durumunda PC'de.",
  },
];
