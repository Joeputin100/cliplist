package com.cliplist.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * "Wavy active track" — a determinate linear loader ported from the hand-off
 * `md3e-loaders.css` (the MD3 Expressive sine-wave active indicator). The active
 * portion (0..progress) is a travelling sine wave; the remainder is a flat track.
 *
 * Hand-ported to a plain [Canvas] because the built-in `LinearWavyProgressIndicator`
 * is not public in the project's pinned Compose version. Colors default to the active
 * theme so it adopts dynamic color on Android 12+ and brand teal/lime below it.
 */
@Composable
fun WavyLinearLoader(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
    amplitude: Dp = 4.dp,
    wavelength: Dp = 22.dp,
    strokeWidth: Dp = 4.dp,
) {
    val phase by rememberInfiniteTransition(label = "wavy").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(amplitude * 2 + strokeWidth * 2)
    ) {
        val sw = strokeWidth.toPx()
        val amp = amplitude.toPx()
        val wl = wavelength.toPx().coerceAtLeast(1f)
        val midY = size.height / 2f
        val left = sw / 2f
        val right = size.width - sw / 2f
        val p = progress().coerceIn(0f, 1f)
        val activeEnd = left + (right - left) * p

        // Flat track underneath the whole width.
        drawLine(trackColor, Offset(left, midY), Offset(right, midY), sw, StrokeCap.Round)

        // Travelling wave over the active portion.
        if (activeEnd - left > sw) {
            val twoPi = (2.0 * PI).toFloat()
            val phasePx = phase * wl
            val path = Path()
            var x = left
            path.moveTo(x, midY + amp * sin((x + phasePx) / wl * twoPi))
            while (x <= activeEnd) {
                path.lineTo(x, midY + amp * sin((x + phasePx) / wl * twoPi))
                x += 2f
            }
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}
