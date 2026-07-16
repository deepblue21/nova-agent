package com.nova.agent

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
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
        val selected = mutableListOf<Mode>()
        composeRule.setContent {
            NovaTheme {
                NovaAppShell(
                    mode = Mode.KONTROL,
                    connection = GatewayConnectionUiState(GatewayConnectionStatus.READY, "PC hazır"),
                    onModeChange = selected::add,
                    onSettings = {},
                    onNewChat = {},
                ) { Box { Text("Kontrol alanı") } }
            }
        }

        composeRule.onNodeWithTag("primary_navigation")
            .assertIsDisplayed()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.ScrollBy))
        composeRule.onNodeWithText("Kontrol alanı").assertIsDisplayed()
        composeRule.onNodeWithText("İşler").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Sohbet").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Modeller").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("PC hazır").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Ayarlar").assertIsDisplayed()
        assertEquals(listOf(Mode.TASKS, Mode.CHAT, Mode.MODELLER), selected)
    }

    @Test
    fun chatModeExposesVoiceToggleAndLocalSubtitle() {
        var voiceToggled = false
        composeRule.setContent {
            NovaTheme {
                NovaAppShell(
                    mode = Mode.CHAT,
                    connection = GatewayConnectionUiState(GatewayConnectionStatus.READY, "PC hazır"),
                    onModeChange = {},
                    onSettings = {},
                    onNewChat = {},
                    localSubtitle = "Telefon · Yerel öncelikli",
                    onToggleVoice = { voiceToggled = true },
                ) { Box { Text("Sohbet alanı") } }
            }
        }

        composeRule.onNodeWithText("Telefon · Yerel öncelikli").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Ses moduna geç")
            .assertIsDisplayed()
            .performClick()
        assertEquals(true, voiceToggled)
    }

    @Test
    fun voiceModeKeepsChatHighlightedAndOffersReturn() {
        composeRule.setContent {
            NovaTheme {
                NovaAppShell(
                    mode = Mode.VOICE,
                    connection = GatewayConnectionUiState(GatewayConnectionStatus.READY, "PC hazır"),
                    onModeChange = {},
                    onSettings = {},
                    onNewChat = {},
                ) { Box { Text("Ses alanı") } }
            }
        }

        composeRule.onNodeWithContentDescription("Sohbete dön").assertIsDisplayed()
    }
}
