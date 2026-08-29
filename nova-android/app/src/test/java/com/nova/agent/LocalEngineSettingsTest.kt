package com.nova.agent

import com.nova.agent.llm.local.ActiveBackend
import com.nova.agent.llm.local.BackendPlan
import com.nova.agent.llm.local.BackendPreference
import com.nova.agent.llm.local.SamplerPreset
import com.nova.agent.llm.local.SamplerSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cihaz motoru ayarlarının saf çekirdeği.
 *
 * En kritik iki değişmez burada kilitleniyor:
 * 1) Kullanıcı bir backend'i AÇIKÇA seçtiyse sessizce başkasına düşülmez.
 * 2) Varsayılan örnekleme "model varsayılanı"dır ve motora hiçbir değer
 *    göndermez — yani bu sürüm kimsenin yanıtlarını kendiliğinden değiştirmez.
 */
class LocalEngineSettingsTest {

    // ---------- backend ----------

    @Test
    fun `bilinmeyen backend kimligi otomatige duser`() {
        assertEquals(BackendPreference.AUTO, BackendPreference.fromId(null))
        assertEquals(BackendPreference.AUTO, BackendPreference.fromId("cuda"))
        assertEquals(BackendPreference.AUTO, BackendPreference.fromId("GPU"))
    }

    @Test
    fun `backend kimlikleri gidip gelirken korunur`() {
        BackendPreference.entries.forEach { preference ->
            assertEquals(preference, BackendPreference.fromId(preference.id))
        }
    }

    @Test
    fun `otomatik once GPU sonra CPU dener`() {
        assertEquals(
            listOf(ActiveBackend.GPU, ActiveBackend.CPU),
            BackendPlan.attempts(BackendPreference.AUTO),
        )
        assertTrue(BackendPlan.allowsFallback(BackendPreference.AUTO))
    }

    /** Açık seçim = tek deneme. Sessiz devir yasağının motor tarafındaki karşılığı. */
    @Test
    fun `acik secimler tek backend dener ve geri dusmez`() {
        listOf(BackendPreference.CPU, BackendPreference.GPU, BackendPreference.NPU)
            .forEach { preference ->
                assertEquals(
                    "${preference.id} tek backend denemeli",
                    1,
                    BackendPlan.attempts(preference).size,
                )
                assertFalse(
                    "${preference.id} sessizce başka backend'e düşmemeli",
                    BackendPlan.allowsFallback(preference),
                )
            }
    }

    @Test
    fun `hicbir plan NONE icermez`() {
        BackendPreference.entries.forEach { preference ->
            assertFalse(
                "${preference.id} planı NONE içeremez",
                BackendPlan.attempts(preference).contains(ActiveBackend.NONE),
            )
        }
    }

    @Test
    fun `hata mesaji ham nedeni korur ve ne yapilacagini soyler`() {
        val cause = "libOpenCL.so bulunamadı"
        BackendPreference.entries.forEach { preference ->
            val message = BackendPlan.failureMessage(preference, cause)
            assertTrue("${preference.id}: ham neden kaybolmamalı", message.contains(cause))
            assertTrue("${preference.id}: mesaj boş olmamalı", message.length > cause.length)
        }
        // Kullanıcı çıkmazda bırakılmaz: açık seçimler çıkış yolunu söyler.
        assertTrue(
            BackendPlan.failureMessage(BackendPreference.GPU, cause).contains("Otomatik"),
        )
        assertTrue(
            BackendPlan.failureMessage(BackendPreference.NPU, cause).contains("Otomatik"),
        )
    }

    // ---------- örnekleme ----------

    @Test
    fun `varsayilan hazir ayar motora hicbir deger gondermez`() {
        assertEquals(SamplerPreset.MODEL_DEFAULT, SamplerPreset.fromId(null))
        assertNull(SamplerPreset.MODEL_DEFAULT.settings)
        assertNull(
            SamplerPreset.resolve(SamplerPreset.MODEL_DEFAULT, SamplerPreset.CUSTOM_SEED),
        )
    }

    @Test
    fun `bilinmeyen hazir ayar model varsayilanina duser`() {
        assertEquals(SamplerPreset.MODEL_DEFAULT, SamplerPreset.fromId("balanced-v2"))
    }

    @Test
    fun `hazir ayarlar elle degeri yok sayar`() {
        val absurd = SamplerSettings(topK = 999, topP = 9.0, temperature = 9.0)
        val resolved = SamplerPreset.resolve(SamplerPreset.PRECISE, absurd)
        assertEquals(SamplerPreset.PRECISE.settings, resolved)
    }

    @Test
    fun `elle secimde kullanici degeri kirpilarak kullanilir`() {
        val absurd = SamplerSettings(topK = 9_999, topP = 42.0, temperature = -3.0)
        val resolved = SamplerPreset.resolve(SamplerPreset.CUSTOM, absurd)
        assertNotNull(resolved)
        assertEquals(SamplerSettings.TOP_K_MAX, resolved!!.topK)
        assertEquals(SamplerSettings.TOP_P_MAX, resolved.topP, 1e-9)
        assertEquals(SamplerSettings.TEMPERATURE_MIN, resolved.temperature, 1e-9)
    }

    @Test
    fun `alt sinirlar da kirpilir`() {
        val tooSmall = SamplerSettings(topK = 0, topP = 0.0, temperature = -1.0)
        val clamped = tooSmall.clamped()
        assertEquals(SamplerSettings.TOP_K_MIN, clamped.topK)
        assertEquals(SamplerSettings.TOP_P_MIN, clamped.topP, 1e-9)
        assertEquals(SamplerSettings.TEMPERATURE_MIN, clamped.temperature, 1e-9)
    }

    @Test
    fun `gecerli deger kirpmadan gecer`() {
        val valid = SamplerSettings(topK = 40, topP = 0.95, temperature = 0.7)
        assertEquals(valid, valid.clamped())
    }

    /** Katalogdaki her hazır ayar kendi sınırları içinde olmalı. */
    @Test
    fun `hazir ayarlarin degerleri gecerli araliktadir`() {
        SamplerPreset.entries.mapNotNull { it.settings }.forEach { settings ->
            assertEquals(
                "Hazır ayar değeri aralık dışında: $settings",
                settings,
                settings.clamped(),
            )
        }
    }

    @Test
    fun `elle ayar tohumu gecerli bir baslangictir`() {
        val seed = SamplerPreset.CUSTOM_SEED
        assertEquals(seed, seed.clamped())
    }

    /**
     * CUSTOM_SEED, ilklendirme sırası riskine girmemek için Dengeli'nin
     * değerlerini kopyalar. Kopya sessizce ayrışırsa bu test yakalar.
     */
    @Test
    fun `elle ayar tohumu dengeli ile ayni kalir`() {
        assertEquals(SamplerPreset.BALANCED.settings, SamplerPreset.CUSTOM_SEED)
    }

    @Test
    fun `kesin ayar greedy cozumlemedir`() {
        // topK=1 gerçek greedy'dir; "Kesin" etiketinin arkasındaki iddia budur.
        assertEquals(1, SamplerPreset.PRECISE.settings!!.topK)
    }
}
