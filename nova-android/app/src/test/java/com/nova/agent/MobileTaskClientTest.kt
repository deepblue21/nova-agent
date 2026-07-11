package com.nova.agent

import com.nova.agent.feature.tasks.MobileTaskStatus
import com.nova.agent.net.MobileTaskClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MobileTaskClientTest {

    @Test
    fun parsesTaskJson() {
        val task = MobileTaskClient.parseTask(
            """{"id":"task-1","prompt":"Open Settings","status":"queued"}""",
        )

        assertNotNull(task)
        assertEquals("task-1", task?.id)
        assertEquals("Open Settings", task?.prompt)
        assertEquals(MobileTaskStatus.QUEUED, task?.status)
    }

    @Test
    fun parsesTaskStateEvent() {
        val event = MobileTaskClient.parseEvent(
            """{"id":"42","task_id":"task-1","type":"task.state","payload":{"status":"paused"}}""",
            null,
        )

        assertNotNull(event)
        assertEquals("42", event?.id)
        assertEquals("task-1", event?.taskId)
        assertEquals("task.state", event?.type)
        assertEquals("paused", event?.summary)
        assertNull(event?.confirmation)
    }

    @Test
    fun parsesConfirmationRequestedEvent() {
        val event = MobileTaskClient.parseEvent(
            """{"id":"43","task_id":"task-1","type":"confirmation.requested","payload":{"confirmation_id":"confirmation-1","risk_level":"R2","action_summary":"Turn Wi-Fi off"}}""",
            null,
        )

        assertNotNull(event)
        assertEquals("confirmation-1", event?.confirmation?.id)
        assertEquals("R2", event?.confirmation?.riskLevel)
        assertEquals("Turn Wi-Fi off", event?.confirmation?.actionSummary)
    }

    @Test
    fun keepsLargeSseEventIdsAsStringsWhenJsonOmitsThem() {
        val id = "900719925474099312345"
        val event = MobileTaskClient.parseEvent(
            """{"task_id":"task-1","type":"task.state","payload":{"status":"executing"}}""",
            id,
        )

        assertEquals(id, event?.id)
    }

    @Test
    fun ignoresDoneAndMalformedEventPayloads() {
        assertNull(MobileTaskClient.parseEvent("[DONE]", "1"))
        assertNull(MobileTaskClient.parseEvent("not-json", "1"))
    }

    @Test
    fun mapsKnownHttpFailuresToSafeTurkishMessages() {
        assertEquals("Yetkisiz erişim", MobileTaskClient.userMessageForHttp(401))
        assertEquals("Görev bulunamadı", MobileTaskClient.userMessageForHttp(404))
        assertEquals("Görev durumu değişti; tekrar deneyin", MobileTaskClient.userMessageForHttp(409))
        assertEquals("Çok fazla istek; daha sonra tekrar deneyin", MobileTaskClient.userMessageForHttp(429))
    }
}
