package com.nova.agent.ui.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.nova.agent.NovaViewModel
import com.nova.agent.data.AppSettings
import com.nova.agent.data.FALLBACK_MODELS
import com.nova.agent.data.FallbackKind
import com.nova.agent.data.ModelOption
import com.nova.agent.data.Mode
import com.nova.agent.data.UiMode
import com.nova.agent.feature.chat.ChatScreen
import com.nova.agent.feature.control.ControlScreen
import com.nova.agent.feature.history.ChatHistoryPanel
import com.nova.agent.feature.models.ModelsScreen
import com.nova.agent.feature.pairing.PairingUiState
import com.nova.agent.feature.settings.SettingsPanel
import com.nova.agent.feature.tasks.MobileTaskScreen
import com.nova.agent.feature.tasks.MobileTaskViewModel
import com.nova.agent.feature.voice.VoiceScreen
import com.nova.agent.llm.ExecutionPolicy
import com.nova.agent.net.GatewayConnectionStatus
import com.nova.agent.llm.local.ActiveBackend
import com.nova.agent.llm.local.BackendPreference
import com.nova.agent.llm.local.LocalModelDiskState
import com.nova.agent.llm.local.SamplerPreset
import com.nova.agent.llm.local.SamplerSettings
import com.nova.agent.llm.local.tools.HorusToolSet
import com.nova.agent.net.DiscoveredGateway
import com.nova.agent.net.GatewayConnectionClient
import com.nova.agent.net.GatewayConnectionUiState
import com.nova.agent.ui.components.rememberNotificationPermissionRequest

