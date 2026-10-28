package org.opencarproject.launcher.presentation.widget

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.opencarproject.launcher.R
import org.opencarproject.launcher.core.model.ClimateState
import org.opencarproject.launcher.core.pure.ClimateProtocol
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Used when the car doesn't report its number of fan levels. */
private const val DEFAULT_FAN_LEVELS = 8 // ponytail: common value; the car's own count wins when sent

/** Fan level as rising bars, one per level the car has, lit up to the current one. */
@Composable
fun FanBars(state: ClimateState, modifier: Modifier = Modifier, barHeight: Dp = 22.dp) {
    val levels = state.maxFanSpeed.takeIf { it > 0 } ?: DEFAULT_FAN_LEVELS
    val lit = if (state.power) state.fanSpeed.coerceIn(0, levels) else 0
    val description = stringResource(R.string.climate_fan_level, lit)
    Row(
        modifier.semantics { contentDescription = description },
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        val on = MaterialTheme.colorScheme.primary
        val off = MaterialTheme.colorScheme.outlineVariant
        repeat(levels) { i ->
            val color by animateColorAsState(if (i < lit) on else off, tween(300), label = "fan$i")
            Box(
                Modifier
                    .width(5.dp)
                    .height(barHeight * (0.35f + 0.65f * (i + 1) / levels))
                    .background(color, RoundedCornerShape(2.dp)),
            )
        }
    }
}

/**
 * The current air direction: a seated person with arrows to the face, the feet and/or the
 * windshield (and their combinations), drawn as vectors in the theme color.
 */
@Composable
fun AirModeIcon(state: ClimateState, modifier: Modifier = Modifier, size: Dp = 32.dp) {
    val color = if (state.power) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    val description = airModeLabel(state)
    // Windshield only ("FRONT"): the usual front defrost symbol reads better than the person.
    if (ClimateProtocol.blowsWindshield(state) && !state.airFace && !state.airFeet) {
        return Icon(painterResource(R.drawable.ic_defrost_front), description, modifier.size(size), tint = color)
    }
    Canvas(modifier.size(size).semantics { contentDescription = description }) {
        val s = this.size.minDimension
        val w = s * 0.09f
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        // Seated person: head, back, thigh and shin.
        drawCircle(color, radius = s * 0.085f, center = p(0.22f, 0.22f))
        drawPath(
            Path().apply {
                moveTo(0.22f * s, 0.38f * s)
                lineTo(0.28f * s, 0.68f * s)
                lineTo(0.56f * s, 0.68f * s)
                lineTo(0.62f * s, 0.92f * s)
            },
            color,
            style = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        fun arrow(from: Offset, to: Offset) {
            drawLine(color, from, to, w * 0.8f, StrokeCap.Round)
            val angle = atan2(to.y - from.y, to.x - from.x)
            for (side in listOf(-1f, 1f)) {
                val a = angle + PI.toFloat() + side * 0.6f
                drawLine(color, to, Offset(to.x + cos(a) * s * 0.13f, to.y + sin(a) * s * 0.13f), w * 0.8f, StrokeCap.Round)
            }
        }
        if (state.airFace) arrow(p(0.92f, 0.32f), p(0.5f, 0.32f))
        if (state.airFeet) arrow(p(0.86f, 0.5f), p(0.86f, 0.86f))
        if (ClimateProtocol.blowsWindshield(state)) {
            // Windshield line with the air going up to it.
            drawLine(color, p(0.55f, 0.1f), p(0.95f, 0.02f), w * 0.8f, StrokeCap.Round)
            arrow(p(0.72f, 0.42f), p(0.72f, 0.14f))
        }
    }
}
