package com.nova.agent.llm.local

import java.io.File
import org.json.JSONObject

/** Bir modelin son çalışma ölçümleri. Cihazda kalır; hiçbir yere gönderilmez. */
data class ModelMetrics(
    val loadMs: Long = 0L,            // motor yükleme süresi (ms)
    val tokensPerSec: Double = 0.0,   // son üretimin yaklaşık hızı
    val lastUsedEpochMs: Long = 0L,   // en son kullanım
    val runs: Int = 0,                // toplam çalışma sayısı
) {
    val hasData: Boolean get() = runs > 0

    /** "~12 tok/sn · yükleme 3,2 sn" gibi kısa özet; veri yoksa null. */
    fun summary(): String? {
        if (!hasData) return null
        val parts = mutableListOf<String>()
        if (tokensPerSec > 0) parts.add("~%.0f tok/sn".format(tokensPerSec))
        if (loadMs > 0) parts.add("yükleme %.1f sn".format(loadMs / 1000.0).replace('.', ','))
        return parts.joinToString(" · ").ifEmpty { null }
    }
}

/**
 * Model başına performans metriklerini tek bir JSON dosyasında tutar.
 * Saf serileştirme yardımcıları JVM'de test edilebilir; dosya IO NoteStore ile
 * aynı desende (yalnız java.io + org.json).
 */
class ModelMetricsStore(private val file: File) {

    @Synchronized
    fun all(): Map<String, ModelMetrics> =
        if (file.exists()) {
            runCatching { parse(file.readText()) }.getOrDefault(emptyMap())
        } else {
            emptyMap()
        }

    fun get(modelId: String): ModelMetrics = all()[modelId] ?: ModelMetrics()

    @Synchronized
    fun clear() {
        if (file.exists()) file.delete()
    }

    @Synchronized
    fun record(modelId: String, loadMs: Long, tokensPerSec: Double, nowMs: Long) {
        val current = all().toMutableMap()
        val prev = current[modelId] ?: ModelMetrics()
        current[modelId] = ModelMetrics(
            // Yükleme her istekte olmaz; 0 gelirse önceki değeri koru.
            loadMs = if (loadMs > 0) loadMs else prev.loadMs,
            tokensPerSec = if (tokensPerSec > 0) tokensPerSec else prev.tokensPerSec,
            lastUsedEpochMs = nowMs,
            runs = prev.runs + 1,
        )
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(serialize(current))
        }
    }

    companion object {
        /** Map -> JSON metni. Saf ve testli. */
        fun serialize(metrics: Map<String, ModelMetrics>): String {
            val root = JSONObject()
            for ((id, m) in metrics) {
                root.put(
                    id,
                    JSONObject()
                        .put("loadMs", m.loadMs)
                        .put("tokensPerSec", m.tokensPerSec)
                        .put("lastUsedEpochMs", m.lastUsedEpochMs)
                        .put("runs", m.runs),
                )
            }
            return root.toString()
        }

        /** JSON metni -> Map. Bozuk girdi boş map döndürür. Saf ve testli. */
        fun parse(text: String): Map<String, ModelMetrics> {
            if (text.isBlank()) return emptyMap()
            val root = JSONObject(text)
            val out = mutableMapOf<String, ModelMetrics>()
            for (id in root.keys()) {
                val o = root.optJSONObject(id) ?: continue
                out[id] = ModelMetrics(
                    loadMs = o.optLong("loadMs", 0L),
                    tokensPerSec = o.optDouble("tokensPerSec", 0.0),
                    lastUsedEpochMs = o.optLong("lastUsedEpochMs", 0L),
                    runs = o.optInt("runs", 0),
                )
            }
            return out
        }

        /** Yaklaşık token sayımı: LiteRT token sayısını vermeden ~4 karakter/token. Saf/testli. */
        fun approxTokens(charCount: Int): Int = if (charCount <= 0) 0 else (charCount + 3) / 4

        /** tok/sn hesabı; süre 0 ise 0. Saf/testli. */
        fun tokensPerSecond(charCount: Int, elapsedMs: Long): Double {
            if (elapsedMs <= 0) return 0.0
            return approxTokens(charCount) * 1000.0 / elapsedMs
        }
    }
}
