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
    fun coldLaunchOpensControlCenterWithFixedShell() {
        composeRule.onNodeWithTag("primary_navigation")
            .assertIsDisplayed()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.ScrollBy))
        composeRule.onNodeWithText("YÜRÜTME POLİTİKASI").assertIsDisplayed()
        composeRule.onNodeWithText("Kontrol").assertIsDisplayed()
        composeRule.onNodeWithText("İşler").assertIsDisplayed()
        composeRule.onNodeWithText("Sohbet").assertIsDisplayed()
        compose