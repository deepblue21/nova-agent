package com.nova.agent

import com.nova.agent.data.ContentReport
import com.nova.agent.data.ContentReportReason
import com.nova.agent.data.ContentReportStore
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Play B6 — yapay zekâ içerik bildirimi deposu.
 *
 * Politika: *"Apps that generate content using AI must contain in-app user
 * reporting or flagging features that allow users to report or flag offensive
 * content to developers without needing to exit the app."*
 */
class ContentReportStoreTest {

    private fun store(): Pair<ContentReportStore, File> {
        val dir = Files.createTempDirectory("reports").toFile()
        val file = File(dir, "content_reports.json")
        return ContentReportStore(file) to file
    }

    private fun report(id: String, reason: ContentReportReason = ContentReportReason.HARMFUL) =
        ContentReport(
            id = id,
            createdAt = 1_000L,
            reason = reason,
            note = "not",
            excerpt = "alinti",
            route = "telefon/qwen3",
        )

    @Test
    fun `bildirim kaydedilir ve okunur`() {
        val (s, _) = store()

        s.add(report("a"))

        val list = s.list()
        assertEquals(1, list.size)
        assertEquals(ContentReportReason.HARMFUL, list.first().reason)
        assertEquals("telefon/qwen3", list.first().route)
    }

    @Test
    fun `en yeni bildirim basta gelir`() {
        val (s, _) = store()

        s.add(report("eski"))
        s.add(report("yeni"))

        assertEquals(listOf("yeni", "eski"), s.list().map { it.id })
    }

    @Test
    fun `depo sinirsiz buyumez`() {
        val (s, _) = store()

        repeat(ContentReportStore.MAX_REPORTS + 20) { s.add(report("r$it")) }

        assertEquals(ContentReportStore.MAX_REPORTS, s.list().size)
    }

    @Test
    fun `alinti sinirlidir`() {
        val long = "x".repeat(5_000)

        assertEquals(ContentReport.MAX_EXCERPT, ContentReport.excerptOf(long).length)
    }

    @Test
    fun `temizleme hepsini siler`() {
        val (s, _) = store()
        s.add(report("a"))

        s.clear()

        assertTrue(s.list().isEmpty())
    }

    @Test
    fun `bozuk dosya sessizce silinmez karantinaya alinir`() {
        val (s, file) = store()
        file.parentFile?.mkdirs()
        file.writeText("{bu json degil")

        assertTrue(s.list().isEmpty())
        assertFalse("bozuk dosya yerinde kalmamali", file.exists())
        assertTrue(
            "icerik korunmali",
            File(file.parentFile, file.name + ".corrupt").exists(),
        )
    }

    @Test
    fun `disa aktarilan metin ne gonderildigini gosterir`() {
        val (s, _) = store()
        s.add(report("a", ContentReportReason.HATE))

        val text = s.exportText()

        assertTrue(text.contains(ContentReportReason.HATE.label))
        assertTrue(text.contains("alinti"))
        assertTrue(text.contains("telefon/qwen3"))
    }

    @Test
    fun `bilinmeyen sebep kimligi cokertmez`() {
        assertEquals(ContentReportReason.OTHER, ContentReportReason.fromId("uydurma"))
        assertEquals(ContentReportReason.OTHER, ContentReportReason.fromId(null))
    }
}
