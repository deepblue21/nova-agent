package com.nova.agent

import com.nova.agent.net.PairingResponses
import com.nova.agent.net.PairingResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingResponsesTest {

    @Test
    fun `claimUrl v1 adresinin sonuna eklenir`() {
        assertEquals("http://192.168.1.20:8088/v1/pair/claim", PairingResponses.claimUrl("http://192.168.1.20:8088/v1"))
        assertEquals("http://192.168.1.20:8088/v1/pair/claim", PairingResponses.claimUrl("http://192.168.1.20:8088/v1/"))
        assertEquals("https://horus.local/v1/pair/claim", PairingResponses.claimUrl("https://horus.local/v1"))
    }

    @Test
    fun `claimUrl bozuk adreste null doner`() {
        assertNull(PairingResponses.claimUrl(""))
        assertNull(PairingResponses.claimUrl(null))
        assertNull(PairingResponses.claimUrl("192.168.1.20:8088/v1"))
        assertNull(PairingResponses.claimUrl("ftp://192.168.1.20/v1"))
    }

    @Test
    fun `parseApiKey gecerli govdeden anahtari cikarir`() {
        assertEquals("nv_ab12cd_secret", PairingResponses.parseApiKey("""{"api_key":"nv_ab12cd_secret","label":"phone"}"""))
    }

    @Test
    fun `parseApiKey bozuk govdede uydurmaz`() {
        assertNull(PairingResponses.parseApiKey(""))
        assertNull(PairingResponses.parseApiKey(null))
        assertNull(PairingResponses.parseApiKey("bu json değil"))
        assertNull(PairingResponses.parseApiKey("""{"label":"phone"}"""))
        assertNull(PairingResponses.parseApiKey("""{"api_key":""}"""))
        assertNull(PairingResponses.parseApiKey("""{"api_key":"   "}"""))
    }

    @Test
    fun `parseLabel eksikse makul varsayilan verir`() {
        assertEquals("phone", PairingResponses.parseLabel("""{"api_key":"x","label":"phone"}"""))
        assertEquals("telefon", PairingResponses.parseLabel("""{"api_key":"x"}"""))
        assertEquals("telefon", PairingResponses.parseLabel("çöp"))
    }

    @Test
    fun `200 anahtarla eslesme uretir`() {
        val r = PairingResponses.interpret(200, """{"api_key":"nv_ab12cd_s","label":"phone"}""", "http://10.0.0.5:8088/v1")
        assertTrue(r is PairingResult.Paired)
        val p = r as PairingResult.Paired
        assertEquals("nv_ab12cd_s", p.apiKey)
        assertEquals("http://10.0.0.5:8088/v1", p.baseUrl)
        assertEquals("phone", p.label)
    }

    @Test
    fun `200 ama anahtarsiz govde durustce hata sayilir`() {
        val r = PairingResponses.interpret(200, """{"ok":true}""", "http://10.0.0.5:8088/v1")
        assertTrue("anahtar yoksa başarı gibi davranılmamalı", r is PairingResult.Failure)
    }

    @Test
    fun `bilinen hata kodlari eyleme donuk ipucu verir`() {
        for (status in listOf(400, 404, 410, 429, 503, 500)) {
            val r = PairingResponses.interpret(status, "", "http://10.0.0.5:8088/v1")
            assertTrue("$status için Failure bekleniyor", r is PairingResult.Failure)
            val f = r as PairingResult.Failure
            assertTrue("$status mesajı boş olmamalı", f.message.isNotBlank())
            assertTrue("$status ipucu boş olmamalı", f.hint.isNotBlank())
        }
    }

    @Test
    fun `410 sure doldu ile kullanildiyi ayni sekilde acikladigimiz icin kullanici yeni kod urettirir`() {
        val f = PairingResponses.interpret(410, "", "http://10.0.0.5:8088/v1") as PairingResult.Failure
        assertTrue(f.hint.contains("yeni bir kod"))
    }

    @Test
    fun `bilinmeyen kod genel hata verir`() {
        val r = PairingResponses.interpret(418, "", "http://10.0.0.5:8088/v1")
        assertTrue(r is PairingResult.Failure)
        assertTrue((r as PairingResult.Failure).message.contains("418"))
    }

    @Test
    fun `basarili yanit baseUrl sonundaki egik cizgiyi temizler`() {
        val p = PairingResponses.interpret(200, """{"api_key":"nv_x"}""", "http://10.0.0.5:8088/v1/") as PairingResult.Paired
        assertEquals("http://10.0.0.5:8088/v1", p.baseUrl)
    }
}
