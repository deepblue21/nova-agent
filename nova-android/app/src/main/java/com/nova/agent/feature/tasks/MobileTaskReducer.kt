package com.nova.agent.feature.tasks

import java.math.BigInteger

data class MobileTaskUiState(
    val prompt: String = "",
    val task: MobileTask? = null,
    val events: List<MobileTaskEvent> = emptyList(),
    val pendingConfirmation: MobileConfirmation? = null,
    /**
     * T4: ekranda duran onay ÇÖZÜLENE kadar bekleyen diğer onaylar burada
     * kuyruğa girer. Kullanıcı A'nın özetini okurken B'nin paneli altına
     * kaymasın diye; bkz. [reduceMobileTask].
     */
    val queuedConfirmations: List<MobileConfirmation> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

val MobileTaskUiState.canResolveConfirmation: Boolean
    get() = !loading && task != null && pendingConfirmation != null

sealed interface MobileTaskMutation {
    data class PromptChanged(val value: String) : MobileTaskMutation
    data class TaskLoaded(val task: MobileTask) : MobileTaskMutation
    data class ConfirmationResolved(
        val task: MobileTask,
        val confirmationId: String,
    ) : MobileTaskMutation
    data class EventReceived(val event: MobileTaskEvent) : MobileTaskMutation
    data class Failed(val message: String) : MobileTaskMutation
    data object Loading : MobileTaskMutation
    data object ErrorCleared : MobileTaskMutation
    data object Reset : MobileTaskMutation
}

fun reduceMobileTask(state: MobileTaskUiState, mutation: MobileTaskMutation): MobileTaskUiState = when (mutation) {
    is MobileTaskMutation.PromptChanged -> state.copy(prompt = mutation.value)
    is MobileTaskMutation.TaskLoaded -> state.copy(task = mutation.task, loading = false, error = null)
    is MobileTaskMutation.ConfirmationResolved -> {
        if (
            state.task?.id != mutation.task.id ||
            state.pendingConfirmation?.id != mutation.confirmationId
        ) {
            state
        } else {
            // Kuyruktan yeni bir onay panele geliyorsa, HTTP yanıtındaki durum
            // ONDAN ESKİDİR: yanıt yoldayken gelen olay görevi tekrar
            // "onay bekleniyor"a taşımıştır. Yanıtı olduğu gibi yazmak, ekranda
            // onay paneli dururken başlıkta "Eylem uygulanıyor" yazmasına yol
            // açardı. Eskiden bu çelişkiden T4 hatası sayesinde KAZAYLA
            // korunuyorduk (onay ezildiği için kimlik eşleşmiyor, mutasyon
            // düşüyordu); T4 kapanınca koruma da gitti, açıkça yazıldı.
            val promoted = state.queuedConfirmations.firstOrNull()
            val statusFromEvents = promoted?.let {
                state.events.asReversed()
                    .firstOrNull { event -> event.taskId == mutation.task.id && event.status != null }
                    ?.status
            }
            state.copy(
                task = statusFromEvents?.let { mutation.task.copy(status = it) } ?: mutation.task,
                pendingConfirmation = promoted,
                queuedConfirmations = state.queuedConfirmations.drop(1),
                loading = false,
                error = null,
            )
        }
    }
    is MobileTaskMutation.Failed -> state.copy(loading = false, error = mutation.message)
    MobileTaskMutation.Loading -> state.copy(loading = true)
    MobileTaskMutation.ErrorCleared -> state.copy(error = null)
    MobileTaskMutation.Reset -> MobileTaskUiState()
    is MobileTaskMutation.EventReceived -> {
        if (
            !state.acceptsEvent(mutation.event) ||
            state.events.any { it.id == mutation.event.id }
        ) {
            state
        } else {
            val (confirmation, queued) = when {
                mutation.event.type == "confirmation.requested" ->
                    enqueueConfirmation(
                        state.pendingConfirmation,
                        state.queuedConfirmations,
                        mutation.event.confirmation,
                    )
                mutation.event.type == "confirmation.approved" ||
                    mutation.event.type == "confirmation.rejected" ->
                    dequeueConfirmation(
                        state.pendingConfirmation,
                        state.queuedConfirmations,
                        mutation.event.confirmation?.id,
                    )
                // T2: görev bittiyse (failed/cancelled/completed) bekleyen onay
                // ARTIK ANLAMSIZ ve temizlenmeli. Eskiden temizlenmiyordu:
                // onay isterken worker zaman aşımına düşüp `failed` yayınlarsa
                // modal karartma katmanı tüm dokunuşları yutuyor, alttaki içerik
                // erişilemez oluyor, "Yeni görev" ulaşılamıyor, Onayla/Reddet
                // 404 dönüyor ve görev terminal olduğu için kurtarıcı bir SSE
                // olayı da gelemiyordu. Çıkış yoktu — uygulamayı zorla kapatmak
                // gerekiyordu.
                mutation.event.status?.isTerminal() == true -> null to emptyList<MobileConfirmation>()
                else -> state.pendingConfirmation to state.queuedConfirmations
            }
            val events = orderEvents(state.events + mutation.event)
            val updatedTask = state.task?.takeIf { it.id == mutation.event.taskId }?.let { current ->
                // Statüsüz olay durumu GERİ ALMAMALI. Eskiden her olayda durum
                // yeniden hesaplanıyordu: kullanıcı "Duraklat"a basıp HTTP yanıtı
                // PAUSED yazdıktan sonra gelen statüsüz bir `worker.*` olayı,
                // geriye bakıp son statülü olayı (EXECUTING) buluyor ve duraklatmayı
                // sessizce geri alıyordu.
                if (mutation.event.status == null) return@let current
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
                queuedConfirmations = queued,
                loading = false,
                // Görev SÜRÜYORSA gelen olay akışın yaşadığını kanıtlar: artık
                // çözülmüş "Bağlantı koptu" mesajı ekranda asılı kalmamalı
                // (`clearError()` bunun için yazılmış ama hiç çağrılmamıştı).
                // Görev BİTTİYSE hata mesajı ne olduğunun kaydıdır, silinmez.
                error = state.error.takeIf {
                    (updatedTask ?: state.task)?.status?.isTerminal() == true
                },
            )
        }
    }
}

internal fun reduceMobileTaskResponse(
    state: MobileTaskUiState,
    task: MobileTask,
    confirmationId: String?,
    expectedEventRevision: Int? = null,
): MobileTaskUiState? {
    if (
        state.task?.id != task.id ||
        expectedEventRevision?.let { state.eventRevision(task.id) != it } == true
    ) {
        return null
    }
    val mutation = if (confirmationId == null) {
        MobileTaskMutation.TaskLoaded(task)
    } else {
        MobileTaskMutation.ConfirmationResolved(task, confirmationId)
    }
    return reduceMobileTask(state, mutation).takeUnless { it === state }
}

internal fun MobileTaskUiState.eventRevision(taskId: String): Int =
    events.count { it.taskId == taskId }

internal fun MobileTaskUiState.acceptsEvent(
    event: MobileTaskEvent,
    expectedTaskId: String? = task?.id,
): Boolean =
    expectedTaskId != null && task?.id == expectedTaskId && event.taskId == expectedTaskId

/**
 * Olayları sıraya koyar — T1.
 *
 * Eskiden `sortedBy { BigInteger(it.id) }` idi ve bu iki katman arasında
 * DOĞRUDAN SÖZLEŞME ÇELİŞKİSİ demekti: `MobileTaskClient` olay kimliğini
 * serbest metin olarak kabul ediyor (testi de bunu bilerek doğruluyor), Reducer
 * ise ondalık tamsayı şart koşuyordu. Gateway ULID/UUID üretmeye başladığı gün
 * (`id: evt_01H8…`) ya da JSON'da kimlik float geldiği gün (`"id":1.0` ->
 * `"1.0"`) ilk olayda ana thread'de `NumberFormatException` ile çökerdi.
 *
 * Kural: kimliklerin TAMAMI sayısalsa sayısal sıraya sokulur (SSE yeniden
 * bağlanmasında olaylar karışık sırada gelebilir, bu sıralama onun için var).
 * Aksi halde geliş sırası korunur — SSE zaten sıralı teslim eder ve sayısal
 * olmayan kimlikte "doğru" bir sayısal sıra yoktur; uydurmak yerine dokunmuyoruz.
 */
internal fun orderEvents(events: List<MobileTaskEvent>): List<MobileTaskEvent> {
    val keys = events.map { it.id.toBigIntegerOrNull() }
    if (keys.any { it == null }) return events
    return events.sortedBy { it.id.toBigIntegerOrNull() ?: BigInteger.ZERO }
}

/**
 * Yeni onay isteği geldiğinde ne olacağı — T4.
 *
 * Eskiden `pendingConfirmation` doğrudan yenisiyle EZİLİYORDU. Ekranda A'nın
 * özeti dururken B düşerse panel parmağın altında değişiyor, kullanıcı A
 * sanarak "Onayla"ya basınca B onaylanmış oluyordu — üstelik B daha riskli
 * olabilir. Riskli eylem onayında "ne onayladığını gördün" garantisi bunun
 * üzerine kurulu, o yüzden ekrandaki onay ÇÖZÜLENE kadar sabit kalır.
 *
 * Yeni istek kaybolmaz, kuyruğa girer; sırası gelince panele kendi gelir.
 */
internal fun enqueueConfirmation(
    pending: MobileConfirmation?,
    queued: List<MobileConfirmation>,
    incoming: MobileConfirmation?,
): Pair<MobileConfirmation?, List<MobileConfirmation>> = when {
    incoming == null -> pending to queued
    pending == null -> incoming to queued
    pending.id == incoming.id -> pending to queued
    queued.any { it.id == incoming.id } -> pending to queued
    else -> pending to (queued + incoming)
}

/**
 * Bir onay çözüldüğünde kuyruktan sıradakini panele alır — T4.
 *
 * `resolvedId` null ise (sunucu hangi onayın çözüldüğünü söylemiyorsa) ekrandaki
 * onay çözülmüş sayılır; eski davranış buydu ve korunuyor. Kuyruktaki bir onay
 * çözüldüyse ekrandakine dokunulmaz, yalnızca kuyruktan düşer.
 */
internal fun dequeueConfirmation(
    pending: MobileConfirmation?,
    queued: List<MobileConfirmation>,
    resolvedId: String?,
): Pair<MobileConfirmation?, List<MobileConfirmation>> = when {
    resolvedId != null && queued.any { it.id == resolvedId } ->
        pending to queued.filterNot { it.id == resolvedId }
    resolvedId != null && pending != null && pending.id != resolvedId ->
        pending to queued
    else -> queued.firstOrNull() to queued.drop(1)
}
