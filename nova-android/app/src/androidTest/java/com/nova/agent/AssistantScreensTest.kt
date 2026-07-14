package com.nova.agent

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.nova.agent.data.VoiceState
import com.nova.agent.feature.chat.ChatScreen
import com.nova.agent.feature.voice.VoiceScreen
import com.nova.agent.ui.theme.NovaTheme
import org.junit.Rule
import org.junit.Test

class AssistantScreensTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun chatComposerHasNamedSendAndStopActions() {
        composeRule.setContent {
            NovaTheme {
                ChatScreen(emptyList(), busy = false, onSend = {}, onStop = {}, onRegenerate = {})
            }
        }

        composeRule.onNodeWithText("Merhaba, ben NOVA").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Mesaj gönder").assertExists()
    }

    @Test
    fun voiceControlNameFollowsState() {
        composeRule.setContent {
            NovaTheme {
                VoiceScreen(
                    VoiceState.IDLE,
                    "Konuşmak için mikrofona dokun",
                    0.08f,
                    onStart = {},
                    onStop = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Dinlemeyi başlat").assertIsDisplayed()
    }
}
