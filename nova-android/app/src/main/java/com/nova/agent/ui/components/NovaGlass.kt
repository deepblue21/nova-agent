package com.nova.agent.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.nova.agent.ui.theme.TextMain

/** Tinted glass without live blur: local inference keeps the device's rendering budget. */
@Composable
fun Modifier.novaGlass(
    shape: Shape = RoundedCornerShape(22.dp),
    emphasized: Boolean = false,
): Modifier {
    val colors = MaterialTheme.colorScheme
    val fill = remember(colors.surface, colors.primary, emphasized) {
        Brush.linearGradient(
            listOf(
                colors.surface.copy(alpha = 0.94f),
                colors.primary.copy(alpha = if (emphasized) 0.22f else 0.085f),
                colors.surface.copy(alpha = 0.90f),
            ),
        )
    }
    val rim = remember(colors.secondary, emphasized) {
        Brush.linearGradient(
            listOf(
                TextMain.copy(alpha = 0.20f),
                colors.secondary.copy(alpha = if (emphasized) 0.38f else 0.14f),
                TextMain.copy(alpha = 0.06f),
            ),
        )
    }
    return clip(shape).background(fill).border(1.dp, rim, shape)
}

/** Static, cached light fields. No idle animation or offscreen blur buffers. */
@Composable
fun NovaBackdrop(content: @Composable BoxScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier.fillMaxSize().background(colors.background).drawWithCache {
            val topLight = Brush.radialGradient(
                listOf(colors.primary.copy(alpha = 0.25f), Color.Transparent),
                center = Offset(size.width * 0.05f, size.height * 0.12f),
                radius = size.width * 1.05f,
            )
            val sideLight = Brush.radialGradient(
                listOf(colors.secondary.copy(alpha = 0.13f), Color.Transparent),
                center = Offset(size.width * 1.05f, size.height * 0.58f),
                radius = size.width * 0.85f,
            )
            onDrawBehind {
                drawRect(topLight)
                drawRect(sideLight)
            }
        },
        content = content,
    )
}
