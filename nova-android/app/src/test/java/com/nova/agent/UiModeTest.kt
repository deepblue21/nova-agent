package com.nova.agent

import com.nova.agent.data.SettingsSection
import com.nova.agent.data.UiMode
import com.nova.agent.data.initialUiModeFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Basit/Gelişmiş modun sözleşmesi. Buradaki testlerin amacı arayüzü değil,
 * **görünürlük kurallarını** kilitlemek: mod bir ayarı gizleyebilir ama asla
 * değerini değiştiremez ve gizlilik/veri kontrollerini kapatamaz.
 */
class UiModeTest {

    @Test
    fun `bilinmeyen kimlik basit moda duser`() {
        assertEquals(UiMode.SIMPLE, UiMode.fromId(null))
        assertEquals(UiMode.SIMPLE, UiMode.fromId(""))
        assertEquals(UiMode.SIMPLE, UiMode.fromId("gelismis"))
        assertEquals(UiMode.SIMPLE, UiMode.fromId("ADVANCED"))
    }

    @Test
    fun `kimlikler gidip gelirken korunur`() {
        UiMode.entries.forEach { mode ->
            assertEquals(mode, UiMode.fromId(mode.id))
        }
    }

    @Test
    fun `gelismis mod her bolumu gosterir`() {
        SettingsSection.entries.forEach { section ->
            assertTrue(
                "Gelişmiş mod ${section.name} bölümünü gizlememeli",
                UiMode.ADVANCED.shows(section),
            )
        }
    }

    @Test
    fun `basit mod yalniz advancedOnly bolumleri gizler`() {
        SettingsSection.entries.forEach { section ->
            assertEquals(
                "Basit modda ${section.name} görünürlüğü advancedOnly ile tutarsız",
                !section.advancedOnly,
                UiMode.SIMPLE.shows(section),
            )
        }
    }

    /**
     * Gizlilik ve veri kontrolleri sadeleştirme adına asla kaybolmaz: kullanıcı
     * verisini silememek, "basit" değil, kabul edilemezdir.
     */
    @Test
    fun `veri ve gizlilik bolumleri basit modda da gorunur`() {
        assertTrue(UiMode.SIMPLE.shows(SettingsSection.DATA))
        assertTrue(UiMode.SIMPLE.shows(SettingsSection.CONNECTION))
        assertTrue(UiMode.SIMPLE.shows(SettingsSection.APPEARANCE))
        assertTrue(UiMode.SIMPLE.shows(SettingsSection.ABOUT))
    }

    @Test
    fun `dusuk seviye ayarlar basit modda gizlidir`() {
        assertFalse(UiMode.SIMPLE.shows(SettingsSection.LOCAL_ENGINE))
        assertFalse(UiMode.SIMPLE.shows(SettingsSection.CONNECTION_MANUAL))
        assertFalse(UiMode.SIMPLE.shows(SettingsSection.HF_TOKEN))
        assertFalse(UiMode.SIMPLE.shows(SettingsSection.PERSONA))
        assertFalse(UiMode.SIMPLE.shows(SettingsSection.GATEWAY_MODEL))
        assertFalse(UiMode.SIMPLE.shows(SettingsSection.GATEWAY_TUNING))
    }

    /**
     * Göç kuralı: temiz kurulum Basit'te başlar, mevcut kurulum Gelişmiş'te
     * kalır. Aksi halde güncelleme, kullanıcının kullandığı kontrolleri
     * habersizce ortadan kaldırırdı.
     */
    @Test
    fun `temiz kurulum basit mevcut kurulum gelismis baslar`() {
        assertEquals(UiMode.SIMPLE, initialUiModeFor(freshInstall = true))
        assertEquals(UiMode.ADVANCED, initialUiModeFor(freshInstall = false))
    }
}
