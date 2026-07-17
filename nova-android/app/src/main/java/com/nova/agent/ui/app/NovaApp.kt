package com.nova.agent.ui.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.nova.agent.NovaViewModel
import com.nova.agent.data.AppSettings
import com.nova.agent.data.MODELS
import com.nova.agent.data.Mode
import com.nova.agent.feature.chat.ChatScreen
import com.nova.agent.feature.control.ControlScreen
import com.nova.agent.feature.models.ModelsScreen
import com.nova.agent.feature.settings.SettingsPanel
import com.nova.agent.feature.tasks.MobileTaskScreen
import com.nova.agent.feature.tasks.MobileTaskViewModel
import com.nova.agent.feature.voice.VoiceScreen
import com.nova.agent.llm.ExecutionPolicy
import com.nova.agent.llm.local.LocalModelDiskState
import com.nova.agent.llm.local.tools.HorusToolSet
import com.nova.agent.net.GatewayConnectionClient
import com.nova.agent.net.GatewayConnectionUiState

@Composable
fun NovaApp(
    vm: NovaViewModel,
    taskVm: MobileTaskViewModel,
    onRequestMic: () -> Unit,
) {
    var showSettings by rememberSaveable { mutableStateOf(false) }

    // Kontrol/Modeller açılınca disk durumunu tazele (indirme dışı değişiklikler için).
    LaunchedEffect(vm.mode) {
        if (vm.mode == Mode.KONTROL || vm.mode == Mode.MODELLER) vm.local.refresh()
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
            )

            Mode.CHAT -> ChatScreen(
                messages = vm.messages,
                busy = vm.busy,
                targetLabel = if (localPolicy) "Telefon · ${vm.executionPolicy.label}" else "PC/Gateway",
                modelLabel = vm.currentModelName(),
                pendingFallback = vm.pendingFallback?.reason,
                fallbackAllowsGateway = vm.pendingFallback?.allowGateway ?: true,
                showAgentHandoff = vm.executionPolicy != ExecutionPolicy.LOCAL_ONLY,
                onSend = vm::send,
                onStop = vm::stop,
                onRegenerate = vm::regenerate,
                onApproveFallback = vm::approveFallback,
                onRejectFallback = vm::rejectFallback,
                onOpenControl = { vm.mode = Mode.KONTROL },
                onOpenModels = { vm.mode = Mode.MODELLER },
                onHandoffToAgent = vm::handoffToPcAgent,
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
                gatewayModels = MODELS,
                gatewaySelectedId = vm.settings.modelId,
                onDownload = { vm.local.startDownload(it.spec, vm.settings.hfToken) },
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
            onThemeChange = vm::setTheme,
            onHfTokenChange = vm::setHfToken,
            onRestoreAppliedConnection = { vm.testConnection() },
            onClose = { showSettings = false },
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
    onThemeChange: (String) -> Unit = {},
    onHfTokenChange: (String) -> Unit = {},
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
        onThemeChange = onThemeChange,
        onHfTokenChange = onHfTokenChange,
        onClose = {
            onRestoreAppliedConnection()
            onClose()
        },
    )
}
