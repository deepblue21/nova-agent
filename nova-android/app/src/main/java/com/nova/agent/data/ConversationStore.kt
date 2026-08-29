package com.nova.agent.data

import com.nova.agent.util.str
import java.io.File
import java.io.FileOutputStream
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

    /**
     * Geçmişi okur — V1.
     *
     * Dosya bozuksa (yarım yazma) ARTIK SESSİZCE SIFIRLANMIYOR: bozuk içerik
     * `<dosya>.corrupt` olarak bir kenara alınır, böylece ilk `save()` onu
     * üzerine yazıp yok etmez ve veri elle kurtarılabilir kalır.
     *
     * Eski davranış şuydu: yarım JSON -> `parseList` yakalayıp BOŞ liste döner
     * -> `save()` dosyayı tek sohbetle ezer. 80 sohbetlik geçmiş, tek uyarı
     * olmadan yok oluyordu.
     */
    @Synchronized
    fun readAll(): List<Conversation> {
        if (!file.exists()) return emptyList()
        val text = runCatching { file.readText() }.getOrNull() ?: return emptyList()
        if (text.isBlank()) return emptyList()
        val parsed = parseList(text)
        if (parsed.isEmpty() && looksLikeContent(text)) quarantine(text)
        return parsed
    }

    /** Boş liste beklenen mi, yoksa bozulma mı — "[]" gerçekten boştur. */
    private fun looksLikeContent(text: String): Boolean = text.trim() !in setOf("[]", "[ ]")

    private fun quarantine(text: String) {
        runCatching {
            val backup = File(file.parentFile, file.name + ".corrupt")
            if (!backup.exists()) backup.writeText(text)
        }
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

    /**
     * Geçmişi ATOMİK yazar — V1.
     *
     * `file.writeText` önce dosyayı kısaltıp (truncate) sonra yazıyordu. Android
     * uygulamayı arka planda her an öldürebildiği için yazma ortasında ölmek
     * dosyayı yarım JSON hâlinde bırakıyordu. Artık geçici dosyaya yazılıp
     * `renameTo` ile yerine konuyor: ya eski tam içerik ya yeni tam içerik
     * görünür, arada bir durum yok.
     *
     * `fd.sync()` rename'den ÖNCE çağrılıyor; aksi halde rename diske işlenip
     * içerik sayfa önbelleğinde kalabilir ve ani güç kesintisinde dosya doğru
     * boyutta ama çöp içerikle kalırdı.
     */
    private fun write(list: List<Conversation>) {
        runCatching {
            file.parentFile?.mkdirs()
            val payload = serializeList(list)
            val tmp = File(file.parentFile, file.name + ".tmp")
            FileOutputStream(tmp).use { out ->
                out.write(payload.toByteArray())
                out.flush()
                out.fd.sync()
            }
            if (!tmp.renameTo(file)) {
                // Aynı dizinde rename başarısızsa (nadir) yedek yol: doğrudan yaz.
                file.writeText(payload)
                tmp.delete()
            }
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

        /** JSON metni -> List. Bozuk/geçersiz girdi boş liste döndürür. Saf/testli. */
        fun parseList(text: String): List<Conversation> {
            if (text.isBlank()) return emptyList()
            return try {
                val arr = JSONArray(text)
                val out = mutableListOf<Conversation>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    out.add(fromJson(o))
                }
                out
            } catch (_: Exception) {
                emptyList()
            }
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
                val route = mo.str("route", "").ifBlank { null }
                msgs.add(
                    ChatMessage(
                        role = mo.str("role", "assistant"),
                        content = mo.str("content", ""),
                        thoughts = mo.str("thoughts", ""),
                        route = route,
                        streaming = false,
                    ),
                )
            }
            return Conversation(
                id = o.str("id"),
                title = o.str("title").ifBlank { "Yeni sohbet" },
                createdAt = o.optLong("createdAt", 0L),
                updatedAt = o.optLong("updatedAt", 0L),
                messages = msgs,
            )
        }
    }
}
