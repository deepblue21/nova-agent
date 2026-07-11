package com.nova.agent.data

/** Tek bir sohbet mesajı. */
data class ChatMessage(
    val role: String,                 // "user" | "assistant"
    val content: String,
    val thoughts: String = "",        // gerçek düşünme token'ları (varsa)
    val route: String? = null,        // gateway x-nova-route
    val streaming: Boolean = false,
)

/** Model seçimi. Android istemci her zaman gateway'e konuşur; model = "<provider>/<model>" ya da "auto". */
data class ModelOption(val id: String, val name: String, val model: String, val group: String)

val MODELS = listOf(
    ModelOption("auto", "Dinamik Yönlendirme", "auto", "Otomatik"),
    ModelOption("opus", "Claude Opus", "anthropic/claude-opus-4-20250514", "Bulut"),
    ModelOption("sonnet", "Claude Sonnet", "anthropic/claude-sonnet-4-20250514", "Bulut"),
    ModelOption("gempro", "Gemini 2.5 Pro", "gemini/gemini-2.5-pro", "Bulut"),
    ModelOption("gemflash", "Gemini 2.5 Flash", "gemini/gemini-2.5-flash", "Bulut"),
    ModelOption("gpt", "GPT-4o mini", "openai/gpt-4o-mini", "Bulut"),
    ModelOption("qwen14", "Qwen3 14B", "ollama/qwen3:14b", "Yerel"),
    ModelOption("qwenvl", "Qwen2.5-VL 7B", "ollama/qwen2.5vl:7b", "Yerel"),
    ModelOption("openclaw", "OpenClaw Ajanı", "openclaw/default", "Ajan"),
)

data class EffortOption(val id: String, val name: String)

val EFFORTS = listOf(
    EffortOption("fast", "Hızlı"),
    EffortOption("balanced", "Dengeli"),
    EffortOption("deep", "Derin"),
    EffortOption("max", "Maks"),
)

enum class Mode { VOICE, CHAT, TASKS }
enum class VoiceState { IDLE, LISTENING, THINKING, SPEAKING }
