// Upstream hata sınıflandırıcı.
//
// Üretimde ham upstream metnini istemciye vermeyiz (iç ayrıntı sızdırabilir).
// Ama "upstream error" demek de kullanıcıyı kör bırakıyor. Çözüm: hatayı
// GATEWAY'İN KENDİ ürettiği sabit bir kod + Türkçe mesaja indirgemek.
//
// Buradaki mesajlar sabittir; upstream'den gelen metin ASLA mesaja
// kopyalanmaz. Yalnızca istemcinin kendi gönderdiği model adı geri yazılır —
// o zaten istemcinin bildiği bir değer, sızıntı değil.
//
// Saf fonksiyonlar: HTTP sunucusu olmadan test edilebilir.

export const UP = {
  UNREACHABLE: "upstream_unreachable",
  TIMEOUT: "upstream_timeout",
  MODEL_MISSING: "model_missing",
  THINK_UNSUPPORTED: "think_unsupported",
  TOOLS_UNSUPPORTED: "tools_unsupported",
  VISION_UNSUPPORTED: "vision_unsupported",
  OUT_OF_MEMORY: "out_of_memory",
  CONTEXT_TOO_LONG: "context_too_long",
  KEY_MISSING: "key_missing",
  AUTH_REJECTED: "auth_rejected",
  RATE_LIMITED: "rate_limited",
  UPSTREAM_DOWN: "upstream_down",
  BAD_REQUEST: "bad_request",
  UNKNOWN: "unknown",
  // Hata değil, bilgi notu: istenen model yüklü olmadığı için vekil seçildi.
  MODEL_SUBSTITUTED: "model_substituted",
};

const raw = (err) => String((err && err.message) || err || "");

// Ollama /api/chat 400 gövdeleri sürümden sürüme değişiyor; hepsini yakala.
const RULES = [
  [UP.THINK_UNSUPPORTED, /does not support think|thinking is not supported|"?think"?\s+is not supported|thinking.*not supported/i],
  [UP.TOOLS_UNSUPPORTED, /does not support tool|tools?\s+is not supported|tool (?:calling|use).*not supported|registry does not support tools/i],
  [UP.VISION_UNSUPPORTED, /does not support (?:image|vision)|image input.*not supported/i],
  [UP.MODEL_MISSING, /not found, try pulling|model '[^']*' not found|model "[^"]*" not found|no such model|file does not exist/i],
  [UP.OUT_OF_MEMORY, /requires more system memory|out of memory|cudaMalloc|insufficient memory|not enough memory/i],
  [UP.CONTEXT_TOO_LONG, /context length|context window|too many tokens|maximum context|prompt is too long|input length.*exceeds/i],
  [UP.KEY_MISSING, /_API_KEY not set/i],
  [UP.AUTH_REJECTED, /\b401\b|\b403\b|invalid api key|incorrect api key|unauthorized|permission denied/i],
  [UP.RATE_LIMITED, /\b429\b|rate limit|too many requests|quota exceeded/i],
  [UP.UNREACHABLE, /fetch failed|ECONNREFUSED|ENOTFOUND|EAI_AGAIN|EHOSTUNREACH|ENETUNREACH|ECONNRESET|socket hang up|other side closed/i],
  [UP.TIMEOUT, /ETIMEDOUT|timed? ?out|AbortError|The operation was aborted/i],
  [UP.UPSTREAM_DOWN, /\b5\d\d\b/],
  [UP.BAD_REQUEST, /\b400\b|\b422\b/],
];

/** Ham hatayı sabit bir koda indirger. Metni asla dışarı taşımaz. */
export function classifyUpstream(err) {
  if (err && err.upstreamCode) return err.upstreamCode;
  if (err && err.name === "AbortError") return UP.TIMEOUT;
  const text = raw(err);
  for (const [code, re] of RULES) if (re.test(text)) return code;
  return UP.UNKNOWN;
}

const PROVIDER_LABEL = {
  ollama: "Ollama",
  openai: "OpenAI",
  anthropic: "Anthropic",
  gemini: "Gemini",
  openclaw: "OpenClaw",
};

