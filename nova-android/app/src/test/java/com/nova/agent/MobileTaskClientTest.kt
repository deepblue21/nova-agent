package com.nova.agent

import com.nova.agent.feature.tasks.MobileTaskStatus
import com.nova.agent.net.MobileTaskClient
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun parsesCompletedWorkerEventStatusAndSanitizedSummary() {
        val event = MobileTaskClient.parseEvent(
            """{"id":"44","task_id":"task-1","type":"worker.completed","payload":{"status":"completed","summary":"Android 17"}}""",
            null,
        )

        assertNotNull(event)
        assertEquals(MobileTaskStatus.COMPLETED, event?.status)
        assertEquals("Android 17", event?.summary)
    }

    @Test
    fun doesNotTreatUnknownWorkerStatusAsFailed() {
        val event = MobileTaskClient.parseEvent(
            """{"id":"45","task_id":"task-1","type":"worker.progress","payload":{"status":"invented","summary":"Android 17"}}""",
            null,
        )

        assertNull(event?.status)
    }

    @Test
    fun parsesContractShapedConfirmationRequestedEvent() {
        val event = MobileTaskClient.parseEvent(
            """{"id":"43","task_id":"task-1","type":"confirmation.requested","payload":{"confirmation_id":"confirmation-1","risk_level":"R2","status":"waiting_for_confirmation"}}""",
            null,
        )

        assertNotNull(event)
        assertEquals("confirmation-1", event?.confirmation?.id)
        assertEquals("R2", event?.confirmation?.riskLevel)
        assertEquals(MobileTaskStatus.WAITING_FOR_CONFIRMATION, event?.status)
        assertEquals("waiting_for_confirmation", event?.summary)
        assertEquals("waiting_for_confirmation", event?.confirmation?.actionSummary)
    }

    @Test
    fun preservesExplicitConfirmationActionSummary() {
        val event = MobileTaskClient.parseEvent(
            """{"id":"46","task_id":"task-1","type":"confirmation.requested","payload":{"confirmation_id":"confirmation-2","risk_level":"R2","action_summary":"Turn Wi-Fi off"}}""",
            null,
        )

        assertEquals("Turn Wi-Fi off", event?.summary)
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

    @Test
    fun mapsOnlyStrictWorkerCreationErrorToSafeWorkerMessage() {
        val strictWorkerError = """{"error":"task is not supported by this emulator worker"}"""

        assertEquals(
            "Bu gorev emulator worker'inda desteklenmiyor",
            taskFailureMessage(strictWorkerError) { client, baseUrl, callback ->
                client.createTask(baseUrl, "", "Open Settings", callback)
            },
        )
        assertEquals(
            "Görev servisi hatası",
            taskFailureMessage("""{"error":"another error"}""") { client, baseUrl, callback ->
                client.createTask(baseUrl, "", "Open Settings", callback)
            },
        )
        assertEquals(
            "Görev servisi hatası",
            taskFailureMessage("""{"error":" task is not supported by this emulator worker "}""") { client, baseUrl, callback ->
                client.createTask(baseUrl, "", "Open Settings", callback)
            },
        )
        assertEquals(
            "Görev servisi hatası",
            taskFailureMessage(strictWorkerError) { client, baseUrl, callback ->
                client.getTask(baseUrl, "", "task-1", callback)
            },
        )
    }

    private fun taskFailureMessage(
        responseBody: String,
        request: (MobileTaskClient, String, (Result<com.nova.agent.feature.tasks.MobileTask>) -> Unit) -> Unit,
    ): String? {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val served = CountDownLatch(1)
        thread(isDaemon = true) {
            try {
                server.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader()
                    while (reader.readLine().isNotEmpty()) {
                        // Consume the request headers before writing the response.
                    }

                    val bytes = responseBody.toByteArray(StandardCharsets.UTF_8)
                    val headers = (
                        "HTTP/1.1 400 Bad Request\r\n" +
                            "Content-Type: application/json\r\n" +
                            "Content-Length: ${bytes.size}\r\n" +
                            "Connection: close\r\n\r\n"
                    ).toByteArray(StandardCharsets.UTF_8)
                    socket.getOutputStream().use { output ->
                        output.write(headers)
                        output.write(bytes)
                        output.flush()
                    }
                }
            } finally {
                served.countDown()
            }
        }
        try {
            val response = CountDownLatch(1)
            var result: Result<com.nova.agent.feature.tasks.MobileTask>? = null
            request(MobileTaskClient(), "http://127.0.0.1:${server.localPort}") {
                result = it
                response.countDown()
            }

            assertTrue(response.await(5, TimeUnit.SECONDS))
            assertTrue(served.await(5, TimeUnit.SECONDS))
            return result?.exceptionOrNull()?.message
        } finally {
            server.close()
        }
    }
}
