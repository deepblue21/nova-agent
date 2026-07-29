// OTOMATİK ÜRETİLDİ — elle düzenleme.
// Kaynak: design/nova-tokens.json · Yeniden üret: npm run tokens
package com.nova.agent.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/* ---- Temel renkler (web CSS değişkenleriyle birebir) ---- */
val Bg = Color(0xFF0A0E1A)
val Bg2 = Color(0xFF0D1326)
val Bg3 = Color(0xFF080A11)
val Surface1 = Color(0x0DFFFFFF)
val Surface2 = Color(0x14FFFFFF)
val Panel = Color(0xC70D1326)
val BlurTint = Color(0xB80A0E1A)
val Scrim = Color(0xAD000000)
val Line = Color(0x17FFFFFF)
val TextMain = Color(0xFFEEF1FB)
val Muted = Color(0xFF9AA3BD)
val Muted2 = Color(0xFF5B6276)
val Success = Color(0xFF53D6A6)
val Warning = Color(0xFFFFC857)
val Danger = Color(0xFFFB7185)
val Ember = Color(0xFFFF8A5B)

/** Pulse Aperture marka işaretinin tema başına renkleri. */
data class NovaApertureColors(
    val light: Color,
    val mid: Color,
    val deep: Color,
    val glow: Color,
    val core: Color,
)

/** Temaya özel mat yüzeyler; tanımlı değilse ortak koyu yüzeyler kullanılır. */
data class NovaSurfaceColors(
    val bg: Color,
    val bg2: Color,
    val bg3: Color,
    val tint: Color,
) {
    companion object {
        fun default(): NovaSurfaceColors = NovaSurfaceColors(
            bg = Bg,
            bg2 = Bg2,
            bg3 = Bg3,
            tint = Bg2,
        )
    }
}

/** Aksan teması — web `ACCENTS` dizisiyle aynı id/sıra. */
data class NovaAccent(
    val id: String,
    val name: String,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val onPrimary: Color,
    val aperture: NovaApertureColors,
    val surface: NovaSurfaceColors? = null,
)

