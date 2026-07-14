package com.nova.agent.ui.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.nova.agent.NovaViewModel
import com.nova.agent.data.Mode
import com.nova.agent.feature.chat.ChatScreen
import com.nova.agent.feature.settings.SettingsPanel
import com.nova.agent.feature.tasks.MobileTaskScreen
import com.nova.agent.feature.tasks.MobileTaskViewModel
import com.nova.agent.feature.voice.VoiceScreen

@Composable
fun NovaApp(
    vm: NovaViewModel,
    taskVm: MobileTaskViewModel,
    onRequestMic: () -> Unit,
) {
    var showSettings by rememberSaveable { mutableStateOf(false) }

    NovaAppShell(
        mode = vm.mode,
        connection = vm.connectionState,
        onModeChange = { vm.mode = it },
        onSettings = { showSettings = true },
        onNewChat = vm::newChat,
    ) {
        when (vm.mode) {
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
                onSend = vm::send,
                onStop = vm::stop,
                onRegenerate = vm::regenerate,
            )

            Mode.VOICE -> VoiceScreen(
                state = vm.voiceState,
                subtitle = vm.voiceSub,
                level = vm.level,
                onStart = onRequestMic,
                onStop = vm::stopListeningOrSpeaking,
            )
        }
    }

    if (showSettings) {
        SettingsPanel(
            settings = vm.settings,
            connection = vm.connectionState,
            onTestConnection = vm::testConnection,
            onSaveConnection = vm::saveConnection,
            onModelChange = vm::setModel,
            onEffortChange = vm::setEffort,
            onReasoningChange = vm::setReasoning,
            onClose = { showSettings = false },
        )
    }
}
