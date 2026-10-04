package com.nova.agent

import androidx.compose.runtime.State
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.nova.agent.feature.models.ModelsScreen
import com.nova.agent.llm.LocalModelUi
import com.nova.agent.llm.local.LocalModelCatalog
import com.nova.agent.llm.local.LocalModelDiskState
import com.nova.agent.ui.theme.NovaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ModelsDialogTest {
    @get:Rule val rule = createComposeRule()
    private val spec = LocalModelCatalog.byId("qwen3-14b-int4")!!
    private val initial = LocalModelUi(spec, LocalModelDiskState.NotInstalled)

    private fun screen(
        models: State<List<LocalModelUi>> = mutableStateOf(listOf(initial)),
        onDownload: (LocalModelUi) -> Unit = {},
        onCancel: (LocalModelUi) -> Unit = {},
        fontScale: Float = 1f,
        restoration: StateRestorationTester = StateRestorationTester(rule),
    ) {
        restoration.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
              NovaTheme {
                ModelsScreen(
                    models = models.value, activeLocalId = "", recommendedId = "",
                    localThinking = false, localTools = false, toolSummary = "",
                    storageUsedBytes = 0, storageFreeBytes = 30_000_000_000,
                    deviceRamGb = 8.0, offlineReady = false, metrics = emptyMap(),
                    gatewayModels = emptyList(), gatewaySelectedId = "",
                    onDownload = onDownload, onCancelDownload = onCancel,
                    onDelete = {}, onVerify = {}, onSelectLocal = {},
                    onLocalThinking = {}, onLocalTools = {}, onSelectGateway = {}, onStartLocalChat = {},
                )
              }
            }
        }
    }

    @Test fun summaryAndActionsAppearOnlyAfterOpeningModel() {
        var downloads = 0
        screen(onDownload = { downloads++ })
        rule.onNodeWithText(spec.note!!).assertDoesNotExist()
        rule.onNodeWithTag("model_download").assertDoesNotExist()
        val row = rule.onNodeWithTag("local_model_${spec.id}").performScrollTo()
        val bounds = row.getUnclippedBoundsInRoot()
        assertTrue(bounds.bottom - bounds.top <= 140.dp)
        row.performClick()
        rule.onNodeWithTag("model_details").assertIsDisplayed()
        rule.onNodeWithText(spec.note!!).performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("model_download").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(1, downloads) }
        rule.onNodeWithTag("close_model_details").performClick()
        rule.onNodeWithTag("model_details").assertDoesNotExist()
    }

    @Test fun openDialogTracksDownloadStateAndDoesNotCancelWhenDismissed() {
        val models = mutableStateOf(listOf(initial))
        var cancelled: LocalModelUi? = null
        screen(models = models, onCancel = { cancelled = it })
        rule.onNodeWithTag("local_model_${spec.id}").performScrollTo().performClick()
        rule.runOnIdle { models.value = listOf(initial.copy(downloading = true, downloadedBytes = 1234)) }
        rule.onNodeWithText("Duraklat").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("model_download").assertDoesNotExist()
        rule.onNodeWithTag("close_model_details").performClick()
        rule.runOnIdle { assertEquals(null, cancelled) }
        rule.onNodeWithTag("local_model_${spec.id}").performScrollTo().performClick()
        rule.onNodeWithText("Duraklat").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(1234L, cancelled?.downloadedBytes) }
    }

    @Test fun dialogSelectionSurvivesRecreation() {
        val restoration = StateRestorationTester(rule)
        screen(restoration = restoration)
        rule.onNodeWithTag("local_model_${spec.id}").performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()
        rule.onNodeWithTag("model_details").assertIsDisplayed()
        rule.onNodeWithText(spec.note!!).performScrollTo().assertIsDisplayed()
    }

    @Test fun largeTextKeepsDownloadAndCloseReachable() {
        screen(fontScale = 2f)
        rule.onNodeWithTag("local_model_${spec.id}").performScrollTo().performClick()
        rule.onNodeWithText(spec.note!!).performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("model_download").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("close_model_details").assertIsDisplayed().performClick()
        rule.onNodeWithTag("model_details").assertDoesNotExist()
    }
}
