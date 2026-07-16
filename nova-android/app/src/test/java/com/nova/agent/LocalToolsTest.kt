package com.nova.agent

import com.nova.agent.llm.local.tools.Calculator
import com.nova.agent.llm.local.tools.NoteStore
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalToolsTest {

    // ---------- hesap makinesi ----------

    private fun ok(expr: String): String {
        val result = Calculator.evaluate(expr)
        assertTrue("beklenen basari: $expr -> $result", result is Calculator.Outcome.Ok)
        return (result as Calculator.Outcome.Ok).formatted
    }

    private fun err(expr: String): String {
        val result = Calculator.evaluate(expr)
        assertTrue("beklenen hata: $expr -> $result", result is Calculator.Outcome.Error)
        return (result as Calculator.Outcome.Error).message
    }

    @Test
    fun `dort islem ve oncelik dogru`() {
        assertEquals("7", ok("1+2*3"))
        assertEquals("9", ok("(1+2)*3"))
        assertEquals("2", ok("10/5"))
        assertEquals("1", ok("10%3"))
        assertEquals("161", ok("23*7"))
    }

    @Test
    fun `us sagdan baglar ve tekli eksi calisir`() {
        assertEquals("512", ok("2^3^2")) // 2^(3^2)
        assertEquals("-8", ok("-2^3"))
        assertEquals("4", ok("(-2)^2"))
        assertEquals("-5", ok("-(2+3)"))
    }

    @Test
    fun `ondalik ve virgul destegi`() {
        assertEquals("8.5", ok("8,5"))
        assertEquals("50", ok("12.5*4"))
    }

    @Test
    fun `hata durumlari kod calistirmadan yakalanir`() {
        err("10/0")
        err("5%0")
        err("(1+2")
        err("abc+1")
        err("")
        err("2+*3")
    }

    // ---------- not deposu ----------

    private fun tempStore(maxNotes: Int = 200): Pair<NoteStore, File> {
        val dir = Files.createTempDirectory("horus_notlar_test").toFile()
        val file = File(dir, "notlar.txt")
        return NoteStore(file, maxNotes) to file
    }

    @Test
    fun `not kaydet ve listele`() {
        val (store, _) = tempStore()
        assertEquals(0, store.count())
        assertEquals(1, store.add("süt al", timestampMs = 1_752_000_000_000))
        assertEquals(2, store.add("faz 2'yi bitir", timestampMs = 1_752_000_060_000))
        val notes = store.list(10)
        assertEquals(2, notes.size)
        assertTrue(notes[0].endsWith("| süt al"))
        assertTrue(notes[1].endsWith("| faz 2'yi bitir"))
    }

    @Test
    fun `not siniri asilinca en eskiler duser`() {
        val (store, _) = tempStore(maxNotes = 3)
        for (i in 1..5) store.add("not $i", timestampMs = 1_752_000_000_000 + i)
        val notes = store.list(10)
        assertEquals(3, notes.size)
        assertTrue(notes.first().endsWith("| not 3"))
        assertTrue(notes.last().endsWith("| not 5"))
    }

    @Test
    fun `satir sonlari tek satira indirgenir ve bos not yazilmaz`() {
        val line = NoteStore.formatLine(1_752_000_000_000, "çok\nsatırlı\r\nnot")
        assertTrue(line.contains("| çok satırlı not"))
        assertEquals("", NoteStore.formatLine(1_752_000_000_000, "   \n  "))
        val (store, _) = tempStore()
        assertEquals(0, store.add("   \n  "))
        assertEquals(0, store.count())
    }

    @Test
    fun `varsayilan ayarlar guvenli taraftadir`() {
        val settings = com.nova.agent.data.AppSettings()
        assertTrue(settings.localTools)
        assertEquals("", settings.hfToken)
        // Hibrit oto-devir varsayılan KAPALI: izin sorulmadan devir yok.
        assertEquals(false, settings.hybridAutoFallback)
    }
}
