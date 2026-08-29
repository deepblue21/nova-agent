package com.nova.agent

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nova.agent.data.AppSettings
import com.nova.agent.data.UiMode
import com.nova.agent.data.FALLBACK_MODELS
import com.nova.agent.data.ModelOption
import com.nova.agent.feature.settings.SettingsPanel
import com.nova.agent.net.GatewayConnectionStatus
import com.nova.agent.net.GatewayConnectionUiState
import com.nova.agent.ui.theme.NovaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * Gateway/Ollama model-katalog akışının cihaz-üstü (emülatör) UI testleri.
 *
 * Kapsam: canlı liste başlığı, yedek liste başlığı, Ollama modelinin seçilebilmesi
 * ve doğru id'yi bildirmesi, anahtarı olmayan bulut modelinin pasif + nedenli
 * gösterilmesi, "Yenile" ile canlı katalog istenmesi ve bağlantı hatasında
 * nedene özel ipucunun ekranda görünmesi.
 *
 * Ağ gerektirmez: SettingsPanel'e kontrollü veri verilir; deterministiktir.
 */
class GatewayModelCatalogUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val liveCatalog = listOf(
        ModelOption("auto", "Dinamik Yönlendirme", "auto", "Otomatik"),
        ModelOption("ollama/gemma3:4b", "Gemma 3 4B", "ollama/gemma3:4b", "Yerel", desc = "Ollama"),
        ModelOption("ollama/qwen3:14b", "Qwen3 14B", "ollama/qwen3:14b", "Yerel", desc = "Ollama"),
        ModelOption(
            "opus",
            "Claude Opus",
            "anthropic/claude-opus-4-8",
            "Bulut",
            available = false,
            reason = "Anahtar tanımlı değil",
        ),
    )

    private fun render(
        connection: GatewayConnectionUiState = GatewayConnectionUiState(),
        models: List<ModelOption> = FALLBACK_MODELS,
        modelsLive: Boolean = false,
        modelsNote: String = "",
        onModelChange: (String) -> Unit = {},
        onRefreshModels: () -> Unit = {},
    ) {
        composeRule.setContent {
            NovaTheme {
                SettingsPanel(
                    settings = AppSettings(uiMode = UiMode.ADVANCED.id),
                    connection = connection,
                    onTestConnection = { _, _ -> },
                    onSaveConnection = { _, _ -> },
                    onModelChange = onModelChange,
                    onEffortChange = {},
                    models = models,
                    modelsLive = modelsLive,
                    modelsNote = modelsNote,
                    onRefreshModels = onRefreshModels,
                    onReasoningChange = {},
                    onClose = {},
                )
            }
        }
    }

    @Test
    fun unreachableConnectionShowsCauseSpecificHint() {
        render(
            connection = GatewayConnectionUiState(
                status = GatewayConnectionStatus.UNREACHABLE,
                message = "Zaman aşımı: PC'ye ulaşılamadı",
                hint = "Telefon ile PC aynı ağda mı? PC'de start-horus -Lan çalıştır.",
            ),
        )
        composeRule.onNodeWithText("Zaman aşımı: PC'ye ulaşılamadı").assertIsDisplayed()
        composeRule.onNodeWithTag("connection_hint").assertIsDisplayed()
    }

    @Test
    fun fallbackListHeaderShownWhenGatewayUnreachable() {
        render(models = FALLBACK_MODELS, modelsLive = false)
        composeRule.onNodeWithText("Yedek liste · gateway'e ulaşılamadı").assertIsDisplayed()
    }

    @Test
    fun liveHeaderShownAndOllamaModelSelectableReportsCanonicalId() {
        var picked: String? = null
        render(models = liveCatalog, modelsLive = true, onModelChange = { picked = it })

        composeRule.onNodeWithText("Canlı liste · gateway").assertIsDisplayed()
        composeRule.onNodeWithTag("model_dropdown").performClick()
        composeRule.onNodeWithText("Qwen3 14B", substring = true).performClick()

        composeRule.runOnIdle {
            assertEquals("ollama/qwen3:14b", picked)
        }
    }

    @Test
    fun cloudModelWithoutKeyIsDisabledAndShowsReason() {
        var picked: String? = null
        render(models = liveCatalog, modelsLive = true, onModelChange = { picked = it })

        composeRule.onNodeWithTag("model_dropdown").performClick()
        composeRule.onNodeWithText("Anahtar tanımlı değil", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Claude Opus", substring = true).assertIsNotEnabled()

        composeRule.runOnIdle {
            assertNull(picked)
        }
    }

    @Test
    fun refreshRequestsLiveCatalog() {
        var refreshed = 0
        render(models = liveCatalog, modelsLive = true, onRefreshModels = { refreshed++ })

        composeRule.onNodeWithText("Yenile").performClick()

        composeRule.runOnIdle {
            assertEquals(1, refreshed)
        }
    }
}
