package org.opencarproject.launcher.presentation.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import org.opencarproject.launcher.core.model.WeatherCondition
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val SunColor = Color(0xFFFFB300)
private val MoonColor = Color(0xFFB4C4FF)
private val RainColor = Color(0xFF4FC3F7)
private val SnowColor = Color(0xFF90CAF9)
private val BoltColor = Color(0xFFFFCA28)

/**
 * Weather icon drawn and animated per condition: spinning sun, moon with stars,
 * floating clouds, falling rain/drizzle/snow, drifting fog and a storm with
 * flashes. Everything is in size-relative coordinates, so it scales to any `size`.
 */
@Composable
fun AnimatedWeatherIcon(
    condition: WeatherCondition,
    isDay: Boolean,
    modifier: Modifier = Modifier,
    /** Cloud/fog color; white works best on a colored background. */
    cloudColor: Color? = null,
) {
    // Short cycle for falling/blinking elements; long cycle for the sun's rotation.
    val t by rememberAmbientFloat(0f, 1f, 2400)
    val spin by rememberAmbientFloat(0f, 360f, 14_000)
    val cloud = cloudColor ?: MaterialTheme.colorScheme.onSurfaceVariant
    // Solid (no alpha): with transparency, overlapping circles leave blotches.
    val backCloud = cloudColor?.let { lerp(it, Color.Gray, 0.35f) } ?: MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier) {
        val drift = sin(t * 2 * PI).toFloat() * 0.03f // horizontal sway of the clouds
        when (condition) {
            WeatherCondition.CLEAR -> if (isDay) sun(Offset(0.5f, 0.5f), 0.2f, spin, t) else moon(t)
            WeatherCondition.PARTLY_CLOUDY -> {
                if (isDay) sun(Offset(0.38f, 0.38f), 0.15f, spin, t) else crescent(Offset(0.38f, 0.36f), 0.18f)
                cloud(Offset(0.58f + drift, 0.62f), 0.8f, cloud)
            }
            WeatherCondition.CLOUDY, WeatherCondition.UNKNOWN -> {
                cloud(Offset(0.38f - drift, 0.42f), 0.65f, backCloud)
                cloud(Offset(0.55f + drift, 0.58f), 0.9f, cloud)
            }
            WeatherCondition.FOG -> fog(t, cloud)
            WeatherCondition.DRIZZLE -> {
                drops(t, count = 2, speed = 0.7f, color = RainColor)
                cloud(Offset(0.5f + drift, 0.42f), 0.95f, cloud)
            }
            WeatherCondition.RAIN -> {
                drops(t, count = 3, speed = 1f, color = RainColor)
                cloud(Offset(0.5f + drift, 0.42f), 0.95f, cloud)
            }
            WeatherCondition.SNOW -> {
                flakes(t)
                cloud(Offset(0.5f + drift, 0.42f), 0.95f, cloud)
            }
            WeatherCondition.THUNDERSTORM -> {
                drops(t, count = 2, speed = 1.2f, color = RainColor)
                cloud(Offset(0.5f, 0.4f), 0.95f, cloud)
                bolt(t)
            }
        }
    }
}

/** Converts relative coordinates (0..1) to canvas pixels. */
private fun DrawScope.p(x: Float, y: Float) = Offset(x * size.width, y * size.height)

private fun DrawScope.sun(center: Offset, radius: Float, spin: Float, t: Float) {
    val s = size.minDimension
    val c = p(center.x, center.y)
    // Slowly rotating rays.
    rotate(spin, pivot = c) {
        for (i in 0 until 8) {
            val a = (i * 45.0) * PI / 180
            val inner = s * radius * 1.45f
            val outer = s * radius * 2.0f
            drawLine(
                color = SunColor,
                start = c + Offset((cos(a) * inner).toFloat(), (sin(a) * inner).toFloat()),
                end = c + Offset((cos(a) * outer).toFloat(), (sin(a) * outer).toFloat()),
                strokeWidth = s * 0.06f,
                cap = StrokeCap.Round,
            )
        }
    }
    // Pulsing core.
    val pulse = 1f + 0.06f * sin(t * 2 * PI).toFloat()
    drawCircle(SunColor, radius = s * radius * pulse, center = c)
}

