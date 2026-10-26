package org.opencarproject.launcher.presentation.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.opencarproject.launcher.core.pure.MotionMath
import org.opencarproject.launcher.data.source.MotionReader
import org.opencarproject.launcher.data.source.VehicleMotion
import kotlinx.coroutines.delay
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.min

/** Yaw rate (rad/s) ignored as noise / lane corrections, and the rate of a clear turn. */
private const val YAW_DEAD_ZONE = 0.05f
private const val YAW_FULL = 0.35f

/** Acceleration (m/s²) ignored as cruising, and that of a firm acceleration or braking. */
private const val ACCEL_DEAD_ZONE = 0.4f
private const val ACCEL_FULL = 3f

/** Strongest glow: noticeable at a glance without competing with the road. */
private const val MAX_ALPHA = 0.6f

/** Demo (turn, accel): sweeps left → up → right, then brakes, so the light travels the border. */
private val DEMO = listOf(1f to 0f, 0.7f to 0.7f, 0f to 1f, -0.7f to 0.7f, -1f to 0f, 0f to -1f)
private const val DEMO_STEP_MS = 900L

/** Spread of the light along the border, as a fraction of the perimeter (Gaussian sigma). */
private const val SPREAD = 0.06f

/** Border segments drawn; enough for a smooth falloff on large screens. */
private const val SEGMENTS = 240

/** Radius of the light pool around the brightest point of the border. */
private val POOL_RADIUS = 240.dp

/** Neon line strokes on the edge: (width, alpha weight), the soft halo first, then the core. */
private val LINE = listOf(24.dp to 0.45f, 10.dp to 0.9f, 4.dp to 1.7f)

/** Brake-light red, so braking reads like the car ahead. */
private val BrakeRed = Color(0xFFFF1744)

/**
 * Neon light that travels along the screen border towards where the car is going: the motion
 * (turn and acceleration) is a direction from the screen center, and the light sits where that
 * direction meets the border — brightest there, fading along the edge on both sides. Left/right
 * while turning, up while speeding up, down and red while braking, and the corners in between.
 * Draw-only: touches pass through.
 * [sensitivity] scales the thresholds (2 = reacts to half the motion) to calibrate real sensors.
 * The light eases with a critically damped spring: unlike a tween it keeps its velocity when the
 * target changes mid-way, so GPS steps (one per second) and sensor jitter become one glide.
 * [smoothness] (0..1) sets how soft that spring is.
 * With [useSensors] false, turns come from the GPS only.
 * With [demo] it ignores the sensors and sweeps the light once around the border, then calls
 * [onDemoEnd].
 */
@Composable
fun MotionGlowOverlay(
    sensitivity: Float,
    smoothness: Float,
    useSensors: Boolean,
    color: Color,
    demo: Boolean,
    onDemoEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val flow = remember(context, useSensors) { MotionReader.motion(context, useSensors) }
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

    val glowSpring = remember(smoothness) {
        spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = MotionMath.glowSpringStiffness(smoothness))
    }
    // The same easing on both axes keeps the light gliding along the border, not jumping.
    val turn by animateFloatAsState(
        demoCue?.first ?: MotionMath.intensity(motion.yawRate * sensitivity, YAW_DEAD_ZONE, YAW_FULL),
        glowSpring,
        label = "turn",
    )
    val accel by animateFloatAsState(
        demoCue?.second ?: MotionMath.intensity(motion.acceleration * sensitivity, ACCEL_DEAD_ZONE, ACCEL_FULL),
        glowSpring,
        label = "accel",
    )

    Canvas(modifier) {
        // Direction of travel on screen: left turn → left, speeding up → up, braking → down.
        val dx = -turn
        val dy = -accel
        val strength = min(1f, hypot(dx, dy))
        if (strength < 0.01f) return@Canvas
        val w = size.width
        val h = size.height
        val perimeter = MotionMath.perimeter(w, h)
        val center = MotionMath.borderPosition(dx, dy, w, h)
        val sigma = perimeter * SPREAD
        // Braking share of the direction tints the glow towards brake-light red.
        val tint = lerp(color, BrakeRed, (dy / strength).coerceIn(0f, 1f))
        val alpha = strength * MAX_ALPHA
        // Light pool: brightest where the direction meets the border, fading in every direction.
        val (cx, cy) = MotionMath.borderPoint(center, w, h)
        val pool = POOL_RADIUS.toPx()
        drawCircle(
            brush = Brush.radialGradient(
                listOf(tint.copy(alpha = alpha * 0.8f), tint.copy(alpha = alpha * 0.25f), Color.Transparent),
                center = Offset(cx, cy),
                radius = pool,
            ),
            radius = pool,
            center = Offset(cx, cy),
        )
        // Neon line hugging the edge, with the same falloff along the border.
        val step = perimeter / SEGMENTS
        for (i in 0 until SEGMENTS) {
            val s = i * step
            val d = MotionMath.borderDistance(s + step / 2, center, perimeter)
            if (d > 3 * sigma) continue
            val falloff = exp(-(d * d) / (2 * sigma * sigma))
            val (x1, y1) = MotionMath.borderPoint(s, w, h)
            val (x2, y2) = MotionMath.borderPoint(s + step, w, h)
            for ((width, weight) in LINE) {
                drawLine(
                    color = tint.copy(alpha = (alpha * falloff * weight).coerceAtMost(1f)),
                    start = Offset(x1, y1),
                    end = Offset(x2, y2),
                    strokeWidth = width.toPx(),
                    cap = StrokeCap.Butt,
                )
            }
        }
    }
}
