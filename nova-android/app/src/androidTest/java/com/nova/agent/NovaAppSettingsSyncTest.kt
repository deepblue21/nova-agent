package com.nova.agent

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import com.nova.agent.data.AppSettings
import com.nova.agent.net.GatewayConnectionUiState
import com.nova.agent.ui.app.NovaSettingsPanel
import com.nova.agent.ui.theme.NovaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NovaAppSettingsSyncTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun saveForwardsSameTrimmedConnectionToTaskOwnerBeforeAssistantOwner() {
        val callbackOrder = mutableListOf<String>()
        var taskConnection: Pair<String, String>? = null
        var assistantConnection: Pair<String, String>? = null

        composeRule.setContent {
            NovaTheme {
                NovaSettingsPanel(
                    settings = AppSettings(),
                    connection = GatewayConnectionUiState(),
                    onTestConnection = { _, _ -> },
                    onUpdateTaskConnection = { baseUrl, token ->
                        callbackOrder += "task"
                        taskConnection = baseUrl to token
                    },
                    onSaveAssistantConnection = { baseUrl, token ->
                        callbackOrder += "assistant"
                        assistantConnection = baseUrl to token
                    },
                    onModelChange = {},
                    onEffortChange = {},
                    onReasoningChange = {},
                    onClose = {},
                )
            }
        }

        composeRule.onNodeWithTag("gateway_url")
            .performTextReplacement("  https://pc.example/v1  ")
        composeRule.onNodeWithContentDescription("Gateway erişim belirteci")
            .performSemanticsAction(SemanticsActions.SetText) {
                it(AnnotatedString("  new-token  "))
            }
        composeRule.onNodeWithText("Kaydet").performClick()

        composeRule.runOnIdle {
            assertEquals(listOf("task", "assistant"), callbackOrder)
            assertTrue(taskConnection == assistantConnection)
            assertEquals("https://pc.example/v1", taskConnection?.first)
            assertTrue(taskConnection?.second == "new-token")
        }
    }
}
