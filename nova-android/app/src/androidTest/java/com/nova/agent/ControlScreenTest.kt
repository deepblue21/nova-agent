package com.nova.agent

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nova.agent.feature.control.ControlScreen
import com.nova.agent.llm.ExecutionPolicy
import com.nova.agent.llm.LocalEngineUi
import com.nova.agent.net.GatewayConnectionUiState
import com.nova.agent.ui.theme.NovaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ControlScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun policySelectionRoutesChange() {
        var chosen: ExecutionPolicy? = null
        composeRule.setContent {
            NovaTheme {
                ControlScreen(
                    policy = ExecutionPolicy.GATEWAY_ONLY,
                    localModelName = "Qwen3 0.6B (int4)",
                    localInstalled = false,
                    localVerified = false,
                    engineState = LocalEngineUi.Idle,
                    connection = GatewayConnectionUiState(),
                    activeTask = null,
                    chatBusy = false,
                    hybridAutoFallback = false,
                    onHybridAutoFallback = {},
                    onPolicyChange = { chosen = it },
                    onNewTask = {},
                    onOpenChat = {},
                    onOpenModels = {},
                )
            }
        }
        composeRule.onNodeWithTag("policy_local_only").assertIsDisplayed().performClick()
        assertEquals(ExecutionPolicy.LOCAL_ONLY, chosen)
    }

    @Test
    fun hybridRulesCardVisibleOnlyInHybrid() {
        composeRule.setContent {
            NovaTheme {
                ControlScreen(
                    policy = ExecutionPolicy.HYBRID,
                    localModelName = "Qwen3 0.6B (int4)",
                    localInstalled = true,
                    localVerified = true,
                    engineState = LocalEngineUi.Idle,
                    connection = GatewayConnectionUiState(),
                    activeTask = null,
                    chatBusy = false,
                    hybridAutoFallback = false,
                    onHybridAutoFallback = {},
                    onPolicyChange = {},
                    onNewTask = {},
                    onOpenChat = {},
                    onOpenModels = {},
                )
            }
        }
        composeRule.onNodeWithTag("hybrid_rules_card").assertIsDisplayed()
        composeRule.onNodeWithText("Hibrit kuralları").assertIsDisplayed()
    }
}
