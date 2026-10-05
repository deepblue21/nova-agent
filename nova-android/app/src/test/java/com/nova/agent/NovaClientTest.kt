package com.nova.agent

import com.nova.agent.net.NovaClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class NovaClientTest {

    @Test
    fun supersededAndCancelledStreamsCannotDeliverCallbacks() {
        val listeners = mutableListOf<okhttp3.sse.EventSourceListener>()
        val sources = mutableListOf<okhttp3.sse.EventSource>()
        val factory = okhttp3.sse.EventSource.Factory { request, listener ->
            listeners += listener
            object : okhttp3.sse.EventSource {
                override fun request() = request
                override fun cancel() {}
            }.also { sources += it }
        }
        val client = NovaClient(factory)
        val events = mutableListOf<String>()
        val cb = object : NovaClient.Callbacks {
            override fun onRoute(route: String) { events += route }
            override fun onToken(text: String) { events += text }
            override fun onThought(text: String) { events += text }
            override fun onDone() { events += "done" }
            override fun onError(message: String) { events += message }
        }
        fun start() = client.stream("http://127.0.0.1:8088/v1", "", "auto", "balanced", false, emptyList(), cb)
        start()
        start()
        val response = okhttp3.Response.Builder().request(sources[0].request())
            .protocol(okhttp3.Protocol.HTTP_1_1).code(200).message("OK").header("x-nova-route", "old").build()
        listeners[0].onOpen(sources[0], response)
        listeners[0].onEvent(sources[0], null, null, """{"choices":[{"delta":{"content":"old","reasoning_content":"old"}}]}""")
        listeners[0].onClosed(sources[0])
        listeners[0].onFailure(sources[0], java.io.IOException("Canceled"), response)
        assertEquals(emptyList<String>(), events)
        listeners[1].onEvent(sources[1], null, null, """{"choices":[{"delta":{"content":"current"}}]}""")
        assertEquals(listOf("current"), events)
        client.cancelStream(sources[1])
        listeners[1].onEvent(sources[1], null, null, """{"choices":[{"delta":{"content":"late"}}]}""")
        listeners[1].onClosed(sources[1])
        listeners[1].onFailure(sources[1], java.io.IOException("Canceled"), response)
        assertEquals(listOf("current"), events)
    }

    @Test
    fun parsesContentDelta() {
        val data = """{"choices":[{"delta":{"content":"merhaba"}}]}"""
        assertEquals("merhaba", NovaClient.parseDelta(data))
    }

    @Test
    fun ignoresDoneSentinel() {
        assertNull(NovaClient.parseDelta("[DONE]"))
    }

    @Test
    fun ignoresEmptyString() {
        assertNull(NovaClient.parseDelta(""))
    }

    @Test
    fun ignoresEmptyDelta() {
        assertNull(NovaClient.parseDelta("""{"choices":[{"delta":{}}]}"""))
    }

    @Test
    fun ignoresMalformedJson() {
        assertNull(NovaClient.parseDelta("not-json"))
    }

    @Test
    fun ignoresMissingChoices() {
        assertNull(NovaClient.parseDelta("""{"id":"x"}"""))
    }

    @Test
    fun malformedPersistedUrlReturnsSanitizedCallbackErrorInsteadOfThrowing() {
        var error: String? = null
        val result = runCatching {
            NovaClient().stream(
                baseUrl = "not a url/private-path",
                token = "private-token",
                model = "auto",
                effort = "balanced",
                reasoning = true,
                history = emptyList(),
                cb = object : NovaClient.Callbacks {
                    override fun onError(message: String) {
                        error = message
                    }
                },
            )
        }

        assertNull("Malformed stored URLs must not throw synchronously", result.exceptionOrNull())
        assertNull(result.getOrNull())
        assertEquals("Gateway adresi geçersiz", error)
        assertFalse(error.orEmpty().contains("not a url"))
        assertFalse(error.orEmpty().contains("private-token"))
    }
}
