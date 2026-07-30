package com.nova.agent.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Renk/aksan/ölçü token'ları NovaTokens.kt'de ÜRETİLİR (kaynak:
// design/nova-tokens.json, `npm run tokens`). Bu dosya yalnız çalışma zamanı
// tema bağlamasını içerir; token değerleri burada tekrar tanımlanmaz.

/** Token adı `Warning`; arayüzdeki tarihsel ad korunur. */
val Amber: Color = Warning

/** Token adı `Ember`; arayüzdeki tarihsel ad korunur. */
val Coral: Color = Ember

/**
 * Yürürlükteki aksan teması.
 *
 * [MaterialTheme.colorScheme] Material bileşenlerini kaplar; Material dışı
 * çizimler (orb, gradyan butonlar, metin imleci) bu CompositionLocal'i okur.
 * İkisi birlikte olmazsa tema değişimi ekranın yarısında takılı kalır.
 */
val LocalNovaAccent = staticCompositionLocalOf { accentFor(DEFAULT_ACCENT_ID) }

/** Marka gradyanı (vurgu → ikincil). Tema değişince kendiliğinden güncellenir. */
@Composable
fun accentBrush(): Brush {
    val accent = LocalNovaAccent.current
    return remember(accent) { Brush.linearGradient(listOf(accent.primary, accent.secondary)) }
}

/**
 * Ses ekranındaki orb paleti: aksanın üç rengi + ortak ember vurgusu.
 * Aksan başına ayrı palet tutulmaz; token şeması tek kaynak kalır.
 */
@Composable
fun orbPalette(): List<Color> {
    val accent = LocalNovaAccent.current
    return remember(accent) {
        listOf(accent.primary, accent.secondary, accent.tertiary, Ember)
    }
}

@Composable
fun NovaTheme(themeId: String = DEFAULT_ACCENT_ID, content: @Composable () -> Unit) {
    val accent = accentFor(themeId)
    val surfaces = accent.surface ?: NovaSurfaceColors.default()
    val colors = darkColorScheme(
        primary = accent.primary,
        secondary = accent.secondary,
        tertiary = accent.tertiary,
        background = surfaces.bg,
        surface = surfaces.bg2,
        surfaceVariant = surfaces.bg3,
        surfaceContainer = surfaces.bg2,
        surfaceContainerLow = surfaces.bg3,
        surfaceContainerLowest = surfaces.bg,
        outline = accent.primary.copy(alpha = 0.20f),
        onPrimary = accent.onPrimary,
        onSecondary = accent.onPrimary,
        onTertiary = accent.onPrimary,
        onBackground = TextMain,
        onSurface = TextMain,
        // Yıkıcı eylem rengi aksandan bağımsız: Kızıl temada bile "sil"
        // düğmesi vurgu rengiyle karışmaz.
        error = Danger,
    )
    CompositionLocalProvider(LocalNovaAccent provides accent) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}
