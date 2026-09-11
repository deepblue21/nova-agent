package com.nova.agent

import com.nova.agent.llm.ExecutionPolicy
import com.nova.agent.llm.VisionSupport
import com.nova.agent.llm.local.LocalModelCatalog
import com.nova.agent.llm.local.LocalModelSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Faz 12A — görselin nereye gideceği.
 *
 * Buradaki tek kural: görsel, onu GERÇEKTEN görebilen bir yere gider ya da hiç
 * gitmez. Sessizce düşürülmesi en kötü sonuç olurdu: kullanıcı ekranda küçük
 * resmi görür, model resmi hiç almamıştır ve gelen yanıt kibar bir uydurmadır.
 */
class VisionSupportTest {

    private fun spec(vision: Boolean) = LocalModelSpec(
        id = "t", displayName = "Test Modeli", family = "T", quantization = "int4",
        fileName = "t.litertlm", downloadUrl = "https://example.invalid/t.litertlm",
        sizeBytes = 1L, sha256 = "0".repeat(64), licenseName = "Apache-2.0",
        licenseUrl = "https://example.invalid", recommendedRamGb = 4,
        supportsThinkingToggle = false, supportsVision = vision,
    )

    private fun decide(
        policy: ExecutionPolicy,
        vision: Boolean = true,
        installed: Boolean = true,
        gatewayReady: Boolean = true,
        gatewaySees: Boolean = true,
    ) = VisionSupport.decide(policy, spec(vision), installed, gatewayReady, gatewaySees)

    @Test
    fun `telefon gorebiliyorsa gorsel cihazdan cikmaz`() {
        val target = decide(ExecutionPolicy.LOCAL_FIRST)
        assertEquals(VisionSupport.Target.OnDevice("Test Modeli"), target)
        // Hibrit de telefonda çalışan bir politika.
        assertTrue(decide(ExecutionPolicy.HYBRID) is VisionSupport.Target.OnDevice)
    }

    @Test
    fun `cevrimdisi modda gorsel asla PC'ye onerilmez`() {
        // Kullanıcı Çevrimdışı'yı seçtiyse bu bir gizlilik sözü. Model göremiyor
        // diye "PC'ye gönderelim mi?" demek o sözü bozardı.
        val target = decide(ExecutionPolicy.LOCAL_ONLY, vision = false, gatewayReady = true)
        assertTrue(target is VisionSupport.Target.Unsupported)
        val reason = (target as VisionSupport.Target.Unsupported).reason
        assertFalse("cevrimdisi modda PC onerilmemeli", reason.contains("PC'ye"))
        assertTrue("cozum model indirmek", target.suggestsModelDownload)
    }

    @Test
    fun `cevrimdisi modda goren model varsa calisir`() {
        assertEquals(
            VisionSupport.Target.OnDevice("Test Modeli"),
            decide(ExecutionPolicy.LOCAL_ONLY, vision = true, gatewayReady = false),
        )
    }

    @Test
    fun `telefon goremiyorsa ve PC hazirsa PC'ye gider`() {
        assertEquals(
            VisionSupport.Target.Gateway,
            decide(ExecutionPolicy.LOCAL_FIRST, vision = false),
        )
        assertEquals(VisionSupport.Target.Gateway, decide(ExecutionPolicy.GATEWAY_ONLY))
    }

    @Test
    fun `kurulu olmayan goren model gorebiliyor sayilmaz`() {
        // Katalogda görsel desteği yazıyor olması dosyanın telefonda
        // olduğu anlamına gelmez.
        assertEquals(
            VisionSupport.Target.Gateway,
            decide(ExecutionPolicy.LOCAL_FIRST, vision = true, installed = false),
        )
    }

    @Test
    fun `PC modeli gormuyorsa sessizce gonderilmez`() {
        val target = decide(ExecutionPolicy.GATEWAY_ONLY, vision = false, gatewaySees = false)
        assertTrue(target is VisionSupport.Target.Unsupported)
        assertTrue((target as VisionSupport.Target.Unsupported).reason.contains("PC modeli"))
        // Çözüm model indirmek değil, PC'de model değiştirmek.
        assertFalse(target.suggestsModelDownload)
    }

    @Test
    fun `hicbir hedef yoksa durum acikca soylenir`() {
        val target = decide(
            ExecutionPolicy.LOCAL_FIRST, vision = false, gatewayReady = false, gatewaySees = false,
        )
        assertTrue(target is VisionSupport.Target.Unsupported)
        assertTrue((target as VisionSupport.Target.Unsupported).suggestsModelDownload)
    }

    @Test
    fun `model yoksa cokmez`() {
        val target = VisionSupport.decide(
            ExecutionPolicy.LOCAL_FIRST, null, localInstalled = false,
            gatewayReady = false, gatewayModelSeesImages = false,
        )
        assertTrue(target is VisionSupport.Target.Unsupported)
    }

    @Test
    fun `gorsel destegi yalniz dogrulanmis modellerde acik`() {
        // Bayrak model kartından doğrulanarak açılır. Yanlış açmak, görseli hiç
        // görmeyen bir modelden kibar bir uydurma yanıt almak demektir.
        val all = LocalModelCatalog.entries
        val seeing = all.filter { it.supportsVision }.map { it.id }.toSet()
        assertEquals(setOf("gemma4-e2b", "gemma4-e4b"), seeing)
        // Metin modelleri kesinlikle kapalı.
        assertTrue(all.filter { it.family == "Qwen3" }.none { it.supportsVision })
    }

    @Test
    fun `gateway gorusu bildirmiyorsa varsayilmaz`() {
        // Eski bir gateway `vision` alanını hiç göndermez. O durumda "evet"
        // saymak, görmeyen bir modele görsel gönderip uydurma yanıt almaktır.
        val body = """{"data":[{"id":"ollama/qwen3:8b","name":"qwen3","group":"Yerel"}]}"""
        val catalog = com.nova.agent.net.GatewayConnectionClient.parseCatalog(body)!!
        assertFalse(catalog.models.first().vision)

        val withVision = """{"data":[{"id":"auto","name":"Oto","group":"G","vision":true}]}"""
        assertTrue(
            com.nova.agent.net.GatewayConnectionClient.parseCatalog(withVision)!!.models.first().vision,
        )
    }

    @Test
    fun `gorsel sinirlari makul`() {
        assertTrue(VisionSupport.MAX_EDGE_PX in 512..1536)
        assertTrue(VisionSupport.JPEG_QUALITY in 70..95)
    }
}
