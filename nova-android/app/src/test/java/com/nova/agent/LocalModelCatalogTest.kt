package com.nova.agent

import com.nova.agent.llm.local.LocalModelCatalog
import com.nova.agent.llm.local.ModelRecommender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalModelCatalogTest {

    @Test
    fun `katalog bos degil ve id'ler benzersiz`() {
        assertTrue(LocalModelCatalog.entries.isNotEmpty())
        val ids = LocalModelCatalog.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `tum indirmeler https ve revizyona kilitli`() {
        val pinned = Regex("/resolve/[0-9a-f]{40}/")
        for (spec in LocalModelCatalog.entries) {
            assertTrue(spec.id, spec.downloadUrl.startsWith("https://"))
            assertTrue(spec.id, pinned.containsMatchIn(spec.downloadUrl))
            assertTrue(spec.id, spec.downloadUrl.endsWith(spec.fileName))
        }
    }

    @Test
    fun `sha256 64 hanelik hex ve boyut pozitif`() {
        val hex = Regex("^[0-9a-f]{64}$")
        for (spec in LocalModelCatalog.entries) {
            assertTrue(spec.id, hex.matches(spec.sha256))
            assertTrue(spec.id, spec.sizeBytes > 0)
            assertTrue(spec.id, spec.licenseName.isNotBlank())
        }
    }

    @Test
    fun `dogrulanmis referans degerleri degismedi`() {
        val int4 = LocalModelCatalog.byId("qwen3-0.6b-int4")!!
        assertEquals(497_664_000L, int4.sizeBytes)
        assertEquals(
            "b1baab462f6be49d70eada79d715c2c52cd9ece0cad00bddf6a2c097d23498e9",
            int4.sha256,
        )
        assertEquals(int4, LocalModelCatalog.default)
        assertTrue(!int4.gated)
    }

    @Test
    fun `functiongemma araci kapili ve kayitli`() {
        val fg = LocalModelCatalog.byId("functiongemma-270m")!!
        assertTrue(fg.gated)
        assertEquals(288_964_608L, fg.sizeBytes)
        assertTrue(fg.downloadUrl.contains(LocalModelCatalog.FUNCTIONGEMMA_REVISION))
    }

    @Test
    fun `buyuk modeller kapisiz apache lisansli ve dogrulanmis degerlerle kayitli`() {
        // 2026-07-19'da HuggingFace API'sinden doğrulanan boyut + SHA-256 değerleri.
        val beklenen = mapOf(
            "qwen3-4b-int4" to (2_659_057_664L to
                "f0794bc77efeaaf4f7af815f04c483b19b8f2ae4a102cef1b7b760a25848a18e"),
            "gemma4-e4b" to (3_659_530_240L to
                "0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0"),
            "qwen3-8b-int4" to (4_887_412_736L to
                "cb4e6d0de4bbf6656d177812cf0c6a983967dedd17e7f88e84b901c3a9862a42"),
            "gemma4-12b" to (6_547_589_312L to
                "74fc29a10c20eb5b3ced6c389471a7994a0ffd657255b2a1c764262fb9054aef"),
            "qwen3-14b-int4" to (8_655_863_808L to
                "71de7d58f1b46a3fcba2f7bb700ebcc3c3715877d9a7028d93dd1bcd89bbe946"),
        )
        for ((id, degerler) in beklenen) {
            val spec = LocalModelCatalog.byId(id)!!
            val (boyut, sha) = degerler
            assertEquals(id, boyut, spec.sizeBytes)
            assertEquals(id, sha, spec.sha256)
            // Hepsi kapısız: ilk kurulumda HF token istemez.
            assertTrue(id, !spec.gated)
            assertEquals(id, "Apache-2.0", spec.licenseName)
            // Büyük modeller dürüst bir RAM beklentisi bildirir.
            assertTrue(id, spec.recommendedRamGb >= 8)
        }
    }

    @Test
    fun `buyuk modellerde RAM beklentisi boyutla birlikte artar`() {
        val sirali = LocalModelCatalog.entries
            .filter { it.recommendedRamGb >= 8 }
            .sortedBy { it.sizeBytes }
        assertTrue("en az 5 büyük model", sirali.size >= 5)
        for ((onceki, sonraki) in sirali.zipWithNext()) {
            assertTrue(
                "${onceki.id} -> ${sonraki.id}",
                sonraki.recommendedRamGb >= onceki.recommendedRamGb,
            )
        }
        // 8 GB RAM'li bir telefon 14B'yi riskli görmeli — taklit yok.
        val enBuyuk = sirali.last()
        assertEquals(
            com.nova.agent.llm.local.ModelRecommender.Fit.RISKY,
            com.nova.agent.llm.local.ModelRecommender.fit(enBuyuk, 8.0),
        )
    }

    @Test
    fun `orta sinif modeller dogrulanmis degerlerle kayitli`() {
        // 2026-08-09'da HuggingFace API'sinden doğrulanan boyut + SHA-256.
        val beklenen = mapOf(
            "granite-4.0-350m" to (468_209_584L to
                "c8e9a29493f62b7c44461fb36980987c4c1454c75e95f57ba0539a8edc9dce76"),
            "qwen3-1.7b-int4" to (977_184_032L to
                "2eeffef7b51bc3e1225ea69fe7aa5f417397934b56a5b6c20cc068d6fd2c918b"),
            "qwen3-1.7b" to (2_056_729_520L to
                "66064a4e9269cb693e124c4e3040bcb8a446b10bca42663896329495add3861c"),
            "gemma4-e2b" to (2_588_147_712L to
                "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"),
        )
        for ((id, degerler) in beklenen) {
            val spec = LocalModelCatalog.byId(id)!!
            val (boyut, sha) = degerler
            assertEquals(id, boyut, spec.sizeBytes)
            assertEquals(id, sha, spec.sha256)
            assertTrue("$id kapısız olmalı", !spec.gated)
            assertEquals(id, "Apache-2.0", spec.licenseName)
        }
    }

    @Test
    fun `yeni kusak ve uzmanlasmis Qwen surumleri dogrulanmis degerlerle kayitli`() {
        // 2026-08-10'da HuggingFace API'sinden doğrulanan boyut + SHA-256.
        val beklenen = mapOf(
            "qwen3.5-0.8b-int8" to (978_249_216L to
                "d1bc816351be3862c16b443bcabf6ba81efcba7507d45429e5571bd2861a966c"),
            "qwen3-4b-instruct-2507" to (2_659_057_664L to
                "9e48b165836256f5344d9d044930607b9c47f6ef34e27f82e96881664f3ba2fd"),
            "qwen3-4b-thinking-2507" to (2_274_193_168L to
                "da668b38f27e93f0e40a1d9efd0930ba77fff28a3067547232c93a2001e1c714"),
        )
        for ((id, degerler) in beklenen) {
            val spec = LocalModelCatalog.byId(id)!!
            val (boyut, sha) = degerler
            assertEquals(id, boyut, spec.sizeBytes)
            assertEquals(id, sha, spec.sha256)
            assertTrue("$id kapısız olmalı", !spec.gated)
            assertEquals(id, "Apache-2.0", spec.licenseName)
        }
    }

    /**
     * Düşünen sürüm düşünmeyi KAPATAMAZ, Instruct sürümü hiç düşünmez.
     * İkisinde de "düşünme anahtarı" iddia etmek yanıltıcı olurdu.
     */
    @Test
    fun `uzmanlasmis 4B surumleri dusunme anahtari iddia etmez`() {
        for (id in listOf("qwen3-4b-instruct-2507", "qwen3-4b-thinking-2507")) {
            assertTrue(id, !LocalModelCatalog.byId(id)!!.supportsThinkingToggle)
        }
    }

    /**
     * int8 sürümleri **aynı depo ve aynı sabit revizyonda**, yalnız farklı
     * dosya. Yeni revizyon eklenmediği için `_BASE` sabitleri paylaşılıyor;
     * bu test dosya adının ve özetin karışmadığını doğrular.
     */
    @Test
    fun `int8 surumleri int4 ile ayni revizyonda ve dogru dosyayi gosteriyor`() {
        val ciftler = listOf(
            Triple("qwen3-4b-int4", "qwen3-4b-int8", LocalModelCatalog.QWEN_4B_REVISION),
            Triple("qwen3-8b-int4", "qwen3-8b-int8", LocalModelCatalog.QWEN_8B_REVISION),
        )
        for ((int4Id, int8Id, revizyon) in ciftler) {
            val int4 = LocalModelCatalog.byId(int4Id)!!
            val int8 = LocalModelCatalog.byId(int8Id)!!

            assertTrue("$int4Id revizyonu", int4.downloadUrl.contains(revizyon))
            assertTrue("$int8Id revizyonu", int8.downloadUrl.contains(revizyon))
            // Aynı depo, FARKLI dosya ve FARKLI özet — kopyala-yapıştır hatası olmasın.
            assertTrue("$int8Id dosya adı int4 ile aynı olamaz", int4.fileName != int8.fileName)
            assertTrue("$int8Id özeti int4 ile aynı olamaz", int4.sha256 != int8.sha256)
            // int8 daha büyük ve daha çok RAM ister; aksi bir kayıt yanlıştır.
            assertTrue("$int8Id int4'ten büyük olmalı", int8.sizeBytes > int4.sizeBytes)
            assertTrue("$int8Id daha çok RAM istemeli", int8.recommendedRamGb > int4.recommendedRamGb)
        }
    }

    @Test
    fun `int8 surumleri dogrulanmis degerlerle kayitli`() {
        // 2026-08-10'da HF API'sinden, SABİTLENMİŞ revizyonda doğrulandı.
        val beklenen = mapOf(
            "qwen3-4b-int8" to (5_672_370_176L to
                "3a44aeffa0a3b02b453215f77440069f98efc015c600cb6520b2a01109f4c063"),
            "qwen3-8b-int8" to (8_307_720_192L to
                "2c24953f2ade203216d6588376dbabcecdd0aebac11667d4c24f1819e418e82c"),
        )
        for ((id, degerler) in beklenen) {
            val spec = LocalModelCatalog.byId(id)!!
            val (boyut, sha) = degerler
            assertEquals(id, boyut, spec.sizeBytes)
            assertEquals(id, sha, spec.sha256)
            assertTrue("$id kapısız olmalı", !spec.gated)
        }
    }

    /**
     * Asıl boşluk buydu: 4 GB'lık 0.6B ile 8 GB'lık 4B arasında hiçbir seçenek
     * yoktu ve 5-7 GB'lık telefonlar (en yaygın sınıf) ya zayıf bir modele ya
     * da "Riskli" bir modele düşüyordu.
     */
    @Test
    fun `her RAM sinifinda kapisiz bir secenek var`() {
        val kapisiz = LocalModelCatalog.entries.filter { !it.gated }
        for (ram in listOf(2, 3, 4, 6, 8, 12, 16)) {
            val uygun = kapisiz.filter {
                ModelRecommender.fit(it, ram.toDouble()) == ModelRecommender.Fit.COMFORTABLE
            }
            assertTrue(
                "$ram GB RAM'li cihaz için kapısız ve rahat çalışan model yok",
                uygun.isNotEmpty(),
            )
        }
    }

    /** 6 GB'lık bir telefona 0.6B'den daha iyisi önerilmeli. */
    @Test
    fun `orta sinif cihaza orta sinif model onerilir`() {
        val onerilen = ModelRecommender.recommend(deviceRamGb = 6.0)
        assertTrue("kapısız olmalı", !onerilen.gated)
        assertTrue(
            "6 GB cihaza 0.6B'den büyük bir model önerilmeli, önerilen=${onerilen.id}",
            onerilen.sizeBytes > LocalModelCatalog.byId("qwen3-0.6b")!!.sizeBytes,
        )
    }

    /**
     * Açıklama sözleşmesi: her modelin ne işe yaradığı yazılı olmalı.
     * Kullanıcı 13 satırlık listede neyi neden seçtiğini bilmeli.
     */
    @Test
    fun `her modelin aciklamasi var`() {
        for (spec in LocalModelCatalog.entries) {
            val note = spec.note
            assertTrue("${spec.id}: açıklama yok", !note.isNullOrBlank())
            assertTrue("${spec.id}: açıklama çok kısa", note!!.length >= 40)
        }
    }

    /** Kapılı modeller token gerekliliğini açıklamada söylemeli. */
    @Test
    fun `kapili modeller token gerekliligini yazar`() {
        for (spec in LocalModelCatalog.entries.filter { it.gated }) {
            assertTrue(
                "${spec.id}: kapılı ama açıklamada token/lisans geçmiyor",
                spec.note!!.contains("token", ignoreCase = true) ||
                    spec.note.contains("lisans", ignoreCase = true),
            )
        }
    }

    @Test
    fun `gemma kapili ve dogrulanmis degerlerle kayitli`() {
        val gemma = LocalModelCatalog.byId("gemma3-1b-int4")!!
        assertTrue(gemma.gated)
        assertEquals(584_417_280L, gemma.sizeBytes)
        assertEquals(
            "1325ae366d31950f137c9c357b9fa89448b176d76998180c08ceaca78bba98be",
            gemma.sha256,
        )
        assertTrue(gemma.downloadUrl.contains(LocalModelCatalog.GEMMA_REVISION))
        // Varsayılan model kapısız kalmalı: ilk kurulum deneyimi token istemez.
        assertTrue(!LocalModelCatalog.default.gated)
    }
}
