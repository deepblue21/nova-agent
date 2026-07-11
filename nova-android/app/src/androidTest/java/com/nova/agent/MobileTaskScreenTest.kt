package com.nova.agent

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.nova.agent.feature.tasks.MobileConfirmation
import com.nova.agent.feature.tasks.MobileTask
import com.nova.agent.feature.tasks.MobileTaskEvent
import com.nova.agent.feature.tasks.MobileTaskScreen
import com.nova.agent.feature.tasks.MobileTaskStatus
import com.nova.agent.feature.tasks.MobileTaskUiState
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
            events = listOf(MobileTaskEvent("1", "task-1", "confirmation.requested", "Turn Wi-Fi off", confirmation)),
            pendingConfirmation = confirmation,
        )

        composeRule.setContent {
            NovaTheme {
                MobileTaskScreen(
                    state = state,
                    onPromptChange = {},
                    onCreateTask = {},
                    onCommand = {},
                    onDecision = { decision = it },
                )
            }
        }

        composeRule.onNodeWithTag("task_prompt").assertIsDisplayed()
        composeRule.onNodeWithTag("task_submit").assertIsDisplayed()
        composeRule.onNodeWithTag("task_timeline").assertIsDisplayed()
        composeRule.onNodeWithTag("confirmation_panel").assertIsDisplayed()
        composeRule.onNodeWithTag("confirmation_approve").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("confirmation_reject").assertIsDisplayed()

        assertEquals("approve", decision)
    }
}
