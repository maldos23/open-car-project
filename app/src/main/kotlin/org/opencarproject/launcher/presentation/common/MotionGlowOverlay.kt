package org.opencarproject.launcher.presentation.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.opencarproject.launcher.core.pure.MotionMath
import org.opencarproject.launcher.data.source.MotionReader
import org.opencarproject.launcher.data.source.VehicleMotion
import kotlinx.coroutines.delay

/** Yaw rate (rad/s) ignored as noise / lane corrections, and the rate of a clear turn. */
private const val YAW_DEAD_ZONE = 0.05f
private const val YAW_FULL = 0.35f

/** Acceleration (m/s²) ignored as cruising, and that of a firm acceleration or braking. */
private const val ACCEL_DEAD_ZONE = 0.4f
private const val ACCEL_FULL = 3f

/** Strongest glow: noticeable at a glance without competing with the road. */
private const val MAX_ALPHA = 0.6f

/** Demo: left, right, speeding up, braking. */
private val DEMO = listOf(1f to 0f, -1f to 0f, 0f to 1f, 0f to -1f)
private const val DEMO_STEP_MS = 1_300L

/** Brake-light red, so braking reads like the car ahead. */
private val BrakeRed = Color(0xFFFF1744)

/**
 * Neon glow on the screen edges towards where the car is going: left/right while turning, top
 * while speeding up and bottom (red) while braking. Draw-only: touches pass through.
 * [sensitivity] scales the thresholds (2 = reacts to half the motion) to calibrate real sensors.
 * With [demo] it ignores the sensors and plays left, right, speeding up and braking once, then
 * calls [onDemoEnd].
 */
@Composable
fun MotionGlowOverlay(
    sensitivity: Float,
    color: Color,
    demo: Boolean,
    onDemoEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val flow = remember(context) { MotionReader.motion(context) }
    val motion by flow.collectAsStateWithLifecycle(VehicleMotion())
    val demoEnd by rememberUpdatedState(onDemoEnd)
    // Demo step: (turn, accel) at full strength, one after another.
    val demoCue by produceState<Pair<Float, Float>?>(null, demo) {
        if (!demo) return@produceState
        for (cue in DEMO) {
            value = cue
            delay(DEMO_STEP_MS)
        }
        value = 0f to 0f
        delay(DEMO_STEP_MS / 2) // lets the last glow fade out
        value = null
        demoEnd()
    }

    val turn by animateFloatAsState(
        demoCue?.first ?: MotionMath.intensity(motion.yawRate * sensitivity, YAW_DEAD_ZONE, YAW_FULL),
        tween(300),
        label = "turn",
    )
    val accel by animateFloatAsState(
        demoCue?.second ?: MotionMath.intensity(motion.acceleration * sensitivity, ACCEL_DEAD_ZONE, ACCEL_FULL),
        tween(400),
        label = "accel",
    )
    val neon = color

    Canvas(modifier) {
        val depth = 96.dp.toPx()
        if (turn > 0f) edge(neon, turn, Offset.Zero, Size(depth, size.height), horizontal = true, fromStart = true)
        if (turn < 0f) edge(neon, -turn, Offset(size.width - depth, 0f), Size(depth, size.height), horizontal = true, fromStart = false)
        if (accel > 0f) edge(neon, accel, Offset.Zero, Size(size.width, depth), horizontal = false, fromStart = true)
        if (accel < 0f) edge(BrakeRed, -accel, Offset(0f, size.height - depth), Size(size.width, depth), horizontal = false, fromStart = false)
    }
}

/** A glow band fading inwards from one edge, with a bright neon line on the edge itself. */
private fun DrawScope.edge(color: Color, strength: Float, topLeft: Offset, band: Size, horizontal: Boolean, fromStart: Boolean) {
    val alpha = strength * MAX_ALPHA
    val stops = listOf(color.copy(alpha = alpha), color.copy(alpha = alpha * 0.35f), Color.Transparent)
    val colors = if (fromStart) stops else stops.reversed()
    val brush = if (horizontal) {
        Brush.horizontalGradient(colors, startX = topLeft.x, endX = topLeft.x + band.width)
    } else {
        Brush.verticalGradient(colors, startY = topLeft.y, endY = topLeft.y + band.height)
    }
    drawRect(brush, topLeft, band)
    val line = 4.dp.toPx()
    val lineTopLeft = when {
        horizontal && fromStart -> topLeft
        horizontal -> Offset(topLeft.x + band.width - line, topLeft.y)
        fromStart -> topLeft
        else -> Offset(topLeft.x, topLeft.y + band.height - line)
    }
    drawRect(color.copy(alpha = (alpha * 1.5f).coerceAtMost(1f)), lineTopLeft, if (horizontal) Size(line, band.height) else Size(band.width, line))
}
