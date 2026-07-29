package com.nova.agent

import com.nova.agent.llm.local.LocalModelCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalModelCatalogTest {

    @Test
    fun `katalog bos degil ve id'ler benzersiz`() {
        assertTrue(LocalModelCatalog.entries.isNotEmpty())
        val ids = LocalModelCatalog.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `tum indirmeler https ve revizyona kilitli`() {
        val pinned = Regex("/resolve/[0-9a-f]{40}/")
        for (spec in LocalModelCatalog.entries) {
            assertTrue(spec.id, spec.downloadUrl.startsWith("https://"))
            assertTrue(spec.id, pinned.containsMatchIn(spec.downloadUrl))
            assertTrue(spec.id, spec.downloadUrl.endsWith(spec.fileName))
        }
    }

    @Test
    fun `sha256 64 hanelik hex ve boyut pozitif`() {
        val hex = Regex("^[0-9a-f]{64}$")
        for (spec in LocalModelCatalog.entries) {
            assertTrue(spec.id, hex.matches(spec.sha256))
            assertTrue(spec.id, spec.sizeBytes > 0)
            assertTrue(spec.id, spec.licenseName.isNotBlank())
        }
    }

    @Test
    fun `dogrulanmis referans degerleri degismedi`() {
        val int4 = LocalModelCatalog.byId("qwen3-0.6b-int4")!!
        assertEquals(497_664_000L, int4.sizeBytes)
        assertEquals(
            "b1baab462f6be49d70eada79d715c2c52cd9ece0cad00bddf6a2c097d23498e9",
            int4.sha256,
        )
        assertEquals(int4, LocalModelCatalog.default)
        assertTrue(!int4.gated)
    }

    @Test
    fun `functiongemma araci kapili ve kayitli`() {
        val fg = LocalModelCatalog.byId("functiongemma-270m")!!
        assertTrue(fg.gated)
        assertEquals(288_964_608L, fg.sizeBytes)
        assertTrue(fg.downloadUrl.contains(LocalModelCatalog.FUNCTIONGEMMA_REVISION))
    }

    @Test
    fun `buyuk modeller kapisiz apache lisansli ve dogrulanmis degerlerle kayitli`() {
        // 2026-07-19'da HuggingFace API'sinden doğrulanan boyut + SHA-256 değerleri.
        val beklenen = mapOf(
            "qwen3-4b-int4" to (2_659_057_664L to
                "f0794bc77efeaaf4f7af815f04c483b19b8f2ae4a102cef1b7b760a25848a18e"),
            "gemma4-e4b" to (3_659_530_240L to
                "0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0"),
            "qwen3-8b-int4" to (4_887_412_736L to
                "cb4e6d0de4bbf6656d177812cf0c6a983967dedd17e7f88e84b901c3a9862a42"),
            "gemma4-12b" to (6_547_589_312L to
                "74fc29a10c20eb5b3ced6c389471a7994a0ffd657255b2a1c764262fb9054aef"),
            "qwen3-14b-int4" to (8_655_863_808L to
                "71de7d58f1b46a3fcba2f7bb700ebcc3c3715877d9a7028d93dd1bcd89bbe946"),
        )
        for ((id, degerler) in beklenen) {
            val spec = LocalModelCatalog.byId(id)!!
            val (boyut, sha) = degerler
            assertEquals(id, boyut, spec.sizeBytes)
            assertEquals(id, sha, spec.sha256)
            // Hepsi kapısız: ilk kurulumda HF token istemez.
            assertTrue(id, !spec.gated)
            assertEquals(id, "Apache-2.0", spec.licenseName)
            // Büyük modeller dürüst bir RAM beklentisi bildirir.
            assertTrue(id, spec.recommendedRamGb >= 8)
        }
    }

    @Test
    fun `buyuk modellerde RAM beklentisi boyutla birlikte artar`() {
        val sirali = LocalModelCatalog.entries
            .filter { it.recommendedRamGb >= 8 }
            .sortedBy { it.sizeBytes }
        assertTrue("en az 5 büyük model", sirali.size >= 5)
        for ((onceki, sonraki) in sirali.zipWithNext()) {
            assertTrue(
                "${onceki.id} -> ${sonraki.id}",
                sonraki.recommendedRamGb >= onceki.recommendedRamGb,
            )
        }
        // 8 GB RAM'li bir telefon 14B'yi riskli görmeli — taklit yok.
        val enBuyuk = sirali.last()
        assertEquals(
            com.nova.agent.llm.local.ModelRecommender.Fit.RISKY,
            com.nova.agent.llm.local.ModelRecommender.fit(enBuyuk, 8.0),
        )
    }

    @Test
    fun `gemma kapili ve dogrulanmis degerlerle kayitli`() {
        val gemma = LocalModelCatalog.byId("gemma3-1b-int4")!!
        assertTrue(gemma.gated)
        assertEquals(584_417_280L, gemma.sizeBytes)
        assertEquals(
            "1325ae366d31950f137c9c357b9fa89448b176d76998180c08ceaca78bba98be",
            gemma.sha256,
        )
        assertTrue(gemma.downloadUrl.contains(LocalModelCatalog.GEMMA_REVISION))
        // Varsayılan model kapısız kalmalı: ilk kurulum deneyimi token istemez.
        assertTrue(!LocalModelCatalog.default.gated)
    }
}
