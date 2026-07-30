package com.nova.agent

import com.nova.agent.data.FALLBACK_MODELS
import com.nova.agent.net.GatewayConnectionClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Gateway'in canlı /v1/models yanıtının ayrıştırılması — saf, JVM'de koşar. */
class GatewayCatalogTest {

    private val body = """
        {
          "data": [
            {"id":"auto","name":"Dinamik Yönlendirme","group":"Otomatik","available":true,
             "desc":"gateway göreve göre model seçer"},
            {"id":"ollama/qwen3:14b","name":"qwen3:14b","group":"Yerel · Ollama",
             "available":true,"desc":"14B · Q4_K_M · 8.4 GB"},
            {"id":"anthropic/claude-opus-4-8","name":"Claude Opus 4.8","group":"Bulut · API Key",
             "available":false,"reason":"ANTHROPIC_API_KEY tanımlı değil"}
          ],
          "defaultModel": "ollama/qwen3:14b",
          "ollama": {"ok": true, "count": 1}
        }
    """.trimIndent()

    @Test
    fun `canli katalog ayristirilir`() {
        val cat = GatewayConnectionClient.parseCatalog(body)!!
        assertEquals(3, cat.models.size)
        assertEquals("ollama/qwen3:14b", cat.defaultModelId)
        assertTrue(cat.ollamaOk)
        assertNull(cat.ollamaError)

        val yerel = cat.models.first { it.id == "ollama/qwen3:14b" }
        assertEquals("qwen3:14b", yerel.name)
        // Android her zaman gateway'e konuşur: model kimliği aynen gönderilir.
        assertEquals("ollama/qwen3:14b", yerel.model)
        assertEquals("14B · Q4_K_M · 8.4 GB", yerel.desc)
        assertTrue(yerel.available)
    }

    @Test
    fun `anahtarsiz bulut modeli pasif ve gerekceli gelir`() {
        val cat = GatewayConnectionClient.parseCatalog(body)!!
        val opus = cat.models.first { it.id == "anthropic/claude-opus-4-8" }
        assertFalse(opus.available)
        assertEquals("ANTHROPIC_API_KEY tanımlı değil", opus.reason)
    }

    @Test
    fun `ollama kapaliysa dürüst hata tasinir`() {
        val cat = GatewayConnectionClient.parseCatalog(
            """{"data":[{"id":"auto","name":"Dinamik"}],"defaultModel":"",
               "ollama":{"ok":false,"error":"Ollama'ya ulaşılamadı"}}""",
        )!!
        assertFalse(cat.ollamaOk)
        assertEquals("Ollama'ya ulaşılamadı", cat.ollamaError)
        // defaultModel boşsa null olur → istemci sessizce auto'ya düşmez.
        assertNull(cat.defaultModelId)
    }

    @Test
    fun `bozuk veya bos govde null doner - uydurma model yok`() {
        assertNull(GatewayConnectionClient.parseCatalog(""))
        assertNull(GatewayConnectionClient.parseCatalog("   "))
        assertNull(GatewayConnectionClient.parseCatalog("bozuk{"))
        assertNull(GatewayConnectionClient.parseCatalog("""{"data":[]}"""))
        assertNull(GatewayConnectionClient.parseCatalog("""{"baska":1}"""))
        // id'siz girdiler atlanır; hiç kalmazsa null.
        assertNull(GatewayConnectionClient.parseCatalog("""{"data":[{"name":"x"},{}]}"""))
    }

    @Test
    fun `eksik alanlar guvenli varsayilana duser`() {
        val cat = GatewayConnectionClient.parseCatalog("""{"data":[{"id":"ollama/a"}]}""")!!
        val m = cat.models.single()
        assertEquals("ollama/a", m.name) // name yoksa id
        assertEquals("Diğer", m.group)
        assertTrue(m.available)          // available yoksa true
        assertEquals("", m.reason)
    }

    @Test
    fun `yedek listede bayat model kimligi kalmadi`() {
        val ids = FALLBACK_MODELS.map { it.model }
        assertNotNull(ids)
        for (eski in listOf(
            "openai/gpt-4o-mini",
            "gemini/gemini-2.5-pro",
            "gemini/gemini-2.5-flash",
            "anthropic/claude-opus-4-20250514",
            "anthropic/claude-sonnet-4-20250514",
            "ollama/qwen3:14b",
        )) {
            assertFalse("bayat model yedekte: $eski", ids.contains(eski))
        }
        // Yedek liste yalnız gateway'e ulaşılamadığında kullanılır; yerel
        // Ollama modelleri artık sabit yazılmaz, canlı gelir.
        assertFalse(FALLBACK_MODELS.any { it.model.startsWith("ollama/") })
    }
}
