package com.nova.agent

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.nova.agent.data.ChatMessage
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

    @Test
    fun thinkingVoiceControlIsDisabledAndAccuratelyDescribed() {
        composeRule.setContent {
            NovaTheme {
                VoiceScreen(
                    VoiceState.THINKING,
                    "Düşünüyor…",
                    0.28f,
                    busy = true,
                    onStart = {},
                    onStop = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Yanıt hazırlanırken sesli komut kullanılamaz")
            .assertIsDisplayed()
            .assertIsNotEnabled()
    }

    @Test
    fun busyChatDisablesIdleVoiceControlAndAccuratelyDescribesIt() {
        composeRule.setContent {
            NovaTheme {
                VoiceScreen(
                    VoiceState.IDLE,
                    "Konuşmak için mikrofona dokun",
                    0.08f,
                    busy = true,
                    onStart = {},
                    onStop = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Yanıt sürerken sesli komut kullanılamaz")
            .assertIsDisplayed()
            .assertIsNotEnabled()
    }

    @Test
    fun streamingTallResponseKeepsTailVisible() {
        var streamingContent by mutableStateOf(
            List(40) { index -> "Akış satırı ${index + 1}" }.joinToString("\n"),
        )
        composeRule.setContent {
            NovaTheme {
                Box(Modifier.width(320.dp).height(240.dp)) {
                    ChatScreen(
                        messages = listOf(
                            ChatMessage(
                                role = "assistant",
                                content = streamingContent,
                                streaming = true,
                            ),
                        ),
                        busy = true,
                        onSend = {},
                        onStop = {},
                        onRegenerate = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            streamingContent += "\n" +
                List(20) { index -> "Yeni akış satırı ${index + 1}" }.joinToString("\n")
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("chat_stream_tail").assertIsDisplayed()
    }
}
