package com.nova.agent

import com.nova.agent.feature.chat.ChatMarkdown
import com.nova.agent.llm.ThinkingText
import com.nova.agent.llm.local.LocalModelStore
import com.nova.agent.llm.local.ModelDownloader
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalLlmHelpersTest {

    // ---------- SHA-256 ----------

    @Test
    fun `sha256 bilinen vektorle eslesir`() {
        val digest = LocalModelStore.sha256Hex(ByteArrayInputStream("abc".toByteArray()))
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            digest,
        )
    }

    @Test
    fun `bos girdi sha256'si dogru`() {
        val digest = LocalModelStore.sha256Hex(ByteArrayInputStream(ByteArray(0)))
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            digest,
        )
    }

    // ---------- Range ----------

    @Test
    fun `range basligi kaldigi yerden devam eder`() {
        assertEquals("bytes=0-", ModelDownloader.rangeHeader(0))
        assertEquals("bytes=123456789-", ModelDownloader.rangeHeader(123_456_789L))
    }

    // ---------- <think> ayrimi ----------

    @Test
    fun `think blogu dusunce ve icerige ayrilir`() {
        val (thoughts, content) = ThinkingText.split("<think>plan yap</think>Merhaba!")
        assertEquals("plan yap", thoughts)
        assertEquals("Merhaba!", content)
    }

    @Test
    fun `think blogu yoksa icerik aynen kalir`() {
        val (thoughts, content) = ThinkingText.split("Sadece cevap.")
        assertEquals("", thoughts)
        assertEquals("Sadece cevap.", content)
    }

    /**
     * SÖZLEŞME DEĞİŞTİ (E1). Bu test eskiden tersini doğruluyordu: kapanmamış
     * blok "şeffaflık" gerekçesiyle içerikte bırakılıyordu. Karar yanlıştı —
     * iki söz veriyor, ikisini de tutmuyordu: balonda ham `<think>` etiketi
     * görünüyor, dışa aktarmaya ve panoya da düşünme metni sızıyordu. Kapanmamış
     * blok akışın düşünmenin ORTASINDA kesildiğini gösterir; kuyruk cevap
     * değildir. Şeffaflık kaybolmuyor: metin düşünme panelinde duruyor.
     */
    @Test
    fun `kapanmamis think blogu icerige degil dusunmeye gider`() {
        val (thoughts, content) = ThinkingText.split("Merhaba. <think>yarim kaldi")
        assertEquals("yarim kaldi", thoughts)
        assertEquals("Merhaba.", content)
    }

    @Test
    fun `kapanmamis blok tek basinaysa icerik bos kalir`() {
        val (thoughts, content) = ThinkingText.split("<think>yarim kaldi")
        assertEquals("yarim kaldi", thoughts)
        assertEquals("", content)
    }

    @Test
    fun `birden fazla think blogu tumuyle ayiklanir`() {
        val (thoughts, content) = ThinkingText.split(
            "<think>once</think>Cevap bir. <think>sonra</think>Cevap iki.",
        )
        assertEquals("once\n\nsonra", thoughts)
        assertEquals("Cevap bir. Cevap iki.", content)
    }

    @Test
    fun `think icindeki metin icerige sizmaz`() {
        val (_, content) = ThinkingText.split("<think>KART 4111 1111 1111 1111</think>Tamam.")
        assertFalse("dusunme metni disa aktarilan icerige gecmemeli", content.contains("4111"))
    }

    // ---------- kod bloklari ----------

    @Test
    fun `citsiz metin tek blok olur`() {
        val blocks = ChatMarkdown.splitBlocks("merhaba dunya")
        assertEquals(listOf<ChatMarkdown.Block>(ChatMarkdown.Block.Text("merhaba dunya")), blocks)
    }

    @Test
    fun `citli kod dili ve icerigiyle ayrilir`() {
        val raw = "Aciklama\n```kotlin\nfun topla(a: Int, b: Int) = a + b\n```\nSon."
        val blocks = ChatMarkdown.splitBlocks(raw)
        assertEquals(3, blocks.size)
        assertEquals(ChatMarkdown.Block.Text("Aciklama"), blocks[0])
        val code = blocks[1] as ChatMarkdown.Block.Code
        assertEquals("kotlin", code.language)
        assertEquals("fun topla(a: Int, b: Int) = a + b", code.content)
        assertEquals(ChatMarkdown.Block.Text("Son."), blocks[2])
    }

    @Test
    fun `kapanmamis cit akista kod sayilir`() {
        val raw = "```python\nprint(1)"
        val blocks = ChatMarkdown.splitBlocks(raw)
        assertEquals(1, blocks.size)
        val code = blocks[0] as ChatMarkdown.Block.Code
        assertEquals("python", code.language)
        assertEquals("print(1)", code.content)
    }

    @Test
    fun `ust uste iki kod blogu ayri kalir`() {
        val raw = "```js\n1\n```\n```ts\n2\n```"
        val blocks = ChatMarkdown.splitBlocks(raw)
        val codes = blocks.filterIsInstance<ChatMarkdown.Block.Code>()
        assertEquals(2, codes.size)
        assertEquals("js", codes[0].language)
        assertEquals("ts", codes[1].language)
    }

    @Test
    fun `varsayilan ayarlar cihaz ustu calisir`() {
        val settings = com.nova.agent.data.AppSettings()
        // Play kararı B5: temiz kurulum PC olmadan da çalışmalı.
        assertEquals("local_first", settings.executionPolicy)
        assertTrue(settings.localModelId.isNotBlank())
        assertEquals("amethyst", settings.themeId)
    }
}
