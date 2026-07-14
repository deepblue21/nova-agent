package com.nova.agent

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nova.agent.data.AppSettings
import com.nova.agent.feature.settings.SettingsPanel
import com.nova.agent.net.GatewayConnectionStatus
import com.nova.agent.net.GatewayConnectionUiState
import com.nova.agent.ui.theme.NovaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsPanelTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun testsConnectionAndExposesModelControlsWithoutExposingToken() {
        var tested: Pair<String, String>? = null
        composeRule.setContent {
            NovaTheme {
                SettingsPanel(
                    settings = AppSettings(
                        baseUrl = "http://10.0.2.2:8088/v1",
                        token = "secret",
                    ),
                    connection = GatewayConnectionUiState(
                        GatewayConnectionStatus.UNKNOWN,
                        "Bağlantı henüz test edilmedi",
                    ),
                    onTestConnection = { url, token -> tested = url to token },
                    onSaveConnection = { _, _ -> },
                    onModelChange = {},
                    onEffortChange = {},
                    onReasoningChange = {},
                    onClose = {},
                )
            }
        }

        composeRule.onNodeWithText("PC bağlantısı").assertIsDisplayed()
        composeRule.onNodeWithTag("gateway_token").assertIsDisplayed()
        composeRule.onAllNodesWithText("secret", useUnmergedTree = true).assertCountEquals(0)
        composeRule.onNodeWithText("Bağlantıyı test et").performClick()
        composeRule.onNodeWithText("Model ve çalışma biçimi").assertIsDisplayed()
        assertEquals("http://10.0.2.2:8088/v1" to "secret", tested)
    }
}
