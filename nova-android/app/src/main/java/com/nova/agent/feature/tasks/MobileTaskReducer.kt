package com.nova.agent.feature.tasks

import java.math.BigInteger

data class MobileTaskUiState(
    val prompt: String = "",
    val task: MobileTask? = null,
    val events: List<MobileTaskEvent> = emptyList(),
    val pendingConfirmation: MobileConfirmation? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

sealed interface MobileTaskMutation {
    data class PromptChanged(val value: String) : MobileTaskMutation
    data class TaskLoaded(val task: MobileTask) : MobileTaskMutation
    data class EventReceived(val event: MobileTaskEvent) : MobileTaskMutation
    data class Failed(val message: String) : MobileTaskMutation
    data object Loading : MobileTaskMutation
    data object ErrorCleared : MobileTaskMutation
}

fun reduceMobileTask(state: MobileTaskUiState, mutation: MobileTaskMutation): MobileTaskUiState = when (mutation) {
    is MobileTaskMutation.PromptChanged -> state.copy(prompt = mutation.value)
    is MobileTaskMutation.TaskLoaded -> state.copy(task = mutation.task, loading = false, error = null)
    is MobileTaskMutation.Failed -> state.copy(loading = false, error = mutation.message)
    MobileTaskMutation.Loading -> state.copy(loading = true)
    MobileTaskMutation.ErrorCleared -> state.copy(error = null)
    is MobileTaskMutation.EventReceived -> {
        if (state.events.any { it.id == mutation.event.id }) {
            state
        } else {
            val confirmation = when (mutation.event.type) {
                "confirmation.requested" -> mutation.event.confirmation
                "confirmation.approved", "confirmation.rejected" -> null
                else -> state.pendingConfirmation
            }
            val events = (state.events + mutation.event).sortedBy { BigInteger(it.id) }
            val updatedTask = state.task?.takeIf { it.id == mutation.event.taskId }?.let { current ->
                events.asReversed()
                    .firstOrNull { it.taskId == current.id && it.status != null }
                    ?.status
                    ?.let { status -> current.copy(status = status) }
                    ?: current
            }
            state.copy(
                task = updatedTask ?: state.task,
                events = events,
                pendingConfirmation = confirmation,
                loading = false,
            )
        }
    }
}
