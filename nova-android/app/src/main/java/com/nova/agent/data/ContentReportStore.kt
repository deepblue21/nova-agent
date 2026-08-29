package com.nova.agent.data

import com.nova.agent.util.str
import java.io.File
import java.io.FileOutputStream
import org.json.JSONArray
import org.json.JSONObject

/**
 * Bildirimlerin CİHAZDAKİ deposu — Play B6.
 *
 * Sunucu yok. Bu bilinçli: bildirimin içinde sohbet alıntısı var ve NOVA
 * "istemler telefondan çıkmaz" diyor. Sessizce yükleseydik hem bu sözü hem de
 * Data Safety beyanını çiğnemiş olurduk. Kayıt cihazda tutulur; kullanıcı
 * dilerse Ayarlar'dan paylaşır.
 *
 * Yazma [ConversationStore] ile aynı atomik deseni kullanır (geçici dosya +
 * `fd.sync()` + `renameTo`): süreç yazma ortasında öldürülürse dosya yarım
 * JSON hâlinde kalmaz. Bozuk dosya sessizce SİLİNMEZ, `.corrupt` olarak
 * karantinaya alınır — V1'de aynı kararı verdik.
 */
class ContentReportStore(private val file: File) {

    fun list(): List<ContentReport> = read()

    fun add(report: ContentReport): List<ContentReport> {
        // Yeni bildirim başa: en son olan en üstte görünsün.
        val next = (listOf(report) + read()).take(MAX_REPORTS)
        write(next)
        return next
    }

    fun clear() {
        write(emptyList())
    }

    /** Kullanıcının paylaşacağı metin. Ne gönderdiğini GÖREREK paylaşır. */
    fun exportText(reports: List<ContentReport> = read()): String {
        if (reports.isEmpty()) return "Bildirim yok."
        return buildString {
            appendLine("NOVA — yapay zekâ içerik bildirimleri (${reports.size})")
            appendLine()
            reports.forEach { r ->
                appendLine("— ${r.reason.label}")
                if (r.route.isNotBlank()) appendLine("  kaynak: ${r.route}")
                if (r.note.isNotBlank()) appendLine("  not: ${r.note}")
                if (r.excerpt.isNotBlank()) appendLine("  alıntı: ${r.excerpt}")
                appendLine()
            }
        }.trimEnd()
    }

    private fun read(): List<ContentReport> {
        if (!file.exists()) return emptyList()
        val raw = runCatching { file.readText() }.getOrNull() ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: run {
            // Bozuk dosya: karantinaya al, üstüne yazıp yok etme.
            runCatching { file.renameTo(File(file.parentFile, file.name + ".corrupt")) }
            return emptyList()
        }
        val out = ArrayList<ContentReport>(array.length())
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val id = o.str("id").ifBlank { continue }
            out += ContentReport(
                id = id,
                createdAt = o.optLong("createdAt"),
                reason = ContentReportReason.fromId(o.str("reason")),
                note = o.str("note"),
                excerpt = o.str("excerpt"),
                route = o.str("route"),
            )
        }
        return out
    }

    private fun write(list: List<ContentReport>) {
        runCatching {
            file.parentFile?.mkdirs()
            val payload = JSONArray().apply {
                list.forEach { r ->
                    put(
                        JSONObject()
                            .put("id", r.id)
                            .put("createdAt", r.createdAt)
                            .put("reason", r.reason.id)
                            .put("note", r.note)
                            .put("excerpt", r.excerpt)
                            .put("route", r.route),
                    )
                }
            }.toString()
            val tmp = File(file.parentFile, file.name + ".tmp")
            FileOutputStream(tmp).use { out ->
                out.write(payload.toByteArray())
                out.flush()
                out.fd.sync()
            }
            if (!tmp.renameTo(file)) {
                file.writeText(payload)
                tmp.delete()
            }
        }
    }

    companion object {
        /** Depo sınırsız büyümesin; en yeniler tutulur. */
        const val MAX_REPORTS = 200
    }
}
