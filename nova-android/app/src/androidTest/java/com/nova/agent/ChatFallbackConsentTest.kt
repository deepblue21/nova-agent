package com.nova.agent

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.nova.agent.data.ChatMessage
import com.nova.agent.feature.chat.ChatScreen
import com.nova.agent.ui.theme.NovaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Devir onay kartı (fallback consent) ve sohbet besteci davranışı.
 *
 * En kritik gizlilik sözleşmesi burada: Çevrimdışı (LOCAL_ONLY) modda
 * "PC'ye gönder" eylemi HİÇBİR koşulda görünmemelidir — sessiz devir yok.
 */
class ChatFallbackConsentTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val localReply = listOf(
        ChatMessage(role = "user", content = "Merhaba"),
        ChatMessage(role = "assistant", content = "Yanıt veremedim"),
    )

    @Test
    fun fallbackCardOffersGatewayHandoffOnlyWithConsent() {
        var approved = false
        var rejected = false
        composeRule.setContent {
            NovaTheme {
                ChatScreen(
                    messages = localReply,
                    busy = false,
                    pendingFallback = "Yerel model belleğe sığmadı",
                    fallbackAllowsGateway = true,
                    onSend = {},
                    onStop = {},
                    onRegenerate = {},
                    onApproveFallback = { approved = true },
                    onRejectFallback = { rejected = true },
                )
            }
        }

        composeRule.onNodeWithTag("fallback_consent").assertIsDisplayed()
        composeRule.onNodeWithText("Telefon modeli yanıt veremedi").assertIsDisplayed()
        composeRule.onNodeWithText("Yerel model belleğe sığmadı").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("PC'ye gönder").assertIsDisplayed().performClick()
        composeRule.onNodeWithContentDescription("Vazgeç").assertIsDisplayed().performClick()

        composeRule.runOnIdle {
            assertTrue("Onay geri çağrısı tetiklenmedi", approved)
            assertTrue("Ret geri çağrısı tetiklenmedi", rejected)
        }
    }

    @Test
    fun offlinePolicyNeverExposesGatewayHandoffAction() {
        var approved = false
        composeRule.setContent {
            NovaTheme {
                ChatScreen(
                    messages = localReply,
                    busy = false,
                    pendingFallback = "Model yüklenemedi",
                    fallbackAllowsGateway = false,
                    onSend = {},
                    onStop = {},
                    onRegenerate = {},
                    onApproveFallback = { approved = true },
                    onRejectFallback = {},
                )
            }
        }

        composeRule.onNodeWithTag("fallback_consent").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("PC'ye gönder").assertCountEquals(0)
        composeRule.onNodeWithText(
            "Çevrimdışı mod: istem cihaz dışına gönderilmez. " +
                "Modeller sekmesinden durumu kontrol edebilirsin.",
        ).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Anladım").assertIsDisplayed().performClick()

        composeRule.runOnIdle {
            assertFalse("Çevrimdışı modda devir onayı tetiklendi", approved)
        }
    }

    @Test
    fun fallbackCardHiddenWhenNothingPending() {
        composeRule.setContent {
            NovaTheme {
                ChatScreen(
                    messages = localReply,
                    busy = false,
                    pendingFallback = null,
                    onSend = {},
                    onStop = {},
                    onRegenerate = {},
                )
            }
        }

        composeRule.onAllNodesWithContentDescription("PC'ye gönder").assertCountEquals(0)
    }

    @Test
    fun composerSendsDraftAndClearsIt() {
        val sent = mutableListOf<String>()
        composeRule.setContent {
            NovaTheme {
                ChatScreen(
                    messages = emptyList(),
                    busy = false,
                    onSend = { sent += it },
                    onStop = {},
                    onRegenerate = {},
                )
            }
        }

        // BasicTextField'a metin girmeden önce odak gerekir (InsertTextAtCursor aksi halde reddeder).
        composeRule.onNodeWithTag("chat_input").performClick().assertIsFocused()
        composeRule.onNodeWithTag("chat_input").performTextInput("Android sürümü nedir")
        composeRule.onNodeWithContentDescription("Mesaj gönder").performClick()

        composeRule.runOnIdle {
            assertEquals(listOf("Android sürümü nedir"), sent)
        }
        // Gönderim sonrası taslak temizlenir; yer tutucu geri gelir.
        composeRule.onNodeWithText("NOVA'ya yaz…").assertIsDisplayed()
    }

    @Test
    fun blankDraftKeepsSendActionDisabled() {
        var sends = 0
        composeRule.setContent {
            NovaTheme {
                ChatScreen(
                    messages = emptyList(),
                    busy = false,
                    onSend = { sends++ },
                    onStop = {},
                    onRegenerate = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Mesaj gönder")
            .assertIsNotEnabled()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(0, sends)
        }
    }

    @Test
    fun busyComposerSwitchesToStopActionAndRoutesIt() {
        var stopped = false
        var sends = 0
        composeRule.setContent {
            NovaTheme {
                ChatScreen(
                    messages = listOf(ChatMessage(role = "assistant", content = "", streaming = true)),
                    busy = true,
                    onSend = { sends++ },
                    onStop = { stopped = true },
                    onRegenerate = {},
                )
            }
        }

        composeRule.onAllNodesWithContentDescription("Mesaj gönder").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("Yanıtı durdur").assertIsDisplayed().performClick()

        composeRule.runOnIdle {
            assertTrue("Durdurma geri çağrısı tetiklenmedi", stopped)
            assertEquals(0, sends)
        }
    }

    @Test
    fun lastAssistantMessageOffersRegenerate() {
        var regenerated = false
        composeRule.setContent {
            NovaTheme {
                ChatScreen(
                    messages = listOf(
                        ChatMessage(role = "user", content = "Soru"),
                        ChatMessage(role = "assistant", content = "Tamamlanmış yanıt"),
                    ),
                    busy = false,
                    onSend = {},
                    onStop = {},
                    onRegenerate = { regenerated = true },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Yeniden oluştur")
            .assertIsDisplayed()
            .performClick()

        composeRule.runOnIdle {
            assertTrue("Yeniden oluştur tetiklenmedi", regenerated)
        }
    }

    @Test
    fun streamingMessageHidesRegenerateAction() {
        composeRule.setContent {
            NovaTheme {
                ChatScreen(
                    messages = listOf(
                        ChatMessage(role = "user", content = "Soru"),
                        ChatMessage(role = "assistant", content = "Yarım", streaming = true),
                    ),
                    busy = true,
                    onSend = {},
                    onStop = {},
                    onRegenerate = {},
                )
            }
        }

        composeRule.onAllNodesWithContentDescription("Yeniden oluştur").assertCountEquals(0)
    }

    @Test
    fun targetChipsRouteToControlModelsAndHistory() {
        var control = false
        var models = false
        var history = false
        composeRule.setContent {
            NovaTheme {
                ChatScreen(
                    messages = emptyList(),
                    busy = false,
                    targetLabel = "Telefon",
                    modelLabel = "qwen3-0.6b-int4",
                    onSend = {},
                    onStop = {},
                    onRegenerate = {},
                    onOpenControl = { control = true },
                    onOpenModels = { models = true },
                    onOpenHistory = { history = true },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Yürütme hedefi: Telefon").performClick()
        composeRule.onNodeWithContentDescription("Model: qwen3-0.6b-int4").performClick()
        composeRule.onNodeWithContentDescription("Sohbet geçmişini aç").performClick()

        composeRule.runOnIdle {
            assertTrue(control)
            assertTrue(models)
            assertTrue(history)
        }
    }

    @Test
    fun agentHandoffChipAppearsOnlyAfterUserTurnAndWhenIdle() {
        composeRule.setContent {
            NovaTheme {
                ChatScreen(
                    messages = emptyList(),
                    busy = false,
                    showAgentHandoff = true,
                    onSend = {},
                    onStop = {},
                    onRegenerate = {},
                )
            }
        }

        // Kullanıcı turu yokken devretme teklif edilmez.
        composeRule.onAllNodesWithContentDescription(
            "Son soruyu tüm bağlamla PC'deki ajana gönder",
        ).assertCountEquals(0)
    }

    @Test
    fun agentHandoffChipRoutesWhenUserTurnExists() {
        var handed = false
        composeRule.setContent {
            NovaTheme {
                ChatScreen(
                    messages = localReply,
                    busy = false,
                    showAgentHandoff = true,
                    onSend = {},
                    onStop = {},
                    onRegenerate = {},
                    onHandoffToAgent = { handed = true },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Son soruyu tüm bağlamla PC'deki ajana gönder")
            .assertIsDisplayed()
            .performClick()

        composeRule.runOnIdle {
            assertTrue("Ajana devretme tetiklenmedi", handed)
        }
    }
}
