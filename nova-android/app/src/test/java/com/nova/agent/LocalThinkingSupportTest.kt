package com.nova.agent

import com.nova.agent.llm.local.LocalModelCatalog
import com.nova.agent.llm.local.LocalThinkingSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Yerel düşünme" anahtarının seçili modele bağlanması.
 *
 * Bu kural katalog 16 modele çıkınca zorunlu hale geldi: anahtar uzun süre
 * modelden bağımsız çiziliyordu ve artık **düşünmeyi desteklemeyen altı
 * model** var. Desteklenmeyen bir anahtarı etkin göstermek, projenin
 * "desteklenmeyen özellik taklit edilmez" değişmezini ihlal eder.
 */
class LocalThinkingSupportTest {

    @Test
    fun `destekleyen modelde anahtar etkin`() {
        val qwen = LocalModelCatalog.byId("qwen3-0.6b-int4")!!
        val ui = LocalThinkingSupport.forModel(qwen)

        assertTrue(ui.interactive)
        assertEquals(LocalThinkingSupport.SUPPORTED_EXPLANATION, ui.explanation)
        assertTrue("Başlık modelin ailesini yazmalı", ui.title.contains(qwen.family))
    }

    @Test
    fun `desteklemeyen modelde anahtar pasif ve nedeni yazili`() {
        val granite = LocalModelCatalog.byId("granite-4.0-350m")!!
        val ui = LocalThinkingSupport.forModel(granite)

        assertFalse(ui.interactive)
        assertTrue(
            "Neden model adıyla açıklanmalı",
            ui.explanation.contains(granite.displayName),
        )
        assertTrue(
            "Kullanıcıya çıkış yolu söylenmeli",
            ui.explanation.contains("model seçerseniz"),
        )
    }

    /**
     * Sözleşme katalogun tamamında geçerli olmalı: `supportsThinkingToggle`
     * ne diyorsa anahtar onu yapmalı. Yeni bir model eklendiğinde bu test
     * onu da kapsar.
     */
    @Test
    fun `katalogun tamaminda anahtar durumu spec ile tutarli`() {
        for (spec in LocalModelCatalog.entries) {
            assertEquals(
                "${spec.id}: anahtar durumu supportsThinkingToggle ile uyuşmuyor",
                spec.supportsThinkingToggle,
                LocalThinkingSupport.forModel(spec).interactive,
            )
        }
    }

    /** Katalogda gerçekten iki grup da bulunmalı; test boş kümede geçmesin. */
    @Test
    fun `katalogda hem destekleyen hem desteklemeyen model var`() {
        val destekleyen = LocalModelCatalog.entries.count { it.supportsThinkingToggle }
        val desteklemeyen = LocalModelCatalog.entries.count { !it.supportsThinkingToggle }

        assertTrue("düşünmeyi destekleyen model yok", destekleyen > 0)
        assertTrue("desteklemeyen model yok — test anlamsızlaşır", desteklemeyen > 0)
    }

    @Test
    fun `model secilmemisken anahtar pasif ve yonlendirici`() {
        val ui = LocalThinkingSupport.forModel(null)

        assertFalse(ui.interactive)
        assertTrue(ui.explanation.contains("model seçin"))
    }

    /** Her durumda açıklama dolu olmalı — boş bir satır kullanıcıya bir şey söylemez. */
    @Test
    fun `aciklama hicbir durumda bos degil`() {
        val hepsi = LocalModelCatalog.entries.map { LocalThinkingSupport.forModel(it) } +
            LocalThinkingSupport.forModel(null)
        for (ui in hepsi) {
            assertTrue(ui.title.isNotBlank())
            assertTrue(ui.explanation.length >= 30)
        }
    }
}
