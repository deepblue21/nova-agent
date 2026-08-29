package com.nova.agent

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.nova.agent.data.AppSettings
import com.nova.agent.data.UiMode
import com.nova.agent.feature.settings.SettingsPanel
import com.nova.agent.llm.local.ActiveBackend
import com.nova.agent.llm.local.BackendPreference
import com.nova.agent.net.GatewayConnectionUiState
import com.nova.agent.ui.theme.NovaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Basit/Gelişmiş modun ekrandaki karşılığı.
 *
 * Sözleşme: Basit mod düşük seviye kontrolleri **çizmez**, ama gizlilik ve
 * veri kontrollerini asla kaldırmaz ve hiçbir ayarın değerini değiştirmez.
 */
class SettingsUiModeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun panel(
        settings: AppSettings,
        activeBackend: ActiveBackend = ActiveBackend.NONE,
        onUiModeChange: (UiMode) -> Unit = {},
        onBackendChange: (BackendPreference) -> Unit = {},
    ) {
        composeRule.setContent {
            NovaTheme {
                SettingsPanel(
                    settings = settings,
                    connection = GatewayConnectionUiState(),
                    onTestConnection = { _, _ -> },
                    onSaveConnection = { _, _ -> },
                    onModelChange = {},
                    onEffortChange = {},
                    onReasoningChange = {},
                    activeBackend = activeBackend,
                    onUiModeChange = onUiModeChange,
                    onBackendChange = onBackendChange,
                    onClose = {},
                )
            }
        }
    }

    @Test
    fun basitModDusukSeviyeKontrolleriGizler() {
        panel(AppSettings(uiMode = UiMode.SIMPLE.id, baseUrl = "http://192.168.1.5:8088/v1"))

        composeRule.onNodeWithTag("gateway_url").assertDoesNotExist()
        composeRule.onNodeWithTag("gateway_token").assertDoesNotExist()
        composeRule.onNodeWithTag("hf_token").assertDoesNotExist()
        composeRule.onNodeWithTag("persona").assertDoesNotExist()
        composeRule.onNodeWithTag("model_dropdown").assertDoesNotExist()
        composeRule.onNodeWithTag("backend_auto").assertDoesNotExist()
    }

    /**
     * Sadeleştirme, kullanıcıyı kendi verisinden etmek değildir: veri silme
     * ve mod değiştirme Basit modda da erişilebilir kalmalı.
     */
    @Test
    fun basitModVeriKontrolleriniKorur() {
        panel(AppSettings(uiMode = UiMode.SIMPLE.id))

        composeRule.onNodeWithTag("wipe_data").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("ui_mode_advanced").performScrollTo().assertIsDisplayed()
    }

    /** Basit modda bağlantı gizlense de kayıtlı olduğu kullanıcıya söylenir. */
    @Test
    fun basitModKayitliBaglantiyiAciklar() {
        panel(AppSettings(uiMode = UiMode.SIMPLE.id, baseUrl = "http://192.168.1.5:8088/v1"))

        composeRule.onNodeWithTag("connection_simple_note").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun gelismisModTumKontrolleriGosterir() {
        panel(AppSettings(uiMode = UiMode.ADVANCED.id))

        composeRule.onNodeWithTag("gateway_url").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("hf_token").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("persona").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("backend_auto").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("sampler_model_default").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun moddegistirmeGeriCagrisiniTetikler() {
        var chosen: UiMode? = null
        panel(AppSettings(uiMode = UiMode.SIMPLE.id), onUiModeChange = { chosen = it })

        composeRule.onNodeWithTag("ui_mode_advanced").performScrollTo().performClick()

        assertEquals(UiMode.ADVANCED, chosen)
    }

    /**
     * "Otomatik" seçiliyken kullanıcının gerçekten hangi hızlandırmanın
     * çalıştığını görmesi gerekir; arayüz tercihi değil, ölçülen sonucu yazar.
     */
    @Test
    fun calisanBackendEkrandaYazar() {
        panel(
            AppSettings(uiMode = UiMode.ADVANCED.id, backendPreference = BackendPreference.AUTO.id),
            activeBackend = ActiveBackend.GPU,
        )

        composeRule.onNodeWithTag("active_backend").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun elleOrneklemeSeciliDegilkenKaydiricilarGizli() {
        panel(AppSettings(uiMode = UiMode.ADVANCED.id))

        composeRule.onNodeWithTag("sampler_top_k").assertDoesNotExist()
        composeRule.onNodeWithTag("sampler_temperature").assertDoesNotExist()
    }
}
