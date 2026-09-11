package com.nova.agent.data

/**
 * Yerel model yanıt veremediğinde gösterilen bildirim.
 * [allowGateway] true ise kullanıcı onayıyla PC'ye devir önerilir;
 * Çevrimdışı (LOCAL_ONLY) modda false'tur ve istem cihaz dışına ASLA çıkmaz.
 */
/**
 * Yerel yol istemi işleyemediğinde gösterilecek izin kartının içeriği.
 *
 * [kind] eklendi (U1): kart eskiden HER durumda "Telefon modeli yanıt veremedi"
 * yazıyordu. Oysa iki bambaşka durum aynı karta düşüyor ve birinde bu cümle
 * doğrudan yanlış: telefonda kurulu model YOKKEN "yanıt veremedi" demek, var
 * olmayan bir modeli suçlamaktır. Kullanıcının o durumda ihtiyacı olan eylem de
 * farklıdır — PC'ye göndermek değil, model indirmek.
 */
enum class FallbackKind {
    /** Telefonda kurulu/doğrulanmış model yok. Çözüm: model indir. */
    NO_LOCAL_MODEL,

    /** Model vardı ama üretim hata verdi. Çözüm: PC'ye devret ya da vazgeç. */
    LOCAL_ERROR,
}

data class PendingFallback(
    val reason: String,
    val allowGateway: Boolean = true,
    val kind: FallbackKind = FallbackKind.LOCAL_ERROR,
)

/** Tek bir sohbet mesajı. */
data class ChatMessage(
    val role: String,                 // "user" | "assistant"
    val content: String,
    val thoughts: String = "",        // gerçek düşünme token'ları (varsa)
    val route: String? = null,        // gateway x-nova-route
    val streaming: Boolean = false,
    /** Ajan modunda kullanılan araçlar; web'deki araç izi kartının eşleniği. */
    val tools: List<ToolStep> = emptyList(),
)

/**
 * Model seçimi. Android istemci her zaman gateway'e konuşur;
 * model = "<provider>/<model>" ya da "auto".
 *
 * [available] false ise sağlayıcı anahtarı gateway'de tanımlı değildir:
 * satır listede kalır ama seçilemez ve [reason] nedeni gösterilir.
 */
data class ModelOption(
    val id: String,
    val name: String,
    val model: String,
    val group: String,
    val desc: String = "",
    val available: Boolean = true,
    val reason: String = "",
    /**
     * Model araç çağırmayı (web arama, belge arama, hesap, MCP) destekliyor mu.
     * Destekleyen bir model seçilince ajan modu kendiliğinden açılır.
     */
    val tools: Boolean = false,
    /** "probe" (Ollama'ya soruldu) · "family" (aile tahmini) · "provider" · "gateway" · "agent" */
    val toolsSource: String = "",
    /**
     * Model görsel girdi alabiliyor mu — Faz 12A. Gateway `/v1/models` içinde
     * bildiriyor. Varsayılan `false`: bayrak gelmiyorsa (eski gateway) görü
     * VARSAYILMAZ — görmeyen bir modele görsel göndermek, kibar bir uydurma
     * yanıt almak demektir.
     */
    val vision: Boolean = false,
) {
    /** Yetenek gerçekten ölçüldü mü, yoksa tahmin mi. */
    val toolsVerified: Boolean get() = toolsSource == "probe" || toolsSource == "provider"
}

/** Araç izinde gösterilen tek bir ajan adımı (gateway `tool_step` deltası). */
data class ToolStep(
    val name: String,
    val query: String = "",
    val done: Boolean = false,
    val sources: List<ToolSource> = emptyList(),
)

data class ToolSource(val title: String, val url: String = "", val index: Int = 0)

/**
 * Gateway'e ulaşılamadığında kullanılan yedek liste. Canlı liste
 * `GET /v1/models` ile gelir (Ollama'da yüklü modeller + bulut sağlayıcılar).
 */
val FALLBACK_MODELS = listOf(
    ModelOption("auto", "Dinamik Yönlendirme", "auto", "Otomatik"),
    ModelOption("opus", "Claude Opus 4.8", "anthropic/claude-opus-4-8", "Bulut"),
    ModelOption("sonnet", "Claude Sonnet 5", "anthropic/claude-sonnet-5", "Bulut"),
    ModelOption("gemflash", "Gemini 3.5 Flash", "gemini/gemini-3.5-flash", "Bulut"),
    ModelOption("gpt", "GPT-5.6 Sol", "openai/gpt-5.6", "Bulut"),
    ModelOption("openclaw", "OpenClaw Ajanı", "openclaw/default", "Ajan"),
)

/**
 * PC'ye devredilmiş bir işin kaydı — Faz 11.
 *
 * Kaynak: `GET /v1/agent/runs`. Telefondan "PC'ye devret" denince istem PC'deki
 * OpenClaw ajanına gidiyor, ama telefon o işin ne olduğunu bir daha göremiyordu:
 * yanıt sohbet balonunda kalıyor, uygulama kapanınca devir izi yok oluyordu.
 *
 * [mode] gateway'in koşum türü: "openclaw" (telefondan devir), "agent"
 * (PC'de araç döngüsü), "team" (çok ajanlı). Uydurulmaz, olduğu gibi gösterilir.
 */
data class PcAgentRun(
    val id: String,
    val mode: String,
    val model: String,
    val prompt: String,
    val tools: String,
    val result: String,
    /** Unix ms; 0 = gateway tarih vermedi (göreli zaman gösterilmez). */
    val createdAt: Long,
) {
    /** Bu koşum telefondan mı devredildi. */
    val fromPhone: Boolean get() = mode == "openclaw"
}

/** Gateway'den gelen canlı model kataloğu. */
data class GatewayCatalog(
    val models: List<ModelOption> = emptyList(),
    /** Kullanıcı seçim yapmadıysa kullanılacak model kimliği (ilk yerel model). */
    val defaultModelId: String? = null,
    /** Ollama listesi okunabildi mi; okunamadıysa [ollamaError] dolu. */
    val ollamaOk: Boolean = true,
    val ollamaError: String? = null,
) {
    val isEmpty: Boolean get() = models.isEmpty()
}

data class EffortOption(val id: String, val name: String)

val EFFORTS = listOf(
    EffortOption("fast", "Hızlı"),
    EffortOption("balanced", "Dengeli"),
    EffortOption("deep", "Derin"),
    EffortOption("max", "Maks"),
)

/**
 * Birincil gezinme hedefleri. KONTROL/MODELLER Faz 1'de eklendi;
 * VOICE alt gezinmede görünmez, Sohbet üst çubuğundaki mikrofonla açılır.
 */
enum class Mode { KONTROL, TASKS, CHAT, MODELLER, VOICE }
enum class VoiceState { IDLE, LISTENING, THINKING, SPEAKING }
