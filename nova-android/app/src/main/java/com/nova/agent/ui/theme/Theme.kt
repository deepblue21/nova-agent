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

private val NovaColors = darkColorScheme(
    primary = Cyan,
    secondary = Azure,
    background = Bg,
    surface = Bg2,
    onPrimary = Color(0xFF04121A),
    onBackground = TextMain,
    onSurface = TextMain,
)

@Composable
fun NovaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = NovaColors, content = content)
}
