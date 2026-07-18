package com.nova.agent

import com.nova.agent.llm.local.DownloadPreflight
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadPreflightTest {

    private val gb = 1_073_741_824L

    @Test
    fun `yeterli alan varsa gecer`() {
        assertTrue(DownloadPreflight.hasRoom(freeBytes = 2 * gb, sizeBytes = 1 * gb))
    }

    @Test
    fun `yetersiz alan reddedilir`() {
        assertFalse(DownloadPreflight.hasRoom(freeBytes = 500 * 1_048_576L, sizeBytes = 1 * gb))
    }

    @Test
    fun `surdurmede yalniz kalan bayt gerekir`() {
        // 1 GB'lik modelin 900 MB'i inmis; 200 MB bos yer yeter.
        val already = 900L * 1_048_576
        assertTrue(DownloadPreflight.hasRoom(200L * 1_048_576, sizeBytes = gb, alreadyBytes = already))
    }

    @Test
    fun `gereken alan pay icerir ve mesaj uretir`() {
        assertTrue(DownloadPreflight.requiredFreeBytes(1000) >= 1000)
        val msg = DownloadPreflight.shortfallMessage(0, gb)
        assertTrue(msg.contains("Yetersiz depolama"))
    }
}
