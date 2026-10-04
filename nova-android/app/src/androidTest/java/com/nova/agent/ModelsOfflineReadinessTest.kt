package com.nova.agent

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.nova.agent.data.FALLBACK_MODELS
import com.nova.agent.data.UiMode
import com.nova.agent.feature.models.ModelsScreen
import com.nova.agent.llm.LocalModelUi
import com.nova.agent.llm.local.LocalModelCatalog
import com.nova.agent.llm.local.LocalModelDiskState
import com.nova.agent.ui.theme.NovaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Modeller ekranı: çevrimdışı hazırlık kartı ve öneri bandı.
 *
 * Sözleşme: "Yerel sohbet başlat" yalnızca model doğrulanmışken sunulur —
 * doğrulanmamış modelle çevrimdışı vaadi verilmez.
 */
class ModelsOfflineReadinessTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val defaultSpec = LocalModelCatalog.default

    private fun catalog(
        overrideDisk: Map<String, LocalModelDiskState> = emptyMap(),
    ) = LocalModelCatalog.entries.map { spec ->
        LocalModelUi(
            spec = spec,
            disk = overrideDisk[spec.id] ?: LocalModelDiskState.NotInstalled,
        )
    }

    private fun screen(
        models: List<LocalModelUi> = catalog(),
        offlineReady: Boolean = false,
        recommendedId: String = defaultSpec.id,
        storageUsedBytes: Long = 0L,
        storageFreeBytes: Long = 8L * 1_073_741_824,
        deviceRamGb: Double = 6.0,
        onDownload: (LocalModelUi) -> Unit = {},
        onSelectLocal: (String) -> Unit = {},
        onStartLocalChat: () -> Unit = {},
        onSelectGateway: (String) -> Unit = {},
        onVerify: (LocalModelUi) -> Unit = {},
        onCancelDownload: (LocalModelUi) -> Unit = {},
        uiMode: UiMode = UiMode.ADVANCED,
    ) {
        composeRule.setContent {
            NovaTheme {
                ModelsScreen(
                    models = models,
                    activeLocalId = defaultSpec.id,
                    localThinking = false,
                    localTools = true,
                    toolSummary = "saat · hesap",
                    storageUsedBytes = storageUsedBytes,
                    storageFreeBytes = storageFreeBytes,
                    deviceRamGb = deviceRamGb,
                    offlineReady = offlineReady,
                    recommendedId = recommendedId,
                    metrics = emptyMap(),
                    gatewayModels = FALLBACK_MODELS,
                    gatewaySelectedId = "auto",
                    uiMode = uiMode,
                    onDownload = onDownload,
                    onCancelDownload = onCancelDownload,
                    onDelete = {},
                    onVerify = onVerify,
                    onSelectLocal = onSelectLocal,
                    onLocalThinking = {},
                    onLocalTools = {},
                    onSelectGateway = onSelectGateway,
                    onStartLocalChat = onStartLocalChat,
                )
            }
        }
    }

    @Test
    fun recommendedDownloadCanBePausedBeforePartialFileExists() {
        var paused = false
        screen(
            uiMode = UiMode.SIMPLE,
            models = catalog().map { if (it.spec.id == defaultSpec.id) it.copy(downloading = true) else it },
            onCancelDownload = { paused = true },
        )
        composeRule.onNodeWithTag("recommendation_banner").assertDoesNotExist()
        composeRule.onNodeWithTag("local_model_${defaultSpec.id}").performScrollTo().performClick()
        composeRule.onNodeWithText("Duraklat").performScrollTo().performClick()
        composeRule.runOnIdle { assertTrue(paused) }
    }

    @Test
    fun recommendedDownloadErrorRemainsVisibleInSimpleMode() {
        screen(
            uiMode = UiMode.SIMPLE,
            models = catalog().map { if (it.spec.id == defaultSpec.id) it.copy(error = "İndirme bağlantısı kesildi") else it },
        )
        composeRule.onNodeWithTag("recommendation_banner").assertDoesNotExist()
        composeRule.onNodeWithText("İşlem tamamlanamadı").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("local_model_${defaultSpec.id}").performScrollTo().performClick()
        composeRule.onNodeWithText("İndirme bağlantısı kesildi").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun simpleModeExpandsAvailableCatalogWithoutGatewayClutter() {
        val other = LocalModelCatalog.entries.first { it.id != defaultSpec.id && !it.gated }
        screen(uiMode = UiMode.SIMPLE)
        composeRule.onNodeWithText("PC GATEWAY MODELLERİ").assertDoesNotExist()
        composeRule.onNodeWithTag("local_model_${other.id}").assertDoesNotExist()
        composeRule.onNodeWithTag("toggle_model_catalog").performScrollTo().performClick()
        composeRule.onNodeWithTag("local_model_${other.id}").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("toggle_model_catalog").performScrollTo().performClick()
        composeRule.onNodeWithTag("local_model_${other.id}").assertDoesNotExist()
    }

    @Test
    fun simpleModeKeepsPartiallyDownloadedModelsAccessible() {
        val other = LocalModelCatalog.entries.first { it.id != defaultSpec.id && !it.gated }
        screen(uiMode = UiMode.SIMPLE, models = catalog(mapOf(other.id to LocalModelDiskState.Partial(100L))))
        composeRule.onNodeWithTag("local_model_${other.id}").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("local_model_${other.id}").performClick()
        composeRule.onNodeWithText("Sürdür").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun notReadyStateAsksForAModelAndHidesLocalChatEntry() {
        screen(offlineReady = false)

        composeRule.onNodeWithTag("offline_readiness").assertIsDisplayed()
        composeRule.onNodeWithText("Çevrimdışı için model gerekli").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Aşağıdan bir model indirip doğrulanmasını bekleyin.",
        ).assertIsDisplayed()
        composeRule.onAllNodesWithTag("start_local_chat").assertCountEquals(0)
    }

    @Test
    fun readyStateOffersLocalChatAndRoutesIt() {
        var started = false
        screen(
            models = catalog(mapOf(defaultSpec.id to LocalModelDiskState.Installed(verified = true))),
            offlineReady = true,
            onStartLocalChat = { started = true },
        )

        composeRule.onNodeWithText("Çevrimdışı kullanılabilir").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Seçili model doğrulandı. Yerel sohbet Gateway olmadan çalışır.",
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("start_local_chat")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        composeRule.runOnIdle {
            assertTrue("Yerel sohbet başlatılmadı", started)
        }
    }

    @Test
    fun installedButUnverifiedModelCanBeRecoveredWithoutDownloadingAgain() {
        var verifiedModel: String? = null
        screen(
            models = catalog(mapOf(defaultSpec.id to LocalModelDiskState.Installed(verified = false))),
            onVerify = { verifiedModel = it.spec.id },
        )

        composeRule.onNodeWithText("Model doğrulaması gerekli").assertIsDisplayed()
        composeRule.onAllNodesWithTag("start_local_chat").assertCountEquals(0)
        composeRule.onNodeWithTag("verify_local_model").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(defaultSpec.id, verifiedModel) }
    }

    @Test
    fun readinessCardStatesLocalRequestsNeverLeaveTheDevice() {
        screen(offlineReady = true)

        composeRule.onNodeWithText("Yerel istekler bu cihazdan çıkmaz").assertIsDisplayed()
    }

    @Test
    fun readinessCardReportsDeviceRamAndStorage() {
        screen(
            deviceRamGb = 6.0,
            storageUsedBytes = 2L * 1_048_576 * 512, // 1024 MB
            storageFreeBytes = 4L * 1_073_741_824,
        )

        // Kaynak kodla aynı biçimlendirme: cihaz yerel ayarı ondalık ayracını değiştirir.
        val ram = "%.1f".format(6.0)
        val free = "%.1f".format(4.0)
        composeRule.onNodeWithText("Cihaz RAM: $ram GB").assertIsDisplayed()
        composeRule.onNodeWithText("Modeller: 1024 MB · Boş depolama: $free GB").assertIsDisplayed()
    }

    @Test
    fun installedRecommendationCanBeActivatedFromItsModelRow() {
        var selected: String? = null
        screen(
            models = catalog(mapOf(defaultSpec.id to LocalModelDiskState.Installed(verified = true))),
            onSelectLocal = { selected = it },
        )

        composeRule.onAllNodesWithTag("recommendation_banner").assertCountEquals(0)
        composeRule.onAllNodesWithTag("recommend_download").assertCountEquals(0)
        composeRule.onNodeWithTag("local_model_${defaultSpec.id}").performScrollTo().performClick()
        composeRule.onNodeWithContentDescription("${defaultSpec.displayName} aktif yerel model")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(defaultSpec.id, selected)
        }
    }

    @Test
    fun recommendationBannerNamesTheModelAndItsSize() {
        screen()

        composeRule.onNodeWithText("Önerilen model").assertIsDisplayed()
        composeRule.onNodeWithTag("recommendation_banner").assertIsDisplayed()
        composeRule.onAllNodesWithText(defaultSpec.displayName).fetchSemanticsNodes().let {
            assertTrue("Önerilen modelin adı hiç görünmüyor", it.isNotEmpty())
        }
    }

    @Test
    fun gatedRecommendationWarnsAboutTokenRequirement() {
        val gated = LocalModelCatalog.entries.first { it.gated }
        screen(recommendedId = gated.id)

        composeRule.onNodeWithTag("recommendation_banner").performScrollTo().performClick()
        composeRule.onNodeWithTag("recommend_download")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("İndir (HF token gerekli)").assertIsDisplayed()
    }

    @Test
    fun unknownRecommendationHidesBannerWithoutCrashing() {
        screen(recommendedId = "does-not-exist")

        composeRule.onAllNodesWithTag("recommendation_banner").assertCountEquals(0)
        // Ekranın geri kalanı yine de çalışır.
        composeRule.onNodeWithText("TELEFON MODELLERİ").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun catalogShowsRecommendationOnceAndAllOtherModels() {
        screen()

        composeRule.onNodeWithTag("recommendation_banner").assertIsDisplayed()
        composeRule.onAllNodesWithTag("local_model_${defaultSpec.id}").assertCountEquals(0)
        LocalModelCatalog.entries.filter { it.id != defaultSpec.id }.forEach { spec ->
            composeRule.onNodeWithTag("local_model_${spec.id}")
                .performScrollTo()
                .assertIsDisplayed()
        }
    }
}
