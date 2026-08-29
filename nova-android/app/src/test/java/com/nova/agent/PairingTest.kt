package com.nova.agent

import com.nova.agent.net.Pairing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bu testler `gateway/test/pairing.test.mjs` ile AYNI vakaları koşar.
 * İki taraf aynı davranmazsa eşleme sessizce bozulur; bu yüzden vakalar
 * bilinçli olarak birebir kopyalanmıştır.
 */
class PairingTest {

    @Test
    fun `alfabede gorsel ikizler yok`() {
        for (ch in "ILOU") {
            assertFalse("$ch alfabede olmamalı", Pairing.ALPHABET.contains(ch))
        }
        assertEquals(32, Pairing.ALPHABET.length)
        assertEquals(32, Pairing.ALPHABET.toSet().size)
    }

    @Test
    fun `normalizeCode gorsel ikizleri duzeltir`() {
        assertEquals("H7K29M4P", Pairing.normalizeCode("h7k2-9m4p"))
        assertEquals("00111234", Pairing.normalizeCode("O0IL1234"))
        assertEquals("H7K29M4P", Pairing.normalizeCode("  H7K2 9M4P  "))
        assertEquals("H7K29M4P", Pairing.normalizeCode("H7K2_9M4P"))
    }

    @Test
    fun `normalizeCode gecersiz girdide kismi kod uydurmaz`() {
        assertEquals("", Pairing.normalizeCode(""))
        assertEquals("", Pairing.normalizeCode(null))
        assertEquals("", Pairing.normalizeCode("H7K2"))
        assertEquals("", Pairing.normalizeCode("H7K29M4PX"))
        assertEquals("", Pairing.normalizeCode("H7K29M4U"))
        assertEquals("", Pairing.normalizeCode("H7K29M4!"))
    }

    @Test
    fun `formatCode insan gozu icin ayirir`() {
        assertEquals("H7K2-9M4P", Pairing.formatCode("h7k29m4p"))
        assertEquals("bozuk", Pairing.formatCode("bozuk"))
    }

    @Test
    fun `baseUrl her zaman v1 ile biter`() {
        assertEquals("http://192.168.1.20:8088/v1", Pairing.baseUrl("192.168.1.20", 8088))
        assertEquals("https://horus.local/v1", Pairing.baseUrl("horus.local", 443, tls = true))
        assertEquals("http://horus.local/v1", Pairing.baseUrl("horus.local", 80))
    }

    @Test
    fun `baseUrl ipv6 adresini koseli paranteze alir`() {
        assertEquals("http://[fe80::1]:8088/v1", Pairing.baseUrl("fe80::1", 8088))
        assertEquals("http://[fe80::1]:8088/v1", Pairing.baseUrl("[fe80::1]", 8088))
    }

    @Test
    fun `baseUrl gecersiz girdide bos doner`() {
        assertEquals("", Pairing.baseUrl("", 8088))
        assertEquals("", Pairing.baseUrl("1.2.3.4", 0))
        assertEquals("", Pairing.baseUrl("1.2.3.4", 70000))
    }

    @Test
    fun `parsePairUri tam uriyi cozer`() {
        val parsed = Pairing.parsePairUri(
            "horus://pair?v=1&code=H7K29M4P&host=192.168.1.20&port=8088&name=SALIH-PC",
        )
        assertNotNull(parsed)
        requireNotNull(parsed)
        assertEquals(1, parsed.version)
        assertEquals("H7K29M4P", parsed.code)
        assertEquals("192.168.1.20", parsed.host)
        assertEquals(8088, parsed.port)
        assertFalse(parsed.tls)
        assertEquals("http://192.168.1.20:8088/v1", parsed.baseUrl)
        assertEquals("SALIH-PC", parsed.name)
    }

    @Test
    fun `parsePairUri kucuk harfli kodu normalize eder`() {
        val parsed = Pairing.parsePairUri("horus://pair?code=h7k2-9m4p&host=1.2.3.4&port=8088")
        assertEquals("H7K29M4P", parsed?.code)
    }

    @Test
    fun `parsePairUri tls bayragini tasir`() {
        val parsed = Pairing.parsePairUri("horus://pair?code=H7K29M4P&host=horus.example&port=443&tls=1")
        assertEquals("https://horus.example/v1", parsed?.baseUrl)
        assertEquals(true, parsed?.tls)
    }

    @Test
    fun `parsePairUri v parametresi yoksa 1 varsayar`() {
        val parsed = Pairing.parsePairUri("horus://pair?code=H7K29M4P&host=1.2.3.4&port=8088")
        assertEquals(1, parsed?.version)
    }

    @Test
    fun `parsePairUri taninmayan girdide null doner`() {
        assertNull(Pairing.parsePairUri(""))
        assertNull(Pairing.parsePairUri(null))
        assertNull(Pairing.parsePairUri("https://example.com"))
        assertNull(Pairing.parsePairUri("horus://other?code=H7K29M4P"))
        assertNull(Pairing.parsePairUri("horus://pair?v=2&code=H7K29M4P&host=1.2.3.4&port=1"))
        assertNull(Pairing.parsePairUri("horus://pair?code=H7K29M4P"))
        assertNull(Pairing.parsePairUri("horus://pair?host=1.2.3.4&port=8088"))
        assertNull(Pairing.parsePairUri("horus://pair?code=H7K29M4P&host=1.2.3.4&port=abc"))
    }

    @Test
    fun `parsePairUri sema buyuk harfle yazilsa da kabul edilir`() {
        assertNotNull(Pairing.parsePairUri("HORUS://PAIR?code=H7K29M4P&host=1.2.3.4&port=8088"))
    }

    @Test
    fun `parsePairUri yuzde kodlamasini cozer`() {
        val parsed = Pairing.parsePairUri("horus://pair?code=H7K29M4P&host=1.2.3.4&port=8088&name=SALIH%20PC")
        assertEquals("SALIH PC", parsed?.name)
    }

    @Test
    fun `parsePairUri isim alanini sinirlar`() {
        val long = "A".repeat(200)
        val parsed = Pairing.parsePairUri("horus://pair?code=H7K29M4P&host=1.2.3.4&port=8088&name=$long")
        assertEquals(64, parsed?.name?.length)
    }

    @Test
    fun `parsePairUri bozuk yuzde kodlamasinda patlamaz`() {
        val parsed = Pairing.parsePairUri("horus://pair?code=H7K29M4P&host=1.2.3.4&port=8088&name=%ZZ")
        assertNotNull("bozuk kodlama URI'yi tamamen reddetmemeli", parsed)
    }

    @Test
    fun `parsePairUri yinelenen anahtarda ilkini alir`() {
        val parsed = Pairing.parsePairUri(
            "horus://pair?code=H7K29M4P&host=1.2.3.4&port=8088&host=9.9.9.9",
        )
        assertEquals("1.2.3.4", parsed?.host)
    }

    @Test
    fun `parsePairUri sonucu dogrudan ayarlara yazilabilir`() {
        // Sözleşme: baseUrl mevcut istemcinin beklediği biçimde olmalı.
        val parsed = Pairing.parsePairUri("horus://pair?code=H7K29M4P&host=192.168.1.20&port=8088")
        requireNotNull(parsed)
        assertTrue(parsed.baseUrl.endsWith("/v1"))
        assertTrue(parsed.baseUrl.startsWith("http"))
    }
}