val NOVA_ACCENTS: List<NovaAccent> = listOf(
    NovaAccent(
        id = "amethyst",
        name = "Ametist",
        primary = Color(0xFF7558FF),
        secondary = Color(0xFF5EE8FF),
        tertiary = Color(0xFFB7A7FF),
        onPrimary = Color(0xFFFFFAF1),
        aperture = NovaApertureColors(
            light = Color(0xFFB7A7FF),
            mid = Color(0xFF7558FF),
            deep = Color(0xFF4525ED),
            glow = Color(0xFF5EE8FF),
            core = Color(0xFFFFFAF1),
        ),
    ),
    NovaAccent(
        id = "arctic",
        name = "Arktik",
        primary = Color(0xFF27D9E8),
        secondary = Color(0xFFB0FFFF),
        tertiary = Color(0xFF8FF7FF),
        onPrimary = Color(0xFF030C10),
        aperture = NovaApertureColors(
            light = Color(0xFF8FF7FF),
            mid = Color(0xFF27D9E8),
            deep = Color(0xFF0795B6),
            glow = Color(0xFFB0FFFF),
            core = Color(0xFFF8FFFF),
        ),
    ),
    NovaAccent(
        id = "sapphire",
        name = "Safir",
        primary = Color(0xFF398CFF),
        secondary = Color(0xFF74F3FF),
        tertiary = Color(0xFF8ED7FF),
        onPrimary = Color(0xFF040914),
        aperture = NovaApertureColors(
            light = Color(0xFF8ED7FF),
            mid = Color(0xFF398CFF),
            deep = Color(0xFF1454E8),
            glow = Color(0xFF74F3FF),
            core = Color(0xFFF8FEFF),
        ),
    ),
    NovaAccent(
        id = "emerald",
        name = "Zümrüt",
        primary = Color(0xFF20BD9C),
        secondary = Color(0xFF66F3E7),
        tertiary = Color(0xFF78E9C4),
        onPrimary = Color(0xFF040D0A),
        aperture = NovaApertureColors(
            light = Color(0xFF78E9C4),
            mid = Color(0xFF20BD9C),
            deep = Color(0xFF078C78),
            glow = Color(0xFF66F3E7),
            core = Color(0xFFF6FFF9),
        ),
    ),
    NovaAccent(
        id = "lime",
        name = "Limon",
        primary = Color(0xFF9FE02A),
        secondary = Color(0xFFE7FF94),
        tertiary = Color(0xFFD7FF76),
        onPrimary = Color(0xFF080D03),
        aperture = NovaApertureColors(
            light = Color(0xFFD7FF76),
            mid = Color(0xFF9FE02A),
            deep = Color(0xFF61A80B),
            glow = Color(0xFFE7FF94),
            core = Color(0xFFFFFDF0),
        ),
    ),
    NovaAccent(
        id = "amber",
        name = "Amber",
        primary = Color(0xFFF6AD2F),
        secondary = Color(0xFFFFC85B),
        tertiary = Color(0xFFFFE18A),
        onPrimary = Color(0xFF0E0A03),
        aperture = NovaApertureColors(
            light = Color(0xFFFFE18A),
            mid = Color(0xFFF6AD2F),
            deep = Color(0xFFD87808),
            glow = Color(0xFFFFC85B),
            core = Color(0xFFFFF9E7),
        ),
    ),
    NovaAccent(
        id = "copper",
        name = "Bakır",
        primary = Color(0xFFDC7136),
        secondary = Color(0xFFFFC27F),
        tertiary = Color(0xFFF5B17C),
        onPrimary = Color(0xFF0E0704),
        aperture = NovaApertureColors(
            light = Color(0xFFF5B17C),
            mid = Color(0xFFDC7136),
            deep = Color(0xFFA63F18),
            glow = Color(0xFFFFC27F),
            core = Color(0xFFFFF7EA),
        ),
    ),
    NovaAccent(
        id = "coral",
        name = "Mercan",
        primary = Color(0xFFFF5F8F),
        secondary = Color(0xFFFFBF8F),
        tertiary = Color(0xFFFFAC9E),
        onPrimary = Color(0xFF0D060B),
        aperture = NovaApertureColors(
            light = Color(0xFFFFAC9E),
            mid = Color(0xFFFF5F8F),
            deep = Color(0xFFD82375),
            glow = Color(0xFFFFBF8F),
            core = Color(0xFFFFF8ED),
        ),
    ),
    NovaAccent(
        id = "ruby",
        name = "Kızıl",
        primary = Color(0xFFEF3158),
        secondary = Color(0xFFFFAD9E),
        tertiary = Color(0xFFFF8F9F),
        onPrimary = Color(0xFF0E0407),
        aperture = NovaApertureColors(
            light = Color(0xFFFF8F9F),
            mid = Color(0xFFEF3158),
            deep = Color(0xFFB80E36),
            glow = Color(0xFFFFAD9E),
            core = Color(0xFFFFF9F2),
        ),
        surface = NovaSurfaceColors(
            bg = Color(0xFF050507),
            bg2 = Color(0xFF0A0A0D),
            bg3 = Color(0xFF030304),
            tint = Color(0xFF17070C),
        ),
    ),
    NovaAccent(
        id = "lavender",
        name = "Lavanta",
        primary = Color(0xFFAD7EE9),
        secondary = Color(0xFFE8CFFF),
        tertiary = Color(0xFFDCC5FF),
        onPrimary = Color(0xFF0A0713),
        aperture = NovaApertureColors(
            light = Color(0xFFDCC5FF),
            mid = Color(0xFFAD7EE9),
            deep = Color(0xFF8050C8),
            glow = Color(0xFFE8CFFF),
            core = Color(0xFFFFFAFB),
        ),
    ),
    NovaAccent(
        id = "moonstone",
        name = "Aytaşı",
        primary = Color(0xFF9EABC1),
        secondary = Color(0xFFCCEFFF),
        tertiary = Color(0xFFE1E8F2),
        onPrimary = Color(0xFF070A11),
        aperture = NovaApertureColors(
            light = Color(0xFFE1E8F2),
            mid = Color(0xFF9EABC1),
            deep = Color(0xFF5F708E),
            glow = Color(0xFFCCEFFF),
            core = Color(0xFFFFFDF8),
        ),
    ),
)

