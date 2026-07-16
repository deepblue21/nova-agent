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
        assertEquals(i