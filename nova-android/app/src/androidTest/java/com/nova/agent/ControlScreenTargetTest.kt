package com.nova.agent

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.nova.agent.feature.control.ControlScreen
import com.nova.agent.feature.tasks.MobileTask
import com.nova.agent.feature.tasks.MobileTaskStatus
import com.nova.agent.llm.ExecutionPolicy
import com.nova.agent.llm.LocalEngineUi
import com.nova.agent.net.GatewayConnectionStatus
import com.nova.agent.net.GatewayConnectionUiState
import com.nova.agent.ui.theme.NovaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Kontrol merkezi hedef kartı ve aktif iş kartı.
 *
 * Ana kural: yerel politika seçiliyken model kurulu DEĞİLSE birincil eylem
 * "Model indir" olmalıdır — kullanıcı çalışmayacak bir göreve yönlendirilmez.
 *
 * Ekran dikey kaydırılabilir olduğu için alt kartlara erişimde performScrollTo()
 * kullanılır; küçük ekranlarda aksi halde düğümler görünür alanın dışında kalır.
 */
class ControlScreenTargetTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun screen(
        policy: ExecutionPolicy = ExecutionPolicy.LOCAL_FIRST,
        localInstalled: Boolean = true,
        localVerified: Boolean = true,
        engineState: LocalEngineUi = LocalEngineUi.Idle,
        connection: GatewayConnectionUiState = GatewayConnectionUiState(
            GatewayConnectionStatus.READY,
            "PC hazır",
        ),
        activeTask: MobileTask? = null,
        chatBusy: Boolean = false,
        hybridAutoFallback: Boolean = false,
        onHybridAutoFallback: (Boolean) -> Unit = {},
        onPolicyChange: (ExecutionPolicy) -> Unit = {},
        onNewTask: () -> Unit = {},
        onOpenChat: () -> Unit = {},
        onOpenModels: () -> Unit = {},
    ) {
        composeRule.setContent {
            NovaTheme {
                ControlScreen(
                    policy = policy,
                    localModelName = "Qwen3 0.6B (int4)",
                    localInstalled = localInstalled,
                    localVerified = localVerified,
                    engineState = engineState,
                    connection = connection,
                    activeTask = activeTask,
                    chatBusy = chatBusy,
                    hybridAutoFallback = hybridAutoFallback,
                    onHybridAutoFallback = onHybridAutoFallback,
                    onPolicyChange = onPolicyChange,
                    onNewTask = onNewTask,
                    onOpenChat = onOpenChat,
                    onOpenModels = onOpenModels,
                )
            }
        }
    }

    @Test
    fun missingLocalModelPromotesDownloadInsteadOfNewTask() {
        var openedModels = false
        var newTasks = 0
        screen(
            policy = ExecutionPolicy.LOCAL_FIRST,
            localInstalled = false,
            localVerified = false,
            onNewTask = { newTasks++ },
            onOpenModels = { openedModels = true },
        )

        composeRule.onAllNodesWithTag("cta_new_task").assertCountEquals(0)
        composeRule.onNodeWithTag("cta_download_model")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        composeRule.runOnIdle {
            assertTrue("Modeller ekranına yönlendirilmedi", openedModels)
            assertEquals(0, newTasks)
        }
    }

    @Test
    fun installedLocalModelPromotesNewTask() {
        var newTask = false
        screen(
            policy = ExecutionPolicy.LOCAL_FIRST,
            localInstalled = true,
            onNewTask = { newTask = true },
        )

        composeRule.onAllNodesWithTag("cta_download_model").assertCountEquals(0)
        composeRule.onNodeWithTag("cta_new_task")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        composeRule.runOnIdle {
            assertTrue("Yeni görev tetiklenmedi", newTask)
        }
    }

    @Test
    fun gatewayOnlyPolicyNeverAsksForLocalDownload() {
        screen(policy = ExecutionPolicy.GATEWAY_ONLY, localInstalled = false)

        composeRule.onAllNodesWithTag("cta_download_model").assertCountEquals(0)
        composeRule.onNodeWithTag("cta_new_task").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(
            "Bulut modelleri de PC'deki Gateway üzerinden çağrılır; anahtarlar telefona gelmez.",
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun gatewayOnlyPolicyShowsConnectionMessageAsSubtitle() {
        screen(
            policy = ExecutionPolicy.GATEWAY_ONLY,
            connection = GatewayConnectionUiState(
                GatewayConnectionStatus.UNREACHABLE,
                "Bağlantı yok",
            ),
        )

        composeRule.onNodeWithText("Bağlantı yok").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun offlinePolicyStatesHandoffIsClosed() {
        screen(policy = ExecutionPolicy.LOCAL_ONLY)

        composeRule.onNodeWithText("Yalnız telefonda çalışır · devir kapalı")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("İstemler hiçbir koşulda cihaz dışına gönderilmez.")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun localSubtitleReflectsInstallAndVerifyState() {
        screen(policy = ExecutionPolicy.LOCAL_FIRST, localInstalled = true, localVerified = false)

        composeRule.onNodeWithText("Qwen3 0.6B (int4) · doğrulanmadı")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun loadingEngineIsSurfacedInSubtitle() {
        screen(
            policy = ExecutionPolicy.LOCAL_FIRST,
            engineState = LocalEngineUi.Loading("Qwen3 0.6B (int4)"),
        )

        composeRule.onNodeWithText("Qwen3 0.6B (int4) · yükleniyor…")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun hybridRulesCardHiddenOutsideHybridPolicy() {
        screen(policy = ExecutionPolicy.LOCAL_FIRST)

        composeRule.onAllNodesWithTag("hybrid_rules_card").assertCountEquals(0)
    }

    @Test
    fun hybridAutoFallbackSwitchReportsAndRoutesState() {
        var toggled: Boolean? = null
        screen(
            policy = ExecutionPolicy.HYBRID,
            hybridAutoFallback = false,
            onHybridAutoFallback = { toggled = it },
        )

        composeRule.onNodeWithText("Kapalı: her seferinde izin kartı sorulur.")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Otomatik PC devri")
            .performScrollTo()
            .assertIsOff()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(true, toggled)
        }
    }

    @Test
    fun hybridAutoFallbackOnStateIsHonestAboutSilentHandoff() {
        screen(policy = ExecutionPolicy.HYBRID, hybridAutoFallback = true)

        composeRule.onNodeWithContentDescription("Otomatik PC devri")
            .performScrollTo()
            .assertIsOn()
        composeRule.onNodeWithText("Açık: devir bildirimsiz yapılır (rota rozetinde görünür).")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun activeWorkCardShowsIdleStateWhenNothingRunning() {
        screen(activeTask = null, chatBusy = false)

        composeRule.onNodeWithTag("active_work_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Aktif iş yok").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun activeWorkCardShowsTaskPromptAndLocalizedStatus() {
        screen(activeTask = MobileTask("task-1", "Ayarlar'ı aç", MobileTaskStatus.EXECUTING))

        composeRule.onNodeWithTag("active_work_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Ayarlar'ı aç").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Eylem uygulanıyor").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun activeWorkCardNamesTheLocalModelWhileGenerating() {
        screen(
            activeTask = null,
            chatBusy = true,
            engineState = LocalEngineUi.Ready("Qwen3 0.6B (int4)"),
        )

        composeRule.onNodeWithText("Telefonda yanıt üretiliyor (Qwen3 0.6B (int4))…")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun runningTaskTakesPrecedenceOverChatBusyLabel() {
        screen(
            activeTask = MobileTask("task-1", "Wi-Fi'yi kapat", MobileTaskStatus.VERIFYING),
            chatBusy = true,
        )

        composeRule.onNodeWithText("Wi-Fi'yi kapat").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Sonuç doğrulanıyor").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun allFourPoliciesAreSelectable() {
        val chosen = mutableListOf<ExecutionPolicy>()
        screen(policy = ExecutionPolicy.GATEWAY_ONLY, onPolicyChange = chosen::add)

        composeRule.onNodeWithTag("policy_local_first").performScrollTo().performClick()
        composeRule.onNodeWithTag("policy_local_only").performScrollTo().performClick()
        composeRule.onNodeWithTag("policy_hybrid").performScrollTo().performClick()

        composeRule.runOnIdle {
            assertEquals(
                listOf(
                    ExecutionPolicy.LOCAL_FIRST,
                    ExecutionPolicy.LOCAL_ONLY,
                    ExecutionPolicy.HYBRID,
                ),
                chosen,
            )
        }
    }

    @Test
    fun startChatActionIsAlwaysAvailable() {
        var openedChat = false
        screen(onOpenChat = { openedChat = true })

        composeRule.onNodeWithText("Sohbet başlat")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        composeRule.runOnIdle {
            assertTrue("Sohbet açılmadı", openedChat)
        }
    }

    @Test
    fun downloadCtaDoesNotDoubleAsTaskCreation() {
        var newTask = false
        screen(
            policy = ExecutionPolicy.LOCAL_ONLY,
            localInstalled = false,
            onNewTask = { newTask = true },
        )

        composeRule.onNodeWithTag("cta_download_model").performScrollTo().performClick()

        composeRule.runOnIdle {
            assertFalse("Model indirme görevi görev oluşturma tetikledi", newTask)
        }
    }
}
