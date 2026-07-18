package com.nova.agent.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Bir sohbeti paylaşılabilir düz metne/Markdown'a çevirir. Saf ve JVM-testli.
 * Rol etiketleri Türkçe; düşünme (thoughts) paylaşımdan çıkarılır (yalnız nihai içerik).
 */
object ConversationExporter {

    fun toMarkdown(convo: Conversation): String {
        val sb = StringBuilder()
        sb.append("# ").append(convo.title.ifBlank { "Sohbet" }).append("\n")
        val date = formatDate(if (convo.updatedAt > 0) convo.updatedAt else convo.createdAt)
        if (date.isNotEmpty()) sb.append("_").append(date).append("_\n")
        sb.append("\n")
        for (m in convo.messages) {
            val who = when (m.role) {
                "user" -> "Sen"
                else -> "NOVA"
            }
            val content = m.content.trim()
            if (content.isEmpty()) continue
            sb.append("**").append(who).append(":** ").append(content).append("\n\n")
        }
        return sb.toString().trimEnd() + "\n"
    }

    private fun formatDate(epochMs: Long): String {
        if (epochMs <= 0) return ""
        return SimpleDateFormat("d MMMM yyyy HH:mm", Locale("tr", "TR")).format(Date(epochMs))
    }
}