@Composable
fun NovaApp(
    vm: NovaViewModel,
    taskVm: MobileTaskViewModel,
    onRequestMic: () -> Unit,
) {
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showHistory by rememberSaveable { mutableStateOf(false) }

    // Model indirmesi başlarken bağlamında istenir; bkz. NotificationPermission.kt.
    val requestNotificationPermission = rememberNotificationPermissionRequest()

    // Kontrol/Modeller açılınca disk durumunu tazele (indirme dışı değişiklikler için).
    // Modeller ekranında ayrıca PC kataloğu da tazelenir: bağlandıktan sonra
    // `ollama pull` edilen bir model, yeniden bağlantı testi beklemeden listeye düşsün.
    // `baseUrl` gövdede OKUNUYOR ama key'de yoktu: Modeller ekranı açıkken
    // eşleme tamamlanıp adres dolduğunda katalog tazelenmiyordu.
    LaunchedEffect(vm.mode, vm.settings.baseUrl) {
        // "İşler" sekmesi kapalıyken oraya düşen bir durum (eski oturum, derin
        // bağlantı) erişilemeyen bir ekranda kilitlenirdi: gezinme çubuğunda o
        // sekme yok, geri dönecek düğme de yok. Sohbet'e alınır.
        if (!PHONE_TASKS_TAB_ENABLED && vm.mode == Mode.TASKS) {
            vm.mode = Mode.CHAT
            return@LaunchedEffect
        }
        if (vm.mode == Mode.KONTROL || vm.mode == Mode.MODELLER) vm.local.refresh()
        if (vm.mode == Mode.MODELLER && vm.settings.baseUrl.isNotBlank()) vm.refreshGatewayModels()
    }

    // Ayarlar'daki bildirim sayacı diskteki gerçeği göstersin.
    LaunchedEffect(showSettings) { if (showSettings) vm.refreshContentReports() }

    // Gorevler ekraninin baglantisi ayarlari TAKIP EDER; kimse elle itmez.
    // Eskiden yalniz Ayarlar'daki elle "Kaydet" yolu onUpdateTaskConnection'i
    // cagiriyordu; ESLEME yolu (PairingController -> saveConnection) cagirmiyordu.
    // Sonuc: QR/kod ile eslenen kullanicida baglanti karti "PC hazir" derken
    // taskVm eski (temiz kurulumda BOS) adresle kaliyor ve "Gorevi baslat"
    // gecersiz adrese gidiyordu. Tek kaynak olarak ayarlari izlemek, ayni
    // hatanin ileride acilacak her yeni baglanti yolunda tekrarlanmasini onler.
    LaunchedEffect(vm.settings.baseUrl, vm.settings.token) {
        taskVm.updateConnectionSettings(vm.settings.baseUrl, vm.settings.token)
    }

    // Bildirim kaydedildiğinde kısa onay. Kullanıcı bildirdiğini GÖRMELİ;
    // yoksa aynı yanıtı tekrar bildirir ya da işe yaramadığını sanır.
    vm.notice?.let { message ->
        LaunchedEffect(message) {
            kotlinx.coroutines.delay(3_500)
            vm.clearNotice()
        }
    }

    val activeSpec = vm.activeLocalSpec()
    val activeLocalUi = vm.local.models.firstOrNull { it.spec.id == activeSpec.id }
    val activeDisk = activeLocalUi?.disk
    val activeInstalled = activeDisk is LocalModelDiskState.Installed
    val activeVerified = (activeDisk as? LocalModelDiskState.Installed)?.verified == true
    val localPolicy = vm.executionPolicy.runsOnDevice

    NovaAppShell(
        mode = vm.mode,
        connection = vm.connectionState,
        localSubtitle = if (localPolicy) {
            "Telefon · ${vm.executionPolicy.label} · ${activeSpec.displayName}"
        } else {
            null
        },
        onModeChange = { vm.mode = it },
        onSettings = { showSettings = true },
        onNewChat = vm::newChat,
        onToggleVoice = {
            if (vm.mode == Mode.VOICE) vm.mode = Mode.CHAT else vm.mode = Mode.VOICE
        },
        notice = vm.notice,
    ) {
        when (vm.mode) {
            Mode.KONTROL -> ControlScreen(
                policy = vm.executionPolicy,
                localModelName = activeSpec.displayName,
                localInstalled = activeInstalled,
                localVerified = activeVerified,
                engineState = vm.local.engineState,
                connection = vm.connectionState,
                activeTask = taskVm.state.task,
                chatBusy = vm.busy,
                hybridAutoFallback = vm.settings.hybridAutoFallback,
                onHybridAutoFallback = vm::setHybridAutoFallback,
                onPolicyChange = vm::setExecutionPolicy,
                onNewTask = { vm.mode = Mode.TASKS },
                onOpenChat = { vm.mode = Mode.CHAT },
                onOpenModels = { vm.mode = Mode.MODELLER },
                // "Herhangi bir model kurulu mu" — aktif modelin durumu değil.
                anyModelInstalled = vm.local.anyInstalled(),
                firstRunGuideDismissed = vm.settings.firstRunGuideDismissed,
                recommendedName = vm.local.recommended.displayName,
                recommendedSize = vm.local.recommended.sizeLabel,
                onDismissFirstRunGuide = vm::dismissFirstRunGuide,
                pcRuns = vm.pcRuns,
                pcRunsLoading = vm.pcRunsLoading,
                onRefreshPcRuns = { vm.refreshPcRuns() },
            )

            Mode.TASKS -> MobileTaskScreen(
                state = taskVm.state,
                connection = vm.connectionState,
                onPromptChange = taskVm::updatePrompt,
                onCreateTask = taskVm::createTask,
                onCommand = {
                    if (it == "pause") taskVm.pause()
                    else if (it == "resume") taskVm.resume()
                    else taskVm.cancel()
                },
                onDecision = {
                    if (it == "approve") taskVm.approve() else taskVm.reject()
                },
                onNewTask = taskVm::newTask,
                onOpenSettings = { showSettings = true },
                onRetryConnection = vm::testConnection,
                onRetryStream = taskVm::retryStream,
            )

            Mode.CHAT -> ChatScreen(
                messages = vm.messages,
                busy = vm.busy,
                targetLabel = if (localPolicy) "Telefon · ${vm.executionPolicy.label}" else "PC/Gateway",
                modelLabel = vm.currentModelName(),
                pendingFallback = vm.pendingFallback?.reason,
                fallbackAllowsGateway = vm.pendingFallback?.allowGateway ?: true,
                fallbackKind = vm.pendingFallback?.kind ?: FallbackKind.LOCAL_ERROR,
                gatewayReady = vm.connectionState.status == GatewayConnectionStatus.READY,
                showAgentHandoff = vm.executionPolicy != ExecutionPolicy.LOCAL_ONLY,
                onSend = vm::send,
                onStop = vm::stop,
                onRegenerate = vm::regenerate,
                onApproveFallback = vm::approveFallback,
                onRejectFallback = vm::rejectFallback,
                onOpenControl = { vm.mode = Mode.KONTROL },
                onOpenModels = { vm.mode = Mode.MODELLER },
                onHandoffToAgent = vm::handoffToPcAgent,
                onOpenHistory = { showHistory = true },
                onReport = vm::reportContent,
            )

            Mode.MODELLER -> ModelsScreen(
                models = vm.local.models,
                activeLocalId = vm.settings.localModelId,
                localThinking = vm.settings.localThinking,
                localTools = vm.settings.localTools,
                toolSummary = HorusToolSet.SUMMARY,
                storageUsedBytes = vm.local.storageUsedBytes,
                storageFreeBytes = vm.local.storageFreeBytes,
                deviceRamGb = vm.local.deviceRamGb,
                offlineReady = activeInstalled && activeVerified,
                recommendedId = vm.local.recommended.id,
                metrics = vm.local.metrics,
                gatewayModels = vm.modelOptions(),
                gatewaySelectedId = vm.settings.modelId,
                uiMode = vm.settings.uiModeValue,
                onDownload = {
                    // Bildirim izni tam BURADA anlam kazanıyor: indirme arka
                    // planda sürecek ve ilerleme/iptal yalnız bildirimde
                    // görünecek. İstek indirmeyi beklemez — reddedilse bile
                    // indirme sürer, yalnız bildirim çıkmaz.
                    requestNotificationPermission()
                    vm.local.startDownload(it.spec, vm.settings.hfToken)
                },
                onCancelDownload = { vm.local.cancelDownload(it.spec) },
                onDelete = { vm.local.deleteModel(it.spec) },
                onVerify = { vm.local.verifyModel(it.spec) },
                onSelectLocal = vm::setLocalModel,
                onLocalThinking = vm::setLocalThinking,
                onLocalTools = vm::setLocalTools,
                onSelectGateway = vm::setModel,
                onStartLocalChat = {
                    vm.setExecutionPolicy(ExecutionPolicy.LOCAL_FIRST)
                    vm.mode = Mode.CHAT
                },
            )

            Mode.VOICE -> VoiceScreen(
                state = vm.voiceState,
                subtitle = vm.voiceSub,
                level = vm.level,
                busy = vm.busy,
                onStart = onRequestMic,
                onStop = vm::stopListeningOrSpeaking,
            )
        }
    }

    if (showHistory) {
        ChatHistoryPanel(
            summaries = vm.history,
            query = vm.historyQuery,
            onQueryChange = vm::updateHistoryQuery,
            onOpen = {
                vm.openConversation(it)
                showHistory = false
            },
            onShare = vm::shareConversation,
            onDelete = vm::deleteConversation,
            onClose = { showHistory = false },
        )
    }

    if (showSettings) {
        NovaSettingsPanel(
            settings = vm.settings,
            connection = vm.connectionState,
            onTestConnection = vm::testConnection,
            onUpdateTaskConnection = taskVm::updateConnectionSettings,
            onSaveAssistantConnection = vm::saveConnection,
            onModelChange = vm::setModel,
            onEffortChange = vm::setEffort,
            onReasoningChange = vm::setReasoning,
            models = vm.modelOptions(),
            modelsLive = vm.gatewayCatalog != null,
            modelsNote = vm.gatewayCatalog?.ollamaError.orEmpty(),
            onRefreshModels = { vm.refreshGatewayModels() },
            onThemeChange = vm::setTheme,
            onHfTokenChange = vm::setHfToken,
            onPersonaChange = vm::setPersona,
            onWipeData = vm::wipeAllLocalData,
            activeBackend = vm.local.activeBackend,
            pairing = vm.pairing.state,
            reportCount = vm.contentReports.size,
            onShareReports = vm::shareContentReports,
            onClearReports = vm::clearContentReports,
            onStartDiscovery = vm.pairing::rescan,
            onSelectGateway = vm.pairing::select,
            onPairCodeChange = vm.pairing::updateCode,
            onSubmitPairing = vm.pairing::submit,
            onDismissPairingMessage = vm.pairing::clearPhase,
            onUiModeChange = vm::setUiMode,
            onBackendChange = vm::setBackendPreference,
            onSamplerPresetChange = vm::setSamplerPreset,
            onCustomSamplerChange = vm::setCustomSampler,
            onRestoreAppliedConnection = { vm.refreshConnectionState() },
            onClose = {
                // Panel kapanınca mDNS taraması arka planda sürmemeli.
                vm.pairing.stopDiscovery()
                showSettings = false
            },
        )
    }
}

