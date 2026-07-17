package com.nova.agent

import com.nova.agent.llm.local.LocalModelCatalog
import com.nova.agent.llm.local.LocalModelSpec
import com.nova.agent.llm.local.ModelMetricsStore
import com.nova.agent.llm.local.ModelRecommender
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelAutomationTest {

    private fun spec(id: String, ram: Int, size: Long, gated: Boolean) = LocalModelSpec(
        id = id, displayName = id, family = "t", quantization = "q",
        fileName = "$id.litertlm", downloadUrl = "https://x/$id", sizeBytes = size,
        sha256 = "0".repeat(64), licenseName = "L", licenseUrl = "u",
        recommendedRamGb = ram, supportsThinkingToggle = false, gated = gated,
    )

    // ---------- uygunluk ----------

    @Test
    fun `uygunluk RAM'e gore siniflanir`() {
        val s = spec("m", ram = 4, size = 1, gated = false)
        assertEquals(ModelRecommender.Fit.COMFORTABLE, ModelRecommender.fit(s, 4.0))
        assertEquals(ModelRecommender.Fit.COMFORTABLE, ModelRecommender.fit(s, 6.0))
        assertEquals(ModelRecommender.Fit.TIGHT, ModelRecommender.fit(s, 3.2))
        assertEquals(ModelRecommender.Fit.RISKY, ModelRecommender.fit(s, 2.0))
        assertEquals(ModelRecommender.Fit.UNKNOWN, ModelRecommender.fit(s, 0.0))
    }

    // ---------- öneri ----------

    @Test
    fun `oneri kapisiz ve rahat calisanlarin en buyugunu secer`() {
        val models = listOf(
            spec("kucuk", ram = 3, size = 300, gated = false),
            spec("buyuk", ram = 4, size = 600, gated = false),
            spec("kapili", ram = 3, size = 900, gated = true),
        )
        // 6 GB'da ikisi de rahat; kapısız en büyük = "buyuk".
        assertEquals("buyuk", ModelRecommender.recommend(models, 6.0).id)
    }

    @Test
    fun `dusuk RAM'de risksiz en iyiye duser, kapiliyi secmez`() {
        val models = listOf(
            spec("kucuk", ram = 3, size = 300, gated = false),
            spec("buyuk", ram = 6, size = 900, gated = false),
        )
        // 3 GB: "buyuk" riskli, "kucuk" rahat → kucuk.
        assertEquals("kucuk", ModelRecommender.recommend(models, 3.0).id)
    }

    @Test
    fun `RAM olculemezse kapisiz en kucuk onerilir`() {
        val rec = ModelRecommender.recommend(deviceRamGb = 0.0)
        assertFalse(rec.gated)
        // Katalogdaki en küçük kapısız model int4 Qwen'dir.
        assertEquals("qwen3-0.6b-int4", rec.id)
    }

    @Test
    fun `gercek katalog dusuk RAM'de kapisiz kalir`() {
        val rec = ModelRecommender.recommend(LocalModelCatalog.entries, 3.0)
        assertFalse(rec.gated)
    }

    // ---------- metrikler ----------

    @Test
    fun `token ve hiz hesabi dogru`() {
        assertEquals(0, ModelMetricsStore.approxTokens(0))
        assertEquals(1, ModelMetricsStore.approxTokens(4))
        assertEquals(25, ModelMetricsStore.approxTokens(100))
        // 100 karakter ~25 token, 1000 ms → 25 tok/sn.
        assertEquals(25.0, ModelMetricsStore.tokensPerSecond(100, 1000), 0.001)
        assertEquals(0.0, ModelMetricsStore.tokensPerSecond(100, 0), 0.001)
    }

    @Test
    fun `metrik serilestirme cift yonlu`() {
        val m = mapOf(
            "a" to com.nova.agent.llm.local.ModelMetrics(1200, 12.5, 1_752_000_000_000, 3),
        )
        val round = ModelMetricsStore.parse(ModelMetricsStore.serialize(m))
        assertEquals(1200L, round["a"]!!.loadMs)
        assertEquals(12.5, round["a"]!!.tokensPerSec, 0.001)
        assertEquals(3, round["a"]!!.runs)
        assertTrue(ModelMetricsStore.parse("").isEmpty())
        assertTrue(ModelMetricsStore.parse("bozuk{").isEmpty())
    }

    @Test
    fun `store kaydeder ve yukleme sifirsa onceki degeri korur`() {
        val dir = Files.createTempDirectory("metrics_test").toFile()
        val store = ModelMetricsStore(File(dir, "m.json"))
        store.record("a", loadMs = 2000, tokensPerSec = 10.0, nowMs = 1)
        store.record("a", loadMs = 0, tokensPerSec = 20.0, nowMs = 2)
        val m = store.get("a")
        assertEquals(2000L, m.loadMs)     // korundu
        assertEquals(20.0, m.tokensPerSec, 0.001) // güncellendi
        assertEquals(2, m.runs)
    }
}
