package org.opencarproject.launcher.presentation.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import org.opencarproject.launcher.core.pure.AudioBarsMath
import kotlin.math.PI

/**
 * Bars that rise and fall like sound, one per color in [colors]: the assistant's voice and the
 * island's level meter. [periodMs] is one full loop; the level is read in the draw phase only.
 */
@Composable
fun AudioBars(colors: List<Color>, modifier: Modifier = Modifier, periodMs: Int = 1600) {
    val transition = rememberInfiniteTransition(label = "audioBars")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(periodMs, easing = LinearEasing)),
        label = "t",
    )
    Canvas(modifier) {
        val barWidth = size.width / (colors.size + (colors.size - 1) * 0.7f)
        val gap = barWidth * 0.7f
        colors.forEachIndexed { i, color ->
            // With rounded caps the line grows by barWidth/2 on each side.
            val length = (size.height - barWidth) * (0.2f + 0.8f * AudioBarsMath.level(t, i))
            val x = barWidth / 2 + i * (barWidth + gap)
            drawLine(
                color = color,
                start = Offset(x, center.y - length / 2),
                end = Offset(x, center.y + length / 2),
                strokeWidth = barWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}
