package com.nova.agent.llm.local.tools

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Cihaz-içi, çevrimdışı not deposu. Uygulama-özel bir metin dosyasında
 * satır satır tutulur; hiçbir veri cihaz dışına çıkmaz. JVM-testli
 * (yalnız java.io kullanır, Android bağımlılığı yok).
 */
class NoteStore(
    private val file: File,
    private val maxNotes: Int = 200,
) {

    @Synchronized
    fun add(text: String, timestampMs: Long = System.currentTimeMillis()): Int {
        val line = formatLine(timestampMs, text)
        if (line.isEmpty()) return count()
        val existing = readLines()
        val updated = (existing + line).takeLast(maxNotes)
        writeLines(updated)
        return updated.size
    }

    @Synchronized
    fun list(limit: Int = 20): List<String> = readLines().takeLast(limit.coerceAtLeast(1))

    @Synchronized
    fun count(): Int = readLines().size

    @Synchronized
    fun clear() {
        if (file.exists()) file.delete()
    }

    private fun readLines(): List<String> =
        if (file.exists()) {
            runCatching { file.readLines().filter { it.isNotBlank() } }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

    private fun writeLines(lines: List<String>) {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(lines.joinToString(separator = "\n", postfix = "\n"))
        }
    }

    companion object {
        /** Tek satır biçimi: "2026-07-16 21:30 | not". Satır sonları temizlenir. Saf/testli. */
        fun formatLine(timestampMs: Long, text: String): String {
            val clean = text.replace(Regex("[\\r\\n]+"), " ").trim()
            if (clean.isEmpty()) return ""
            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(timestampMs))
            return "$stamp | $clean"
        }
    }
}
