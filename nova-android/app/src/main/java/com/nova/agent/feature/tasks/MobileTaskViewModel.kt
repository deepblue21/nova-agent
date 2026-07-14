package com.nova.agent.feature.tasks

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nova.agent.data.AppSettings
import com.nova.agent.data.SettingsStore
import com.nova.agent.net.MobileTaskClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.sse.EventSource

class MobileTaskViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SettingsStore(app)
    private val client = MobileTaskClient()
    private val main = Handler(Looper.getMainLooper())

    private var settings = AppSettings()
    private var eventSource: EventSource? = null
    private var reconnectJob: Job? = null
    private var reconnectAttempt = 0
    private var streamGeneration = 0L

    var state by mutableStateOf(MobileTaskUiState())
        private set

    init {
        viewModelScope.launch { settings = store.load() }
    }

    fun updatePrompt(value: String) = update(MobileTaskMutation.PromptChanged(value))

    fun clearError() = update(MobileTaskMutation.ErrorCleared)

    fun newTask() {
        disconnect()
        update(MobileTaskMutation.Reset)
    }

    fun createTask() {
        val prompt = state.prompt.trim()
        if (prompt.isEmpty() || state.loading) return

        disconnect()
        update(MobileTaskMutation.Loading)
        client.createTask(settings.baseUrl, settings.token, prompt) { result ->
            onMain {
                result.fold(
                    onSuccess = { task ->
                        update(MobileTaskMutation.TaskLoaded(task))
                        state = state.copy(prompt = "")
                        connect(task.id)
                    },
                    onFailure = ::showFailure,
                )
            }
        }
    }

    fun pause() = command("pause")

    fun resume() = command("resume")

    fun cancel() = command("cancel")

    fun approve() = decide("approve")

    fun reject() = decide("reject")

    private fun command(value: String) {
        val task = state.task ?: return
        update(MobileTaskMutation.Loading)
        client.command(settings.baseUrl, settings.token, task.id, value) { result ->
            onMain { acceptTaskResult(result) }
        }
    }

    private fun decide(decision: String) {
        val task = state.task ?: return
        val confirmation = state.pendingConfirmation ?: return
        update(MobileTaskMutation.Loading)
        client.resolveConfirmation(
            settings.baseUrl,
            settings.token,
            task.id,
            confirmation.id,
            decision,
        ) { result ->
            onMain { acceptTaskResult(result) }
        }
    }

    private fun acceptTaskResult(result: Result<MobileTask>) {
        result.fold(
            onSuccess = { task ->
                update(MobileTaskMutation.TaskLoaded(task))
                if (isTerminal(task)) disconnect() else connect(task.id)
            },
            onFailure = ::showFailure,
        )
    }

    private fun connect(taskId: String) {
        val task = state.task ?: return
        if (task.id != taskId || isTerminal(task)) return

        stopStream()
        val generation = streamGeneration
        val lastEventId = state.events.lastOrNull()?.id
        eventSource = client.streamEvents(
            settings.baseUrl,
            settings.token,
            taskId,
            lastEventId,
            object : MobileTaskClient.EventCallbacks {
                override fun onEvent(event: MobileTaskEvent) = onMain {
                    if (generation == streamGeneration) {
                        reconnectAttempt = 0
                        update(MobileTaskMutation.EventReceived(event))
                    }
                }

                override fun onClosed() = onMain {
                    if (generation == streamGeneration) eventSource = null
                }

                override fun onError(message: String, recoverable: Boolean) = onMain {
                    if (generation == streamGeneration) {
                        update(MobileTaskMutation.Failed(message))
                        if (recoverable) scheduleReconnect() else stopStream()
                    }
                }
            },
        )
    }

    private fun scheduleReconnect() {
        val task = state.task ?: return
        if (isTerminal(task)) return

        stopStream()
        reconnectJob?.cancel()
        val delayMs = minOf(1_000L shl reconnectAttempt.coerceAtMost(4), 10_000L)
        reconnectAttempt += 1
        reconnectJob = viewModelScope.launch {
            delay(delayMs)
            onMain {
                state.task?.takeUnless(::isTerminal)?.let { connect(it.id) }
            }
        }
    }

    private fun showFailure(error: Throwable) {
        update(MobileTaskMutation.Failed(error.message ?: "Görev isteği başarısız oldu"))
    }

    private fun update(mutation: MobileTaskMutation) {
        state = reduceMobileTask(state, mutation)
    }

    private fun stopStream() {
        streamGeneration += 1
        eventSource?.cancel()
        eventSource = null
    }

    private fun disconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
        stopStream()
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    private fun isTerminal(task: MobileTask): Boolean = task.status in setOf(
        MobileTaskStatus.COMPLETED,
        MobileTaskStatus.FAILED,
        MobileTaskStatus.CANCELLED,
    )

    override fun onCleared() {
        disconnect()
        super.onCleared()
    }
}
