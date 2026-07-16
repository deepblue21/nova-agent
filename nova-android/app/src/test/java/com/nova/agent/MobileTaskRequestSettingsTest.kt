package com.nova.agent

import com.nova.agent.data.AppSettings
import com.nova.agent.feature.tasks.MobileTask
import com.nova.agent.feature.tasks.MobileTaskCallRegistry
import com.nova.agent.feature.tasks.MobileTaskMutation
import com.nova.agent.feature.tasks.MobileTaskRequestSettings
import com.nova.agent.feature.tasks.MobileTaskStatus
import com.nova.agent.feature.tasks.MobileTaskUiState
import com.nova.agent.feature.tasks.reduceMobileTask
import com.nova.agent.net.MobileTaskClient
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MobileTaskRequestSettingsTest {
    @Test
    fun taskCreationUsesUpdatedConnectionAfterEarlierStoreLoad() {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val served = CountDownLatch(1)
        var usedUpdatedConnection = false
        thread(isDaemon = true) {
            try {
                server.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader()
                    val requestLine = reader.readLine()
                    var usedUpdatedToken = false
                    while (true) {
                        val header = reader.readLine()
                        if (header.isEmpty()) break
                        if (header == "Authorization: Bearer new-token") usedUpdatedToken = true
                    }
                    usedUpdatedConnection =
                        requestLine == "POST /v1/mobile/tasks HTTP/1.1" && usedUpdatedToken

                    val body = """{"id":"task-1","prompt":"Open Settings","status":"queued"}"""
                        .toByteArray(StandardCharsets.UTF_8)
                    val headers = (
                        "HTTP/1.1 201 Created\r\n" +
                            "Content-Type: application/json\r\n" +
                            "Content-Length: ${body.size}\r\n" +
                            "Connection: close\r\n\r\n"
                    ).toByteArray(StandardCharsets.UTF_8)
                    socket.getOutputStream().use { output ->
                        output.write(headers)
                        output.write(body)
                        output.flush()
                    }
                }
            } finally {
                served.countDown()
            }
        }

        try {
            val settings = MobileTaskRequestSettings(
                AppSettings(baseUrl = "http://127.0.0.1:1/v1", token = "old-token"),
            )
            val earlierLoad = settings.beginStoreLoad()
            settings.updateConnectionSettings(
                baseUrl = "  http://127.0.0.1:${server.localPort}  ",
                token = "  new-token  ",
            )
            settings.applyStoreLoad(
                earlierLoad,
                AppSettings(baseUrl = "http://127.0.0.1:1/v1", token = "old-token"),
            )

            val response = CountDownLatch(1)
            var succeeded = false
            settings.createTask("Open Settings") {
                succeeded = it.isSuccess
                response.countDown()
            }

            assertTrue(response.await(5, TimeUnit.SECONDS))
            assertTrue(served.await(5, TimeUnit.SECONDS))
            assertTrue(succeeded)
            assertTrue(usedUpdatedConnection)
        } finally {
            server.close()
        }
    }

    @Test
    fun connectionUpdateDuringCreationOnlyAffectsFutureTaskRequests() {
        val firstServer = ServerSocket(0, 7, InetAddress.getByName("127.0.0.1"))
        val secondServer = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val firstRequests = Collections.synchronizedList(mutableListOf<RecordedRequest>())
        val secondRequests = Collections.synchronizedList(mutableListOf<RecordedRequest>())
        val creationAccepted = CountDownLatch(1)
        val releaseCreation = CountDownLatch(1)

        thread(isDaemon = true) {
            try {
                repeat(7) { requestIndex ->
                    firstServer.accept().use { socket ->
                        val request = readRequest(socket)
                        firstRequests += request
                        if (requestIndex == 0) {
                            creationAccepted.countDown()
                            releaseCreation.await(5, TimeUnit.SECONDS)
                            writeTaskResponse(socket, status = "queued", code = 201, reason = "Created")
                        } else if (request.line.endsWith("/events HTTP/1.1")) {
                            writeTaskEventStream(socket, eventId = requestIndex.toString())
                        } else {
                            writeTaskResponse(socket, status = "executing")
                        }
                    }
                }
            } catch (_: Exception) {
                // The unused accept is released when the test closes the server.
            }
        }
        thread(isDaemon = true) {
            try {
                secondServer.accept().use { socket ->
                    secondRequests += readRequest(socket)
                    writeTaskResponse(socket, taskId = "task-2", status = "queued", code = 201, reason = "Created")
                }
            } catch (_: Exception) {
                // The correct implementation leaves this server unused.
            }
        }

        try {
            val settings = MobileTaskRequestSettings(
                AppSettings(baseUrl = "http://127.0.0.1:${firstServer.localPort}/v1", token = "first-token"),
            )
            val created = CountDownLatch(1)
            settings.createTask("Open Settings") { result ->
                assertTrue(result.isSuccess)
                created.countDown()
            }

            assertTrue(creationAccepted.await(5, TimeUnit.SECONDS))
            settings.updateConnectionSettings(
                "http://127.0.0.1:${secondServer.localPort}/v1",
                "second-token",
            )
            releaseCreation.countDown()
            assertTrue(created.await(5, TimeUnit.SECONDS))

            settings.updateConnectionSettings(
                "  http://127.0.0.1:${secondServer.localPort}/v1  ",
                "  second-token  ",
            )

            listOf("pause", "resume", "cancel").forEach { command ->
                val commanded = CountDownLatch(1)
                settings.command("task-1", command) { result ->
                    assertTrue(result.isSuccess)
                    commanded.countDown()
                }
                assertTrue(commanded.await(5, TimeUnit.SECONDS))
            }

            val decided = CountDownLatch(1)
            settings.resolveConfirmation("task-1", "confirmation-1", "approve") { result ->
                assertTrue(result.isSuccess)
                decided.countDown()
            }
            assertTrue(decided.await(5, TimeUnit.SECONDS))

            repeat(2) {
                val eventReceived = CountDownLatch(1)
                val source = settings.streamEvents(
                    "task-1",
                    null,
                    object : MobileTaskClient.EventCallbacks {
                        override fun onEvent(event: com.nova.agent.feature.tasks.MobileTaskEvent) {
                            eventReceived.countDown()
                        }

                        override fun onClosed() = Unit

                        override fun onError(message: String, recoverable: Boolean) = Unit
                    },
                )
                assertTrue(eventReceived.await(5, TimeUnit.SECONDS))
                source.cancel()
            }

            val futureCreated = CountDownLatch(1)
            settings.createTask("Future task") { result ->
                assertEquals("task-2", result.getOrNull()?.id)
                futureCreated.countDown()
            }
            assertTrue(futureCreated.await(5, TimeUnit.SECONDS))

            assertEquals(
                listOf(
                    "POST /v1/mobile/tasks HTTP/1.1",
                    "POST /v1/mobile/tasks/task-1/commands HTTP/1.1",
                    "POST /v1/mobile/tasks/task-1/commands HTTP/1.1",
                    "POST /v1/mobile/tasks/task-1/commands HTTP/1.1",
                    "POST /v1/mobile/tasks/task-1/confirmations/confirmation-1 HTTP/1.1",
                    "GET /v1/mobile/tasks/task-1/events HTTP/1.1",
                    "GET /v1/mobile/tasks/task-1/events HTTP/1.1",
                ),
                firstRequests.map { it.line },
            )
            assertTrue(firstRequests.all { it.authorization == "Bearer first-token" })
            assertEquals(listOf("POST /v1/mobile/tasks HTTP/1.1"), secondRequests.map { it.line })
            assertEquals(listOf("Bearer second-token"), secondRequests.map { it.authorization })
        } finally {
            releaseCreation.countDown()
            firstServer.close()
            secondServer.close()
        }
    }

    @Test
    fun delayedCreateCallbackCannotReplaceANewerTaskSession() {
        val firstServer = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val secondServer = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val firstAccepted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val firstServed = CountDownLatch(1)
        thread(isDaemon = true) {
            try {
                firstServer.accept().use { socket ->
                    readRequestLine(socket)
                    firstAccepted.countDown()
                    releaseFirst.await(5, TimeUnit.SECONDS)
                    writeTaskResponse(socket, taskId = "task-1", status = "queued", code = 201, reason = "Created")
                }
            } finally {
                firstServed.countDown()
            }
        }
        thread(isDaemon = true) {
            secondServer.accept().use { socket ->
                readRequestLine(socket)
                writeTaskResponse(socket, taskId = "task-2", status = "queued", code = 201, reason = "Created")
            }
        }

        try {
            val settings = MobileTaskRequestSettings(
                AppSettings(baseUrl = "http://127.0.0.1:${firstServer.localPort}/v1"),
            )
            val callbacks = Collections.synchronizedList(mutableListOf<String>())
            val staleCallback = CountDownLatch(1)
            settings.createTask("First") { result ->
                result.getOrNull()?.let { callbacks += it.id }
                staleCallback.countDown()
            }
            assertTrue(firstAccepted.await(5, TimeUnit.SECONDS))

            settings.updateConnectionSettings("http://127.0.0.1:${secondServer.localPort}/v1", "")
            val secondCompleted = CountDownLatch(1)
            settings.createTask("Second") { result ->
                result.getOrNull()?.let { callbacks += it.id }
                secondCompleted.countDown()
            }
            assertTrue(secondCompleted.await(5, TimeUnit.SECONDS))

            releaseFirst.countDown()
            assertTrue(firstServed.await(5, TimeUnit.SECONDS))
            assertFalse(staleCallback.await(300, TimeUnit.MILLISECONDS))

            assertEquals(listOf("task-2"), callbacks.toList())
        } finally {
            releaseFirst.countDown()
            firstServer.close()
            secondServer.close()
        }
    }

    @Test
    fun delayedHttpCallbackIsIgnoredAfterTaskSessionReset() {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val accepted = CountDownLatch(1)
        val releaseResponse = CountDownLatch(1)
        thread(isDaemon = true) {
            try {
                server.accept().use { socket ->
                    readRequestLine(socket)
                    accepted.countDown()
                    releaseResponse.await(5, TimeUnit.SECONDS)
                    writeTaskResponse(socket, status = "queued", code = 201, reason = "Created")
                }
            } catch (_: Exception) {
                // Reset may cancel the in-flight socket before the delayed response is written.
            }
        }

        try {
            val settings = MobileTaskRequestSettings(
                AppSettings(baseUrl = "http://127.0.0.1:${server.localPort}/v1"),
            )
            val callback = CountDownLatch(1)
            settings.createTask("Open Settings") { callback.countDown() }
            assertTrue(accepted.await(5, TimeUnit.SECONDS))

            settings.reset()
            releaseResponse.countDown()

            assertFalse(callback.await(300, TimeUnit.MILLISECONDS))
        } finally {
            releaseResponse.countDown()
            server.close()
        }
    }

    @Test
    fun delayedCommandCallbackIsIgnoredAfterTaskSessionReset() {
        val server = ServerSocket(0, 2, InetAddress.getByName("127.0.0.1"))
        val commandAccepted = CountDownLatch(1)
        val releaseCommand = CountDownLatch(1)
        thread(isDaemon = true) {
            try {
                server.accept().use { socket ->
                    readRequestLine(socket)
                    writeTaskResponse(socket, status = "executing", code = 201, reason = "Created")
                }
                server.accept().use { socket ->
                    readRequestLine(socket)
                    commandAccepted.countDown()
                    releaseCommand.await(5, TimeUnit.SECONDS)
                    writeTaskResponse(socket, status = "paused")
                }
            } catch (_: Exception) {
                // Reset may cancel the command before its delayed response is written.
            }
        }

        try {
            val settings = MobileTaskRequestSettings(
                AppSettings(baseUrl = "http://127.0.0.1:${server.localPort}/v1"),
            )
            val created = CountDownLatch(1)
            settings.createTask("Open Settings") { result ->
                assertTrue(result.isSuccess)
                created.countDown()
            }
            assertTrue(created.await(5, TimeUnit.SECONDS))

            val callback = CountDownLatch(1)
            settings.command("task-1", "pause") { callback.countDown() }
            assertTrue(commandAccepted.await(5, TimeUnit.SECONDS))
            settings.reset()
            releaseCommand.countDown()

            assertFalse(callback.await(300, TimeUnit.MILLISECONDS))
        } finally {
            releaseCommand.countDown()
            server.close()
        }
    }

    @Test
    fun delayedConfirmationDecisionCallbackIsIgnoredAfterTaskSessionReset() {
        val server = ServerSocket(0, 2, InetAddress.getByName("127.0.0.1"))
        val decisionAccepted = CountDownLatch(1)
        val releaseDecision = CountDownLatch(1)
        thread(isDaemon = true) {
            try {
                server.accept().use { socket ->
                    readRequestLine(socket)
                    writeTaskResponse(
                        socket,
                        status = "waiting_for_confirmation",
                        code = 201,
                        reason = "Created",
                    )
                }
                server.accept().use { socket ->
                    readRequestLine(socket)
                    decisionAccepted.countDown()
                    releaseDecision.await(5, TimeUnit.SECONDS)
                    writeTaskResponse(socket, status = "executing")
                }
            } catch (_: Exception) {
                // Reset may cancel the decision before its delayed response is written.
            }
        }

        try {
            val settings = MobileTaskRequestSettings(
                AppSettings(baseUrl = "http://127.0.0.1:${server.localPort}/v1"),
            )
            val created = CountDownLatch(1)
            settings.createTask("Open Settings") { result ->
                assertTrue(result.isSuccess)
                created.countDown()
            }
            assertTrue(created.await(5, TimeUnit.SECONDS))

            val callback = CountDownLatch(1)
            settings.resolveConfirmation(
                "task-1",
                "confirmation-1",
                "approve",
            ) { callback.countDown() }
            assertTrue(decisionAccepted.await(5, TimeUnit.SECONDS))
            settings.reset()
            releaseDecision.countDown()

            assertFalse(callback.await(300, TimeUnit.MILLISECONDS))
        } finally {
            releaseDecision.countDown()
            server.close()
        }
    }

    @Test
    fun wrongTaskIdCommandResponseInvokesSanitizedFailureAndClearsLoading() {
        val state = wrongTaskIdResponseState { settings, callback ->
            settings.command("task-1", "pause", callback)
        }

        assertFalse(state.loading)
        assertEquals(MobileTaskClient.userMessageForHttp(409), state.error)
        assertEquals("task-1", state.task?.id)
    }

    @Test
    fun wrongTaskIdConfirmationResponseInvokesSanitizedFailureAndClearsLoading() {
        val state = wrongTaskIdResponseState { settings, callback ->
            settings.resolveConfirmation(
                "task-1",
                "confirmation-1",
                "approve",
                callback,
            )
        }

        assertFalse(state.loading)
        assertEquals(MobileTaskClient.userMessageForHttp(409), state.error)
        assertEquals("task-1", state.task?.id)
    }

    @Test
    fun completedHttpCallsAreNotRetainedAndAttachRacesAreSafe() {
        val registry = MobileTaskCallRegistry()
        val client = OkHttpClient()

        val attachedCall = client.newCall(Request.Builder().url("http://127.0.0.1/").build())
        val attachedOperation = registry.start()
        registry.attach(attachedOperation, attachedCall)
        registry.complete(attachedOperation)

        assertEquals(0, registry.removeAll().size)
        assertFalse(attachedCall.isCanceled())

        val completedBeforeAttach = client.newCall(Request.Builder().url("http://127.0.0.1/").build())
        val earlyCompletion = registry.start()
        registry.complete(earlyCompletion)
        registry.attach(earlyCompletion, completedBeforeAttach)

        assertEquals(0, registry.removeAll().size)
        assertFalse(completedBeforeAttach.isCanceled())

        val invalidatedBeforeAttach = client.newCall(Request.Builder().url("http://127.0.0.1/").build())
        val invalidatedOperation = registry.start()
        assertEquals(0, registry.removeAll().size)
        registry.attach(invalidatedOperation, invalidatedBeforeAttach)

        assertTrue(invalidatedBeforeAttach.isCanceled())
    }

    private fun wrongTaskIdResponseState(
        request: (MobileTaskRequestSettings, (Result<MobileTask>) -> Unit) -> Unit,
    ): MobileTaskUiState {
        val server = ServerSocket(0, 2, InetAddress.getByName("127.0.0.1"))
        thread(isDaemon = true) {
            server.accept().use { socket ->
                readRequestLine(socket)
                writeTaskResponse(socket, status = "executing", code = 201, reason = "Created")
            }
            server.accept().use { socket ->
                readRequestLine(socket)
                writeTaskResponse(socket, taskId = "task-2", status = "paused")
            }
        }

        try {
            val settings = MobileTaskRequestSettings(
                AppSettings(baseUrl = "http://127.0.0.1:${server.localPort}/v1"),
            )
            val created = CountDownLatch(1)
            settings.createTask("Open Settings") { result ->
                assertTrue(result.isSuccess)
                created.countDown()
            }
            assertTrue(created.await(5, TimeUnit.SECONDS))

            var state = MobileTaskUiState(
                task = MobileTask("task-1", "Open Settings", MobileTaskStatus.EXECUTING),
                loading = true,
            )
            val completed = CountDownLatch(1)
            request(settings) { result ->
                val error = requireNotNull(result.exceptionOrNull())
                state = reduceMobileTask(
                    state,
                    MobileTaskMutation.Failed(requireNotNull(error.message)),
                )
                completed.countDown()
            }
            assertTrue("Wrong-task success must complete as a protocol failure", completed.await(1, TimeUnit.SECONDS))
            return state
        } finally {
            server.close()
        }
    }

    private data class RecordedRequest(
        val line: String,
        val authorization: String?,
    )

    private fun readRequest(socket: java.net.Socket): RecordedRequest {
        val reader = socket.getInputStream().bufferedReader()
        val requestLine = reader.readLine()
        var authorization: String? = null
        while (true) {
            val header = reader.readLine()
            if (header.isEmpty()) break
            if (header.startsWith("Authorization: Bearer ")) {
                authorization = header.removePrefix("Authorization: ")
            }
        }
        return RecordedRequest(requestLine, authorization)
    }

    private fun readRequestLine(socket: java.net.Socket): String = readRequest(socket).line

    private fun writeTaskResponse(
        socket: java.net.Socket,
        taskId: String = "task-1",
        status: String,
        code: Int = 200,
        reason: String = "OK",
    ) {
        val body = """{"id":"$taskId","prompt":"Open Settings","status":"$status"}"""
            .toByteArray(StandardCharsets.UTF_8)
        val headers = (
            "HTTP/1.1 $code $reason\r\n" +
                "Content-Type: application/json\r\n" +
                "Content-Length: ${body.size}\r\n" +
                "Connection: close\r\n\r\n"
        ).toByteArray(StandardCharsets.UTF_8)
        socket.getOutputStream().use { output ->
            output.write(headers)
            output.write(body)
            output.flush()
        }
    }

    private fun writeTaskEventStream(socket: java.net.Socket, eventId: String) {
        val body = (
            "data: {\"id\":\"$eventId\",\"task_id\":\"task-1\",\"type\":\"task.state\",\"payload\":{\"status\":\"executing\"}}\r\n\r\n"
        ).toByteArray(StandardCharsets.UTF_8)
        val headers = (
            "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/event-stream\r\n" +
                "Content-Length: ${body.size}\r\n" +
                "Connection: close\r\n\r\n"
        ).toByteArray(StandardCharsets.UTF_8)
        socket.getOutputStream().use { output ->
            output.write(headers)
            output.write(body)
            output.flush()
        }
    }
}
