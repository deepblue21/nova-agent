package com.nova.agent.feature.tasks

enum class MobileTaskStatus {
    QUEUED, ROUTING, OBSERVING, PLANNING, EXECUTING, VERIFYING,
    WAITING_FOR_CONFIRMATION, WAITING_FOR_DEVICE, WAITING_FOR_COMPUTE,
    PAUSED, COMPLETED, FAILED, CANCELLED;

    companion object {
        fun fromWire(value: String) = entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
            ?: FAILED
    }
}

data class MobileTask(val id: String, val prompt: String, val status: MobileTaskStatus)

data class MobileConfirmation(val id: String, val riskLevel: String, val actionSummary: String)

data class MobileTaskEvent(
    val id: String,
    val taskId: String,
    val type: String,
    val summary: String,
    val confirmation: MobileConfirmation? = null,
)
