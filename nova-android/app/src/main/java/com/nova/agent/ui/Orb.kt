package com.nova.agent.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import com.nova.agent.ui.theme.Azure
import com.nova.agent.ui.theme.Coral
import com.nova.agent.ui.theme.Cyan
import com.nova.agent.ui.theme.Violet
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun Orb(level: Float, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "orb")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart),
        label = "phase"
    )
    val shimmer = 0.5f + 0.5f * sin(t * 3f)
    val lv = (level + 0.05f * shimmer).coerceIn(0f, 1f)
    val palette = listOf(Cyan, Azure, Violet, Coral)

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val base = min(size.width, size.height) * 0.25f
        val r = base * (1f + lv * 0.22f)
        val center = Offset(cx, cy)

        // bloom
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Cyan.copy(alpha = 0.16f + lv * 0.25f), Color.Transparent),
                center = center, radius = r * 2.5f
            ),
            radius = r * 2.5f, center = center
        )

        // canlı bloblar (additive)
        for (i in 0 until 5) {
            val a = t * (1f + i * 0.15f) + i * (Math.PI * 2 / 5).toFloat()
            val dist = r * 0.30f * (0.6f + 0.4f * sin(t * (i + 1) * 0.5f))
            val x = cx + cos(a) * dist * (1f + lv * 0.5f)
            val y = cy + sin(a * 1.1f) * dist * (1f + lv * 0.5f)
            val rad = r * (0.42f + 0.20f * sin(t * (1f + i)))
            val c = palette[i % palette.size]
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(c.copy(alpha = 0.5f + lv * 0.3f), Color.Transparent),
                    center = Offset(x, y), radius = rad
                ),
                radius = rad, center = Offset(x, y), blendMode = BlendMode.Plus
            )
        }

        // parlak çekirdek
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.45f + lv * 0.4f), Color.Transparent),
                center = center, radius = r * 0.55f
            ),
            radius = r * 0.55f, center = center
        )

        // ince halka
        drawCircle(
            color = Cyan.copy(alpha = 0.10f + lv * 0.18f),
            radius = r * 1.16f, center = center, style = Stroke(width = 2f)
        )
    }
}
