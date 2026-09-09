package com.nova.agent

import com.nova.agent.llm.local.LocalModelDiskState
import com.nova.agent.llm.local.LocalModelSpec
import com.nova.agent.llm.local.LocalModelStore
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Model dosyasının diskteki durumu — **çevrimdışı kullanımın kalbi** ve bu
 * turdan önce hiç test edilmemişti.
 *
 * Buradaki tek bir yanlış karar çevrimdışı sohbeti tamamen durduruyor:
 * yönlendirme "kurulu" der, üretim yolu "doğrulanmadı" diye reddeder ve
 * çevrimdışı modda PC'ye devir kapalı olduğu için istem hiçbir yere gitmez.
 *
 * `sha256Hex` burada beklenen değeri üretmek için kullanılıyor; kendisi
 * bağımsız olarak `LocalLlmHelpersTest`te bilinen bir vektörle doğrulanıyor.
 */
class LocalModelStoreTest {

    private val content = "NOVA test model baytlari".toByteArray()
    private val contentSha = LocalModelStore.sha256Hex(content.inputStream())

    private fun spec(sha: String = contentSha, size: Long = content.size.toLong()) =
        LocalModelSpec(
            id = "test-model",
            displayName = "Test",
            family = "Test",
            quantization = "int4",
            fileName = "test.litertlm",
            downloadUrl = "https://example.invalid/test.litertlm",
            sizeBytes = size,
            sha256 = sha,
            licenseName = "Apache-2.0",
            licenseUrl = "https://example.invalid/LICENSE",
            recommendedRamGb = 2,
            supportsThinkingToggle = false,
        )

    private fun store(): LocalModelStore =
        LocalModelStore(Files.createTempDirectory("models-root").toFile())

    private fun install(s: LocalModelStore, sp: LocalModelSpec, bytes: ByteArray = content): File {
        s.modelsDir.mkdirs()
        return s.modelFile(sp).apply { writeBytes(bytes) }
    }

    @Test
    fun `dosya yokken kurulu degil`() {
        val s = store()
        assertEquals(LocalModelDiskState.NotInstalled, s.diskState(spec()))
        assertFalse(s.isInstalled(spec()))
    }

    @Test
    fun `part dosyasi yarim indirme sayilir`() {
        val s = store()
        val sp = spec()
        s.modelsDir.mkdirs()
        s.partFile(sp).writeBytes(content.copyOfRange(0, 5))
        assertEquals(LocalModelDiskState.Partial(5L), s.diskState(sp))
    }

    @Test
    fun `bos part dosyasi kurulmamis sayilir`() {
        val s = store()
        val sp = spec()
        s.modelsDir.mkdirs()
        s.partFile(sp).writeBytes(ByteArray(0))
        assertEquals(LocalModelDiskState.NotInstalled, s.diskState(sp))
    }

    @Test
    fun `beklenenden farkli boyutlu dosya yarim sayilir`() {
        val s = store()
        val sp = spec(size = content.size + 10L)
        install(s, sp)
        assertEquals(LocalModelDiskState.Partial(content.size.toLong()), s.diskState(sp))
    }

    @Test
    fun `isaret yoksa kurulu ama dogrulanmamis`() {
        val s = store()
        val sp = spec()
        install(s, sp)
        // Bu, indirme bitip `renameTo` olduktan sonra isaret yazilmadan
        // surecin olmesi ya da diskin dolmasi hâli. Baytlar SAGLAM.
        assertEquals(LocalModelDiskState.Installed(verified = false), s.diskState(sp))
        assertTrue("yonlendirme burada 'kurulu' der", s.isInstalled(sp))
    }

    @Test
    fun `dogru isaretle dogrulanmis sayilir`() {
        val s = store()
        val sp = spec()
        install(s, sp)
        assertTrue(s.writeMarker(sp))
        assertEquals(LocalModelDiskState.Installed(verified = true), s.diskState(sp))
    }

    @Test
    fun `baska bir modelin ozetini tasiyan isaret dogrulanmis saymaz`() {
        val s = store()
        val sp = spec()
        install(s, sp)
        s.markerFile(sp).writeText("0".repeat(64))
        assertEquals(LocalModelDiskState.Installed(verified = false), s.diskState(sp))
    }

    @Test
    fun `isaret buyuk harfli yazilmis olsa da kabul edilir`() {
        val s = store()
        val sp = spec()
        install(s, sp)
        s.markerFile(sp).writeText(contentSha.uppercase())
        assertEquals(LocalModelDiskState.Installed(verified = true), s.diskState(sp))
    }

    @Test
    fun `verify saglam dosyada isareti yazar`() {
        val s = store()
        val sp = spec()
        install(s, sp)
        assertTrue(s.verify(sp))
        assertEquals(LocalModelDiskState.Installed(verified = true), s.diskState(sp))
    }

    @Test
    fun `verify bozuk dosyayi diskten siler`() {
        val s = store()
        val sp = spec(sha = "f".repeat(64))
        install(s, sp)
        assertFalse(s.verify(sp))
        // Y2 — bozuk .litertlm native motora gitmemeli; dosya BIRAKILMAZ.
        assertFalse("bozuk dosya diskte kalmamali", s.modelFile(sp).exists())
        assertEquals(LocalModelDiskState.NotInstalled, s.diskState(sp))
    }

    @Test
    fun `verify olmayan dosyada basarisiz olur`() {
        val s = store()
        assertFalse(s.verify(spec()))
    }

    @Test
    fun `delete model part ve isaret dosyalarinin ucunu de kaldirir`() {
        val s = store()
        val sp = spec()
        install(s, sp)
        s.partFile(sp).writeBytes(content)
        s.writeMarker(sp)
        s.delete(sp)
        assertFalse(s.modelFile(sp).exists())
        assertFalse(s.partFile(sp).exists())
        assertFalse(s.markerFile(sp).exists())
        assertEquals(LocalModelDiskState.NotInstalled, s.diskState(sp))
    }
}
