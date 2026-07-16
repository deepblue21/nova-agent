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
    val localPolicy = vm.executionPolicy == ExecutionPolicy.LOCAL_FIRST

    NovaAppShell(
        mode = vm.mode,
        connection = vm.connectionState,
        localSubtitle = if (localPolicy) {
            "Telefon · Yerel öncelikli · ${activeSpec.displayName}"
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
                targetLabel = if (localPolicy) "Telefon" else "PC/Gateway",
                modelLabel = vm.currentModelName(),
                pendingFallback = vm.pendingFallback?.reason,
                onSend = vm::send,
                onStop = vm::stop,
                onRegenerate = vm::regenerate,
                onApproveFallback = vm::approveFallback,
                onRejectFallback = vm::rejectFallback,
                onOpenControl = { vm.mode = Mode.KONTROL },
                onOpenModels = { vm.mode = Mode.MODELLER },
            )

            Mode.MODELLER -> ModelsScreen(
                models = vm.local.models,
 