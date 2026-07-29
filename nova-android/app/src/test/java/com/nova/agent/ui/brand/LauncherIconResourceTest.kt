package com.nova.agent.ui.brand

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherIconResourceTest {
    private val appDir: File = run {
        val workingDir = File(System.getProperty("user.dir"))
        if (File(workingDir, "src/main/res").isDirectory) workingDir else File(workingDir, "app")
    }

    @Test
    fun `adaptive icon uses selected aperture and amethyst cyan palette`() {
        val background = drawable("ic_launcher_background.xml")
        val foreground = drawable("ic_launcher_foreground.xml")
        val monochrome = drawable("ic_launcher_monochrome.xml")

        assertTrue(background.contains("#FF15162B"))
        assertTrue(background.contains("#FF060711"))
        assertTrue(foreground.contains("M280,128"))
        assertTrue(foreground.contains("#FF7558FF"))
        assertTrue(foreground.contains("#FF5EE8FF"))
        assertTrue(monochrome.contains("M280,128"))
    }

    @Test
    fun `legacy Horus eye geometry is absent`() {
        val foreground = drawable("ic_launcher_foreground.xml")
        val monochrome = drawable("ic_launcher_monochrome.xml")

        assertFalse(foreground.contains("M27,51"))
        assertFalse(foreground.contains("M28,37.5"))
        assertFalse(monochrome.contains("M27,51"))
        assertFalse(monochrome.contains("M28,37.5"))
    }

    private fun drawable(name: String): String =
        File(appDir, "src/main/res/drawable/$name").readText()
}