@Composable
internal fun NovaSettingsPanel(
    settings: AppSettings,
    connection: GatewayConnectionUiState,
    onTestConnection: (String, String) -> Unit,
    onUpdateTaskConnection: (String, String) -> Unit,
    onSaveAssistantConnection: (String, String) -> Unit,
    onModelChange: (String) -> Unit,
    onEffortChange: (String) -> Unit,
    onReasoningChange: (Boolean) -> Unit,
    models: List<ModelOption> = FALLBACK_MODELS,
    modelsLive: Boolean = false,
    modelsNote: String = "",
    onRefreshModels: () -> Unit = {},
    onThemeChange: (String) -> Unit = {},
    onHfTokenChange: (String) -> Unit = {},
    onPersonaChange: (String) -> Unit = {},
    onWipeData: (Boolean) -> Unit = {},
    activeBackend: ActiveBackend = ActiveBackend.NONE,
    pairing: PairingUiState = PairingUiState(),
    /** Play B6 — cihazda kayıtlı yapay zekâ içerik bildirimi sayısı. */
    reportCount: Int = 0,
    onShareReports: () -> Unit = {},
    onClearReports: () -> Unit = {},
    onStartDiscovery: () -> Unit = {},
    onSelectGateway: (DiscoveredGateway) -> Unit = {},
    onPairCodeChange: (String) -> Unit = {},
    onSubmitPairing: () -> Unit = {},
    onDismissPairingMessage: () -> Unit = {},
    onUiModeChange: (UiMode) -> Unit = {},
    onBackendChange: (BackendPreference) -> Unit = {},
    onSamplerPresetChange: (SamplerPreset) -> Unit = {},
    onCustomSamplerChange: (SamplerSettings) -> Unit = {},
    onRestoreAppliedConnection: () -> Unit = {
        onTestConnection(settings.baseUrl, settings.token)
    },
    onClose: () -> Unit,
) {
    SettingsPanel(
        settings = settings,
        connection = connection,
        onTestConnection = onTestConnection,
        onSaveConnection = { baseUrl, token ->
            val trimmedBaseUrl = baseUrl.trim()
            val trimmedToken = token.trim()
            val canonicalBaseUrl = GatewayConnectionClient
                .canonicalBaseUrl(trimmedBaseUrl)
                ?.toString()
            if (canonicalBaseUrl == null) {
                onTestConnection(trimmedBaseUrl, trimmedToken)
            } else {
                onUpdateTaskConnection(canonicalBaseUrl, trimmedToken)
                onSaveAssistantConnection(canonicalBaseUrl, trimmedToken)
            }
        },
        onModelChange = onModelChange,
        onEffortChange = onEffortChange,
        onReasoningChange = onReasoningChange,
        models = models,
        modelsLive = modelsLive,
        modelsNote = modelsNote,
        onRefreshModels = onRefreshModels,
        onThemeChange = onThemeChange,
        onHfTokenChange = onHfTokenChange,
        onPersonaChange = onPersonaChange,
        onWipeData = onWipeData,
        activeBackend = activeBackend,
        pairing = pairing,
        reportCount = reportCount,
        onShareReports = onShareReports,
        onClearReports = onClearReports,
        onStartDiscovery = onStartDiscovery,
        onSelectGateway = onSelectGateway,
        onPairCodeChange = onPairCodeChange,
        onSubmitPairing = onSubmitPairing,
        onDismissPairingMessage = onDismissPairingMessage,
        onUiModeChange = onUiModeChange,
        onBackendChange = onBackendChange,
        onSamplerPresetChange = onSamplerPresetChange,
        onCustomSamplerChange = onCustomSamplerChange,
        onClose = {
            onRestoreAppliedConnection()
            onClose()
        },
    )
}
