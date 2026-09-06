package com.nova.agent

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
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

    /**
     * Gerçek panoya dokunmadan kopyalama çağrısını yakalar.
     *
     * Eskiden burada `ClipboardManager`'ı taklit eden bir sınıf vardı ve
     * derlenmiyordu: Kotlin, yedek alandan `getText()/setText()` üretiyor,
     * bunlar arayüzün aynı JVM imzalı üyeleriyle çakışıyor ("platform
     * declaration clash") ve `:app:compileDebugAndroidTestKotlin` dört hatayla
     * düşüyordu. Yani enstrümanlı test kaynağı uzun süre HİÇ derlenmedi — push
     * CI'ı yalnız JVM testlerini koştuğu için fark edilmemişti.
     *
     * Üretim kodu artık Android arayüzü yerine sade bir `(String) -> Unit`
     * alıyor; taklit edilecek bir imza kalmadığı için tuzak da ortadan kalktı.
     */
    private class RecordingCopy : (String) -> Unit {
        val copied = mutableListOf<String>()
        override fun invoke(value: String) { copied += value }
    }

    @Test
    fun kopyalaDugmesiPanoyaYazar() {
        val clipboard = RecordingCopy()
        composeRule.setContent {
            NovaTheme {
                NovaSecretField(
                    value = "nv_ab12cd_gizli",
                    onValueChange = {},
                    label = "Erişim belirteci",
                    testTag = "secret",
                    onCopy = clipboard,
                )
            }
        }

        composeRule.onNodeWithTag("secret_copy").assertIsEnabled().performClick()
        composeRule.waitForIdle()
        assertEquals(listOf("nv_ab12cd_gizli"), clipboard.copied)
        // TEK yazma: eskiden once isaretsiz clip yaziliyor, ardindan
        // isaretlisiyle uzerine yaziliyordu. Ilk yazma Android 13+ onizlemesini
        // anahtar duz metinken tetikliyordu.
        assertEquals(1, clipboard.copied.size)
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
