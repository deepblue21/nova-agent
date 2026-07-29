package com.nova.agent

import androidx.compose.ui.graphics.Color
import com.nova.agent.ui.theme.DEFAULT_ACCENT_ID
import com.nova.agent.ui.theme.NOVA_ACCENTS
import com.nova.agent.ui.theme.accentFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Üretilen tema kataloğunun (design/nova-tokens.json → NovaTokens.kt)
 * sözleşmesi: kimlikler benzersiz, bilinmeyen kimlik varsayılana düşer ve
 * her aksanın okunur bir üst-metin rengi olur.
 */
class NovaAccentTest {

    @Test
    fun shippedThemesMatchWebOrder() {
        assertEquals(
            listOf(
                "amethyst",
                "arctic",
                "sapphire",
                "emerald",
                "lime",
                "amber",
                "copper",
                "coral",
                "ruby",
                "lavender",
                "moonstone",
            ),
            NOVA_ACCENTS.map { it.id },
        )
    }

    @Test
    fun unknownOrMissingIdFallsBackToDefault() {
        assertEquals("amethyst", DEFAULT_ACCENT_ID)
        val fallback = accentFor(DEFAULT_ACCENT_ID)
        assertEquals(fallback, accentFor(null))
        assertEquals(fallback, accentFor(""))
        assertEquals(fallback, accentFor("bilinmeyen-tema"))
    }

    @Test
    fun rubyUsesBlackDominantSurfaces() {
        val ruby = accentFor("ruby")
        assertEquals("Kızıl", ruby.name)
        assertEquals(Color(0xFF050507), ruby.surface?.bg)
        assertEquals(Color(0xFF0A0A0D), ruby.surface?.bg2)
        assertEquals(Color(0xFF030304), ruby.surface?.bg3)
        assertEquals(Color(0xFF17070C), ruby.surface?.tint)
    }

    @Test
    fun apertureAccentMatchesTheApprovedBrandPalette() {
        val amethyst = accentFor("amethyst")
        assertEquals("amethyst", amethyst.id)
        assertEquals("Ametist", amethyst.name)
        assertEquals(Color(0xFF7558FF), amethyst.primary)
        assertEquals(Color(0xFF5EE8FF), amethyst.secondary)
        assertEquals(Color(0xFFB7A7FF), amethyst.tertiary)
        assertEquals(Color(0xFFFFFAF1), amethyst.onPrimary)
        assertEquals(Color(0xFF4525ED), amethyst.aperture.deep)
    }

    @Test
    fun everyAccentHasReadableOnPrimary() {
        NOVA_ACCENTS.forEach { accent ->
            assertTrue(accent.id, accent.name.isNotBlank())
            // Vurgu üstü metin rengi vurgunun kendisi olamaz (okunmaz olurdu).
            assertTrue(accent.id, accent.onPrimary != accent.primary)
        }
    }

    @Test
    fun primaryColorsAreDistinctAcrossAccents() {
        val primaries = NOVA_ACCENTS.map { it.primary }
        assertEquals(primaries.size, primaries.toSet().size)
    }
}
