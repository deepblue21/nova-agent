package com.nova.agent

import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.nova.agent.data.AppSettings
import com.nova.agent.feature.settings.SettingsPanel
import com.nova.agent.net.GatewayConnectionStatus
import com.nova.agent.net.GatewayConnectionUiState
import com.nova.agent.ui.theme.NovaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

class SettingsPanelTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun testsConnectionAndExposesModelControlsWithoutExposingToken() {
        var tested: Pair<String, String>? = null
        composeRule.setContent {
            NovaTheme {
                SettingsPanel(
                    settings = AppSettings(
                        baseUrl = "http://10.0.2.2:8088/v1",
                        token = "secret",
                    ),
                    connection = GatewayConnectionUiState(
                        GatewayConnectionStatus.UNKNOWN,
                        "Bağlantı henüz test edilmedi",
                    ),
                    onTestConnection = { url, token -> tested = url to token },
                    onSaveConnection = { _, _ -> },
                    onModelChange = {},
                    onEffortChange = {},
                    onReasoningChange = {},
                    onClose = {},
                )
            }
        }

        composeRule.onNodeWithText("PC bağlantısı").assertIsDisplayed()
        composeRule.onNodeWithTag("gateway_token").assertIsDisplayed()
        composeRule.onAllNodesWithText("secret", useUnmergedTree = true).assertCountEquals(0)
        composeRule.onNodeWithText("Bağlantıyı test et").performClick()
        composeRule.onNodeWithText("Model ve çalışma biçimi").assertIsDisplayed()
        assertEquals("http://10.0.2.2:8088/v1" to "secret", tested)
    }
}

class SettingsPanelStatusBarTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun keepsHeaderAndCloseTargetBelowStatusBarAtDefaultFontScale() {
        setFontScale(1f)
        assertHeaderClearsStatusBar()
    }

    @Test
    fun keepsHeaderAndCloseTargetBelowStatusBarAtLargeFontScale() {
        setFontScale(1.3f)
        assertHeaderClearsStatusBar()
    }

    @After
    fun restoreFontScale() {
        setFontScale(1f)
    }

    private fun assertHeaderClearsStatusBar() {
        composeRule.onNodeWithContentDescription("Ayarlar").performClick()
        composeRule.onNodeWithText("Ayarlar").assertIsDisplayed()
        composeRule.waitForIdle()

        val rootInsets = requireNotNull(
            ViewCompat.getRootWindowInsets(composeRule.activity.window.decorView),
        )
        val statusBarTopPx = rootInsets.getInsets(WindowInsetsCompat.Type.statusBars()).top
        val titleTop = composeRule.onNodeWithText("Ayarlar")
            .fetchSemanticsNode().boundsInWindow.top
        val closeTop = composeRule.onNodeWithContentDescription("Ayarları kapat")
            .fetchSemanticsNode().boundsInWindow.top

        assertTrue("Expected a non-zero status-bar inset", statusBarTopPx > 0)
        assertTrue("Settings title top $titleTop overlaps inset $statusBarTopPx", titleTop >= statusBarTopPx)
        assertTrue("Close target top $closeTop overlaps inset $statusBarTopPx", closeTop >= statusBarTopPx)
    }

    private fun setFontScale(scale: Float) {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("settings put system font_scale $scale")
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { stream ->
            while (stream.read() != -1) {
                // Drain the shell command before waiting for the configuration update.
            }
        }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            abs(composeRule.activity.resources.configuration.fontScale - scale) < 0.01f
        }
        composeRule.waitForIdle()
    }
}
