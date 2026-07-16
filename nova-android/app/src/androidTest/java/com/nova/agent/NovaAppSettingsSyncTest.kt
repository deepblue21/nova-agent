package com.nova.agent

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
    fun saveForwardsSameCanonicalConnectionToTaskOwnerBeforeAssistantOwner() {
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
            .performTextReplacement("  https://pc.example  ")
        composeRule.onNodeWithTag("gateway_token")
            .performTextReplacement("  new-token  ")
        composeRule.onNodeWithText("Kaydet").performClick()

        composeRule.runOnIdle {
            assertEquals(listOf("task", "assistant"), callbackOrder)
            assertTrue(taskConnection == assistantConnection)
            assertEquals("https://pc.example/v1", taskConnection?.first)
            assertTrue(taskConnection?.second == "new-token")
        }
    }

    @Test
    fun closingAfterTestingUnsavedDraftRestoresAppliedConnectionWithoutSaving() {
        val testedConnections = mutableListOf<Pair<String, String>>()
        var taskSaves = 0
        var assistantSaves = 0

        composeRule.setContent {
            NovaTheme {
                NovaSettingsPanel(
                    settings = AppSettings(
                        baseUrl = "https://applied.example/v1",
                        token = "applied-token",
                    ),
                    connection = GatewayConnectionUiState(),
                    onTestConnection = { baseUrl, token ->
                        testedConnections += baseUrl to token
                    },
                    onUpdateTaskConnection = { _, _ -> taskSaves++ },
                    onSaveAssistantConnection = { _, _ -> assistantSaves++ },
                    onModelChange = {},
                    onEffortChange = {},
                    onReasoningChange = {},
                    onClose = {},
                )
            }
        }

        composeRule.onNodeWithTag("gateway_url")
            .performTextReplacement("https://draft.example/v1")
        composeRule.onNodeWithTag("gateway_token")
            .performTextReplacement("draft-token")
        composeRule.onNodeWithText("Bağlantıyı test et").performClick()
        composeRule.onNodeWithContentDescription("Ayarları kapat").performClick()

        composeRule.runOnIdle {
            assertEquals(
                listOf(
                    "https://draft.example/v1" to "draft-token",
                    "https://applied.example/v1" to "applied-token",
                ),
                testedConnections,
            )
            assertEquals(0, taskSaves)
            assertEquals(0, assistantSaves)
        }
    }

    @Test
    fun invalidGatewayUrlIsNotForwardedForPersistenceOrTasks() {
        val testedConnections = mutableListOf<Pair<String, String>>()
        var taskConnection: Pair<String, String>? = null
        var assistantConnection: Pair<String, String>? = null

        composeRule.setContent {
            NovaTheme {
                NovaSettingsPanel(
                    settings = AppSettings(),
                    connection = GatewayConnectionUiState(),
                    onTestConnection = { baseUrl, token ->
                        testedConnections += baseUrl to token
                    },
                    onUpdateTaskConnection = { baseUrl, token ->
                        taskConnection = baseUrl to token
                    },
                    onSaveAssistantConnection = { baseUrl, token ->
                        assistantConnection = baseUrl to token
                    },
                    onModelChange = {},
                    onEffortChange = {},
                    onReasoningChange = {},
                    onClose = {},
                )
            }
        }

        composeRule.onNodeWithTag("gateway_url").performTextReplacement("not a url")
        composeRule.onNodeWithTag("gateway_token").performTextReplacement("private-token")
        composeRule.onNodeWithText("Kaydet").performClick()

        composeRule.runOnIdle {
            assertEquals(listOf("not a url" to "private-token"), testedConnections)
            assertEquals(null, taskConnection)
            assertEquals(null, assistantConnection)
        }
    }
}
