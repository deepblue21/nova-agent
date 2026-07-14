package com.nova.agent

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class NovaAppLaunchTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun coldLaunchUsesTaskFirstNonScrollingShell() {
        composeRule.onNodeWithTag("primary_navigation")
            .assertIsDisplayed()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.ScrollBy))
        composeRule.onNodeWithText("Telefonunda ne yapmamı istersin?").assertIsDisplayed()
        composeRule.onNodeWithText("Görevler").assertIsDisplayed()
        composeRule.onNodeWithText("Sohbet").assertIsDisplayed()
        composeRule.onNodeWithText("Ses").assertIsDisplayed()
    }
}
