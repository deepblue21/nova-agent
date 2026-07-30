package com.nova.agent.ui.brand

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.nova.agent.ui.theme.NovaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NovaThinkingIndicatorTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun reducedMotionIndicatorIsVisibleAndAccessible() {
        composeRule.setContent {
            NovaThinkingIndicator(animated = false)
        }

        composeRule
            .onNodeWithTag("nova_thinking_indicator")
            .assertIsDisplayed()
            .assertContentDescriptionEquals("Yanıt hazırlanıyor")
    }

    @Test
    fun indicatorRendersTheActiveThemePalette() {
        val themeId = mutableStateOf("amethyst")
        composeRule.setContent {
            NovaTheme(themeId = themeId.value) {
                NovaThinkingIndicator(
                    animated = false,
                    showHalo = false,
                    testTag = "theme_indicator",
                )
            }
        }

        val amethyst = composeRule.onNodeWithTag("theme_indicator")
            .captureToImage()
            .toPixelMap()
        composeRule.runOnIdle { themeId.value = "amber" }
        val amber = composeRule.onNodeWithTag("theme_indicator")
            .captureToImage()
            .toPixelMap()

        assertEquals(amethyst.width, amber.width)
        assertEquals(amethyst.height, amber.height)
        var differingPixels = 0
        for (y in 0 until amethyst.height) {
            for (x in 0 until amethyst.width) {
                if (amethyst[x, y] != amber[x, y]) differingPixels++
            }
        }
        assertTrue("Tema değişince marka rengi değişmedi", differingPixels > 20)
    }
}
