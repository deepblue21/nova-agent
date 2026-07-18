package com.nova.agent

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nova.agent.data.ConversationSummary
import com.nova.agent.feature.history.ChatHistoryPanel
import com.nova.agent.ui.theme.NovaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ChatHistoryPanelTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val sample = listOf(
        ConversationSummary("a", "Kotlin soruları", 1_752_000_000_000, 4, "bir dil"),
        ConversationSummary("b", "Hava durumu", 1_752_000_100_000, 2, "yağmurlu"),
    )

    @Test
    fun listsConversationsAndRoutesOpen() {
        var opened: String? = null
        composeRule.setContent {
            NovaTheme {
                ChatHistoryPanel(
                    summaries = sample,
                    query = "",
                    onQueryChange = {},
                    onOpen = { opened = it },
                    onShare = {},
                    onDelete = {},
                    onClose = {},
                )
            }
        }
        composeRule.onNodeWithText("Kotlin soruları").assertIsDisplayed()
        composeRule.onNodeWithText("Hava durumu").assertIsDisplayed().performClick()
        assertEquals("b", opened)
    }

    @Test
    fun shareAndDeleteActionsFire() {
        var shared: String? = null
        var deleted: String? = null
        composeRule.setContent {
            NovaTheme {
                ChatHistoryPanel(
                    summaries = sample.take(1),
                    query = "",
                    onQueryChange = {},
                    onOpen = {},
                    onShare = { shared = it },
                    onDelete = { deleted = it },
                    onClose = {},
                )
            }
        }
        composeRule.onNodeWithContentDescription("Sohbeti paylaş").performClick()
        composeRule.onNodeWithContentDescription("Sohbeti sil").performClick()
        assertEquals("a", shared)
        assertEquals("a", deleted)
    }

    @Test
    fun emptyStateShownWhenNoConversations() {
        composeRule.setContent {
            NovaTheme {
                ChatHistoryPanel(
                    summaries = emptyList(),
                    query = "",
                    onQueryChange = {},
                    onOpen = {},
                    onShare = {},
                    onDelete = {},
                    onClose = {},
                )
            }
        }
        composeRule.onNodeWithText("Henüz kayıtlı sohbet yok.").assertIsDisplayed()
    }
}
