package com.nova.agent.data

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * Sohbet geçmişini tek bir JSON dosyasında tutar (cihazda; hiçbir yere gönderilmez).
 * Serileştirme yardımcıları saf ve JVM'de test edilebilir; dosya IO NoteStore ile
 * aynı desende. En yeni sohbet başta olacak şekilde sıralı döner, [maxConversations]
 * aşılırsa en eskiler düşer.
 */
class ConversationStore(
    private val file: File,
    private val maxConversations: Int = 100,
) {

    @Synchronized
    fun readAll(): List<Conversation> =
        if (file.exists()) {
            runCatching { parseList(file.readText()) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

    @Synchronized
    fun list(): List<ConversationSummary> =
        readAll().sortedByDescending { it.updatedAt }.map { it.toSummary() }

    @Synchronized
    fun search(query: String): List<ConversationSummary> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return list()
        return readAll()
            .filter { convo ->
                convo.title.lowercase().contains(q) ||
                    convo.messages.any { it.content.lowercase().contains(q) }
            }
            .sortedByDescending { it.updatedAt }
            .map { it.toSummary() }
    }

    @Synchronized
    fun load(id: String): Conversation? = readAll().firstOrNull { it.id == id }

    @Synchronized
    fun save(convo: Conversation) {
        val others = readAll().filter { it.id != convo.id }
        val updated = (others + convo)
            .sortedByDescending { it.updatedAt }
            .take(maxConversations)
        write(updated)
    }

    @Synchronized
    fun delete(id: String) {
        val remaining = readAll().filter { it.id != id }
        write(remaining)
    }

    @Synchronized
    fun clear() {
        if (file.exists()) file.delete()
    }

    private fun write(list: List<Conversation>) {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(serializeList(list))
        }
    }

    companion object {
        private fun Conversation.toSummary() = ConversationSummary(
            id = id,
            title = title,
            updatedAt = updatedAt,
            messageCount = messages.size,
            snippet = ConversationText.snippetFrom(messages),
        )

        /** List -> JSON metni. Saf/testli. */
        fun serializeList(list: List<Conversation>): String {
            val arr = JSONArray()
            for (c in list) arr.put(toJson(c))
            return arr.toString()
        }

        /** JSON metni -> List. Bozuk girdi boş liste. Saf/testli. */
        fun parseList(text: String): List<Conversation> {
            if (text.isBlank()) return emptyList()
            val arr = JSONArray(text)
            val out = mutableListOf<Conversation>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                out.add(fromJson(o))
            }
            return out
        }

        private fun toJson(c: Conversation): JSONObject {
            val msgs = JSONArray()
            for (m in c.messages) {
                msgs.put(
                    JSONObject()
                        .put("role", m.role)
                        .put("content", m.content)
                        .put("thoughts", m.thoughts)
                        .put("route", m.route ?: JSONObject.NULL),
                )
            }
            return JSONObject()
                .put("id", c.id)
                .put("title", c.title)
                .put("createdAt", c.createdAt)
                .put("updatedAt", c.updatedAt)
                .put("messages", msgs)
        }

        private fun fromJson(o: JSONObject): Conversation {
            val msgsJson = o.optJSONArray("messages") ?: JSONArray()
            val msgs = mutableListOf<ChatMessage>()
            for (i in 0 until msgsJson.length()) {
                val mo = msgsJson.optJSONObject(i) ?: continue
                val route = mo.optString("route", "").ifBlank { null }
                msgs.add(
                    ChatMessage(
                        role = mo.optString("role", "assistant"),
                        content = mo.optString("content", ""),
                        thoughts = mo.optString("thoughts", ""),
                        route = route,
                        streaming = false,
                    ),
                )
            }
            return Conversation(
                id = o.optString("id"),
                title = o.optString("title").ifBlank { "Yeni sohbet" },
                createdAt = o.optLong("createdAt", 0L),
                updatedAt = o.optLong("updatedAt", 0L),
                messages = msgs,
            )
        }
    }
}
