package com.nova.agent

import com.nova.agent.voice.TtsChunker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S1 — TTS uzunluk sınırı.
 *
 * `TextToSpeech.speak()` girdisi cihazın `getMaxSpeechInputLength()` sınırını
 * aşarsa ERROR döner ve HİÇBİR geri çağrı gelmez; `onDone` beklenen her yer
 * sonsuza dek asılı kalır. Bölücü saf olduğu için sözleşmesi burada kilitlenir.
 */
class TtsChunkerTest {

    @Test
    fun `sinira sigan metin bolunmez`() {
        assertEquals(listOf("Kısa cevap."), TtsChunker.chunk("Kısa cevap.", limit = 100))
    }

    @Test
    fun `bos metin hic parca uretmez`() {
        assertTrue(TtsChunker.chunk("   \n  ", limit = 100).isEmpty())
    }

    @Test
    fun `hicbir parca siniri asmaz`() {
        val text = (1..400).joinToString(" ") { "cumle$it burada biraz uzun bir metin parcasi." }
        val chunks = TtsChunker.chunk(text, limit = 500)

        assertTrue("bolme gerekliydi", chunks.size > 1)
        chunks.forEach { assertTrue("parca ${it.length} > 500", it.length <= 500) }
    }

    @Test
    fun `dort bin karakterlik yanit gercek sinirin altina inar`() {
        // Hatanin gercek senaryosu: 5000 karakterlik yanit, cihaz siniri 4000.
        val text = (1..250).joinToString(" ") { "Bu yirmi karakterden uzun bir cumledir numara $it." }
        assertTrue("senaryo 4000'i asmali", text.length > 4000)

        val chunks = TtsChunker.chunk(text, limit = 3_900)
        chunks.forEach { assertTrue(it.length <= 3_900) }
    }

    @Test
    fun `cumle siniri tercih edilir`() {
        val chunks = TtsChunker.chunk("Bir. Iki. Uc. Dort.", limit = 10)

        // Kelimenin ortasindan kesilmemeli.
        chunks.forEach { assertTrue("parca yarim kelimeyle bitmis: '$it'", !it.endsWith("U")) }
        assertEquals("Bir. Iki.", chunks.first())
    }

    @Test
    fun `limitten uzun tek kelime sert kesilir`() {
        val word = "a".repeat(25)
        val chunks = TtsChunker.chunk(word, limit = 10)

        assertEquals(3, chunks.size)
        assertEquals(word, chunks.joinToString(""))
    }

    @Test
    fun `icerik kaybolmaz`() {
        val text = "Bir cumle. Iki cumle. Uc cumle. Dort cumle. Bes cumle."
        val chunks = TtsChunker.chunk(text, limit = 20)

        val rebuilt = chunks.joinToString(" ")
        listOf("Bir", "Iki", "Uc", "Dort", "Bes").forEach {
            assertTrue("'$it' kayboldu: $rebuilt", rebuilt.contains(it))
        }
    }

    @Test
    fun `sifir limit cokertmez`() {
        assertTrue(TtsChunker.chunk("abc", limit = 0).isNotEmpty())
    }
}
