package com.nova.agent

import com.nova.agent.net.NovaClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class NovaClientTest {

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
