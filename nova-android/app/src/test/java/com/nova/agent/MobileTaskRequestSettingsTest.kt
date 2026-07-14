package com.nova.agent

import com.nova.agent.data.AppSettings
import com.nova.agent.feature.tasks.MobileTaskRequestSettings
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
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
                baseUrl = "  http://127.0.0.1:${server.localPort}/v1  ",
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
}
