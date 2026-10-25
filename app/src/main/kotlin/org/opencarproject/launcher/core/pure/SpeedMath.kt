package org.opencarproject.launcher.core.pure

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Android-independent speed calculations (for GPS and sensors). */
object SpeedMath {

    private const val EARTH_RADIUS_M = 6_371_000.0

    /** Distance in meters between two coordinates (haversine formula). */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(a))
    }

    /**
     * km/h between two readings when the GPS doesn't report speed. Ignores intervals that are
     * too short (noise) or too long (lost signal): returns `null`.
     */
    fun speedKmh(lat1: Double, lon1: Double, t1Ms: Long, lat2: Double, lon2: Double, t2Ms: Long): Float? {
        val dt = (t2Ms - t1Ms) / 1000.0
        if (dt < 0.5 || dt > 30) return null
        return (distanceMeters(lat1, lon1, lat2, lon2) / dt * 3.6).toFloat()
    }

    /** Exponential smoothing: high [alpha] = reacts quickly; low = more stable. */
    fun smooth(previous: Float, sample: Float, alpha: Float): Float = previous + alpha * (sample - previous)

    /**
     * Rough sensor-based estimate: vibration/linear acceleration (RMS, m/s²) grows with the
     * car's speed. Below [STILL_RMS] it is considered stopped. Not a speedometer.
     */
    fun estimateKmhFromMotion(rmsAcceleration: Float): Float =
        if (rmsAcceleration < STILL_RMS) 0f else ((rmsAcceleration - STILL_RMS) * MOTION_TO_KMH).coerceAtMost(160f)

    const val STILL_RMS = 0.08f
    private const val MOTION_TO_KMH = 55f
}
