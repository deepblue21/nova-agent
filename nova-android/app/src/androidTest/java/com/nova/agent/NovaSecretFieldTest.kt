package com.nova.agent

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.nova.agent.ui.components.NovaSecretField
import com.nova.agent.ui.theme.NovaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Gizli değer alanının sözleşmesi: varsayılan maskeli, göz düğmesi açar,
 * kopyala düğmesi panoya yazar, alan boşken iki düğme de pasif.
 */
class NovaSecretFieldTest {
    @get:Rule val composeRule = createComposeRule()

    private fun setField(initial: String) {
        composeRule.setContent {
            NovaTheme {
                var value by remember { mutableStateOf(initial) }
                Column {
                    NovaSecretField(
                        value = value,
                        onValueChange = { value = it },
                        label = "Erişim belirteci",
                        testTag = "secret",
                    )
                }
            }
        }
    }

    @Test
    fun varsayilanMaskeliGozDugmesiAcar() {
        setField("nv_ab12cd_gizli")

        composeRule.onNodeWithTag("secret").assertIsDisplayed()
        // Maskeliyken ham değer ekranda görünmez ve Password semantiği bildirilir.
        composeRule.onAllNodesWithText("nv_ab12cd_gizli", useUnmergedTree = true)
            .assertCountEquals(0)
        composeRule.onNodeWithTag("secret")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))

        composeRule.onNodeWithTag("secret_reveal").performClick()

        composeRule.onAllNodesWithText("nv_ab12cd_gizli", useUnmergedTree = true)
            .assertCountEquals(1)
        // Maske açıkken Password bildirilmez — ekran okuyucu yanılmasın.
        composeRule.onNodeWithTag("secret")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password).not())

        composeRule.onNodeWithTag("secret_reveal").performClick()
        composeRule.onAllNodesWithText("nv_ab12cd_gizli", useUnmergedTree = true)
            .assertCountEquals(0)
    }

    /** Gerçek panoya dokunmadan setText çağrısını yakalayan sahte. */
    private class FakeClipboard : ClipboardManager {
        // Yedek alanin adi 'text' OLAMAZ: Kotlin ondan getText()/setText()
        // uretir, bunlar ClipboardManager'in ayni JVM imzali uyeleriyle
        // catisir ("platform declaration clash") ve :app:compileDebugAndroidTestKotlin
        // 4 hatayla duser. Yani enstrumanli test kaynagi bugune kadar HIC
        // derlenmiyordu -- push CI'i yalniz JVM testlerini kostugu ve
        // enstrumanli workflow elle tetiklendigi icin fark edilmemis.
        private var stored: AnnotatedString? = null
        override fun setText(annotatedString: AnnotatedString) { stored = annotatedString }
        override fun getText(): AnnotatedString? = stored
        override fun hasText(): Boolean = !stored?.text.isNullOrEmpty()
    }

    @Test
    fun kopyalaDugmesiPanoyaYazar() {
        val clipboard = FakeClipboard()
        composeRule.setContent {
            NovaTheme {
                NovaSecretField(
                    value = "nv_ab12cd_gizli",
                    onValueChange = {},
                    label = "Erişim belirteci",
                    testTag = "secret",
                    clipboard = clipboard,
                )
            }
        }

        composeRule.onNodeWithTag("secret_copy").assertIsEnabled().performClick()
        composeRule.waitForIdle()
        assertEquals("nv_ab12cd_gizli", clipboard.getText()?.text)
    }

    @Test
    fun bosAlandaDugmelerPasif() {
        setField("")

        composeRule.onNodeWithTag("secret_reveal").assertIsNotEnabled()
        composeRule.onNodeWithTag("secret_copy").assertIsNotEnabled()

        composeRule.onNodeWithTag("secret").performTextReplacement("nv_x")

        composeRule.onNodeWithTag("secret_reveal").assertIsEnabled()
        composeRule.onNodeWithTag("secret_copy").assertIsEnabled()
    }
}
