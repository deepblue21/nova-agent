package com.nova.agent

import com.nova.agent.net.DiscoveredGateway
import com.nova.agent.net.GatewayDiscovery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GatewayDiscoveryTest {

    private val txt = mapOf("v" to "1", "path" to "/v1", "tls" to "0", "name" to "SALIH-PC")

    @Test
    fun `yayindaki servis kullanilabilir gatewaye cevrilir`() {
        val g = GatewayDiscovery.fromService("SALIH-PC", "192.168.1.20", 8088, txt)
        assertNotNull(g)
        requireNotNull(g)
        assertEquals("SALIH-PC", g.displayName)
        assertEquals("192.168.1.20", g.host)
        assertEquals(8088, g.port)
        assertEquals("http://192.168.1.20:8088/v1", g.baseUrl)
    }

    @Test
    fun `txt yoksa makul varsayilanlar kullanilir`() {
        val g = GatewayDiscovery.fromService("PC", "10.0.0.5", 8088, emptyMap())
        assertEquals("http://10.0.0.5:8088/v1", g?.baseUrl)
        assertEquals("PC", g?.displayName)
    }

    @Test
    fun `tls bayragi https uretir`() {
        val g = GatewayDiscovery.fromService("PC", "horus.local", 443, mapOf("tls" to "1"))
        assertEquals("https://horus.local/v1", g?.baseUrl)
    }

    @Test
    fun `ozel path txtden okunur`() {
        // SÖZLEŞME DEĞİŞTİ. Bu test eskiden özel yolun KABUL edildiğini
        // doğruluyordu; oysa `canonicalBaseUrl` Y1'den beri `/v1` dışını
        // reddediyor. Yani test, iki bileşen arasındaki çelişkiyi kilitliyordu:
        // keşif "olur" diyor, istemci "olmaz" diyordu. Böyle bir gateway
        // listede çıkıp eşlemeyi "başarılı" gösteriyor, sonra her istek
        // sessizce düşüyordu. Kullanılamayacak PC hiç önerilmez.
        assertNull(GatewayDiscovery.fromService("PC", "10.0.0.5", 8088, mapOf("path" to "/api/v1")))
        // Açıkça yazılmış `/v1` ve boş değer geçerlidir.
        assertEquals(
            "http://10.0.0.5:8088/v1",
            GatewayDiscovery.fromService("PC", "10.0.0.5", 8088, mapOf("path" to "/v1"))?.baseUrl,
        )
    }

    @Test
    fun `desteklenmeyen surume baglanilmaz`() {
        assertNull(
            "v=2 gelecekteki bir sözleşme — sessizce bağlanmak yanlış olur",
            GatewayDiscovery.fromService("PC", "10.0.0.5", 8088, mapOf("v" to "2")),
        )
    }

    @Test
    fun `bozuk adres reddedilir`() {
        assertNull(GatewayDiscovery.fromService("PC", "", 8088, txt))
        assertNull(GatewayDiscovery.fromService("PC", null, 8088, txt))
        assertNull(GatewayDiscovery.fromService("PC", "10.0.0.5", 0, txt))
        assertNull(GatewayDiscovery.fromService("PC", "10.0.0.5", 70000, txt))
    }

    @Test
    fun `host adinin sonundaki nokta atilir`() {
        val g = GatewayDiscovery.fromService("PC", "salih-pc.local.", 8088, emptyMap())
        assertEquals("salih-pc.local", g?.host)
        assertEquals("http://salih-pc.local:8088/v1", g?.baseUrl)
    }

    @Test
    fun `gorunen ad txt name servis adi host sirasiyla secilir`() {
        assertEquals("TXT-AD", GatewayDiscovery.fromService("servis", "10.0.0.5", 1, mapOf("name" to "TXT-AD"))?.displayName)
        assertEquals("servis", GatewayDiscovery.fromService("servis", "10.0.0.5", 1, emptyMap())?.displayName)
        assertEquals("10.0.0.5", GatewayDiscovery.fromService("", "10.0.0.5", 1, emptyMap())?.displayName)
        assertEquals("10.0.0.5", GatewayDiscovery.fromService(null, "10.0.0.5", 1, mapOf("name" to "  "))?.displayName)
    }

    @Test
    fun `merge ayni gatewayi tekrar listelemez`() {
        val a = DiscoveredGateway("PC", "10.0.0.5", 8088, "http://10.0.0.5:8088/v1")
        val duplicate = a.copy(displayName = "PC (wlan1)")
        assertEquals(1, GatewayDiscovery.merge(listOf(a, duplicate)).size)
    }

    @Test
    fun `merge kararli siralar`() {
        val list = listOf(
            DiscoveredGateway("Zeta", "10.0.0.9", 8088, "http://10.0.0.9:8088/v1"),
            DiscoveredGateway("alpha", "10.0.0.2", 8088, "http://10.0.0.2:8088/v1"),
            DiscoveredGateway("Beta", "10.0.0.3", 8088, "http://10.0.0.3:8088/v1"),
        )
        assertEquals(listOf("alpha", "Beta", "Zeta"), GatewayDiscovery.merge(list).map { it.displayName })
        // Aynı girdi iki kez sıralanınca aynı sonucu vermeli.
        assertEquals(GatewayDiscovery.merge(list), GatewayDiscovery.merge(list.reversed()))
    }

    @Test
    fun `merge bos listede bos doner`() {
        assertEquals(emptyList<DiscoveredGateway>(), GatewayDiscovery.merge(emptyList()))
    }

    @Test
    fun `servis tipi yayinciyla ayni`() {
        // scripts/announce-mdns.mjs içindeki SERVICE_TYPE ile birebir olmalı.
        assertEquals("_horus._tcp", GatewayDiscovery.SERVICE_TYPE)
        assertEquals("_horus._tcp.", GatewayDiscovery.NSD_SERVICE_TYPE)
    }
}