private fun DrawScope.crescent(center: Offset, radius: Float) {
    val s = size.minDimension
    val c = p(center.x, center.y)
    val full = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, s * radius)) }
    val bite = Path().apply {
        addOval(androidx.compose.ui.geometry.Rect(c + Offset(s * radius * 0.55f, -s * radius * 0.35f), s * radius))
    }
    drawPath(Path.combine(PathOperation.Difference, full, bite), MoonColor)
}

private fun DrawScope.moon(t: Float) {
    crescent(Offset(0.45f, 0.52f), 0.28f)
    // Stars twinkling out of phase.
    val stars = listOf(Offset(0.78f, 0.22f), Offset(0.86f, 0.5f), Offset(0.66f, 0.12f))
    stars.forEachIndexed { i, star ->
        val alpha = 0.35f + 0.65f * (0.5f + 0.5f * sin((t + i / 3f) * 2 * PI).toFloat())
        drawCircle(MoonColor.copy(alpha = alpha), radius = size.minDimension * 0.035f, center = p(star.x, star.y))
    }
}

/** Cloud: rounded base + three "puffs". `scale` is relative to the canvas. */
private fun DrawScope.cloud(center: Offset, scale: Float, color: Color) {
    val s = size.minDimension * scale
    val c = p(center.x, center.y)
    drawRoundRect(
        color = color,
        topLeft = c + Offset(-s * 0.36f, -s * 0.02f),
        size = Size(s * 0.72f, s * 0.24f),
        cornerRadius = CornerRadius(s * 0.12f),
    )
    drawCircle(color, radius = s * 0.16f, center = c + Offset(-s * 0.17f, -s * 0.01f))
    drawCircle(color, radius = s * 0.21f, center = c + Offset(s * 0.03f, -s * 0.09f))
    drawCircle(color, radius = s * 0.13f, center = c + Offset(s * 0.2f, s * 0.04f))
}

/** Drops falling below the cloud, each out of phase; they fade out at the end. */
private fun DrawScope.drops(t: Float, count: Int, speed: Float, color: Color) {
    val s = size.minDimension
    for (i in 0 until count) {
        val x = 0.5f + (i - (count - 1) / 2f) * 0.17f
        val phase = ((t * speed) + i / count.toFloat()) % 1f
        val y = 0.6f + phase * 0.32f
        drawLine(
            color = color.copy(alpha = 1f - phase),
            start = p(x - 0.02f, y),
            end = p(x - 0.05f, y + 0.12f),
            strokeWidth = s * 0.055f,
            cap = StrokeCap.Round,
        )
    }
}

/** Flakes falling with a sway. */
private fun DrawScope.flakes(t: Float) {
    for (i in 0 until 3) {
        val phase = (t * 0.6f + i / 3f) % 1f
        val x = 0.32f + i * 0.18f + sin((phase + i) * 2 * PI).toFloat() * 0.04f
        val y = 0.62f + phase * 0.32f
        drawCircle(SnowColor.copy(alpha = 1f - phase * 0.8f), radius = size.minDimension * 0.045f, center = p(x, y))
    }
}

/** Always-faint lightning bolt (so the storm is recognizable) that flashes twice per cycle. */
private fun DrawScope.bolt(t: Float) {
    val flash = t < 0.08f || (t > 0.16f && t < 0.22f)
    val path = Path().apply {
        val a = p(0.52f, 0.52f); moveTo(a.x, a.y)
        val b = p(0.4f, 0.74f); lineTo(b.x, b.y)
        val c = p(0.5f, 0.74f); lineTo(c.x, c.y)
        val d = p(0.44f, 0.96f); lineTo(d.x, d.y)
        val e = p(0.64f, 0.66f); lineTo(e.x, e.y)
        val f = p(0.53f, 0.66f); lineTo(f.x, f.y)
        val g = p(0.6f, 0.52f); lineTo(g.x, g.y)
        close()
    }
    drawPath(path, BoltColor.copy(alpha = if (flash) 1f else 0.4f))
}

/** Fog: three bands drifting at different speeds. */
private fun DrawScope.fog(t: Float, color: Color) {
    for (i in 0 until 3) {
        val shift = sin((t + i * 0.3f) * 2 * PI).toFloat() * 0.06f
        val y = 0.35f + i * 0.16f
        drawLine(
            color = color.copy(alpha = 0.85f - i * 0.2f),
            start = p(0.18f + shift, y),
            end = p(0.82f + shift, y),
            strokeWidth = size.minDimension * 0.075f,
            cap = StrokeCap.Round,
        )
    }
}
