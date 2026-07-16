package com.nova.agent.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Bg = Color(0xFF06070B)
val Bg2 = Color(0xFF0B0D14)
val Surface1 = Color(0x0FFFFFFF)
val Surface2 = Color(0x14FFFFFF)
val Line = Color(0x17FFFFFF)
val LineBright = Color(0x4738E1D6)
val TextMain = Color(0xFFE9EDF6)
val Muted = Color(0xFF8B93A7)
val Muted2 = Color(0xFF5B6276)
val Cyan = Color(0xFF38E1D6)
val Azure = Color(0xFF2BA0FF)
val Violet = Color(0xFF786EFF)
val Coral = Color(0xFFFF8A5B)
val Amber = Color(0xFFFFC857)
val Success = Color(0xFF53D6A6)

/** Seçilebilir vurgu temaları (Faz 1). Ayarlar > Görünüm'den değiştirilir. */
data class NovaAccent(
    val id: String,
    val name: String,
    val primary: Color,
    val secondary: Color,
)

val NOVA_ACCENTS = listOf(
    NovaAccent("nova", "Turkuaz", Cyan, Azure),
    NovaAccent("aurora", "Aurora", Violet, Color(0xFFB388FF)),
  