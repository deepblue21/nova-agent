package com.nova.agent

import com.nova.agent.net.BoundedEventSourceFactory
import com.nova.agent.net.MobileTaskClient
import com.nova.agent.net.withGatewayPolicy
import java.net.ServerSocket
import java.net.Socket
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import org.junit.Assert.*
import org.junit.Test

class GatewayTransportTest {
    @Test
    fun redirectsRecheckCleartextPolicyBeforeWritingThePrompt() {
        val captured = AtomicInteger()
        val server = LoopbackServer { path, output, port ->
            when (path) {
                "/public" -> respond(output, 307, "Location: http://public.invalid:$port/capture\r\n")
                "/local" -> respond(output, 307, "Location: http://127.0.0.1:$port/capture\r\n")
                else -> { captured.incrementAndGet(); respond(output, 200, body = "ok") }
            }
        }
        val client = OkHttpClient.Builder().dns(object : Dns {
            override fun lookup(hostname: String) = listOf(InetAddress.getByName("127.0.0.1"))
        }).callTimeout(2, TimeUnit.SECONDS).build().withGatewayPolicy()
        fun req(path: String) = Request.Builder().url("http://127.0.0.1:${server.port}/$path").post("private prompt".toRequestBody()).build()
        try {
            assertThrows(java.io.IOException::class.java) { client.newCall(req("public")).execute().close() }
            assertEquals(0, captured.get())
            client.newCall(req("local")).execute().use { assertEquals("ok", it.body.string()) }
            assertEquals(1, captured.get())
        } finally { server.close(); client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown() }
    }

    @Test
    fun streamHeartbeatCannotExtendDeadlineAndCancelSuppressesLateCallbacks() {
        lateinit var callback: EventSourceListener
        val cancelled = AtomicInteger()
        val req = Request.Builder().url("http://127.0.0.1/events").build()
        val underlying = object : EventSource { override fun request() = req; override fun cancel() { cancelled.incrementAndGet() } }
        val failed = CountDownLatch(1)
        val failures = AtomicInteger()
        val events = AtomicInteger()
        val factory = BoundedEventSourceFactory(EventSource.Factory { _, listener -> callback = listener; underlying }, 80)
        val source = factory.newEventSource(req, object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) { events.incrementAndGet() }
            override fun onFailure(eventSource: EventSource, t: Throwable?, response: okhttp3.Response?) { failures.incrementAndGet(); failed.countDown() }
        })
        val beat = Executors.newSingleThreadScheduledExecutor()
        beat.scheduleAtFixedRate({ callback.onEvent(underlying, null, null, "heartbeat") }, 0, 5, TimeUnit.MILLISECONDS)
        try {
            assertTrue(failed.await(2, TimeUnit.SECONDS))
            val before = events.get()
            source.cancel(); callback.onEvent(underlying, null, null, "late"); callback.onFailure(underlying, null, null)
            assertEquals(before, events.get()); assertEquals(1, failures.get()); assertTrue(cancelled.get() > 0)
        } finally { beat.shutdownNow(); source.cancel() }
    }

    @Test
    fun ordinaryMobileTaskRequestTimesOutWhenServerNeverResponds() {
        for (headersSent in listOf(false, true)) {
        val release = CountDownLatch(1)
        val server = LoopbackServer { _, output, _ ->
            if (headersSent) {
                output.write("HTTP/1.1 200 OK\r\nContent-Length: 100\r\nConnection: close\r\n\r\n".toByteArray()); output.flush()
            }
            release.await(3, TimeUnit.SECONDS)
        }
        val client = OkHttpClient.Builder().readTimeout(80, TimeUnit.MILLISECONDS).callTimeout(120, TimeUnit.MILLISECONDS).build()
        val completed = CountDownLatch(1)
        var success = true
        try {
            MobileTaskClient(client).createTask("http://127.0.0.1:${server.port}/v1", "", "task") { result -> success = result.isSuccess; completed.countDown() }
            assertTrue(completed.await(2, TimeUnit.SECONDS)); assertFalse(success)
        } finally { release.countDown(); server.close(); client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
        }
    }
}

private fun respond(output: OutputStream, status: Int, headers: String = "", body: String = "") {
    val bytes = body.toByteArray()
    val length = bytes.size
    output.write(("HTTP/1.1 $status Response\r\n" + headers + "Content-Length: $length\r\nConnection: close\r\n\r\n").toByteArray())
    output.write(bytes); output.flush()
}

private class LoopbackServer(private val handle: (String, OutputStream, Int) -> Unit) : AutoCloseable {
    private val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    val port: Int get() = server.localPort
    private val sockets = ConcurrentHashMap.newKeySet<Socket>()
    private val pool = Executors.newCachedThreadPool { r -> Thread(r, "loopback-test").apply { isDaemon = true } }
    init {
        pool.execute {
            while (!server.isClosed) {
                val socket = try { server.accept() } catch (_: java.io.IOException) { break }
                sockets += socket
                pool.execute {
                    try {
                        socket.use { s ->
                            s.soTimeout = 2000
                            val input = s.getInputStream().buffered()
                            val header = StringBuilder()
                            while (!header.endsWith("\r\n\r\n") && header.length < 32768) {
                                val ch = input.read()
                                if (ch < 0) return@use
                                header.append(ch.toChar())
                            }
                            val length = Regex("(?i)Content-Length: (\\d+)").find(header)?.groupValues?.get(1)?.toInt() ?: 0
                            repeat(length.coerceAtMost(100000)) { input.read() }
                            handle(header.toString().substringBefore("\r\n").split(" ")[1], s.getOutputStream(), port)
                        }
                    } catch (_: Exception) { /* closed by timeout/test teardown */ }
                    finally { sockets -= socket }
                }
            }
        }
    }
    override fun close() {
        server.close()
        sockets.forEach { it.close() }
        pool.shutdownNow()
    }
}
