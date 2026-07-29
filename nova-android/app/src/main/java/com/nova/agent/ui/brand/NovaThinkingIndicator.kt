package com.nova.agent.ui.brand

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nova.agent.ui.theme.LocalNovaAccent
import com.nova.agent.ui.theme.NovaApertureColors
import com.nova.agent.ui.theme.NovaApertureTokens
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Eğri, web'deki brand.aperture.motion.easing ile aynı: cubic-bezier(.45,0,.25,1)
private val MotionEasing = CubicBezierEasing(0.45f, 0f, 0.25f, 1f)

private data class NovaMotionFrame(
    val breath: Float,
    val corePulse: Float,
    val auraAlpha: Float,
    val orbitDegrees: Float,
    val shimmer: Float,
    val tiltDegrees: Float,
)

@Composable
private fun rememberNovaMotionFrame(animated: Boolean): NovaMotionFrame {
    if (!animated) {
        return NovaMotionFrame(
            breath = 0.36f,
            corePulse = 1f,
            auraAlpha = 0.72f,
            orbitDegrees = 18f,
            shimmer = 0.54f,
            tiltDegrees = 0f,
        )
    }

    val transition = rememberInfiniteTransition(label = "nova-aperture")
    val breath by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(NovaApertureTokens.breathDurationMillis, easing = MotionEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "body-breath",
    )
    val corePulse by transition.animateFloat(
        initialValue = 0.82f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(NovaApertureTokens.corePulseDurationMillis, easing = MotionEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "core-pulse",
    )
    val auraAlpha by transition.animateFloat(
        initialValue = 0.38f,
        targetValue = 0.94f,
        animationSpec = infiniteRepeatable(
            animation = tween(NovaApertureTokens.breathDurationMillis, easing = MotionEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "aura-pulse",
    )
    val orbitDegrees by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(NovaApertureTokens.orbitDurationMillis),
        ),
        label = "orbital-halo",
    )
    val shimmer by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(NovaApertureTokens.shimmerDurationMillis, easing = MotionEasing),
        ),
        label = "light-sweep",
    )
    val tiltDegrees by transition.animateFloat(
        initialValue = -NovaApertureTokens.microTiltDegrees,
        targetValue = NovaApertureTokens.microTiltDegrees,
        animationSpec = infiniteRepeatable(
            animation = tween(NovaApertureTokens.breathDurationMillis, easing = MotionEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "micro-tilt",
    )
    return NovaMotionFrame(
        breath = breath,
        corePulse = corePulse,
        auraAlpha = auraAlpha,
        orbitDegrees = orbitDegrees,
        shimmer = shimmer,
        tiltDegrees = tiltDegrees,
    )
}

/**
 * NOVA marka işareti — uygulamanın tek marka görseli.
 *
 * Gövde nefes alır ve yumuşak bir ışık yakalar; seyrek bir halo çevresinde
 * döner, gövdenin kendisi dönmez. Renkler aktif aksanın
 * [NovaApertureColors] setinden gelir: tema değişince ikon da değişir.
 * Geometri ve zamanlama [NovaApertureTokens] üzerinden web ile ortaktır.
 */
@Composable
fun NovaThinkingIndicator(
    modifier: Modifier = Modifier,
    animated: Boolean = true,
    showHalo: Boolean = true,
    contentLabel: String = "Yanıt hazırlanıyor",
    testTag: String = "nova_thinking_indicator",
) {
    val motion = rememberNovaMotionFrame(animated)
    val palette = LocalNovaAccent.current.aperture
    val bodyPath = remember {
        PathParser().parsePathString(NovaApertureTokens.bodyPathData).toPath()
    }
    val foldPath = remember {
        PathParser().parsePathString(NovaApertureTokens.foldPathData).toPath()
    }

    Canvas(
        modifier = modifier
            .size(NovaApertureTokens.componentSizeDp.dp)
            .semantics { contentDescription = contentLabel }
            .testTag(testTag),
    ) {
        val component = size.minDimension
        val center = Offset(size.width / 2f, size.height / 2f)
        val haloRadius = component * (NovaApertureTokens.haloDiameterDp /
            NovaApertureTokens.componentSizeDp) / 2f

        if (showHalo) {
            drawCircle(
                color = Color(0xFF8792C8).copy(alpha = 0.13f),
                radius = haloRadius,
                center = center,
                style = Stroke(width = 0.7.dp.toPx()),
            )

            repeat(NovaApertureTokens.haloDotCount) { index ->
                val degrees = motion.orbitDegrees + index * (360f / NovaApertureTokens.haloDotCount)
                val radians = degrees / 180f * PI.toFloat()
                val dotCenter = Offset(
                    x = center.x + cos(radians) * haloRadius,
                    y = center.y + sin(radians) * haloRadius,
                )
                val emphasized = index % 5 == 0
                drawCircle(
                    color = if (index % 3 == 0) {
                        palette.glow.copy(alpha = if (emphasized) 0.94f else 0.58f)
                    } else {
                        Color(0xFFE1E5FF).copy(alpha = if (emphasized) 0.82f else 0.46f)
                    },
                    radius = if (emphasized) 1.15.dp.toPx() else 0.72.dp.toPx(),
                    center = dotCenter,
                )
            }

            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color.Transparent,
                        palette.glow.copy(alpha = 0.88f),
                        palette.light.copy(alpha = 0.72f),
                        Color.Transparent,
                    ),
                    center = center,
                ),
                startAngle = motion.orbitDegrees - 38f,
                sweepAngle = 76f,
                useCenter = false,
                topLeft = Offset(center.x - haloRadius * 0.92f, center.y - haloRadius * 0.92f),
                size = Size(haloRadius * 1.84f, haloRadius * 1.84f),
                style = Stroke(width = 1.15.dp.toPx(), cap = StrokeCap.Round),
            )
        }

        val restRatio = NovaApertureTokens.markRestSizeDp / NovaApertureTokens.componentSizeDp
        val peakRatio = NovaApertureTokens.markPeakSizeDp / NovaApertureTokens.componentSizeDp
        val markSize = component * (restRatio + (peakRatio - restRatio) * motion.breath)
        val markScale = markSize / 314f

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.mid.copy(alpha = motion.auraAlpha * 0.38f),
                    palette.deep.copy(alpha = motion.auraAlpha * 0.13f),
                    Color.Transparent,
                ),
                center = center,
                radius = markSize * 0.95f,
            ),
            radius = markSize,
            center = center,
        )

        withTransform({
            translate(center.x, center.y)
            rotate(motion.tiltDegrees, pivot = Offset.Zero)
            scale(markScale, markScale, pivot = Offset.Zero)
            translate(-NovaApertureTokens.originX, -NovaApertureTokens.originY)
        }) {
            drawPath(
                path = bodyPath,
                brush = Brush.linearGradient(
                    colors = listOf(palette.light, palette.mid, palette.deep),
                    start = Offset(
                        NovaApertureTokens.bodyGradientStartX,
                        NovaApertureTokens.bodyGradientStartY,
                    ),
                    end = Offset(
                        NovaApertureTokens.bodyGradientEndX,
                        NovaApertureTokens.bodyGradientEndY,
                    ),
                ),
            )

            clipPath(bodyPath) {
                drawCircle(
                    color = palette.glow.copy(alpha = 0.48f),
                    radius = 74f,
                    center = Offset(235f + motion.breath * 24f, 242f),
                )
                drawCircle(
                    color = palette.deep.copy(alpha = 0.62f),
                    radius = 92f,
                    center = Offset(338f - motion.breath * 22f, 316f),
                )
                drawPath(
                    path = foldPath,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            palette.light.copy(alpha = 0.92f),
                            palette.mid.copy(alpha = 0.68f),
                            palette.deep.copy(alpha = 0.16f),
                        ),
                        start = Offset(
                            NovaApertureTokens.foldGradientStartX,
                            NovaApertureTokens.foldGradientStartY,
                        ),
                        end = Offset(
                            NovaApertureTokens.foldGradientEndX,
                            NovaApertureTokens.foldGradientEndY,
                        ),
                    ),
                )

                val sweepX = 72f + motion.shimmer * 420f
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            palette.glow.copy(alpha = 0.12f),
                            Color.White.copy(alpha = 0.52f),
                            Color.Transparent,
                        ),
                        start = Offset(sweepX - 60f, 190f),
                        end = Offset(sweepX + 55f, 370f),
                    ),
                    topLeft = Offset(sweepX - 80f, 100f),
                    size = Size(160f, 370f),
                )
            }

            drawPath(
                path = bodyPath,
                color = palette.light.copy(alpha = NovaApertureTokens.strokeAlpha),
                style = Stroke(width = 1.5f),
            )

            val core = Offset(NovaApertureTokens.coreX, NovaApertureTokens.coreY)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = motion.auraAlpha),
                        palette.core.copy(alpha = motion.auraAlpha * 0.72f),
                        palette.light.copy(alpha = 0f),
                    ),
                    center = core,
                    radius = NovaApertureTokens.coreGlowRadius * motion.corePulse,
                ),
                radius = NovaApertureTokens.coreGlowRadius * motion.corePulse,
                center = core,
            )
            drawCircle(
                color = palette.core,
                radius = NovaApertureTokens.coreRadius * motion.corePulse,
                center = core,
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.9f),
                radius = NovaApertureTokens.highlightRadius * motion.corePulse,
                center = Offset(NovaApertureTokens.highlightX, NovaApertureTokens.highlightY),
            )
        }
    }
}

/** Marka işaretinin durağan hâli — üst çubuk, boş ekran ve avatar için. */
@Composable
fun NovaBrandMark(
    modifier: Modifier = Modifier,
    animated: Boolean = true,
    contentLabel: String = "NOVA",
) = NovaThinkingIndicator(
    modifier = modifier,
    animated = animated,
    showHalo = false,
    contentLabel = contentLabel,
    testTag = "nova_brand_mark",
)
