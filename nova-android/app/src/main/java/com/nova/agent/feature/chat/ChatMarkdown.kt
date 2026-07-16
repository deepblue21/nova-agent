package com.nova.agent.feature.chat

/**
 * Asistan mesajlarını düz metin ve ``` çitli kod bloklarına ayırır.
 * Her kod bloğu ayrı kartta, blok başına Kopyala düğmesiyle gösterilir.
 * Saf/JVM-testli; tam Markdown işlemez, yalnız çitli blokları tanır.
 */
object ChatMarkdown {

    sealed interface Block {
        data class Text(val content: String) : Block
        data class Code(val language: String, val content: String) : Block
    }

    fun splitBlocks(raw: String): List<Block> {
        if (!raw.contains("```")) {
            return if (raw.isEmpty()) emptyList() else listOf(Block.Text(raw))
        }
        val blocks = mutableListOf<Block>()
        var rest = raw
        while (true) {
            val fenceStart = rest.indexOf("```")
            if (fenceStart < 0) {
                if (rest.isNotBlank()) blocks.add(Block.Text(rest.trim('\n')))
                break
            }
            val before = rest.substring(0, fenceStart)
            if (before.isNotBlank()) blocks.add(Block.Text(before.trim('\n')))

            val afterFence = rest.substring(fenceStart + 3)
            val newlineIdx = afterFence.indexOf('\n')
            if (newlineIdx < 0) {
                // Açılış çitinden sonra satır yok (akış yarıda): kalanı kod say.
                val language = afterFence.trim()
                blocks.add(Block.Code(language, ""))
                break
            }
            val language = afterFence.substring(0, newlineIdx).trim()
            val bodyStart = newlineIdx + 1
            val closing = afterFence.indexOf("```", bodyStart)
            if (closing < 0) {
                // Kapanmamış blok (akış sürüyor): kalan her şey kod.
                blocks.add(Block.Code(language, afterFence.substring(bodyStart).trimEnd('\n')))
                break
            }
            blocks.add(
                Block.Code(language, afterFence.substring(bodyStart, closing).trimEnd('\n')),
            )
            rest = afterFence.substring(closing + 3)
        }
        return blocks
    }
}
