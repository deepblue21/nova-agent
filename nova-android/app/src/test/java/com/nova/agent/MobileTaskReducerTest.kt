package com.nova.agent

import com.nova.agent.feature.tasks.MobileConfirmation
import com.nova.agent.feature.tasks.MobileTask
import com.nova.agent.feature.tasks.MobileTaskEvent
import com.nova.agent.feature.tasks.MobileTaskMutation
import com.nova.agent.feature.tasks.MobileTaskStatus
import com.nova.agent.feature.tasks.MobileTaskUiState
import com.nova.agent.feature.tasks.reduceMobileTask
import com.nova.agent.feature.tasks.userLabel
import com.nova.agent.net.MobileTaskClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MobileTaskReducerTest {

    @Test
    fun startsEmptyAndKeepsPromptChanges() {
        val state = reduceMobileTask(MobileTaskUiState(), MobileTaskMutation.PromptChanged("Open Settings"))

        assertEquals("Open Settings", state.prompt)
        assertNull(state.task)
        assertEquals(emptyList<MobileTaskEvent>(), state.events)
    }

    @Test
    fun loadsCreatedTaskAndStopsLoading() {
        val task = MobileTask("task-1", "Open Settings", MobileTaskStatus.QUEUED)
        val state = reduceMobileTask(
            MobileTaskUiState(loading = true, error = "old error"),
            MobileTaskMutation.TaskLoaded(task),
        )

        assertEquals(task, state.task)
        assertEquals(false, state.loading)
        assertNull(state.error)
    }

    @Test
    fun ordersEventsNumericallyAndDropsDuplicateIds() {
        val later = event("10", "task.state", "executing")
        val earlier = event("2", "task.state", "queued")
        val state = reduceMobileTask(
            reduceMobileTask(
                reduceMobileTask(MobileTaskUiState(), MobileTaskMutation.EventReceived(later)),
                MobileTaskMutation.EventReceived(earlier),
            ),
            MobileTaskMutation.EventReceived(event("2", "task.state", "paused")),
        )

        assertEquals(listOf("2", "10"), state.events.map { it.id })
        assertEquals("queued", state.events.first().summary)
    }

    @Test
    fun updatesOnlyTheMatchingTaskFromAWorkerStatusEvent() {
        val event = MobileTaskEvent(
            id = "2",
            taskId = "task-1",
            type = "worker.completed",
            summary = "Android 17",
            status = MobileTaskStatus.COMPLETED,
        )
        val matchingState = reduceMobileTask(
            MobileTaskUiState(task = MobileTask("task-1", "Open Settings", MobileTaskStatus.EXECUTING), loading = true),
            MobileTaskMutation.EventReceived(event),
        )
        val otherTaskState = reduceMobileTask(
            MobileTaskUiState(task = MobileTask("task-2", "Open Settings", MobileTaskStatus.EXECUTING)),
            MobileTaskMutation.EventReceived(event),
        )

        assertEquals(MobileTaskStatus.COMPLETED, matchingState.task?.status)
        assertEquals("Android 17", matchingState.events.single().summary)
        assertEquals(false, matchingState.loading)
        assertEquals(MobileTaskStatus.EXECUTING, otherTaskState.task?.status)
    }

    @Test
    fun replaysPersistedCompletedWorkerEventWithSanitizedSummary() {
        val event = MobileTaskClient.parseEvent(
            """{"id":"44","task_id":"task-1","type":"worker.completed","payload":{"status":"completed","summary":"Android 17","steps":3,"error_code":"execution_failed"}}""",
            null,
        )
        val state = reduceMobileTask(
            MobileTaskUiState(task = MobileTask("task-1", "Open Settings", MobileTaskStatus.EXECUTING)),
            MobileTaskMutation.EventReceived(requireNotNull(event)),
        )

        assertEquals(MobileTaskStatus.COMPLETED, state.task?.status)
        assertEquals("Android 17", state.events.single().summary)
    }

    @Test
    fun doesNotRegressCompletedTaskWhenOlderRunningEventArrivesLate() {
        val completed = MobileTaskEvent(
            id = "10",
            taskId = "task-1",
            type = "worker.completed",
            summary = "Android 17",
            status = MobileTaskStatus.COMPLETED,
        )
        val lateRunning = MobileTaskEvent(
            id = "2",
            taskId = "task-1",
            type = "worker.running",
            summary = "executing",
            status = MobileTaskStatus.EXECUTING,
        )
        val state = reduceMobileTask(
            reduceMobileTask(
                MobileTaskUiState(task = MobileTask("task-1", "Open Settings", MobileTaskStatus.EXECUTING)),
                MobileTaskMutation.EventReceived(completed),
            ),
            MobileTaskMutation.EventReceived(lateRunning),
        )

        assertEquals(MobileTaskStatus.COMPLETED, state.task?.status)
        assertEquals(listOf("2", "10"), state.events.map { it.id })
    }

    @Test
    fun showsPendingConfirmationThenClearsItAfterApproval() {
        val confirmation = MobileConfirmation("confirmation-1", "R2", "Turn Wi-Fi off")
        val requested = event("2", "confirmation.requested", "Turn Wi-Fi off", confirmation)
        val approved = event("3", "confirmation.approved", "executing")

        val waiting = reduceMobileTask(MobileTaskUiState(), MobileTaskMutation.EventReceived(requested))
        val approvedState = reduceMobileTask(waiting, MobileTaskMutation.EventReceived(approved))

        assertEquals(confirmation, waiting.pendingConfirmation)
        assertNull(approvedState.pendingConfirmation)
    }

    @Test
    fun keepsCancelledTaskAndRetainsConnectionErrorUntilCleared() {
        val cancelled = MobileTask("task-1", "Open Settings", MobileTaskStatus.CANCELLED)
        val failed = reduceMobileTask(MobileTaskUiState(), MobileTaskMutation.Failed("Bağlantı hatası"))
        val withEvent = reduceMobileTask(failed, MobileTaskMutation.EventReceived(event("1", "task.cancel", "cancelled")))
        val complete = reduceMobileTask(withEvent, MobileTaskMutation.TaskLoaded(cancelled))

        assertEquals("Bağlantı hatası", withEvent.error)
        assertEquals(MobileTaskStatus.CANCELLED, complete.task?.status)
        assertNull(reduceMobileTask(complete, MobileTaskMutation.ErrorCleared).error)
    }

    @Test
    fun exposesTurkishLabelsWithoutChangingWireStatus() {
        assertEquals("Sıraya alındı", MobileTaskStatus.QUEUED.userLabel)
        assertEquals("Eylem uygulanıyor", MobileTaskStatus.EXECUTING.userLabel)
        assertEquals("Tamamlandı", MobileTaskStatus.COMPLETED.userLabel)
        assertEquals("worker.completed", event("1", "worker.completed", "Android 17").type)
    }

    @Test
    fun resetsTaskFlowToItsEmptyState() {
        val active = MobileTaskUiState(
            prompt = "Ayarlar'ı aç",
            task = MobileTask("task-1", "Ayarlar'ı aç", MobileTaskStatus.COMPLETED),
            events = listOf(event("1", "worker.completed", "Tamamlandı")),
            loading = true,
            error = "Eski hata",
        )

        assertEquals(MobileTaskUiState(), reduceMobileTask(active, MobileTaskMutation.Reset))
    }

    private fun event(
        id: String,
        type: String,
        summary: String,
        confirmation: MobileConfirmation? = null,
    ) = MobileTaskEvent(
        id = id,
        taskId = "task-1",
        type = type,
        summary = summary,
        confirmation = confirmation,
    )
}
