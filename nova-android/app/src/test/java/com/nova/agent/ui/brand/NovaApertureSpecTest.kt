package com.nova.agent.ui.brand

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NovaApertureSpecTest {
    @Test
    fun `selected motion remains compact calm and orbital`() {
        assertEquals(44f, NovaApertureSpec.componentSizeDp, 0f)
        assertEquals(14, NovaApertureSpec.haloDotCount)
        assertEquals(18f, NovaApertureSpec.markRestSizeDp, 0f)
        assertEquals(23f, NovaApertureSpec.markPeakSizeDp, 0f)
        assertEquals(2_750, NovaApertureSpec.breathDurationMillis)
        assertEquals(4_200, NovaApertureSpec.orbitDurationMillis)
        assertTrue(NovaApertureSpec.microTiltDegrees <= 0.7f)
    }

    @Test
    fun `selected aperture geometry includes body and inner fold`() {
        assertTrue(NovaApertureSpec.bodyPathData.startsWith("M280,128"))
        assertTrue(NovaApertureSpec.bodyPathData.endsWith("Z"))
        assertTrue(NovaApertureSpec.foldPathData.startsWith("M250,130"))
        assertTrue(NovaApertureSpec.foldPathData.endsWith("Z"))
    }
}
