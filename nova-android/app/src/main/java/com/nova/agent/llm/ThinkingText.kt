package com.nova.agent.llm

/**
 * Qwen3'ün <think>…</think> bloğunu nihai içerikten ayırır.
 * Kapanmamış blok olduğu gibi bırakılır (şeffaflık). Saf/JVM-testli.
 */
object ThinkingText {
    fun split(raw: String): Pair<String, String> {
        val start = raw.indexOf("<think>")
        if (start < 0) return "" to raw
        val end = raw.indexOf("</think>", start)
        if (end < 0) return "" to raw
        val thoughts = raw.substring(start + "<think>".length, end).trim()
        val content = (raw.substring(0, start) + raw.substring(end + "</think>".length)).trim()
        return thoughts to content
    }
}
