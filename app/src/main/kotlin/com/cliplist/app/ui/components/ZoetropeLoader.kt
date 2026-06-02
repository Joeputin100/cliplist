package com.cliplist.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * "Zoetrope strobe" — an indeterminate circular loader inspired by Platon Infante's
 * kinetic sculpture *Interchron (Two Times)* (2024).
 *
 * Two counter-spinning rings of dots are advanced in discrete steps (a persistence-of-vision
 * / phenakistoscope read) around a breathing hub: 12 dots clockwise in 12 steps over 1.8s,
 * 8 dots counter-clockwise in 8 steps over 1.2s. Each ring's dot radii cycle in a 3-pattern,
 * so stepping makes the size pattern appear to flow around the ring.
 *
 * Ported faithfully from the hand-off `md3e-loaders.css` (viewBox 48). Colors default to the
 * active Material 3 color roles, so the loader adopts dynamic color on Android 12+ and the
 * brand teal/lime below it.
 */
@Composable
fun ZoetropeLoader(
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    outerColor: Color = MaterialTheme.colorScheme.primary,
    innerColor: Color = MaterialTheme.colorScheme.secondary,
    hubColor: Color = MaterialTheme.colorScheme.tertiary,
    speed: Float = 1f,
) {
    val transition = rememberInfiniteTransition(label = "zoetrope")

    // Outer ring: 12 discrete steps clockwise over 1.8s.
    val outerStep by transition.animateFloat(
        initialValue = 0f,
        targetValue = OUTER_DOTS.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween((1800f / speed).toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "outer",
    )
    // Inner ring: 8 discrete steps counter-clockwise over 1.2s.
    val innerStep by transition.animateFloat(
        initialValue = 0f,
        targetValue = INNER_DOTS.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween((1200f / speed).toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "inner",
    )
    // Hub: breathes 0.85 -> 1.05 and back over 1.8s (ease-in-out).
    val hubScale by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween((900f / speed).toInt(), easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "hub",
    )

    Canvas(modifier = modifier.size(size)) {
        val unit = this.size.minDimension / VIEWBOX     // scale from the 48px viewBox
        val center = Offset(this.size.width / 2f, this.size.height / 2f)

        val outerRotDeg = (outerStep.toInt() % OUTER_DOTS) * (360f / OUTER_DOTS)
        drawDotRing(center, ringR = 19f * unit, count = OUTER_DOTS, rotationDeg = outerRotDeg,
            radii = OUTER_RADII, unit = unit, color = outerColor)

        val innerRotDeg = -(innerStep.toInt() % INNER_DOTS) * (360f / INNER_DOTS)
        drawDotRing(center, ringR = 10f * unit, count = INNER_DOTS, rotationDeg = innerRotDeg,
            radii = INNER_RADII, unit = unit, color = innerColor)

        drawCircle(color = hubColor, radius = 3f * unit * hubScale, center = center)
    }
}

private fun DrawScope.drawDotRing(
    center: Offset,
    ringR: Float,
    count: Int,
    rotationDeg: Float,
    radii: FloatArray,
    unit: Float,
    color: Color,
) {
    for (i in 0 until count) {
        // -90° so the first dot sits at the top, matching the SVG layout.
        val angleRad = Math.toRadians((rotationDeg + i * (360f / count) - 90f).toDouble())
        val x = center.x + ringR * cos(angleRad).toFloat()
        val y = center.y + ringR * sin(angleRad).toFloat()
        drawCircle(color = color, radius = radii[i % radii.size] * unit, center = Offset(x, y))
    }
}

private const val VIEWBOX = 48f
private const val OUTER_DOTS = 12
private const val INNER_DOTS = 8
private val OUTER_RADII = floatArrayOf(1.5f, 2.05f, 2.6f)
private val INNER_RADII = floatArrayOf(1.3f, 1.85f, 2.4f)
