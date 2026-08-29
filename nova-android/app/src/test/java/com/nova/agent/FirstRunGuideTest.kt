package com.nova.agent

import com.nova.agent.data.AppSettings
import com.nova.agent.data.FirstRunGuide
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * İlk açılış yönlendirmesinin kuralları.
 *
 * Kart, uygulamanın manşet özelliğini (telefonda çevrimdışı LLM) yeni
 * kullanıcıya görünür kılmak için var — ama **yolu tıkamamalı**. Buradaki
 * testler iki tarafı da koruyor: gereksiz yere görünmemeli, gereğinde
 * görünmeli.
 */
class FirstRunGuideTest {

    @Test
    fun `temiz kurulumda gorunur`() {
        assertTrue(
            FirstRunGuide.shouldShow(anyModelInstalled = false, dismissed = false),
        )
    }

    /** Model kurulduysa kartın işi bitmiştir. */
    @Test
    fun `model kurulunca gorunmez`() {
        assertFalse(
            FirstRunGuide.shouldShow(anyModelInstalled = true, dismissed = false),
        )
    }

    /** "Şimdilik atla" kalıcıdır; kart bir daha çıkmaz. */
    @Test
    fun `kapatilinca gorunmez`() {
        assertFalse(
            FirstRunGuide.shouldShow(anyModelInstalled = false, dismissed = true),
        )
    }

    /** İndirme sürerken kart tekrar davet etmez. */
    @Test
    fun `indirme surerken gorunmez`() {
        assertFalse(
            FirstRunGuide.shouldShow(
                anyModelInstalled = false,
                dismissed = false,
                downloadInProgress = true,
            ),
        )
    }

    /** Varsayılan ayarlarda kart kapatılmamış olmalı — yoksa hiç görünmezdi. */
    @Test
    fun `varsayilan ayarda kart kapatilmamis`() {
        assertFalse(AppSettings().firstRunGuideDismissed)
        assertTrue(
            FirstRunGuide.shouldShow(
                anyModelInstalled = false,
                dismissed = AppSettings().firstRunGuideDismissed,
            ),
        )
    }

    /**
     * Metin, önerilen modeli çağırandan alır ve **uydurmaz**. Ayrıca PC'ye
     * bağlanmak isteyen kullanıcıya bu adımın zorunlu olmadığını söylemeli.
     */
    @Test
    fun `metin onerilen modeli ve atlanabilirligi yazar`() {
        val body = FirstRunGuide.body("Qwen3 1.7B (int4)", "0,9 GB")

        assertTrue(body.contains("Qwen3 1.7B (int4)"))
        assertTrue(body.contains("0,9 GB"))
        assertTrue("Zorunlu olmadığı söylenmeli", body.contains("gerekmez"))
        assertTrue("Gizlilik kazancı söylenmeli", body.contains("çıkmaz"))
    }

    @Test
    fun `baslik ve eylem metinleri bos degil`() {
        assertTrue(FirstRunGuide.TITLE.isNotBlank())
        assertTrue(FirstRunGuide.PRIMARY_ACTION.isNotBlank())
        assertTrue(FirstRunGuide.DISMISS_ACTION.isNotBlank())
    }
}
