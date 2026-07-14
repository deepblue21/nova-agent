package com.nova.agent

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nova.agent.feature.tasks.MobileConfirmation
import com.nova.agent.feature.tasks.MobileTask
import com.nova.agent.feature.tasks.MobileTaskEvent
import com.nova.agent.feature.tasks.MobileTaskScreen
import com.nova.agent.feature.tasks.MobileTaskStatus
import com.nova.agent.feature.tasks.MobileTaskUiState
import com.nova.agent.net.GatewayConnectionStatus
import com.nova.agent.net.GatewayConnectionUiState
import com.nova.agent.ui.theme.NovaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MobileTaskScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsTaskControlsAndSendsApprovalDecision() {
        var decision: String? = null
        val confirmation = MobileConfirmation("confirmation-1", "R2", "Turn Wi-Fi off")
        val state = MobileTaskUiState(
            task = MobileTask("task-1", "Open Settings", MobileTaskStatus.WAITING_FOR_CONFIRMATION),
            events = listOf(
                MobileTaskEvent(
                    id = "1",
                    taskId = "task-1",
                    type = "confirmation.requested",
                    summary = "Turn Wi-Fi off",
                    confirmation = confirmation,
                ),
            ),
            pendingConfirmation = confirmation,
        )

        composeRule.setContent {
            NovaTheme {
                MobileTaskScreen(
                    state = state,
                    connection = GatewayConnectionUiState(GatewayConnectionStatus.READY, "PC hazır"),
                    onPromptChange = {},
                    onCreateTask = {},
                    onCommand = {},
                    onDecision = { decision = it },
                    onNewTask = {},
                    onOpenSettings = {},
                    onRetryConnection = {},
                )
            }
        }

        composeRule.onNodeWithTag("task_timeline").assertIsDisplayed()
        composeRule.onNodeWithTag("confirmation_panel").assertIsDisplayed()
        composeRule.onNodeWithTag("confirmation_approve").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("confirmation_reject").assertIsDisplayed()

        assertEquals("approve", decision)
    }

    @Test
    fun showsCompletedWorkerStatusAndSanitizedSummary() {
        val state = MobileTaskUiState(
            task = MobileTask("task-1", "Open Settings", MobileTaskStatus.COMPLETED),
            events = listOf(
                MobileTaskEvent(
                    id = "2",
                    taskId = "task-1",
                    type = "worker.completed",
                    summary = "Android 17",
                    status = MobileTaskStatus.COMPLETED,
                ),
            ),
        )

        composeRule.setContent {
            NovaTheme {
                MobileTaskScreen(
                    state = state,
                    connection = GatewayConnectionUiState(GatewayConnectionStatus.READY, "PC hazır"),
                    onPromptChange = {},
                    onCreateTask = {},
                    onCommand = {},
                    onDecision = {},
                    onNewTask = {},
                    onOpenSettings = {},
                    onRetryConnection = {},
                )
            }
        }

        composeRule.onNodeWithText("Tamamlandı").assertIsDisplayed()
        composeRule.onNodeWithText("Android 17").assertIsDisplayed()
    }

    @Test
    fun showsTaskFirstEmptyStateAndQuickPrompt() {
        var prompt = ""
        composeRule.setContent {
            NovaTheme {
                MobileTaskScreen(
                    state = MobileTaskUiState(),
                    connection = GatewayConnectionUiState(GatewayConnectionStatus.READY, "PC hazır"),
                    onPromptChange = { prompt = it },
                    onCreateTask = {},
                    onCommand = {},
                    onDecision = {},
                    onNewTask = {},
                    onOpenSettings = {},
                    onRetryConnection = {},
                )
            }
        }

        composeRule.onNodeWithText("Telefonunda ne yapmamı istersin?").assertIsDisplayed()
        composeRule.onNodeWithText("Android sürümünü bul").performClick()
        assertEquals("Android sürümünü bul", prompt)
    }

    @Test
    fun blocksSubmissionAndOffersSettingsWhenGatewayIsUnavailable() {
        var opened = false
        composeRule.setContent {
            NovaTheme {
                MobileTaskScreen(
                    state = MobileTaskUiState(prompt = "Ayarlar'ı aç"),
                    connection = GatewayConnectionUiState(
                        GatewayConnectionStatus.UNREACHABLE,
                        "Bağlantı yok",
                    ),
                    onPromptChange = {},
                    onCreateTask = {},
                    onCommand = {},
                    onDecision = {},
                    onNewTask = {},
                    onOpenSettings = { opened = true },
                    onRetryConnection = {},
                )
            }
        }

        composeRule.onNodeWithText("Bağlantıyı ayarla").performClick()
        assertEquals(true, opened)
    }

    @Test
    fun showsLocalizedLifecycleAndNewTaskAction() {
        var newTask = false
        val state = MobileTaskUiState(
            task = MobileTask("task-1", "Android sürümünü bul", MobileTaskStatus.COMPLETED),
            events = listOf(
                MobileTaskEvent(
                    "1",
                    "task-1",
                    "worker.completed",
                    "Android 17",
                    MobileTaskStatus.COMPLETED,
                ),
            ),
        )
        composeRule.setContent {
            NovaTheme {
                MobileTaskScreen(
                    state = state,
                    connection = GatewayConnectionUiState(GatewayConnectionStatus.READY, "PC hazır"),
                    onPromptChange = {},
                    onCreateTask = {},
                    onCommand = {},
                    onDecision = {},
                    onNewTask = { newTask = true },
                    onOpenSettings = {},
                    onRetryConnection = {},
                )
            }
        }

        composeRule.onNodeWithText("Tamamlandı").assertIsDisplayed()
        composeRule.onNodeWithText("Android 17").assertIsDisplayed()
        composeRule.onNodeWithText("Yeni görev").performClick()
        assertEquals(true, newTask)
    }
}
