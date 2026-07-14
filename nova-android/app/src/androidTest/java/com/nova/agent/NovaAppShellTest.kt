package com.nova.agent

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nova.agent.data.Mode
import com.nova.agent.net.GatewayConnectionStatus
import com.nova.agent.net.GatewayConnectionUiState
import com.nova.agent.ui.app.NovaAppShell
import com.nova.agent.ui.theme.NovaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NovaAppShellTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsFixedDestinationsAndRoutesClicks() {
        var selected = Mode.TASKS
        composeRule.setContent {
            NovaTheme {
                NovaAppShell(
                    mode = Mode.TASKS,
                    connection = GatewayConnectionUiState(GatewayConnectionStatus.READY, "PC hazır"),
                    onModeChange = { selected = it },
                    onSettings = {},
                    onNewChat = {},
                ) { Box { Text("İçerik") } }
            }
        }

        composeRule.onNodeWithTag("primary_navigation").assertIsDisplayed()
        composeRule.onNodeWithText("Görevler").assertIsDisplayed()
        composeRule.onNodeWithText("Sohbet").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Ses").assertIsDisplayed()
        composeRule.onNodeWithText("PC hazır").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Ayarlar").assertIsDisplayed()
        assertEquals(Mode.CHAT, selected)
    }
}
