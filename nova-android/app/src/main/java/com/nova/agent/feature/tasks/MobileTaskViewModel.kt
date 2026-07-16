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
import okhttp3.Call
import okhttp3.sse.EventSource

private class MobileTaskRequestConnection(
    val baseUrl: String,
    val token: String,
)

private class MobileTaskRequestContext(
    val generation: Long,
    val connection: MobileTaskRequestConnection,
)

internal data class MobileTaskRequestToken(
    val generation: Long,
    val taskId: String?,
)

internal class MobileTaskCallRegistry {
    private class Entry(
        var call: Call? = null,
        var completed: Boolean = false,
    )

    private var nextId = 0L
    private val entries = mutableMapOf<Long, Entry>()

    @Synchronized
    fun start(): Long {
        val id = ++nextId
        entries[id] = Entry()
        return id
    }

    fun attach(id: Long, call: Call) {
        val cancel = synchronized(this) {
            val entry = entries[id]
            when {
                entry == null -> true
                entry.completed -> {
                    entries.remove(id)
                    false
                }
                else -> {
                    entry.call = call
                    false
                }
            }
        }
        if (cancel) call.cancel()
    }

    @Synchronized
    fun complete(id: Long) {
        val entry = entries[id] ?: return
        if (entry.call == null) {
            entry.completed = true
        } else {
            entries.remove(id)
        }
    }

    @Synchronized
    fun removeAll(): List<Call> = entries.values
        .mapNotNull { it.call }
        .also { entries.clear() }
}

