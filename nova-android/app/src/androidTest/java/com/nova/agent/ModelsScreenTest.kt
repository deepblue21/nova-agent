package com.nova.agent

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nova.agent.data.MODELS
import com.nova.agent.llm.LocalModelUi
import com.nova.agent.llm.local.LocalModelCatalog
import com.nova.agent.llm.local.LocalModelDiskState
import com.nova.agent.ui.theme.NovaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ModelsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun uiList() = LocalModelCatalog.entries.map {
        LocalModelUi(spec = it, disk = LocalModelDiskState.NotInstalled)
    }

    @Test
    fun recommendationBannerShownAndDownloadFires() {
        var downloaded: String? = null
        composeRule.setContent {
            NovaTheme {
                ModelsScreen(
                    models = uiList(),
                    activeLocalId = LocalModelCatalog.default.id,
                    localThinking = false,
                    localTools = true,
                    toolSummary = "saat · hesap",
                    storageUsedBytes = 0,
                    storageFreeBytes = 8L * 1_073_741_824,
                    deviceRamGb = 6.0,
                    offlineReady = false,
                    recommendedId = LocalModelCatalog.default.id,
                    metrics = emptyMap(),
                    gatewayModels = MODELS,
                    gatewaySelectedId = "auto",
                    onDownload = { downloaded = it.spec.id },
                    onCancelDownload = {},
                    onDelete = {},
                    onVerify = {},
                    onSelectLocal = {},
                    onLocalThinking = {},
                    onLocalTools = {},
                    onSelectGateway = {},
                    onStartLocalChat = {},
                )
            }
        }
        composeRule.onNodeWithTag("recommendation_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("recommend_download").assertIsDisplayed().performClick()
        assertEquals(LocalModelCatalog.default.id, downloaded)
    }

    @Test
    fun deviceModelsSectionListsCatalog() {
        composeRule.setContent {
            NovaTheme {
                ModelsScreen(
                    models = uiList(),
                    activeLocalId = LocalModelCatalog.default.id,
                    localThinking = false,
                    localTools = true,
                    toolSummary = "saat · hesap",
                    storageUsedBytes = 0,
                    storageFreeBytes = 8L * 1_073_741_824,
                    deviceRamGb = 6.0,
                    offlineReady = false,
                    recommendedId = LocalModelCatalog.default.id,
                    metrics = emptyMap(),
                    gatewayModels = MODELS,
                    gatewaySelectedId = "auto",
                    onDownload = {},
                    onCancelDownload = {},
                    onDelete = {},
                    onVerify = {},
                    onSelectLocal = {},
                    onLocalThinking = {},
                    onLocalTools = {},
                    onSelectGateway = {},
                    onStartLocalChat = {},
                )
            }
        }
        composeRule.onNodeWithText("CİHAZDAKİ MODELLER").assertIsDisplayed()
        composeRule.onNodeWithTag("local_model_${LocalModelCatalog.default.id}").assertIsDisplayed()
    }
}
