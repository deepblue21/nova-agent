package com.nova.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LatestConnectionProbeTest {
    @Test
    fun onlyLatestProbeCanComplete() {
        val probes = LatestConnectionProbe()
        val completions = mutableListOf<String>()
        val stale = probes.start()
        val latest = probes.start()

        probes.complete(stale) { completions += "stale" }
        probes.complete(latest) { completions += "latest" }

        assertEquals(listOf("latest"), completions)
    }

    @Test
    fun invalidationPreventsAlreadyPostedCompletion() {
        val probes = LatestConnectionProbe()
        val posted = probes.start()
        var completed = false

        probes.invalidate()
        probes.complete(posted) { completed = true }

        assertFalse(completed)
    }
}
