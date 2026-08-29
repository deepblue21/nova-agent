package com.nova.agent

import com.nova.agent.llm.ThinkingText
import com.nova.agent.llm.local.OnDeviceEngine
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Konuşma yeniden kullanımı (donma düzeltmesi) — motorun tuttuğu transcript,
 * ViewModel'in bir sonraki turda göndereceği geçmişle BİREBİR aynı biçimde
 * olmalı; yoksa her turda gereksiz yeniden prefill (E4B'de dakikalar) yaşanır.
 * Saf/JVM.
 */
class OnDeviceEngineSessionTest {

    /** ViewModel.finishLocal'ın kayıt biçimi (oradaki mantığın aynası). */
    private fun finishLocalForm(raw: String): String {
        val (_, content) = ThinkingText.split(raw)
        return content.ifBlank { "(boş yanıt)" }
    }

    @Test
    fun `dusunce blogu icerikten ayrilir`() {
        assertEquals(
            "Merhaba, nasıl yardımcı olabilirim?",
            OnDeviceEngine.normalizeAssistantText(
                "<think>kısa selamlama planla</think>Merhaba, nasıl yardımcı olabilirim?",
            ),
        )
    }

    @Test
    fun `bos ve yalniz-dusunce yanitlar yer tutucuya doner`() {
        assertEquals("(boş yanıt)", OnDeviceEngine.normalizeAssistantText(""))
        assertEquals("(boş yanıt)", OnDeviceEngine.normalizeAssistantText("<think>yalnız düşünce</think>"))
        assertEquals("(boş yanıt)", OnDeviceEngine.normalizeAssistantText("   "))
    }

    /**
     * SÖZLEŞME DEĞİŞTİ (E1). Kapanmamış blok artık içerikte bırakılmıyor:
     * akış düşünmenin ortasında kesilmişse ortada cevap yoktur, "(boş yanıt)"
     * doğru kayıttır. Metin kaybolmuyor — düşünme paneline yazılıyor.
     * Eskiden ham `<think>` etiketi geçmişe, dışa aktarmaya ve panoya sızıyordu.
     */
    @Test
    fun `kapanmamis dusunce blogu icerige yazilmaz`() {
        assertEquals("(boş yanıt)", OnDeviceEngine.normalizeAssistantText("<think>açık kaldı"))
        assertEquals("Yanıt.", OnDeviceEngine.normalizeAssistantText("Yanıt. <think>açık kaldı"))
    }

    @Test
    fun `normalize her girdide finishLocal ile ayni sonucu uretir`() {
        // Eşleşme bozulursa hızlı yol (KV önbelleği) sessizce devre dışı kalır;
        // bu test iki uygulamanın ayrışmasını erken yakalar.
        listOf(
            "<think>plan</think> Sonuç 42. ",
            "düz yanıt, düşünce yok",
            "<think>açık uçlu",
            "",
            "  \n ",
            "Öncesi <think>orta</think> sonrası",
        ).forEach { raw ->
            assertEquals(
                "girdi: $raw",
                finishLocalForm(raw),
                OnDeviceEngine.normalizeAssistantText(raw),
            )
        }
    }
}
