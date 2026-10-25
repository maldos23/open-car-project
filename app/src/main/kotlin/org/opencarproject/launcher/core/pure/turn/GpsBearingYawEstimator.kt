package org.opencarproject.launcher.core.pure.turn

import org.opencarproject.launcher.core.pure.MotionMath
import org.opencarproject.launcher.core.pure.SpeedMath

/**
 * Yaw rate from the change of GPS bearing between consecutive fixes. Lags about a fix behind the
 * gyroscope, but works on any head unit with a GPS.
 */
class GpsBearingYawEstimator : YawRateEstimator {

    override var currentYawRateRadiansPerSecond: Float = 0f
        private set

    override var isProducingReliableSignal: Boolean = false
        private set

    private var previousBearingDegrees: Float? = null
    private var previousFixTimestampNanos: Long? = null

    /**
     * Feeds a GPS fix. [bearingDegrees] is null when the receiver gives none and the caller
     * couldn't derive it from the positions (e.g. the car barely moved).
     */
    fun updateGpsFix(timestampNanos: Long, speedMetersPerSecond: Float, bearingDegrees: Float?) {
        val previousBearing = previousBearingDegrees
        val previousTimestamp = previousFixTimestampNanos
        previousFixTimestampNanos = timestampNanos
        isProducingReliableSignal = true
        val elapsedSeconds = previousTimestamp?.let { (timestampNanos - it) / 1e9f } ?: 0f
        if (elapsedSeconds <= 0f || elapsedSeconds > MAX_GAP_BETWEEN_FIXES_SECONDS) {
            previousBearingDegrees = bearingDegrees
            return
        }
        // ponytail: bearing is meaningless when crawling; 2 m/s ≈ 7 km/h is the cut-off.
        if (speedMetersPerSecond < MIN_SPEED_FOR_BEARING_METERS_PER_SECOND || bearingDegrees == null) {
            previousBearingDegrees = null
            currentYawRateRadiansPerSecond = SpeedMath.smooth(currentYawRateRadiansPerSecond, 0f, YAW_SMOOTHING)
            return
        }
        previousBearingDegrees = bearingDegrees
        if (previousBearing == null) return
        // Bearing grows clockwise (to the right); yaw is positive to the left.
        val bearingChangeRadians = Math.toRadians(MotionMath.bearingDelta(previousBearing, bearingDegrees).toDouble()).toFloat()
        currentYawRateRadiansPerSecond = SpeedMath.smooth(currentYawRateRadiansPerSecond, -bearingChangeRadians / elapsedSeconds, YAW_SMOOTHING)
    }

    /** No fix for a while (tunnel, signal lost): the last turn must not stay lit. */
    fun markSignalStaleIfOlderThan(nowNanos: Long, maxAgeNanos: Long = STALE_AFTER_NANOS) {
        val lastFix = previousFixTimestampNanos ?: return
        if (nowNanos - lastFix > maxAgeNanos) resetEstimate()
    }

    override fun resetEstimate() {
        currentYawRateRadiansPerSecond = 0f
        isProducingReliableSignal = false
        previousBearingDegrees = null
        previousFixTimestampNanos = null
    }

    private companion object {
        const val YAW_SMOOTHING = 0.5f
        const val MIN_SPEED_FOR_BEARING_METERS_PER_SECOND = 2f
        const val MAX_GAP_BETWEEN_FIXES_SECONDS = 5f
        const val STALE_AFTER_NANOS = 2_500_000_000L
    }
}
