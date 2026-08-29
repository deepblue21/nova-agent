package com.nova.agent.voice

/**
 * Uzun yanıtı TTS'in kabul ettiği boyda parçalara böler — S1.
 *
 * `TextToSpeech.speak()` girdisi `getMaxSpeechInputLength()` (çoğu cihazda 4000)
 * karakteri geçerse **ERROR döner ve hiçbir geri çağrı gelmez**: ne `onDone`, ne
 * `onError`. Eski kod dönüş değerine de bakmıyordu, sonuç şu oluyordu — 5000
 * karakterlik bir yanıtta orb sonsuza dek "Konuşuyorum" durumunda kalıyor, ses
 * çıkmıyor, kullanıcı ekranı ancak uygulamayı kapatarak kurtarıyordu.
 *
 * Saf fonksiyon: Android bağımlılığı yok, JVM biriminde test edilir.
 */
internal object TtsChunker {

    /** Sınır cihazdan okunur; bu değer yalnızca yedek. */
    const val FALLBACK_LIMIT = 3_500

    /**
     * Metni sırayla okunacak parçalara böler. Her parça [limit] karakteri
     * geçmez, parça listesi birleştirildiğinde konuşulan içerik korunur.
     *
     * Kesim önceliği: cümle sonu → kelime boşluğu → sert kesim. Sert kesim
     * yalnızca tek bir "kelime" limitten uzunsa devreye girer (yapıştırılmış
     * base64 veya URL gibi); bu durumda hecelemek, hiç konuşmamaktan iyidir.
     */
    fun chunk(text: String, limit: Int = FALLBACK_LIMIT): List<String> {
        val safeLimit = limit.coerceAtLeast(1)
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()
        if (trimmed.length <= safeLimit) return listOf(trimmed)

        val chunks = mutableListOf<String>()
        val current = StringBuilder()
        for (piece in segments(trimmed, safeLimit)) {
            when {
                current.isEmpty() -> current.append(piece)
                current.length + 1 + piece.length <= safeLimit -> current.append(' ').append(piece)
                else -> {
                    chunks += current.toString()
                    current.setLength(0)
                    current.append(piece)
                }
            }
        }
        if (current.isNotEmpty()) chunks += current.toString()
        return chunks
    }

    /** Limite sığan parçalara indirger: cümle, sonra kelime, sonra sert kesim. */
    private fun segments(text: String, limit: Int): List<String> {
        val out = mutableListOf<String>()
        for (sentence in SENTENCE.split(text).map { it.trim() }.filter { it.isNotEmpty() }) {
            if (sentence.length <= limit) {
                out += sentence
                continue
            }
            for (word in sentence.split(WHITESPACE).filter { it.isNotEmpty() }) {
                if (word.length <= limit) {
                    out += word
                    continue
                }
                var i = 0
                while (i < word.length) {
                    out += word.substring(i, minOf(i + limit, word.length))
                    i += limit
                }
            }
        }
        return out
    }

    private val SENTENCE = Regex("(?<=[.!?…])\\s+|\\n+")
    private val WHITESPACE = Regex("\\s+")
}
