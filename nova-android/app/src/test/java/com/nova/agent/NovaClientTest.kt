package com.nova.agent

import com.nova.agent.net.NovaClient
import org.junit.Assert.assertEquals
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
}
