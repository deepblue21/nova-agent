package com.nova.agent

import com.nova.agent.net.GatewayConnectionClient
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ağ hatası sınıflandırması: her katman hatası, kullanıcıya nedene özel
 * Türkçe mesaj + eyleme dönük ipucu üretmeli. Saf/JVM.
 */
class GatewayConnectionFailureTest {

    @Test
    fun `zaman asimi ayni ag ve start-horus ipucusu verir`() {
        val (message, hint) = GatewayConnectionClient.classifyFailure(SocketTimeoutException("timed out"))
        assertTrue(message.contains("Zaman aşımı"))
        assertTrue(hint.contains("aynı ağda"))
        assertTrue(hint.contains("start-horus"))
    }

    @Test
    fun `baglanti reddi port ve loopback ipucusu verir`() {
        val (message, hint) = GatewayConnectionClient.classifyFailure(ConnectException("Connection refused"))
        assertTrue(message.contains("reddedildi"))
        assertTrue(hint.contains("127.0.0.1"))
    }

    @Test
    fun `bilinmeyen sunucu adi ip ornegi onerir`() {
        val (message, hint) = GatewayConnectionClient.classifyFailure(UnknownHostException("nova.local"))
        assertTrue(message.contains("çözülemedi"))
        assertTrue(hint.contains("192.168."))
    }

    @Test
    fun `rota yoksa ag degisikligi onerilir`() {
        val (message, hint) = GatewayConnectionClient.classifyFailure(NoRouteToHostException("no route"))
        assertTrue(message.contains("rota"))
        assertTrue(hint.contains("Tailscale"))
    }

    @Test
    fun `tls hatasi http onerir`() {
        val (message, hint) = GatewayConnectionClient.classifyFailure(SSLHandshakeException("handshake failed"))
        assertTrue(message.contains("TLS"))
        assertTrue(hint.contains("http://"))
    }

    @Test
    fun `sarmalanmis neden de siniflanir`() {
        val wrapped = IOException("io", SocketTimeoutException("inner"))
        val (message, _) = GatewayConnectionClient.classifyFailure(wrapped)
        assertTrue(message.contains("Zaman aşımı"))
    }

    @Test
    fun `bilinmeyen hata guvenli genel mesaja duser`() {
        val (message, hint) = GatewayConnectionClient.classifyFailure(IOException("tuhaf durum"))
        assertEquals("PC Gateway'e ulaşılamadı", message)
        assertTrue(hint.contains("tuhaf durum"))
    }

    @Test
    fun `mesajsiz bilinmeyen hata bos ipucu tasir`() {
        val (message, hint) = GatewayConnectionClient.classifyFailure(IOException())
        assertEquals("PC Gateway'e ulaşılamadı", message)
        assertEquals("", hint)
    }
}
