package com.nova.agent

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nova.agent.feature.tasks.MobileTask
import com.nova.agent.feature.tasks.MobileTaskEvent
import com.nova.agent.feature.tasks.MobileTaskScreen
import com.nova.agent.feature.tasks.MobileTaskStatus
import com.nova.agent.feature.tasks.MobileTaskUiState
import com.nova.agent.net.GatewayConnectionStatus
import com.nova.agent.net.GatewayConnectionUiState
import com.nova.agent.net.MobileTaskClient
import com.nova.agent.ui.theme.NovaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MobileTaskScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsContractShapedConfirmationSummaryAndSendsDecision() {
        var decision: String? = null
        val state = confirmationState()

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

        composeRule.onNodeWithTag("confirmation_panel").assertIsDisplayed()
            .assert(hasAnyDescendant(hasText("Ayarlar'ı aç")))
        composeRule.onAllNodesWithText("waiting_for_confirmation").assertCountEquals(0)
        composeRule.onNodeWithTag("confirmation_approve").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("confirmation_reject").assertIsDisplayed()

        assertEquals("approve", decision)
    }

    @Test
    fun confirmationPanelExcludesUnderlyingTaskActionsFromSemantics() {
        composeRule.setContent {
            NovaTheme {
                MobileTaskScreen(
                    state = confirmationState(),
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

        composeRule.onAllNodesWithText("Duraklat").assertCountEquals(0)
        composeRule.onAllNodesWithText("İptal et").assertCountEquals(0)
        composeRule.onAllNodesWithTag("task_timeline").assertCountEquals(0)
        composeRule.onNodeWithTag("confirmation_panel").assertIsDisplayed()
    }

    @Test
    fun modalHasOnlyLabeledDecisionClickActions() {
        composeRule.setContent {
            NovaTheme {
                MobileTaskScreen(
                    state = confirmationState(),
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

        val clickAction = SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)
        composeRule.onAllNodes(clickAction).assertCountEquals(2)
        composeRule.onNodeWithTag("confirmation_approve")
            .assert(clickAction)
            .assert(hasText("Onayla"))
        composeRule.onNodeWithTag("confirmation_reject")
            .assert(clickAction)
            .assert(hasText("Reddet"))
        composeRule.onAllNodesWithText("Duraklat").assertCountEquals(0)
        composeRule.onAllNodesWithText("İptal et").assertCountEquals(0)
    }

    @Test
    fun disablesConfirmationDecisionsWhileLoading() {
        composeRule.setContent {
            NovaTheme {
                MobileTaskScreen(
                    state = confirmationState(loading = true),
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

        composeRule.onNodeWithTag("confirmation_approve").assertIsNotEnabled()
        composeRule.onNodeWithTag("confirmation_reject").assertIsNotEnabled()
    }

    @Test
    fun replacesRawWireStatusTimelineSummaryWithTurkishLabel() {
        val event = requireNotNull(
            MobileTaskClient.parseEvent(
                """{"id":"44","task_id":"task-1","type":"task.state","payload":{"status":"queued"}}""",
                null,
            ),
        )
        composeRule.setContent {
            NovaTheme {
                MobileTaskScreen(
                    state = MobileTaskUiState(
                        task = MobileTask("task-1", "Android sürümünü bul", MobileTaskStatus.QUEUED),
                        events = listOf(event),
                    ),
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

        composeRule.onAllNodesWithText("queued").assertCountEquals(0)
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

    private fun confirmationState(loading: Boolean = false): MobileTaskUiState {
        val event = requireNotNull(
            MobileTaskClient.parseEvent(
                """{"id":"43","task_id":"task-1","type":"confirmation.requested","payload":{"confirmation_id":"confirmation-1","risk_level":"R2","status":"waiting_for_confirmation"}}""",
                null,
            ),
        )
        return MobileTaskUiState(
            task = MobileTask("task-1", "Ayarlar'ı aç", MobileTaskStatus.WAITING_FOR_CONFIRMATION),
            events = listOf(event),
            pendingConfirmation = requireNotNull(event.confirmation),
            loading = loading,
        )
    }
}
