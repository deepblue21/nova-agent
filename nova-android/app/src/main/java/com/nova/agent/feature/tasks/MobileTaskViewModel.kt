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

private class MobileTaskRequestConnection(
    val baseUrl: String,
    val token: String,
)

internal class MobileTaskRequestSettings(
    initial: AppSettings = AppSettings(),
    private val client: MobileTaskClient = MobileTaskClient(),
) {
    private var generation = 0L
    private var connection = initial.toRequestConnection()

    @Synchronized
    fun beginStoreLoad(): Long = generation

    @Synchronized
    fun applyStoreLoad(loadGeneration: Long, loaded: AppSettings) {
        if (loadGeneration == generation) connection = loaded.toRequestConnection()
    }

    @Synchronized
    fun updateConnectionSettings(baseUrl: String, token: String) {
        generation += 1
        connection = MobileTaskRequestConnection(baseUrl.trim(), token.trim())
    }

    @Synchronized
    private fun current(): MobileTaskRequestConnection = connection

    fun createTask(prompt: String, callback: (Result<MobileTask>) -> Unit) {
        val request = current()
        client.createTask(request.baseUrl, request.token, prompt, callback)
    }

    fun command(
        taskId: String,
        command: String,
        callback: (Result<MobileTask>) -> Unit,
    ) {
        val request = current()
        client.command(request.baseUrl, request.token, taskId, command, callback = callback)
    }

    fun resolveConfirmation(
        taskId: String,
        confirmationId: String,
        decision: String,
        callback: (Result<MobileTask>) -> Unit,
    ) {
        val request = current()
        client.resolveConfirmation(
            request.baseUrl,
            request.token,
            taskId,
            confirmationId,
            decision,
            callback,
        )
    }

    fun streamEvents(
        taskId: String,
        lastEventId: String?,
        callbacks: MobileTaskClient.EventCallbacks,
    ): EventSource {
        val request = current()
        return client.streamEvents(
            request.baseUrl,
            request.token,
            taskId,
            lastEventId,
            callbacks,
        )
    }

    private fun AppSettings.toRequestConnection() = MobileTaskRequestConnection(
        baseUrl = baseUrl.trim(),
        token = token.trim(),
    )
}

class MobileTaskViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SettingsStore(app)
    private val main = Handler(Looper.getMainLooper())

    private val requestSettings = MobileTaskRequestSettings()
    private val initialSettingsLoad = requestSettings.beginStoreLoad()
    private var eventSource: EventSource? = null
    private var reconnectJob: Job? = null
    private var reconnectAttempt = 0
    private var streamGeneration = 0L

    var state by mutableStateOf(MobileTaskUiState())
        private set

    init {
        viewModelScope.launch {
            requestSettings.applyStoreLoad(initialSettingsLoad, store.load())
        }
    }

    fun updateConnectionSettings(baseUrl: String, token: String) {
        requestSettings.updateConnectionSettings(baseUrl, token)
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
        requestSettings.createTask(prompt) { result ->
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
        requestSettings.command(task.id, value) { result ->
            onMain { acceptTaskResult(result) }
        }
    }

    private fun decide(decision: String) {
        if (!state.canResolveConfirmation) return
        val task = state.task ?: return
        val confirmation = state.pendingConfirmation ?: return
        update(MobileTaskMutation.Loading)
        requestSettings.resolveConfirmation(
            task.id,
            confirmation.id,
            decision,
        ) { result ->
            onMain { acceptTaskResult(result, confirmationResolved = true) }
        }
    }

    private fun acceptTaskResult(
        result: Result<MobileTask>,
        confirmationResolved: Boolean = false,
    ) {
        result.fold(
            onSuccess = { task ->
                update(
                    if (confirmationResolved) {
                        MobileTaskMutation.ConfirmationResolved(task)
                    } else {
                        MobileTaskMutation.TaskLoaded(task)
                    },
                )
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
        eventSource = requestSettings.streamEvents(
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
