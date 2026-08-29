package com.nova.agent

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.nova.agent.data.AppSettings
import com.nova.agent.data.UiMode
import com.nova.agent.feature.pairing.PairingPhase
import com.nova.agent.feature.pairing.PairingUiState
import com.nova.agent.feature.settings.SettingsPanel
import com.nova.agent.net.DiscoveredGateway
import com.nova.agent.net.GatewayConnectionUiState
import com.nova.agent.ui.theme.NovaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Eşleme bölümünün ekrandaki sözleşmesi (Faz A).
 *
 * En önemlisi: eşleme **Basit modda da görünür**. VPN/IP bilmeyen kullanıcının
 * bağlanma yolu bu; Gelişmiş'e saklanırsa tüm işin anlamı kalmaz.
 */
class PairingSectionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val pc = DiscoveredGateway(
        displayName = "Masaüstü",
        host = "192.168.1.5",
        port = 8088,
        baseUrl = "http://192.168.1.5:8088/v1",
    )

    private fun panel(
        mode: UiMode = UiMode.SIMPLE,
        pairing: PairingUiState = PairingUiState(),
        onStartDiscovery: () -> Unit = {},
        onSelectGateway: (DiscoveredGateway) -> Unit = {},
        onSubmitPairing: () -> Unit = {},
    ) {
        composeRule.setContent {
            NovaTheme {
                SettingsPanel(
                    settings = AppSettings(uiMode = mode.id),
                    connection = GatewayConnectionUiState(),
                    onTestConnection = { _, _ -> },
                    onSaveConnection = { _, _ -> },
                    onModelChange = {},
                    onEffortChange = {},
                    onReasoningChange = {},
                    pairing = pairing,
                    onStartDiscovery = onStartDiscovery,
                    onSelectGateway = onSelectGateway,
                    onSubmitPairing = onSubmitPairing,
                    onClose = {},
                )
            }
        }
    }

    /** Faz A'nın bütün gerekçesi: yeni kullanıcı Basit modda eşleyebilmeli. */
    @Test
    fun eslemeBasitModdaGorunur() {
        panel(mode = UiMode.SIMPLE, pairing = PairingUiState(found = listOf(pc)))

        composeRule.onNodeWithTag("pairing_code").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("pairing_gateway_${pc.host}").performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun panelAcilincaTaramaBaslar() {
        var started = 0
        panel(onStartDiscovery = { started++ })

        composeRule.waitForIdle()
        assertTrue("Panel açılışında keşif başlamalı", started >= 1)
    }

    /**
     * Hiçbir şey bulunamadığında sonsuza kadar "aranıyor" yazmaz; neden ve
     * çıkış yolu görünür.
     */
    @Test
    fun bosListedeNedenVeCikisYoluYazar() {
        panel(pairing = PairingUiState(scanning = false, found = emptyList()))

        composeRule.onNodeWithTag("pairing_empty").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun eksikKodlaEslemeDugmesiPasif() {
        panel(pairing = PairingUiState(found = listOf(pc), selectedBaseUrl = pc.baseUrl, code = "ABC"))

        composeRule.onNodeWithTag("pairing_submit").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun tamKodVeSecimVarkenEslemeDugmesiAktif() {
        panel(
            pairing = PairingUiState(
                found = listOf(pc),
                selectedBaseUrl = pc.baseUrl,
                code = "ABCD2345",
            ),
        )

        composeRule.onNodeWithTag("pairing_submit").performScrollTo().assertIsEnabled()
    }

    /** Tek kullanımlık kod, süren takas sırasında ikinci kez gönderilemez. */
    @Test
    fun takasSurerkenDugmePasif() {
        panel(
            pairing = PairingUiState(
                found = listOf(pc),
                selectedBaseUrl = pc.baseUrl,
                code = "ABCD2345",
                phase = PairingPhase.Claiming,
            ),
        )

        composeRule.onNodeWithTag("pairing_submit").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun pcSecimiGeriCagriyiTetikler() {
        var chosen: DiscoveredGateway? = null
        panel(pairing = PairingUiState(found = listOf(pc)), onSelectGateway = { chosen = it })

        composeRule.onNodeWithTag("pairing_gateway_${pc.host}").performScrollTo().performClick()

        assertEquals(pc, chosen)
    }

    /**
     * Keşif hiçbir şey bulamasa bile (AP izolasyonu, farklı ağ) yapıştırılan
     * eşleme bağlantısı adresi de taşır — Basit modda çıkmaz kalmaz.
     */
    @Test
    fun yapistirilanBaglantiListeBosOlsaDaEslemeyiAcar() {
        panel(
            pairing = PairingUiState(
                found = emptyList(),
                selectedBaseUrl = null,
                code = "horus://pair?v=1&code=ABCD2345&host=192.168.1.5&port=8088&name=SALIH-PC",
            ),
        )

        composeRule.onNodeWithTag("pairing_submit").performScrollTo().assertIsEnabled()
        // Gönderimden önce nereye bağlanılacağı yazmalı.
        composeRule.onNodeWithTag("pairing_target").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun bozukBaglantiEslemeyiAcmaz() {
        panel(pairing = PairingUiState(code = "horus://pair?v=1&code=ABCD2345"))

        composeRule.onNodeWithTag("pairing_submit").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun hataMesajiIpucuylaGorunur() {
        panel(
            pairing = PairingUiState(
                found = listOf(pc),
                selectedBaseUrl = pc.baseUrl,
                phase = PairingPhase.Failed("Kodun süresi doldu", "PC'de yeni bir kod üret."),
            ),
        )

        composeRule.onNodeWithTag("pairing_error").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun basariMesajiGorunur() {
        panel(
            pairing = PairingUiState(
                found = listOf(pc),
                selectedBaseUrl = pc.baseUrl,
                phase = PairingPhase.Paired("telefon", pc.baseUrl),
            ),
        )

        composeRule.onNodeWithTag("pairing_success").performScrollTo().assertIsDisplayed()
    }
}
