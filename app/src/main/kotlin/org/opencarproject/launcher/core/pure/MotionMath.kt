package org.opencarproject.launcher.core.pure

import kotlin.math.abs
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
     * Glow intensity in -1..1: 0 inside the dead zone (sensor noise, small corrections), then
     * linear up to [fullScale], where it saturates.
     */
    fun intensity(value: Float, deadZone: Float, fullScale: Float): Float {
        val magnitude = ((abs(value) - deadZone) / (fullScale - deadZone)).coerceIn(0f, 1f)
        return magnitude * sign(value)
    }
}
