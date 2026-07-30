package com.nova.agent

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.nova.agent.data.AppSettings
import com.nova.agent.data.ModelOption
import com.nova.agent.feature.settings.SettingsPanel
import com.nova.agent.net.GatewayConnectionUiState
import com.nova.agent.ui.theme.NovaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Ayarlar: kişilik, Hugging Face token'ı, veri temizleme ve model listesi.
 *
 * İki gizlilik sözleşmesi test edilir: HF token'ı ekranda asla düz metin
 * görünmez ve veri silme tek dokunuşla değil, açık onayla yapılır.
 */
class SettingsDataControlsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun panel(
        settings: AppSettings = AppSettings(),
        models: List<ModelOption> = com.nova.agent.data.FALLBACK_MODELS,
        modelsLive: Boolean = false,
        modelsNote: String = "",
        onModelChange: (String) -> Unit = {},
        onRefreshModels: () -> Unit = {},
        onThemeChange: (String) -> Unit = {},
        onHfTokenChange: (String) -> Unit = {},
        onPersonaChange: (String) -> Unit = {},
        onWipeData: (Boolean) -> Unit = {},
        onClose: () -> Unit = {},
    ) {
        composeRule.setContent {
            NovaTheme {
                SettingsPanel(
                    settings = settings,
                    connection = GatewayConnectionUiState(),
                    onTestConnection = { _, _ -> },
                    onSaveConnection = { _, _ -> },
                    onModelChange = onModelChange,
                    onEffortChange = {},
                    models = models,
                    modelsLive = modelsLive,
                    modelsNote = modelsNote,
                    onRefreshModels = onRefreshModels,
                    onReasoningChange = {},
                    onThemeChange = onThemeChange,
                    onHfTokenChange = onHfTokenChange,
                    onPersonaChange = onPersonaChange,
                    onWipeData = onWipeData,
                    onClose = onClose,
                )
            }
        }
    }

    @Test
    fun personaIsEditedAndSavedExplicitly() {
        var saved: String? = null
        panel(onPersonaChange = { saved = it })

        composeRule.onNodeWithTag("persona")
            .performScrollTo()
            .performTextReplacement("Kısa ve net yanıt ver")

        // Kaydet'e basmadan hiçbir şey kalıcılaşmaz.
        composeRule.runOnIdle { assertNull(saved) }

        composeRule.onNodeWithText("Kişiliği kaydet").performScrollTo().performClick()

        composeRule.runOnIdle {
            assertEquals("Kısa ve net yanıt ver", saved)
        }
    }

    @Test
    fun personaSurvivesAsPlainTextBecauseItIsNotASecret() {
        panel(settings = AppSettings(persona = "Türkçe konuş"))

        composeRule.onNodeWithTag("persona").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("Türkçe konuş", useUnmergedTree = true)
            .fetchSemanticsNodes().let {
                assertTrue("Kişilik metni görünmüyor", it.isNotEmpty())
            }
    }

    @Test
    fun huggingFaceTokenIsMaskedAndCarriesPasswordSemantics() {
        var saved: String? = null
        panel(onHfTokenChange = { saved = it })

        composeRule.onNodeWithTag("hf_token")
            .performScrollTo()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
            .performTextReplacement("hf_verysecrettoken")

        composeRule.onAllNodesWithText("hf_verysecrettoken", useUnmergedTree = true)
            .assertCountEquals(0)

        composeRule.onNodeWithText("HF token'ı kaydet").performScrollTo().performClick()

        composeRule.runOnIdle {
            assertEquals("hf_verysecrettoken", saved)
        }
    }

    @Test
    fun huggingFaceSectionStatesWhereTheTokenTravels() {
        panel()

        composeRule.onNodeWithText(
            "Yalnız lisans onaylı model indirmede kullanılır; token cihazda kalır ve " +
                "yalnız huggingface.co'ya gönderilir.",
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun wipeDataRequiresConfirmationDialog() {
        val wipes = mutableListOf<Boolean>()
        panel(onWipeData = { wipes += it })

        composeRule.onNodeWithTag("wipe_data").performScrollTo().performClick()

        // Tek dokunuş silmez; onay diyaloğu açılır.
        composeRule.runOnIdle { assertEquals(emptyList<Boolean>(), wipes) }
        composeRule.onNodeWithText(
            "Sohbet geçmişi, notlar ve performans kayıtları silinsin mi? " +
                "Ayarların ve Gateway bağlantın korunur.",
        ).assertIsDisplayed()
    }

    @Test
    fun wipeDialogKeepsModelsWhenTheNarrowOptionIsChosen() {
        val wipes = mutableListOf<Boolean>()
        panel(onWipeData = { wipes += it })

        composeRule.onNodeWithTag("wipe_data").performScrollTo().performClick()
        composeRule.onNodeWithText("Veriyi sil (modeller kalsın)").performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(false), wipes)
        }
    }

    @Test
    fun wipeDialogAlsoOffersRemovingDownloadedModels() {
        val wipes = mutableListOf<Boolean>()
        panel(onWipeData = { wipes += it })

        composeRule.onNodeWithTag("wipe_data").performScrollTo().performClick()
        composeRule.onNodeWithText("Veriyi + indirilen modelleri sil").performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(true), wipes)
        }
    }

    @Test
    fun wipeDialogCanBeDismissedWithoutDeletingAnything() {
        val wipes = mutableListOf<Boolean>()
        panel(onWipeData = { wipes += it })

        composeRule.onNodeWithTag("wipe_data").performScrollTo().performClick()
        composeRule.onNodeWithText("Vazgeç").performClick()

        composeRule.runOnIdle {
            assertEquals(emptyList<Boolean>(), wipes)
        }
        composeRule.onAllNodesWithText("Veriyi sil (modeller kalsın)").assertCountEquals(0)
    }

    @Test
    fun modelDropdownShowsSelectionAndRoutesChange() {
        var chosen: String? = null
        panel(settings = AppSettings(modelId = "auto"), onModelChange = { chosen = it })

        composeRule.onNodeWithTag("model_dropdown")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithText("Claude Opus 4.8").performClick()

        composeRule.runOnIdle {
            assertEquals("opus", chosen)
        }
    }

    @Test
    fun unavailableModelIsListedButNotSelectable() {
        var chosen: String? = null
        panel(
            models = listOf(
                ModelOption("auto", "Dinamik Yönlendirme", "auto", "Otomatik"),
                ModelOption(
                    id = "gpt",
                    name = "GPT-5.6 Sol",
                    model = "openai/gpt-5.6",
                    group = "Bulut",
                    available = false,
                    reason = "OPENAI_API_KEY tanımlı değil",
                ),
            ),
            onModelChange = { chosen = it },
        )

        composeRule.onNodeWithTag("model_dropdown").performScrollTo().performClick()
        composeRule.onNodeWithText("OPENAI_API_KEY tanımlı değil").assertIsDisplayed()
        composeRule.onNodeWithText("GPT-5.6 Sol").assertIsNotEnabled().performClick()

        composeRule.runOnIdle {
            assertNull("Kullanılamayan model seçilebildi", chosen)
        }
    }

    @Test
    fun fallbackListIsLabelledHonestlyWhenGatewayIsUnreachable() {
        panel(modelsLive = false)

        composeRule.onNodeWithText("Yedek liste · gateway'e ulaşılamadı")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun liveListIsLabelledAndRefreshable() {
        var refreshed = false
        panel(modelsLive = true, onRefreshModels = { refreshed = true })

        composeRule.onNodeWithText("Canlı liste · gateway").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Yenile").performScrollTo().performClick()

        composeRule.runOnIdle {
            assertTrue("Model listesi yenilenmedi", refreshed)
        }
    }

    @Test
    fun modelsNoteIsSurfacedWhenPresent() {
        panel(modelsNote = "Ollama listesi okunamadı")

        composeRule.onNodeWithText("Ollama listesi okunamadı")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun amethystThemeIsVisibleAndSelectable() {
        var selected: String? = null
        panel(onThemeChange = { selected = it })

        composeRule.onNodeWithTag("theme_amethyst")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        composeRule.runOnIdle {
            assertEquals("amethyst", selected)
        }
    }
}
