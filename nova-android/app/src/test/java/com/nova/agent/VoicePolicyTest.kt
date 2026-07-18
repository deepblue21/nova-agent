package com.nova.agent

import com.nova.agent.llm.ExecutionPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicePolicyTest {

    @Test
    fun `telefonda calisan politikalar cevrimdisi sesi tercih eder`() {
        assertTrue(ExecutionPolicy.LOCAL_FIRST.prefersOfflineVoice)
        assertTrue(ExecutionPolicy.LOCAL_ONLY.prefersOfflineVoice)
        assertTrue(ExecutionPolicy.HYBRID.prefersOfflineVoice)
    }

    @Test
    fun `salt gateway en iyi tanima icin cevrimdisi zorlamaz`() {
        assertFalse(ExecutionPolicy.GATEWAY_ONLY.prefersOfflineVoice)
    }
}
