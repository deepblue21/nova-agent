package com.nova.agent

import com.nova.agent.data.AppSettings
import com.nova.agent.data.UiMode
import com.nova.agent.llm.local.BackendPreference
import com.nova.agent.llm.local.SamplerPreset
import com.nova.agent.llm.local.SamplerSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `AppSettings`in türetilmiş alanları. Bunlar arayüz ile motor arasındaki
 * sözleşme: bozuk bir kayıtlı değer uygulamayı kilitlememeli, varsayılan
 * ayarlar da mevcut davranışı değiştirmemeli.
 */
class AppSettingsEngineTest {

    /**
     * En önemli test: kutudan çıktığı hâliyle bu sürüm, cihaz-üstü üretimi
     * hiçbir şekilde değiştirmez. effectiveSampler null → motora samplerConfig
     * verilmez → bugünkü davranış birebir korunur.
     */
    @Test
    fun `varsayilan ayarlar uretimi degistirmez`() {
        val defaults = AppSettings()
        assertNull(defaults.effectiveSampler)
        assertEquals(SamplerPreset.MODEL_DEFAULT, defaults.samplerPresetValue)
        assertEquals(BackendPreference.AUTO, defaults.backendPreferenceValue)
    }

    @Test
    fun `varsayilan arayuz modu basittir`() {
        assertEquals(UiMode.SIMPLE, AppSettings().uiModeValue)
    }

    @Test
    fun `bozuk kayitli degerler guvenli varsayilana duser`() {
        val corrupt = AppSettings(
            uiMode = "expert",
            backendPreference = "vulkan",
            samplerPreset = "wild",
        )
        assertEquals(UiMode.SIMPLE, corrupt.uiModeValue)
        assertEquals(BackendPreference.AUTO, corrupt.backendPreferenceValue)
        assertEquals(SamplerPreset.MODEL_DEFAULT, corrupt.samplerPresetValue)
        assertNull(corrupt.effectiveSampler)
    }

    @Test
    fun `hazir ayar secilince motora o degerler gider`() {
        val settings = AppSettings(samplerPreset = SamplerPreset.CREATIVE.id)
        assertEquals(SamplerPreset.CREATIVE.settings, settings.effectiveSampler)
    }

    @Test
    fun `elle ayar secilince kayitli degerler kirpilarak gider`() {
        val settings = AppSettings(
            samplerPreset = SamplerPreset.CUSTOM.id,
            samplerTopK = 500,
            samplerTopP = 0.6,
            samplerTemperature = 5.0,
        )
        val effective = settings.effectiveSampler
        assertNotNull(effective)
        assertEquals(SamplerSettings.TOP_K_MAX, effective!!.topK)
        assertEquals(0.6, effective.topP, 1e-9)
        assertEquals(SamplerSettings.TEMPERATURE_MAX, effective.temperature, 1e-9)
    }

    /**
     * Elle değerler hazır ayar seçiliyken de saklanır: kullanıcı "Dengeli"ye
     * geçip geri döndüğünde kendi ayarını bulmalı.
     */
    @Test
    fun `elle degerler hazir ayar seciliyken korunur`() {
        val settings = AppSettings(
            samplerPreset = SamplerPreset.PRECISE.id,
            samplerTopK = 33,
            samplerTopP = 0.5,
            samplerTemperature = 0.4,
        )
        assertEquals(SamplerPreset.PRECISE.settings, settings.effectiveSampler)
        assertEquals(SamplerSettings(33, 0.5, 0.4), settings.customSampler)
    }
}
