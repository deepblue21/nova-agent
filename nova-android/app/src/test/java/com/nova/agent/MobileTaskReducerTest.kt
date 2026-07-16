package com.nova.agent

import com.nova.agent.feature.tasks.MobileConfirmation
import com.nova.agent.feature.tasks.MobileTask
import com.nova.agent.feature.tasks.MobileTaskEvent
import com.nova.agent.feature.tasks.MobileTaskMutation
import com.nova.agent.feature.tasks.MobileTaskStatus
import com.nova.agent.feature.tasks.MobileTaskUiState
import com.nova.agent.feature.tasks.canResolveConfirmation
import com.nova.agent.feature.tasks.reduceMobileTask
import com.nova.agent.feature.tasks.reduceMobileTaskResponse
import com.nova.agent.feature.tasks.userLabel
import com.nova.agent.feature.tasks.userSummary
import com.nova.agent.net.MobileTaskClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
                reduceMobileTask(
                    MobileTaskUiState(
                        task = MobileTask("task-1", "Open Settings", MobileTaskStatus.QUEUED),
                    ),
                    MobileTaskMutation.EventReceived(later),
                ),
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
    fun rejectsForeignTaskEventWithoutMutatingAnyActiveTaskState() {
        val activeConfirmation = MobileConfirmation("confirmation-a", "R2", "Current action")
        val state = MobileTaskUiState(
            task = MobileTask("task-1", "Open Settings", MobileTaskStatus.WAITING_FOR_CONFIRMATION),
            pendingConfirmation = activeConfirmation,
            loading = true,
            error = "current error",
        )
        val foreignEvent = MobileTaskEvent(
            id = "99",
            taskId = "task-2",
            type = "confirmation.requested",
            summary = "Foreign action",
            status = MobileTaskStatus.COMPLETED,
            confirmation = MobileConfirmation("confirmation-b", "R3", "Foreign action"),
        )

        val reduced = reduceMobileTask(state, MobileTaskMutation.EventReceived(foreignEvent))

        assertTrue(state === reduced)
        assertEquals(emptyList<MobileTaskEvent>(), reduced.events)
        assertEquals(activeConfirmation, reduced.pendingConfirmation)
        assertEquals(MobileTaskStatus.WAITING_FOR_CONFIRMATION, reduced.task?.status)
        assertTrue(reduced.loading)
        assertEquals("current error", reduced.error)
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

        val waiting = reduceMobileTask(
            MobileTaskUiState(
                task = MobileTask("task-1", "Open Settings", MobileTaskStatus.WAITING_FOR_CONFIRMATION),
            ),
            MobileTaskMutation.EventReceived(requested),
        )
        val approvedState = reduceMobileTask(waiting, MobileTaskMutation.EventReceived(approved))

        assertEquals(confirmation, waiting.pendingConfirmation)
        assertNull(approvedState.pendingConfirmation)
    }

    @Test
    fun keepsCancelledTaskAndRetainsConnectionErrorUntilCleared() {
        val cancelled = MobileTask("task-1", "Open Settings", MobileTaskStatus.CANCELLED)
        val failed = reduceMobileTask(
            MobileTaskUiState(task = MobileTask("task-1", "Open Settings", MobileTaskStatus.CANCELLED)),
            MobileTaskMutation.Failed("Bağlantı hatası"),
        )
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

    @Test
    fun derivesSafeUiSummariesFromContractWireFallbacks() {
        val confirmation = requireNotNull(
            MobileTaskClient.parseEvent(
                """{"id":"43","task_id":"task-1","type":"confirmation.requested","payload":{"confirmation_id":"confirmation-1","risk_level":"R2","status":"waiting_for_confirmation"}}""",
                null,
            ),
        )
        val queued = requireNotNull(
            MobileTaskClient.parseEvent(
                """{"id":"44","task_id":"task-1","type":"task.state","payload":{"status":"queued"}}""",
                null,
            ),
        )

        assertEquals("Ayarlar'ı aç", confirmation.userSummary("Ayarlar'ı aç"))
        assertEquals("Sıraya alındı", queued.userSummary("Ayarlar'ı aç"))
        assertEquals("waiting_for_confirmation", confirmation.summary)
        assertEquals("queued", queued.summary)
    }

    @Test
    fun blocksConfirmationDecisionsWhileARequestIsInFlight() {
        val confirmation = MobileConfirmation("confirmation-1", "R2", "waiting_for_confirmation")
        val ready = MobileTaskUiState(
            task = MobileTask("task-1", "Ayarlar'ı aç", MobileTaskStatus.WAITING_FOR_CONFIRMATION),
            pendingConfirmation = confirmation,
        )

        assertTrue(ready.canResolveConfirmation)
        assertFalse(ready.copy(loading = true).canResolveConfirmation)
        assertFalse(ready.copy(task = null).canResolveConfirmation)
        assertFalse(ready.copy(pendingConfirmation = null).canResolveConfirmation)
    }

    @Test
    fun successfulDecisionResponsePreventsSecondResolutionWithoutSse() {
        val confirmation = MobileConfirmation("confirmation-1", "R2", "waiting_for_confirmation")
        val waiting = MobileTaskUiState(
            task = MobileTask("task-1", "Ayarlar'ı aç", MobileTaskStatus.WAITING_FOR_CONFIRMATION),
            pendingConfirmation = confirmation,
        )
        val loading = reduceMobileTask(waiting, MobileTaskMutation.Loading)
        val response = MobileTask("task-1", "Ayarlar'ı aç", MobileTaskStatus.EXECUTING)
        val resolved = reduceMobileTask(
            loading,
            MobileTaskMutation.ConfirmationResolved(response, confirmation.id),
        )

        assertEquals(response, resolved.task)
        assertFalse(resolved.loading)
        assertNull(resolved.pendingConfirmation)
        assertFalse(resolved.canResolveConfirmation)

        val delayedSse = reduceMobileTask(
            resolved,
            MobileTaskMutation.EventReceived(event("50", "confirmation.approved", "executing")),
        )
        assertNull(delayedSse.pendingConfirmation)
        assertFalse(delayedSse.canResolveConfirmation)
    }

    @Test
    fun olderConfirmationResponseDoesNotClearOrOverwriteNewerConfirmationEvent() {
        val confirmationA = MobileConfirmation("confirmation-a", "R2", "First action")
        val confirmationB = MobileConfirmation("confirmation-b", "R2", "Second action")
        val waitingForA = MobileTaskUiState(
            task = MobileTask("task-1", "Ayarlar'ı aç", MobileTaskStatus.WAITING_FOR_CONFIRMATION),
            pendingConfirmation = confirmationA,
        )
        val resolvingA = reduceMobileTask(waitingForA, MobileTaskMutation.Loading)
        val waitingForB = reduceMobileTask(
            resolvingA,
            MobileTaskMutation.EventReceived(
                MobileTaskEvent(
                    id = "51",
                    taskId = "task-1",
                    type = "confirmation.requested",
                    summary = "Second action",
                    status = MobileTaskStatus.WAITING_FOR_CONFIRMATION,
                    confirmation = confirmationB,
                ),
            ),
        )
        val responseForA = MobileTask("task-1", "Ayarlar'ı aç", MobileTaskStatus.EXECUTING)

        val afterDelayedResponse = reduceMobileTask(
            waitingForB,
            MobileTaskMutation.ConfirmationResolved(responseForA, confirmationA.id),
        )

        assertEquals(confirmationB, afterDelayedResponse.pendingConfirmation)
        assertEquals(waitingForB.task, afterDelayedResponse.task)
        assertFalse(afterDelayedResponse.loading)
        assertNull(reduceMobileTaskResponse(waitingForB, responseForA, confirmationA.id))
    }

    @Test
    fun commandResponseIsRejectedAfterANewerSameTaskEventWasAccepted() {
        val active = MobileTaskUiState(
            task = MobileTask("task-1", "Open Settings", MobileTaskStatus.EXECUTING),
        )
        val eventRevisionAtCommandStart = active.events.count { it.taskId == "task-1" }
        val loading = reduceMobileTask(active, MobileTaskMutation.Loading)
        val afterNewerEvent = reduceMobileTask(
            loading,
            MobileTaskMutation.EventReceived(
                MobileTaskEvent(
                    id = "70",
                    taskId = "task-1",
                    type = "worker.completed",
                    summary = "Android 17",
                    status = MobileTaskStatus.COMPLETED,
                ),
            ),
        )
        val olderHttpSnapshot = MobileTask("task-1", "Open Settings", MobileTaskStatus.PAUSED)

        val responseState = responseAtRevision(
            afterNewerEvent,
            olderHttpSnapshot,
            confirmationId = null,
            expectedEventRevision = eventRevisionAtCommandStart,
        )

        assertNull(responseState)
        assertEquals(MobileTaskStatus.COMPLETED, afterNewerEvent.task?.status)
    }

    @Test
    fun confirmationResponseIsRejectedAfterANewerSameTaskEventWasAccepted() {
        val confirmation = MobileConfirmation("confirmation-a", "R2", "Current action")
        val waiting = MobileTaskUiState(
            task = MobileTask("task-1", "Open Settings", MobileTaskStatus.WAITING_FOR_CONFIRMATION),
            pendingConfirmation = confirmation,
        )
        val eventRevisionAtDecisionStart = waiting.events.count { it.taskId == "task-1" }
        val loading = reduceMobileTask(waiting, MobileTaskMutation.Loading)
        val afterNewerEvent = reduceMobileTask(
            loading,
            MobileTaskMutation.EventReceived(
                MobileTaskEvent(
                    id = "71",
                    taskId = "task-1",
                    type = "worker.progress",
                    summary = "Still verifying",
                    status = MobileTaskStatus.VERIFYING,
                ),
            ),
        )
        val olderHttpSnapshot = MobileTask("task-1", "Open Settings", MobileTaskStatus.EXECUTING)

        val responseState = responseAtRevision(
            afterNewerEvent,
            olderHttpSnapshot,
            confirmationId = confirmation.id,
            expectedEventRevision = eventRevisionAtDecisionStart,
        )

        assertNull(responseState)
        assertEquals(MobileTaskStatus.VERIFYING, afterNewerEvent.task?.status)
        assertEquals(confirmation, afterNewerEvent.pendingConfirmation)
    }

    private fun responseAtRevision(
        state: MobileTaskUiState,
        task: MobileTask,
        confirmationId: String?,
        expectedEventRevision: Int,
    ): MobileTaskUiState? {
        return reduceMobileTaskResponse(
            state,
            task,
            confirmationId,
            expectedEventRevision,
        )
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
