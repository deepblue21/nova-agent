package com.nova.agent.feature.tasks

enum class MobileTaskStatus {
    QUEUED, ROUTING, OBSERVING, PLANNING, EXECUTING, VERIFYING,
    WAITING_FOR_CONFIRMATION, WAITING_FOR_DEVICE, WAITING_FOR_COMPUTE,
    PAUSED, COMPLETED, FAILED, CANCELLED;

    companion object {
        fun fromWire(value: String) = entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
            ?: FAILED

        fun fromWireOrNull(value: String) = entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}

data class MobileTask(val id: String, val prompt: String, val status: MobileTaskStatus)

data class MobileConfirmation(val id: String, val riskLevel: String, val actionSummary: String)

data class MobileTaskEvent(
    val id: String,
    val taskId: String,
    val type: String,
    val summary: String,
    val status: MobileTaskStatus? = null,
    val confirmation: MobileConfirmation? = null,
)

val MobileTaskStatus.userLabel: String
    get() = when (this) {
        MobileTaskStatus.QUEUED -> "Sıraya alındı"
        MobileTaskStatus.ROUTING -> "Yönlendiriliyor"
        MobileTaskStatus.OBSERVING -> "Cihaz inceleniyor"
        MobileTaskStatus.PLANNING -> "Plan hazırlanıyor"
        MobileTaskStatus.EXECUTING -> "Eylem uygulanıyor"
        MobileTaskStatus.VERIFYING -> "Sonuç doğrulanıyor"
        MobileTaskStatus.WAITING_FOR_CONFIRMATION -> "Onay bekleniyor"
        MobileTaskStatus.WAITING_FOR_DEVICE -> "Telefon bekleniyor"
        MobileTaskStatus.WAITING_FOR_COMPUTE -> "PC bekleniyor"
        MobileTaskStatus.PAUSED -> "Duraklatıldı"
        MobileTaskStatus.COMPLETED -> "Tamamlandı"
        MobileTaskStatus.FAILED -> "Başarısız"
        MobileTaskStatus.CANCELLED -> "İptal edildi"
    }

val MobileTaskEvent.userLabel: String
    get() = status?.userLabel ?: when (type) {
        "worker.claimed" -> "Görev alındı"
        "worker.executing", "worker.running" -> "Eylem uygulanıyor"
        "worker.observing" -> "Cihaz inceleniyor"
        "worker.completed" -> "Tamamlandı"
        "confirmation.requested" -> "Onay bekleniyor"
        else -> "Görev güncellendi"
    }

fun MobileTaskEvent.userSummary(taskPrompt: String?): String {
    val value = summary.trim()
    val wireStatus = MobileTaskStatus.fromWireOrNull(value)
    val parserFallback = value.isEmpty() || value == type || wireStatus != null
    if (!parserFallback) return value

    if (type == "confirmation.requested") {
        taskPrompt?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    }
    return wireStatus?.userLabel ?: userLabel
}
