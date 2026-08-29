package com.nova.agent

import com.nova.agent.feature.tasks.MobileConfirmation
import com.nova.agent.feature.tasks.MobileTask
import com.nova.agent.feature.tasks.MobileTaskEvent
import com.nova.agent.feature.tasks.MobileTaskMutation
import com.nova.agent.feature.tasks.MobileTaskStatus
import com.nova.agent.feature.tasks.MobileTaskUiState
import com.nova.agent.feature.tasks.canResolveConfirmation
import com.nova.agent.feature.tasks.orderEvents
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

        // BU SATIR T4 İLE DEĞİŞTİ. Eskiden burada `assertNull` vardı: gecikmiş
        // yanıt DÜŞÜYORDU. Ama düşme nedeni bir koruma değil, T4 hatasının
        // kendisiydi — B, A'yı ekrandan ezdiği için onay kimliği tutmuyor ve
        // mutasyon geçersiz sayılıyordu. T4 kapanınca (A ekranda kalır, B
        // kuyruğa girer) yanıt artık GEÇERLİ: A gerçekten çözüldü, sıradaki B
        // panele gelir. Testin asıl koruduğu şey duruyor ve üstteki üç iddiayla
        // ölçülüyor: A'nın eski durumu (EXECUTING) B'nin panelinin üstüne
        // yazılmıyor.
        assertEquals(
            afterDelayedResponse,
            reduceMobileTaskResponse(waitingForB, responseForA, confirmationA.id),
        )
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

    // ---------- T1: olay kimligi sozlesmesi ----------

    private fun ev(id: String, status: MobileTaskStatus? = null, type: String = "worker.log") =
        MobileTaskEvent(id = id, taskId = "t1", type = type, summary = "s", status = status)

    /**
     * Asil regresyon: ayristirici kimligi serbest metin kabul ediyor (bkz.
     * MobileTaskClientTest), reducer ise `BigInteger(it.id)` diyordu. Gateway
     * ULID uretmeye baslasa ilk olayda ana thread'de cokerdi.
     */
    @Test
    fun `sayisal olmayan olay kimligi cokertmez ve gelis sirasi korunur`() {
        val gelen = listOf(ev("evt_01H8XYZ"), ev("evt_01H8ABC"), ev("evt_01H8DEF"))

        val sirali = orderEvents(gelen)

        assertEquals(gelen.map { it.id }, sirali.map { it.id })
    }

    @Test
    fun `tamami sayisal kimlikler sayisal siraya girer`() {
        val sirali = orderEvents(listOf(ev("10"), ev("2"), ev("44")))

        assertEquals(listOf("2", "10", "44"), sirali.map { it.id })
    }

    @Test
    fun `float gibi gelen kimlik de cokertmez`() {
        // JSON'da sayi float gelirse toString "1.0" verir; BigInteger("1.0") atardi.
        val sirali = orderEvents(listOf(ev("1.0"), ev("2")))

        assertEquals(listOf("1.0", "2"), sirali.map { it.id })
    }

    @Test
    fun `karisik kimlikte siralama yapilmaz`() {
        // Sayisal olmayan varsa "dogru" bir sayisal sira yoktur; uydurmuyoruz.
        val gelen = listOf(ev("10"), ev("evt_x"), ev("2"))

        assertEquals(gelen.map { it.id }, orderEvents(gelen).map { it.id })
    }

    @Test
    fun `sayisal olmayan kimlikli olay reducer uzerinden gecebilir`() {
        val basla = reduceMobileTaskResponse(
            MobileTaskUiState(task = MobileTask("t1", "p", MobileTaskStatus.EXECUTING)),
            MobileTask("t1", "p", MobileTaskStatus.EXECUTING),
            confirmationId = null,
        ) ?: MobileTaskUiState(task = MobileTask("t1", "p", MobileTaskStatus.EXECUTING))

        val sonra = reduceMobileTask(basla, MobileTaskMutation.EventReceived(ev("evt_01H8XYZ")))

        assertEquals(1, sonra.events.size)
    }

    // ---------- T2: terminal gorev bekleyen onayi temizler ----------

    /**
     * Kilit senaryosu: onay istendi, kullanici karar vermeden worker zaman
     * asimina dusup `failed` yayinladi. Bekleyen onay temizlenmezse modal
     * karartma katmani tum dokunuslari yutuyor, "Yeni gorev" ulasilamiyor ve
     * gorev terminal oldugu icin kurtarici bir olay da gelemiyordu -- cikis yok.
     */
    @Test
    fun `basarisiz gorev bekleyen onayi temizler`() {
        val onayli = MobileTaskUiState(
            task = MobileTask("t1", "p", MobileTaskStatus.WAITING_FOR_CONFIRMATION),
            pendingConfirmation = MobileConfirmation("c1", "R3", "Ayarlar'i ac"),
        )

        val sonra = reduceMobileTask(
            onayli,
            MobileTaskMutation.EventReceived(
                ev("1", status = MobileTaskStatus.FAILED, type = "task.state"),
            ),
        )

        assertNull("terminal gorevde bekleyen onay kalmamali", sonra.pendingConfirmation)
    }

    @Test
    fun `iptal edilen gorev de bekleyen onayi temizler`() {
        val onayli = MobileTaskUiState(
            task = MobileTask("t1", "p", MobileTaskStatus.WAITING_FOR_CONFIRMATION),
            pendingConfirmation = MobileConfirmation("c1", "R2", "Dosya sil"),
        )

        val sonra = reduceMobileTask(
            onayli,
            MobileTaskMutation.EventReceived(
                ev("1", status = MobileTaskStatus.CANCELLED, type = "task.state"),
            ),
        )

        assertNull(sonra.pendingConfirmation)
    }

    @Test
    fun `terminal olmayan durum bekleyen onayi korur`() {
        val onay = MobileConfirmation("c1", "R1", "Ekran goruntusu al")
        val onayli = MobileTaskUiState(
            task = MobileTask("t1", "p", MobileTaskStatus.WAITING_FOR_CONFIRMATION),
            pendingConfirmation = onay,
        )

        val sonra = reduceMobileTask(
            onayli,
            MobileTaskMutation.EventReceived(
                ev("1", status = MobileTaskStatus.EXECUTING, type = "task.state"),
            ),
        )

        assertEquals(onay, sonra.pendingConfirmation)
    }

    // ---------- T4: onay paneli parmagin altinda degismez ----------

    private fun taskState(status: MobileTaskStatus = MobileTaskStatus.EXECUTING) =
        MobileTaskUiState(task = MobileTask("t1", "dosyayi sil", status))

    private fun confirmationEvent(id: String, eventId: String, summary: String) =
        MobileTaskEvent(
            id = eventId,
            taskId = "t1",
            type = "confirmation.requested",
            summary = summary,
            confirmation = MobileConfirmation(id, "high", summary),
        )

    @Test
    fun `ikinci onay istegi ekrandakini ezmez kuyruga girer`() {
        var state = reduceMobileTask(
            taskState(),
            MobileTaskMutation.EventReceived(confirmationEvent("c1", "1", "A dosyasini sil")),
        )
        state = reduceMobileTask(
            state,
            MobileTaskMutation.EventReceived(confirmationEvent("c2", "2", "TUM diski bicimlendir")),
        )

        assertEquals("ekrandaki onay sabit kalmali", "c1", state.pendingConfirmation?.id)
        assertEquals(listOf("c2"), state.queuedConfirmations.map { it.id })
    }

    @Test
    fun `ekrandaki onay cozulunce kuyruktaki panele gelir`() {
        var state = reduceMobileTask(
            taskState(),
            MobileTaskMutation.EventReceived(confirmationEvent("c1", "1", "A")),
        )
        state = reduceMobileTask(
            state,
            MobileTaskMutation.EventReceived(confirmationEvent("c2", "2", "B")),
        )
        state = reduceMobileTask(
            state,
            MobileTaskMutation.ConfirmationResolved(
                MobileTask("t1", "dosyayi sil", MobileTaskStatus.EXECUTING),
                "c1",
            ),
        )

        assertEquals("c2", state.pendingConfirmation?.id)
        assertTrue(state.queuedConfirmations.isEmpty())
    }

    @Test
    fun `kuyruktaki onay cozulunce ekrandakine dokunulmaz`() {
        var state = reduceMobileTask(
            taskState(),
            MobileTaskMutation.EventReceived(confirmationEvent("c1", "1", "A")),
        )
        state = reduceMobileTask(
            state,
            MobileTaskMutation.EventReceived(confirmationEvent("c2", "2", "B")),
        )
        state = reduceMobileTask(
            state,
            MobileTaskMutation.EventReceived(
                MobileTaskEvent(
                    id = "3",
                    taskId = "t1",
                    type = "confirmation.rejected",
                    summary = "reddedildi",
                    confirmation = MobileConfirmation("c2", "high", "B"),
                ),
            ),
        )

        assertEquals("c1", state.pendingConfirmation?.id)
        assertTrue(state.queuedConfirmations.isEmpty())
    }

    @Test
    fun `ayni onay iki kez gelirse kuyruk sismez`() {
        var state = reduceMobileTask(
            taskState(),
            MobileTaskMutation.EventReceived(confirmationEvent("c1", "1", "A")),
        )
        state = reduceMobileTask(
            state,
            MobileTaskMutation.EventReceived(confirmationEvent("c1", "2", "A")),
        )

        assertEquals("c1", state.pendingConfirmation?.id)
        assertTrue(state.queuedConfirmations.isEmpty())
    }

    @Test
    fun `terminal gorev kuyrugu da temizler`() {
        var state = reduceMobileTask(
            taskState(),
            MobileTaskMutation.EventReceived(confirmationEvent("c1", "1", "A")),
        )
        state = reduceMobileTask(
            state,
            MobileTaskMutation.EventReceived(confirmationEvent("c2", "2", "B")),
        )
        state = reduceMobileTask(
            state,
            MobileTaskMutation.EventReceived(
                MobileTaskEvent("3", "t1", "task.failed", "hata", MobileTaskStatus.FAILED),
            ),
        )

        assertNull(state.pendingConfirmation)
        assertTrue(state.queuedConfirmations.isEmpty())
    }

    // ---------- statusuz olay durumu geri almaz ----------

    @Test
    fun `statusuz olay duraklatmayi geri almaz`() {
        var state = taskState(MobileTaskStatus.EXECUTING)
        state = reduceMobileTask(
            state,
            MobileTaskMutation.EventReceived(
                MobileTaskEvent("1", "t1", "worker.executing", "calisiyor", MobileTaskStatus.EXECUTING),
            ),
        )
        // Kullanici "Duraklat"a basti; durum HTTP yanitindan geldi, olaydan degil.
        state = reduceMobileTask(
            state,
            MobileTaskMutation.TaskLoaded(MobileTask("t1", "dosyayi sil", MobileTaskStatus.PAUSED)),
        )
        // Ardindan statusuz bir olay dusuyor.
        state = reduceMobileTask(
            state,
            MobileTaskMutation.EventReceived(MobileTaskEvent("2", "t1", "worker.log", "cikti")),
        )

        assertEquals(
            "statusuz olay geriye bakip EXECUTING'i geri getiriyordu",
            MobileTaskStatus.PAUSED,
            state.task?.status,
        )
    }

    @Test
    fun `statulu olay durumu ilerletmeye devam eder`() {
        var state = taskState(MobileTaskStatus.QUEUED)
        state = reduceMobileTask(
            state,
            MobileTaskMutation.EventReceived(
                MobileTaskEvent("1", "t1", "task.planning", "plan", MobileTaskStatus.PLANNING),
            ),
        )

        assertEquals(MobileTaskStatus.PLANNING, state.task?.status)
    }

    @Test
    fun `gelen olay yapiskan hatayi temizler`() {
        val state = reduceMobileTask(
            taskState().copy(error = "Baglanti koptu"),
            MobileTaskMutation.EventReceived(MobileTaskEvent("1", "t1", "worker.log", "cikti")),
        )

        assertNull("akis yasiyorsa cozulmus hata ekranda kalmamali", state.error)
    }
}
