package com.nova.agent

import com.nova.agent.net.GatewayConnectionClient
import com.nova.agent.net.NetworkPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GÜVENLİK — ağ politikasının gerçekten UYGULANDIĞINI doğrular (Y1).
 *
 * `NetworkPolicyTest` politikanın saf mantığını kapsıyor: "bu adres yerel mi".
 * Ama denetimde çıkan kusur mantıkta değildi — politika **çağrılmıyordu**.
 * `NetworkPolicy` yalnız `PairingClient`te kullanılıyor, sohbet / bağlantı testi
 * / görev yolları hiç sormuyordu. Manifestteki `usesCleartextTraffic="true"`
 * yorumu ise "gerçek kontrol kodda" diye güvence veriyordu.
 *
 * Bu dosya o boşluğu kilitler: kontrolün ÇAĞRI NOKTASINDA durduğunu sınar.
 */
class NetworkPolicyEnforcementTest {

    // ---------- boğaz noktası: canonicalBaseUrl ----------

    @Test
    fun `sifresiz http genel bir adrese izin verilmez`() {
        assertNull(
            "acik internete Bearer belirteci sifresiz gidemez",
            GatewayConnectionClient.canonicalBaseUrl("http://93.184.216.34:18088/v1"),
        )
        assertNull(GatewayConnectionClient.canonicalBaseUrl("http://gateway.example.com/v1"))
    }

    @Test
    fun `https her zaman serbesttir`() {
        assertNotNull(
            "bulut gateway'i https ile calisabilmeli",
            GatewayConnectionClient.canonicalBaseUrl("https://nova.example.com/v1"),
        )
    }

    @Test
    fun `yerel agda sifresiz http serbesttir`() {
        assertNotNull(GatewayConnectionClient.canonicalBaseUrl("http://192.168.1.20:18088/v1"))
        assertNotNull(GatewayConnectionClient.canonicalBaseUrl("http://10.0.2.2:18088/v1"))
        assertNotNull(GatewayConnectionClient.canonicalBaseUrl("http://127.0.0.1:18088/v1"))
        assertNotNull(GatewayConnectionClient.canonicalBaseUrl("http://salih-pc.local:18088/v1"))
    }

    /** Model listesi de aynı kapıdan geçer; ayrı bir yol açılmamalı. */
    @Test
    fun `modelsUrl de ayni politikaya tabidir`() {
        assertNull(GatewayConnectionClient.modelsUrl("http://93.184.216.34:18088/v1"))
        assertNotNull(GatewayConnectionClient.modelsUrl("http://192.168.1.20:18088/v1"))
    }

    // ---------- atlatma denemeleri ----------

    /**
     * `hostOf` yetkili bölümün sonunu YALNIZ '/' ile arıyordu; '?' ve '#'
     * hesaba katılmadığı için "http://evil.com?@192.168.1.5" adresinde
     * userinfo kırpması yanlış çalışıp host'u "192.168.1.5" sanıyordu —
     * politika izin verirken OkHttp gerçekte evil.com'a bağlanacaktı.
     */
    @Test
    fun `soru isareti ile userinfo numarasi host degistiremez`() {
        assertEquals("evil.com", NetworkPolicy.hostOf("http://evil.com?@192.168.1.5"))
        assertEquals("evil.com", NetworkPolicy.hostOf("http://evil.com#@192.168.1.5"))
        assertFalse(NetworkPolicy.allows("http://evil.com?@192.168.1.5"))
    }

    @Test
    fun `gercek userinfo hala kirpilir`() {
        assertEquals("192.168.1.5", NetworkPolicy.hostOf("http://kullanici:parola@192.168.1.5:18088/v1"))
    }

    @Test
    fun `kimlik bilgisi tasiyan adres kanonik hale getirilmez`() {
        assertNull(
            GatewayConnectionClient.canonicalBaseUrl("http://kullanici:parola@192.168.1.5:18088/v1"),
        )
    }

    // ---------- allowsHost: cozulmus host uzerinden karar ----------

    @Test
    fun `allowsHost sema ve host uzerinden karar verir`() {
        assertTrue(NetworkPolicy.allowsHost("https", "evil.com"))
        assertFalse(NetworkPolicy.allowsHost("http", "evil.com"))
        assertTrue(NetworkPolicy.allowsHost("http", "192.168.1.5"))
        assertTrue(NetworkPolicy.allowsHost("HTTP", "10.0.0.5"))
        assertFalse(NetworkPolicy.allowsHost("ftp", "192.168.1.5"))
    }
}
