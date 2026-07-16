package com.nova.agent

import android.view.WindowManager
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.nova.agent.feature.tasks.MobileTask
import com.nova.agent.feature.tasks.MobileTaskEvent
import com.nova.agent.feature.tasks.MobileTaskScreen
import com.nova.agent.feature.tasks.MobileTaskStatus
import com.nova.agent.feature.tasks.MobileTaskUiState
import com.nova.agent.data.Mode
import com.nova.agent.net.GatewayConnectionStatus
import com.nova.agent.net.GatewayConnectionUiState
import com.nova.agent.net.MobileTaskClient
import com.nova.agent.ui.theme.NovaTheme
import com.nova.agent.ui.app.NovaAppShell
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

class MobileTaskScreenImeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    @Suppress("DEPRECATION")
    fun keepsComposerAndSubmitAboveImeAtLargeFontScale() {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.enableEdgeToEdge()
            activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 1.3f),
            ) {
                NovaTheme {
                    val connection = GatewayConnectionUiState(
                        GatewayConnectionStatus.READY,
                        "PC hazır",
                    )
                    NovaAppShell(
                        mode = Mode.TASKS,
                        connection = connection,
                        onModeChange = {},
                        onSettings = {},
                        onNewChat = {},
                    ) {
                        MobileTaskScreen(
                            state = MobileTaskUiState(prompt = "Ayarlar'ı aç"),
                            connection = connection,
                            onPromptChange = {},
                            onCreateTask = {},
                            onCommand = {},
                            onDecision = {},
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("task_prompt").performClick()
        var lastImeBottomPx = -1
        var stableSinceMs = 0L
        composeRule.waitUntil(timeoutMillis = 10_000) {
            val rootInsets = ViewCompat.getRootWindowInsets(composeRule.activity.window.decorView)
            val imeBottomPx = rootInsets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
            val now = SystemClock.uptimeMillis()
            if (imeBottomPx != lastImeBottomPx) {
                lastImeBottomPx = imeBottomPx
                stableSinceMs = now
            }
            rootInsets?.isVisible(WindowInsetsCompat.Type.ime()) == true &&
                imeBottomPx > 0 && now - stableSinceMs >= 200L
        }
        composeRule.waitForIdle()

        val rootInsets = requireNotNull(
            ViewCompat.getRootWindowInsets(composeRule.activity.window.decorView),
        )
        val imeBottomPx = rootInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        val windowHeight = composeRule.activity.window.decorView.height.toFloat()
        val windowWidth = composeRule.activity.window.decorView.width.toFloat()
        val imeTop = windowHeight - imeBottomPx
        val prompt = composeRule.onNodeWithTag("task_prompt").assertIsDisplayed()
            .fetchSemanticsNode().boundsInWindow
        val submit = composeRule.onNodeWithTag("task_submit").assertIsDisplayed()
            .fetchSemanticsNode().boundsInWindow

        assertTrue("Expected a visible IME inset", imeBottomPx > 0)
        assertTrue("Task composer is clipped above the window: $prompt", prompt.top >= 0f)
        assertTrue("Task composer exceeds the window width: $prompt", prompt.left >= 0f && prompt.right <= windowWidth)
        assertTrue("Task composer bottom ${prompt.bottom} is below IME top $imeTop", prompt.bottom <= imeTop)
        assertTrue("Task submit is clipped above the window: $submit", submit.top >= 0f)
        assertTrue("Task submit exceeds the window width: $submit", submit.left >= 0f && submit.right <= windowWidth)
        assertTrue("Task submit bottom ${submit.bottom} is below IME top $imeTop", submit.bottom <= imeTop)
    }
}
