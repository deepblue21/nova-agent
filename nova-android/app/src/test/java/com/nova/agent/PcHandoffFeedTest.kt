package com.nova.agent

import com.nova.agent.data.PcAgentRun
import com.nova.agent.data.PcHandoffFeed
import com.nova.agent.net.GatewayConnectionClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Faz 11 — telefondan PC'ye devredilen işin GÖRÜNÜRLÜĞÜ.
 *
 * Bu testler yazılmadan önceki durum: `handoffToPcAgent` istemi PC'ye
 * gönderiyordu, `NovaViewModel` yorumu "koşu ajan geçmişine otomatik
 * kaydolur" diyordu, ama gateway'in ajan dalı `provider === "ollama"` ile
 * sınırlı olduğu için OpenClaw yolu o dala hiç girmiyor ve `agent_runs`'a
 * hiçbir şey yazılmıyordu. Yani kod kendi hakkında yanlış konuşuyordu ve
 * devredilen işin hiçbir izi yoktu.
 */
class PcHandoffFeedTest {

    private fun run(
        id: String = "11111111-1111-4111-8111-111111111111",
        mode: String = "openclaw",
        prompt: String = "faturayı özetle",
        createdAt: Long = 1_000_000L,
    ) = PcAgentRun(
        id = id, mode = mode, model = "openclaw/default", prompt = prompt,
        tools = "", result = "özet", createdAt = createdAt,
    )

    @Test
    fun `cevrimdisi politikada kart gosterilmez`() {
        // PC yolu kapalıyken koşum geçmişi göstermek, olmayan bir yeteneği
        // varmış gibi sunardı.
        assertFalse(PcHandoffFeed.shouldShow(gatewayRelevant = false))
        assertTrue(PcHandoffFeed.shouldShow(gatewayRelevant = true))
    }

    @Test
    fun `ulasilamadi ile hic kosum yok ayri cumlelerdir`() {
        assertEquals("Geçmiş okunuyor…", PcHandoffFeed.emptyMessage(null, loading = true))
        assertTrue(PcHandoffFeed.emptyMessage(null, loading = false)!!.contains("okunamadı"))
        assertTrue(PcHandoffFeed.emptyMessage(emptyList(), loading = false)!!.contains("Henüz"))
        assertNull(PcHandoffFeed.emptyMessage(listOf(run()), loading = false))
    }

    @Test
    fun `siralama sunucunun sozune birakilmaz`() {
        val runs = listOf(run(id = "eski", createdAt = 10L), run(id = "yeni", createdAt = 99L))
        assertEquals(listOf("yeni", "eski"), PcHandoffFeed.visibleRuns(runs).map { it.id })
    }

    @Test
    fun `kart en fazla bes satir gosterir`() {
        val runs = (1..12).map { run(id = "id$it", createdAt = it.toLong()) }
        val visible = PcHandoffFeed.visibleRuns(runs)
        assertEquals(PcHandoffFeed.MAX_ROWS, visible.size)
        assertEquals("id12", visible.first().id)
    }

    @Test
    fun `telefondan devredilen kosum rozetiyle ayirt edilir`() {
        assertTrue(run(mode = "openclaw").fromPhone)
        assertFalse(run(mode = "agent").fromPhone)
        assertEquals("telefondan", PcHandoffFeed.badge(run(mode = "openclaw")))
        assertEquals("takım", PcHandoffFeed.badge(run(mode = "team")))
        assertEquals("araçlı", PcHandoffFeed.badge(run(mode = "agent")))
        // Bilinmeyen tür uydurulmaz, olduğu gibi yazılır.
        assertEquals("yeni-mod", PcHandoffFeed.badge(run(mode = "yeni-mod")))
    }

    @Test
    fun `bos istem uydurulmaz`() {
        assertEquals("(boş istem)", PcHandoffFeed.title(run(prompt = "   \n  ")))
        assertEquals("ilk satır", PcHandoffFeed.title(run(prompt = "  ilk satır\nikinci")))
        val long = "a".repeat(200)
        assertEquals(72, PcHandoffFeed.title(run(prompt = long)).length)
        assertTrue(PcHandoffFeed.title(run(prompt = long)).endsWith("…"))
    }

