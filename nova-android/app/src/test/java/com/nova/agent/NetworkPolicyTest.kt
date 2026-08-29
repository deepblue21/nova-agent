package com.nova.agent

import com.nova.agent.net.NetworkPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkPolicyTest {

    @Test
    fun `rfc1918 adresleri yerel sayilir`() {
        for (h in listOf("192.168.1.20", "10.0.0.5", "172.16.0.1", "172.31.255.254", "127.0.0.1")) {
            assertTrue("$h yerel olmalı", NetworkPolicy.isLocalHost(h))
        }
    }

    @Test
    fun `172_15 ve 172_32 rfc1918 disindadir`() {
        assertFalse(NetworkPolicy.isLocalHost("172.15.0.1"))
        assertFalse(NetworkPolicy.isLocalHost("172.32.0.1"))
    }

    @Test
    fun `link local ve cgnat yerel sayilir`() {
        assertTrue(NetworkPolicy.isLocalHost("169.254.1.1"))
        assertTrue("tailnet aralığı", NetworkPolicy.isLocalHost("100.64.0.1"))
        assertTrue(NetworkPolicy.isLocalHost("100.127.255.255"))
        assertFalse("100.128+ genel internettir", NetworkPolicy.isLocalHost("100.128.0.1"))
        assertFalse(NetworkPolicy.isLocalHost("100.63.0.1"))
    }

    @Test
    fun `mdns ve localhost adlari yerel sayilir`() {
        assertTrue(NetworkPolicy.isLocalHost("localhost"))
        assertTrue(NetworkPolicy.isLocalHost("salih-pc.local"))
        assertTrue("sondaki nokta atılmalı", NetworkPolicy.isLocalHost("salih-pc.local."))
        assertTrue("büyük harf duyarsız", NetworkPolicy.isLocalHost("SALIH-PC.LOCAL"))
    }

    @Test
    fun `ipv6 loopback ve yerel araliklar`() {
        assertTrue(NetworkPolicy.isLocalHost("::1"))
        assertTrue(NetworkPolicy.isLocalHost("fe80::1"))
        assertTrue(NetworkPolicy.isLocalHost("[fe80::1]"))
        assertTrue(NetworkPolicy.isLocalHost("fd00::1"))
        assertFalse(NetworkPolicy.isLocalHost("2606:4700::1111"))
    }

    @Test
    fun `genel adresler yerel degildir`() {
        for (h in listOf("8.8.8.8", "1.1.1.1", "93.184.216.34", "example.com", "horus.example.org")) {
            assertFalse("$h yerel sayılmamalı", NetworkPolicy.isLocalHost(h))
        }
    }

    @Test
    fun `bozuk girdi yerel sayilmaz`() {
        assertFalse(NetworkPolicy.isLocalHost(""))
        assertFalse(NetworkPolicy.isLocalHost(null))
        assertFalse(NetworkPolicy.isLocalHost("192.168.1"))
        assertFalse(NetworkPolicy.isLocalHost("192.168.1.999"))
        assertFalse(NetworkPolicy.isLocalHost("192.168.1.a"))
    }

    @Test
    fun `hostOf adresi ayiklar`() {
        assertEquals("192.168.1.20", NetworkPolicy.hostOf("http://192.168.1.20:8088/v1"))
        assertEquals("horus.local", NetworkPolicy.hostOf("https://horus.local/v1"))
        assertEquals("fe80::1", NetworkPolicy.hostOf("http://[fe80::1]:8088/v1"))
        assertEquals("example.com", NetworkPolicy.hostOf("http://user:pw@example.com:80/v1"))
        assertEquals("", NetworkPolicy.hostOf("bozuk"))
        assertEquals("", NetworkPolicy.hostOf(null))
    }

    @Test
    fun `https her zaman kabul edilir`() {
        assertTrue(NetworkPolicy.allows("https://horus.example.org/v1"))
        assertTrue(NetworkPolicy.allows("https://192.168.1.20:8443/v1"))
    }

    @Test
    fun `http yerel agda kabul edilir`() {
        assertTrue(NetworkPolicy.allows("http://192.168.1.20:8088/v1"))
        assertTrue(NetworkPolicy.allows("http://10.0.2.2:8088/v1"))
        assertTrue("emülatör varsayılanı çalışmaya devam etmeli", NetworkPolicy.allows("http://10.0.2.2:8088/v1"))
        assertTrue(NetworkPolicy.allows("http://salih-pc.local:8088/v1"))
    }

    @Test
    fun `http genel adrese engellenir ve gerekce verir`() {
        val v = NetworkPolicy.check("http://93.184.216.34:8088/v1")
        assertTrue(v is NetworkPolicy.Verdict.Blocked)
        val blocked = v as NetworkPolicy.Verdict.Blocked
        assertTrue("gerekçe boş olmamalı", blocked.reason.isNotBlank())
        assertTrue("ipucu adresi içermeli", blocked.hint.contains("93.184.216.34"))
    }

    @Test
    fun `desteklenmeyen sema engellenir`() {
        assertFalse(NetworkPolicy.allows("ftp://192.168.1.20/v1"))
        assertFalse(NetworkPolicy.allows("192.168.1.20:8088/v1"))
        assertFalse(NetworkPolicy.allows(""))
        assertFalse(NetworkPolicy.allows(null))
    }

    @Test
    fun `sema buyuk harfli olsa da calisir`() {
        assertTrue(NetworkPolicy.allows("HTTP://192.168.1.20:8088/v1"))
        assertTrue(NetworkPolicy.allows("HTTPS://example.org/v1"))
    }
}