internal class MobileTaskRequestSettings(
    initial: AppSettings = AppSettings(),
    private val client: MobileTaskClient = MobileTaskClient(),
) {
    private var connectionGeneration = 0L
    private var connection = initial.toRequestConnection()
    private var taskGeneration = 0L
    private var taskConnection: MobileTaskRequestConnection? = null
    private var activeTaskId: String? = null
    private val taskCalls = MobileTaskCallRegistry()

    @Synchronized
    fun beginStoreLoad(): Long = connectionGeneration

    @Synchronized
    fun applyStoreLoad(loadGeneration: Long, loaded: AppSettings) {
        if (loadGeneration == connectionGeneration) connection = loaded.toRequestConnection()
    }

    @Synchronized
    fun updateConnectionSettings(baseUrl: String, token: String) {
        connectionGeneration += 1
        connection = MobileTaskRequestConnection(baseUrl.trim(), token.trim())
    }

    fun createTask(prompt: String, callback: (Result<MobileTask>) -> Unit) {
        createTask(prompt) { _, result -> callback(result) }
    }

    fun createTask(
        prompt: String,
        callback: (MobileTaskRequestToken, Result<MobileTask>) -> Unit,
    ) {
        val (requestGeneration, request, operation, staleCalls) = synchronized(this) {
            taskGeneration += 1
            val stale = taskCalls.removeAll()
            val snapshot = connection.also {
                taskConnection = it
                activeTaskId = null
            }
            RequestStart(taskGeneration, snapshot, taskCalls.start(), stale)
        }
        staleCalls.forEach(Call::cancel)
        val call = client.createTask(request.baseUrl, request.token, prompt) { result ->
            try {
                val token = synchronized(this) {
                    if (requestGeneration != taskGeneration || taskConnection !== request) {
                        null
                    } else {
                        val taskId = result.getOrNull()?.id
                        if (taskId != null) activeTaskId = taskId
                        MobileTaskRequestToken(requestGeneration, taskId)
                    }
                }
                if (token != null) callback(token, result)
            } finally {
                taskCalls.complete(operation)
            }
        }
        taskCalls.attach(operation, call)
    }

    fun command(
        taskId: String,
        command: String,
        callback: (Result<MobileTask>) -> Unit,
    ) {
        command(taskId, command) { _, result -> callback(result) }
    }

    fun command(
        taskId: String,
        command: String,
        callback: (MobileTaskRequestToken, Result<MobileTask>) -> Unit,
    ) {
        val (request, operation) = startRequest(taskId)
        val call = client.command(
            request.connection.baseUrl,
            request.connection.token,
            taskId,
            command,
        ) { result ->
            try {
                if (accepts(request, taskId)) {
                    callback(
                        MobileTaskRequestToken(request.generation, taskId),
                        validateTaskId(taskId, result),
                    )
                }
            } finally {
                taskCalls.complete(operation)
            }
        }
        taskCalls.attach(operation, call)
    }

    fun resolveConfirmation(
        taskId: String,
        confirmationId: String,
        decision: String,
        callback: (Result<MobileTask>) -> Unit,
    ) {
        resolveConfirmation(taskId, confirmationId, decision) { _, result -> callback(result) }
    }

    fun resolveConfirmation(
        taskId: String,
        confirmationId: String,
        decision: String,
        callback: (MobileTaskRequestToken, Result<MobileTask>) -> Unit,
    ) {
        val (request, operation) = startRequest(taskId)
        val call = client.resolveConfirmation(
            request.connection.baseUrl,
            request.connection.token,
            taskId,
            confirmationId,
            decision,
        ) { result ->
            try {
                if (accepts(request, taskId)) {
                    callback(
                        MobileTaskRequestToken(request.generation, taskId),
                        validateTaskId(taskId, result),
                    )
                }
            } finally {
                taskCalls.complete(operation)
            }
        }
        taskCalls.attach(operation, call)
    }

    fun streamEvents(
        taskId: String,
        lastEventId: String?,
        callbacks: MobileTaskClient.EventCallbacks,
    ): EventSource {
        val request = requestFor(taskId)
        return client.streamEvents(
            request.connection.baseUrl,
            request.connection.token,
            taskId,
            lastEventId,
            callbacks,
        )
    }

    private fun AppSettings.toRequestConnection() = MobileTaskRequestConnection(
        baseUrl = baseUrl.trim(),
        token = token.trim(),
    )

    @Synchronized
    private fun requestFor(taskId: String): MobileTaskRequestContext {
        val request = requireNotNull(taskConnection?.takeIf { activeTaskId == taskId }) {
            "Mobile task session is no longer active"
        }
        return MobileTaskRequestContext(taskGeneration, request)
    }

    @Synchronized
    private fun startRequest(taskId: String): Pair<MobileTaskRequestContext, Long> =
        requestFor(taskId) to taskCalls.start()

    @Synchronized
    fun isCurrent(token: MobileTaskRequestToken): Boolean =
        token.generation == taskGeneration &&
            (token.taskId == null || token.taskId == activeTaskId)

    fun reset() {
        val calls = synchronized(this) {
            taskGeneration += 1
            taskConnection = null
            activeTaskId = null
            taskCalls.removeAll()
        }
        calls.forEach(Call::cancel)
    }

    @Synchronized
    private fun accepts(
        request: MobileTaskRequestContext,
        taskId: String,
    ): Boolean =
        request.generation == taskGeneration &&
            activeTaskId == taskId &&
            taskConnection === request.connection

    private fun validateTaskId(
        expectedTaskId: String,
        result: Result<MobileTask>,
    ): Result<MobileTask> {
        val task = result.getOrNull()
        return if (task != null && task.id != expectedTaskId) {
            Result.failure(IllegalStateException(MobileTaskClient.userMessageForHttp(409)))
        } else {
            result
        }
    }

    private data class RequestStart(
        val generation: Long,
        val connection: MobileTaskRequestConnection,
        val operation: Long,
        val staleCalls: List<Call>,
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
        requestSettings.reset()
        update(MobileTaskMutation.Reset)
    }

    fun createTask() {
        val prompt = state.prompt.trim()
        if (prompt.isEmpty() || state.loading) return

        disconnect()
        update(MobileTaskMutation.Loading)
        requestSettings.createTask(prompt) { request, result ->
            onMain {
                if (!requestSettings.isCurrent(request)) return@onMain
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
        val eventRevision = state.eventRevision(task.id)
        update(MobileTaskMutation.Loading)
        requestSettings.command(task.id, value) { request, result ->
            onMain {
                if (requestSettings.isCurrent(request)) {
                    acceptTaskResult(result, task.id, eventRevision)
                }
            }
        }
    }

    private fun decide(decision: String) {
        if (!state.canResolveConfirmation) return
        val task = state.task ?: return
        val confirmation = state.pendingConfirmation ?: return
        val eventRevision = state.eventRevision(task.id)
        update(MobileTaskMutation.Loading)
        requestSettings.resolveConfirmation(
            task.id,
            confirmation.id,
            decision,
        ) { request, result ->
            onMain {
                if (requestSettings.isCurrent(request)) {
                    acceptTaskResult(
                        result,
                        task.id,
                        eventRevision,
                        confirmationId = confirmation.id,
                    )
                }
            }
        }
    }

    private fun acceptTaskResult(
        result: Result<MobileTask>,
        taskId: String,
        expectedEventRevision: Int,
        confirmationId: String? = null,
    ) {
        if (
            state.task?.id != taskId ||
            state.eventRevision(taskId) != expectedEventRevision
        ) {
            return
        }
        result.fold(
            onSuccess = { task ->
                state = reduceMobileTaskResponse(
                    state,
                    task,
                    confirmationId,
                    expectedEventRevision,
                ) ?: return@fold
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
                    if (
                        generation == streamGeneration &&
                        state.acceptsEvent(event, taskId)
                    ) {
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
        requestSettings.reset()
        super.onCleared()
    }
}
