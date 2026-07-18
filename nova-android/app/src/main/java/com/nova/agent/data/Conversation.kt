package com.nova.agent.data

/** Kaydedilmiş bir sohbet (tüm mesajlarıyla). */
data class Conversation(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messages: List<ChatMessage>,
)

/** Geçmiş listesinde gösterilen özet satırı. */
data class ConversationSummary(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val messageCount: Int,
    val snippet: String,
)

/** Sohbet başlığı/özet üretimi için saf yardımcılar (JVM-testli). */
object ConversationText {

    private const val TITLE_MAX = 40
    private const val SNIPPET_MAX = 60

    /** İlk kullanıcı mesajından başlık; yoksa "Yeni sohbet". */
    fun titleFrom(messages: List<ChatMessage>): String {
        val firstUser = messages.firstOrNull { it.role == "user" }?.content?.oneLine()
        if (firstUser.isNullOrBlank()) return "Yeni sohbet"
        return firstUser.clip(TITLE_MAX)
    }

    /** Son mesajdan kısa önizleme. */
    fun snippetFrom(messages: List<ChatMessage>): String {
        val last = messages.lastOrNull { it.content.isNotBlank() }?.content?.oneLine().orEmpty()
        return last.clip(SNIPPET_MAX)
    }

    private fun String.oneLine(): String = replace(Regex("\\s+"), " ").trim()

    private fun String.clip(max: Int): String =
        if (length <= max) this else take(max).trimEnd() + "…"
}
