package com.nova.agent.feature.chat

import com.nova.agent.data.ChatMessage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatStreamingIndicatorPolicyTest {
    @Test
    fun `empty streaming assistant message shows aperture`() {
        val message = ChatMessage(role = "assistant", content = "", streaming = true)

        assertTrue(shouldShowNovaThinkingIndicator(message))
    }

    @Test
    fun `content user messages and completed messages do not show aperture`() {
        assertFalse(
            shouldShowNovaThinkingIndicator(
                ChatMessage(role = "assistant", content = "Yanıt", streaming = true),
            ),
        )
        assertFalse(
            shouldShowNovaThinkingIndicator(
                ChatMessage(role = "user", content = "", streaming = true),
            ),
        )
        assertFalse(
            shouldShowNovaThinkingIndicator(
                ChatMessage(role = "assistant", content = "", streaming = false),
            ),
        )
    }
}