    @Test
    fun `tarihsiz kosum icin zaman uydurulmaz`() {
        assertEquals("", PcHandoffFeed.relativeTime(0L, now = 5_000_000L))
        assertEquals("", PcHandoffFeed.relativeTime(-3L, now = 5_000_000L))
    }

    @Test
    fun `goreli zaman esiklerde dogru`() {
        val now = 1_000_000_000L
        assertEquals("az önce", PcHandoffFeed.relativeTime(now - 59_000L, now))
        assertEquals("1 dk", PcHandoffFeed.relativeTime(now - 60_000L, now))
        assertEquals("59 dk", PcHandoffFeed.relativeTime(now - 59 * 60_000L, now))
        assertEquals("1 sa", PcHandoffFeed.relativeTime(now - 60 * 60_000L, now))
        assertEquals("23 sa", PcHandoffFeed.relativeTime(now - 23 * 3_600_000L, now))
        assertEquals("1 gün", PcHandoffFeed.relativeTime(now - 24 * 3_600_000L, now))
        assertEquals("6 gün", PcHandoffFeed.relativeTime(now - 6 * 86_400_000L, now))
        assertEquals("1 hafta", PcHandoffFeed.relativeTime(now - 7 * 86_400_000L, now))
        // Saat farkı yüzünden ileri tarihli damga "-3 dk" değil "az önce".
        assertEquals("az önce", PcHandoffFeed.relativeTime(now + 120_000L, now))
    }

    // ---------- GET /v1/agent/runs ayrıştırma ----------

    @Test
    fun `kosum listesi ayristirilir`() {
        val body = """
            {"data":[
              {"id":"a1","mode":"openclaw","model":"openclaw/default","prompt":"özetle",
               "tools":"","result":"özet","created_at":1730000000000},
              {"id":"a2","mode":"team","model":"auto","prompt":"araştır",
               "tools":"web_search×2","result":"rapor","created_at":1730000001000}
            ]}
        """.trimIndent()
        val runs = GatewayConnectionClient.parseAgentRuns(body)!!
        assertEquals(2, runs.size)
        assertEquals("a1", runs[0].id)
        assertTrue(runs[0].fromPhone)
        assertEquals("web_search×2", runs[1].tools)
        assertEquals(1730000001000L, runs[1].createdAt)
    }

    @Test
    fun `bozuk govde null doner bos liste degil`() {
        // null = "ulaşılamadı", boş liste = "gerçekten koşum yok".
        assertNull(GatewayConnectionClient.parseAgentRuns(""))
        assertNull(GatewayConnectionClient.parseAgentRuns("bu json değil"))
        assertNull(GatewayConnectionClient.parseAgentRuns("""{"error":"nope"}"""))
        assertEquals(emptyList<PcAgentRun>(), GatewayConnectionClient.parseAgentRuns("""{"data":[]}"""))
    }

    @Test
    fun `kimliksiz satir atlanir`() {
        // Silme çağrısı kimliğe dayanır; kimliksiz satır silinemez bir hayalet olurdu.
        val body = """{"data":[{"mode":"openclaw","prompt":"x"},{"id":"ok","mode":"openclaw","prompt":"y"}]}"""
        val runs = GatewayConnectionClient.parseAgentRuns(body)!!
        assertEquals(listOf("ok"), runs.map { it.id })
    }

    @Test
    fun `eksik alanlar cokertmez`() {
        val runs = GatewayConnectionClient.parseAgentRuns("""{"data":[{"id":"a1"}]}""")!!
        assertEquals(1, runs.size)
        assertEquals("", runs[0].prompt)
        assertEquals(0L, runs[0].createdAt)
        assertEquals("", PcHandoffFeed.relativeTime(runs[0].createdAt, now = 1L))
    }

    @Test
    fun `kosum adresi v1 altinda kurulur`() {
        assertEquals(
            "http://192.168.1.20:18088/v1/agent/runs",
            GatewayConnectionClient.agentRunsUrl("http://192.168.1.20:18088/v1").toString(),
        )
        // Ağ politikası aynı boğazdan geçer: şifresiz http genel adrese gitmez.
        assertNull(GatewayConnectionClient.agentRunsUrl("http://93.184.216.34:18088/v1"))
        assertNull(GatewayConnectionClient.agentRunsUrl("ftp://x/v1"))
    }
}