/**
 * Koddan kullanıcıya gösterilecek Türkçe mesajı üretir.
 * @param {string} code   classifyUpstream çıktısı
 * @param {object} o      { provider, model }  — ikisi de istemciden gelen değerler
 */
export function upstreamMessage(code, { provider = "", model = "", wanted = "" } = {}) {
  const who = PROVIDER_LABEL[provider] || "Sağlayıcı";
  const m = model ? `“${model}”` : "Seçili model";

  switch (code) {
    case UP.MODEL_SUBSTITUTED:
      return wanted
        ? `“${wanted}” bu makinede yüklü değil; ${m} ile yanıtlandı. Kalıcı çözüm: \`ollama pull ${wanted}\` ya da gateway'de DEFAULT_MODEL/ROUTE_* değerlerini yüklü bir modele çevir.`
        : `İstenen model yüklü değil; ${m} ile yanıtlandı.`;
    case UP.UNREACHABLE:
      return provider === "ollama"
        ? "Ollama'ya ulaşılamadı. Ollama açık mı ve gateway'in OLLAMA_URL değeri doğru mu kontrol et."
        : `${who} sunucusuna ulaşılamadı.`;
    case UP.TIMEOUT:
      return "Yanıt süre sınırını aştı. Daha kısa bir istem dene ya da daha küçük bir model seç.";
    case UP.MODEL_MISSING:
      return `${m} sunucuda yüklü değil. Önce indir: \`ollama pull ${model || "<model>"}\``;
    case UP.THINK_UNSUPPORTED:
      return `${m} “Düşün” modunu desteklemiyor. Düşün'ü kapat ya da düşünen bir model seç (qwen3, deepseek-r1…).`;
    case UP.TOOLS_UNSUPPORTED:
      return `${m} araç çağırmayı desteklemiyor; ajan modu bu modelle çalışamaz.`;
    case UP.VISION_UNSUPPORTED:
      return `${m} görsel girdiyi desteklemiyor. Görme yeteneği olan bir model seç.`;
    case UP.OUT_OF_MEMORY:
      return `${m} için yeterli bellek yok. Daha küçük bir model ya da daha düşük quantization dene.`;
    case UP.CONTEXT_TOO_LONG:
      return "İstem modelin bağlam penceresine sığmıyor. Sohbeti kısalt ya da yeni bir sohbet aç.";
    case UP.KEY_MISSING:
      return `${who} anahtarı sunucuda tanımlı değil. Bu modeli kullanmak için gateway'e anahtar ekle.`;
    case UP.AUTH_REJECTED:
      return `${who} kimlik doğrulamayı reddetti. Sunucudaki API anahtarı geçersiz ya da süresi dolmuş.`;
    case UP.RATE_LIMITED:
      return `${who} hız sınırına takıldı. Biraz bekleyip tekrar dene.`;
    case UP.UPSTREAM_DOWN:
      return `${who} sunucusu hata döndürdü. Birazdan tekrar dene.`;
    case UP.BAD_REQUEST:
      return `${who} isteği reddetti. Model ya da parametreler bu sağlayıcıyla uyumsuz olabilir.`;
    default:
      return `${who} yanıt veremedi.`;
  }
}

/** İstemciye dönecek son metin. prod'da bile anlamlı, ama ham metin içermez. */
export function describeUpstreamError(err, meta = {}) {
  const code = classifyUpstream(err);
  return { code, message: upstreamMessage(code, meta) };
}

/** Hataya kod iliştirir; classifyUpstream sonradan yeniden tahmin etmesin diye. */
export function tagUpstream(err, code) {
  if (err && typeof err === "object") err.upstreamCode = code;
  return err;
}

/** Yeteneği desteklenmediği için düşürülebilir mi? (think/tools geri çekilebilir) */
export function isCapabilityError(code) {
  return code === UP.THINK_UNSUPPORTED
    || code === UP.TOOLS_UNSUPPORTED
    || code === UP.VISION_UNSUPPORTED;
}
