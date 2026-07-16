package com.nova.agent

import com.nova.agent.net.GatewayConnectionClient
import com.nova.agent.net.GatewayConnectionResult
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayConnectionClientTest {
    @Test fun canonicalizesSupportedGatewayBaseUrlsToV1() {
        assertEquals(
            "https://pc.example/v1",
            GatewayConnectionClient.canonicalBaseUrl("  https://pc.example  ")?.toString(),
        )
        assertEquals(
            "https://pc.example/v1",
            GatewayConnectionClient.canonicalBaseUrl("https://pc.example/v1/")?.toString(),
        )
        assertNull(GatewayConnectionClient.canonicalBaseUrl("https://pc.example/private"))
        assertNull(GatewayConnectionClient.canonicalBaseUrl("https://user:pass@pc.example/v1"))
        assertNull(GatewayConnectionClient.canonicalBaseUrl("https://pc.example/v1?token=secret"))
    }

    @Test fun buildsAuthenticatedModelsUrl() {
        assertEquals(
            "http://127.0.0.1:8088/v1/models",
            GatewayConnectionClient.modelsUrl("http://127.0.0.1:8088/v1/")?.toString(),
        )
        assertEquals(
            "http://127.0.0.1:8088/v1/models",
            GatewayConnectionClient.modelsUrl("http://127.0.0.1:8088")?.toString(),
        )
        assertNull(GatewayConnectionClient.modelsUrl("not a url"))
        assertNull(GatewayConnectionClient.modelsUrl("http://127.0.0.1:8088/private"))
        assertNull(GatewayConnectionClient.modelsUrl("http://user:pass@127.0.0.1:8088/v1"))
        assertNull(GatewayConnectionClient.modelsUrl("http://127.0.0.1:8088/v1?token=secret"))
    }

    @Test fun sendsBearerTokenAndMapsReady() {
        val exchange = exchange(200, "{\"data\":[]}")
        val result = awaitResult(exchange.baseUrl, "secret-token")
        assertEquals(GatewayConnectionResult.Ready, result)
        assertTrue(exchange.request.contains("GET /v1/models HTTP/1.1"))
        assertTrue(exchange.request.contains("Authorization: Bearer secret-token"))
        exchange.close()
    }

    @Test fun mapsUnauthorizedWithoutLeakingResponseBody() {
        val exchange = exchange(401, "{\"error\":\"secret upstream detail\"}")
        assertEquals(GatewayConnectionResult.AuthRequired, awaitResult(exchange.baseUrl, "bad"))
        exchange.close()
    }

    @Test fun mapsNetworkFailureToSafeMessage() {
        val closed = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val port = closed.localPort
        closed.close()
        assertEquals(
            GatewayConnectionResult.Failure("PC Gateway'e ulaşılamadı"),
            awaitResult("http://127.0.0.1:$port/v1", ""),
        )
    }

    private fun awaitResult(baseUrl: String, token: String): GatewayConnectionResult {
        val latch = CountDownLatch(1)
        var result: GatewayConnectionResult? = null
        GatewayConnectionClient().test(baseUrl, token) { result = it; latch.countDown() }
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        return requireNotNull(result)
    }

    private fun exchange(code: Int, body: String): Exchange {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val requestLatch = CountDownLatch(1)
        val lines = mutableListOf<String>()
        thread(isDaemon = true) {
            server.accept().use { socket ->
                val reader = socket.getInputStream().bufferedReader()
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                    lines += line
                }
                val bytes = body.toByteArray(StandardCharsets.UTF_8)
                val reason = if (code == 200) "OK" else "Unauthorized"
                socket.getOutputStream().use { out ->
                    out.write("HTTP/1.1 $code $reason\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    out.write(bytes)
                }
            }
            requestLatch.countDown()
        }
        return Exchange(server, "http://127.0.0.1:${server.localPort}/v1", lines, requestLatch)
    }

    private data class Exchange(
        val server: ServerSocket,
        val baseUrl: String,
        val lines: List<String>,
        val requestLatch: CountDownLatch,
    ) {
        val request: String get() { requestLatch.await(5, TimeUnit.SECONDS); return lines.joinToString("\n") }
        fun close() = server.close()
    }
}
