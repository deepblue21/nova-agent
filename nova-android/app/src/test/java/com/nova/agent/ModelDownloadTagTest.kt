package com.nova.agent

import com.nova.agent.llm.local.ModelDownloadJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * İndirme işinin model kimliğini ETİKETTEN geri okuma sözleşmesi.
 *
 * Neden test edilmeye değer: WorkManager iş uçtaki bir duruma geçtiğinde
 * `progress`i temizler ve iptal edilen işin `outputData`'sı boştur. Gözlemci
 * yalnız o ikisine bakıyordu, dolayısıyla iptal edilen indirme arayüzde
 * sonsuza dek "indiriliyor" olarak asılı kalıyordu. Etiket işin ömrü boyunca
 * değişmeyen tek kaynak; kırılırsa aynı hata sessizce geri döner.
 */
class ModelDownloadTagTest {

    @Test
    fun `etiket yazilip geri okunur`() {
        val tag = ModelDownloadJob.modelTag("qwen3-0.6b-int4")
        assertEquals("qwen3-0.6b-int4", ModelDownloadJob.modelIdFromTags(setOf(tag)))
    }

    @Test
    fun `genel etiket model kimligi sanilmaz`() {
        // TAG ("model-download") ve sinif adi da etiket kumesinde bulunur;
        // yalniz model onekini tasiyan eslesmeli.
        val tags = setOf(
            ModelDownloadJob.TAG,
            // WorkManager, worker sınıfının adını da etiket olarak ekler.
            "com.nova.agent.llm.local.ModelDownloadWorker",
            ModelDownloadJob.modelTag("gemma4-e4b"),
        )
        assertEquals("gemma4-e4b", ModelDownloadJob.modelIdFromTags(tags))
    }

    @Test
    fun `model etiketi yoksa null doner`() {
        assertNull(ModelDownloadJob.modelIdFromTags(setOf(ModelDownloadJob.TAG)))
        assertNull(ModelDownloadJob.modelIdFromTags(emptySet()))
    }

    @Test
    fun `bos kimlik null sayilir`() {
        assertNull(ModelDownloadJob.modelIdFromTags(setOf(ModelDownloadJob.modelTag(""))))
    }

    @Test
    fun `model etiketi benzersiz is adiyla karismaz`() {
        // uniqueName "model-download:<id>", etiket "model-download/model=<id>".
        // Ayri onekler; biri digerini yanlislikla eslestirmemeli.
        val unique = ModelDownloadJob.uniqueName("qwen3-4b-int4")
        assertNotEquals(unique, ModelDownloadJob.modelTag("qwen3-4b-int4"))
        assertNull(ModelDownloadJob.modelIdFromTags(setOf(unique)))
    }

    @Test
    fun `farkli modeller farkli etiket alir`() {
        assertNotEquals(
            ModelDownloadJob.modelTag("qwen3-0.6b-int4"),
            ModelDownloadJob.modelTag("qwen3-0.6b"),
        )
    }
}
