package com.nova.agent.data

/**
 * Yerel model yanıt veremediğinde gösterilen bildirim.
 * [allowGateway] true ise kullanıcı onayıyla PC'ye devir önerilir;
 * Çevrimdışı (LOCAL_ONLY) modda false'tur ve istem cihaz dışına ASLA çıkmaz.
 */
data class PendingFallback(val reason: String, val allowGateway: Boolean = true)

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