const val DEFAULT_ACCENT_ID = "amethyst"

val NOVA_THEME_ALIASES: Map<String, String> = mapOf(
    "aurora" to "arctic",
    "nova" to "arctic",
    "plum" to "lavender",
    "violet" to "amethyst",
    "aperture" to "amethyst",
    "kizil" to "ruby",
    "okyanus" to "sapphire",
    "zumrut" to "emerald",
    "gul" to "coral",
    "gunbatimi" to "copper",
    "amber" to "amber",
)

fun normalizeThemeId(id: String?): String {
    val mapped = id?.let { NOVA_THEME_ALIASES[it] ?: it }
    return mapped?.takeIf { candidate -> NOVA_ACCENTS.any { it.id == candidate } }
        ?: DEFAULT_ACCENT_ID
}

fun accentFor(id: String?): NovaAccent =
    NOVA_ACCENTS.first { it.id == normalizeThemeId(id) }

/** Köşe yarıçapları — web `--radius-*` ile aynı. */
object NovaRadius {
    val xs = 8.dp
    val sm = 10.dp
    val md = 13.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 26.dp
    val pill = 999.dp
}

/** Boşluk skalası — web `--space-*` ile aynı. */
object NovaSpace {
    val xxs = 4.dp
    val xs = 6.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 26.dp
    val xxxl = 32.dp
}

/** Hareket süreleri (ms) — web `--dur-*` ile aynı. */
object NovaDuration {
    const val instant = 120
    const val fast = 180
    const val base = 240
    const val slow = 420
    const val reveal = 620
    const val orbCycle = 9000
    const val auroraDrift = 28000
}

/** Orb geometrisi — web canvas orb'u ile aynı formül. */
object NovaOrb {
    const val blobCount = 5
    const val particleCount = 54
    const val coreRadiusRatio = 0.55f
    const val ringRadiusRatio = 1.16f
    const val levelGain = 0.22f
}

/**
 * NOVA Pulse Aperture marka işareti — web `APERTURE` ile birebir aynı
 * geometri, yollar ve zamanlama. Renkler burada DEĞİL: aktif aksanın
 * [NovaAccent.aperture] setinden gelir.
 */
object NovaApertureTokens {
    const val viewport = 560f
    const val originX = 280f
    const val originY = 282f
    const val componentSizeDp = 44f
    const val haloDiameterDp = 36f
    const val haloDotCount = 14
    const val markRestSizeDp = 18f
    const val markPeakSizeDp = 23f
    const val coreX = 280f
    const val coreY = 286f
    const val coreGlowRadius = 58f
    const val coreRadius = 22f
    const val highlightX = 273f
    const val highlightY = 278f
    const val highlightRadius = 6.5f
    const val strokeAlpha = 0.42f

    const val breathDurationMillis = 2750
    const val corePulseDurationMillis = 1375
    const val orbitDurationMillis = 4200
    const val shimmerDurationMillis = 5500
    const val microTiltDegrees = 0.7f

    const val bodyPathData = "M280,128 C319,128 319,205 354,226 C381,242 429,244 437,277 C445,311 390,323 361,342 C327,365 319,433 280,436 C241,433 233,365 199,342 C170,323 115,311 123,277 C131,244 179,242 206,226 C241,205 241,128 280,128Z"

    const val foldPathData = "M250,130 C285,178 324,198 316,239 C309,277 250,282 246,324 C243,359 286,386 294,434 C261,420 248,379 219,354 C188,327 153,319 126,297 C164,285 203,267 225,238 C249,207 242,165 250,130Z"

    const val bodyGradientStartX = 138f
    const val bodyGradientStartY = 128f
    const val bodyGradientEndX = 420f
    const val bodyGradientEndY = 428f
    const val foldGradientStartX = 174f
    const val foldGradientStartY = 132f
    const val foldGradientEndX = 332f
    const val foldGradientEndY = 420f
}
