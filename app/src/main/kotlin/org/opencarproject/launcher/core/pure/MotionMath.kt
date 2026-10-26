package org.opencarproject.launcher.core.pure

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sqrt

/** Vehicle motion from the head unit's sensors, independent of how the screen is mounted. */
object MotionMath {

    /**
     * Yaw rate (rad/s, positive = turning left) from the gyroscope and the gravity vector, both
     * in device axes. At rest the accelerometer/gravity sensor points "up", so projecting the
     * rotation on it isolates the rotation around the vertical axis whatever the mount angle.
     */
    fun yawRate(gx: Float, gy: Float, gz: Float, upX: Float, upY: Float, upZ: Float): Float {
        val norm = sqrt(upX * upX + upY * upY + upZ * upZ)
        if (norm < 1e-3f) return 0f
        return (gx * upX + gy * upY + gz * upZ) / norm
    }

    /** Signed bearing change in degrees (-180..180], positive = clockwise (turning right). */
    fun bearingDelta(from: Float, to: Float): Float {
        val d = ((to - from) % 360f + 360f) % 360f
        return if (d > 180f) d - 360f else d
    }

    /**
     * Where a ray from the center of a [w]×[h] rectangle towards ([dx], [dy]) leaves it, as a
     * distance along the border measured clockwise from the top-left corner (y grows downwards).
     */
    fun borderPosition(dx: Float, dy: Float, w: Float, h: Float): Float {
        val tx = if (dx != 0f) (w / 2) / abs(dx) else Float.MAX_VALUE
        val ty = if (dy != 0f) (h / 2) / abs(dy) else Float.MAX_VALUE
        val t = minOf(tx, ty)
        val x = (w / 2 + dx * t).coerceIn(0f, w)
        val y = (h / 2 + dy * t).coerceIn(0f, h)
        return when {
            ty <= tx && dy < 0f -> x // top
            tx < ty && dx > 0f -> w + y // right
            ty <= tx -> w + h + (w - x) // bottom
            else -> 2 * w + h + (h - y) // left
        }
    }

    /** Point (x, y) at distance [s] along the border, same convention as [borderPosition]. */
    fun borderPoint(s: Float, w: Float, h: Float): Pair<Float, Float> {
        val p = ((s % perimeter(w, h)) + perimeter(w, h)) % perimeter(w, h)
        return when {
            p < w -> p to 0f
            p < w + h -> w to (p - w)
            p < 2 * w + h -> (w - (p - w - h)) to h
            else -> 0f to (h - (p - 2 * w - h))
        }
    }

    fun perimeter(w: Float, h: Float): Float = 2 * (w + h)

    /** Shortest distance between two border positions, going either way around. */
    fun borderDistance(a: Float, b: Float, perimeter: Float): Float {
        val d = abs(a - b) % perimeter
        return minOf(d, perimeter - d)
    }

    /**
     * Stiffness of the glow's spring for a [smoothness] in 0..1: 0 → 200 (snappy, follows every
     * step), 0.5 → ~63, 1 → 20 (very soft, lags more). Exponential so each slider step feels alike.
     */
    fun glowSpringStiffness(smoothness: Float): Float = 200f * 0.1f.pow(smoothness.coerceIn(0f, 1f))

    /**
     * Glow intensity in -1..1: 0 inside the dead zone (sensor noise, small corrections), then
     * linear up to [fullScale], where it saturates.
     */
    fun intensity(value: Float, deadZone: Float, fullScale: Float): Float {
        val magnitude = ((abs(value) - deadZone) / (fullScale - deadZone)).coerceIn(0f, 1f)
        return magnitude * sign(value)
    }
}
