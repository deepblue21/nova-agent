package com.nova.agent.llm

/**
 * Qwen3 tarzı `<think>…</think>` bloklarını nihai içerikten ayırır. Saf/JVM-testli.
 *
 * Sözleşme (E1'de düzeltildi):
 * - Kapanmamış blok **düşünme sayılır**, içerikte bırakılmaz. Eskiden ham hâliyle
 *   içeriğe düşüyordu ("şeffaflık" gerekçesiyle) ama bu iki söz veriyordu ve
 *   ikisini de tutmuyordu: balonda ham `<think>` etiketi görünüyor, dışa
 *   aktarmaya ve panoya da düşünme metni sızıyordu. Kapanmamış blok akışın
 *   düşünmenin ORTASINDA kesildiği anlamına gelir; kuyruk cevap değildir.
 *   Şeffaflık kaybolmuyor: metin düşünme panelinde duruyor, yalnızca doğru yere
 *   yazılıyor.
 * - Birden fazla blok destekleniyor. Bazı modeller araç turları arasında yeni
 *   blok açar; tek bloğa bakan eski kod ikincisini içerikte bırakıyordu.
 */
object ThinkingText {
    private const val OPEN = "<think>"
    private const val CLOSE = "</think>"

    fun split(raw: String): Pair<String, String> {
        if (!raw.contains(OPEN)) return "" to raw

        val thoughts = StringBuilder()
        val content = StringBuilder()
        var cursor = 0
        while (true) {
            val start = raw.indexOf(OPEN, cursor)
            if (start < 0) {
                content.append(raw, cursor, raw.length)
                break
            }
            content.append(raw, cursor, start)
            val bodyStart = start + OPEN.length
            val end = raw.indexOf(CLOSE, bodyStart)
            if (end < 0) {
                appendBlock(thoughts, raw.substring(bodyStart))
                break
            }
            appendBlock(thoughts, raw.substring(bodyStart, end))
            cursor = end + CLOSE.length
        }
        return thoughts.toString().trim() to content.toString().trim()
    }

    private fun appendBlock(target: StringBuilder, block: String) {
        val trimmed = block.trim()
        if (trimmed.isEmpty()) return
        if (target.isNotEmpty()) target.append("\n\n")
        target.append(trimmed)
    }
}
