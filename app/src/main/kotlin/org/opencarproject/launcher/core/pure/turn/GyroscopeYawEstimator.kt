package org.opencarproject.launcher.core.pure.turn

import org.opencarproject.launcher.core.pure.MotionMath
import org.opencarproject.launcher.core.pure.SpeedMath
import kotlin.math.abs

/**
 * Yaw rate from the gyroscope projected on the gravity vector, so the mount angle doesn't matter.
 *
 * A gyroscope that is present but never moves (the emulator's, a dummy one on some head units, or
 * one without a gravity vector to project on) reads 0 in every turn. So it is only trusted once it
 * has proven to be alive by measuring a real rotation; until then the next estimator is used.
 */
class GyroscopeYawEstimator : YawRateEstimator {

    override var currentYawRateRadiansPerSecond: Float = 0f
        private set

    override var isProducingReliableSignal: Boolean = false
        private set

    /** Low-pass filtered "up" direction in device axes. */
    private val gravityVector = floatArrayOf(0f, 0f, 0f)

    /**
     * Feeds a gravity (or raw accelerometer) sample in m/s², device axes. Low-pass filtered:
     * a raw accelerometer also carries the car's own acceleration.
     */
    fun updateGravityVector(x: Float, y: Float, z: Float) {
        gravityVector[0] = SpeedMath.smooth(gravityVector[0], x, GRAVITY_SMOOTHING)
        gravityVector[1] = SpeedMath.smooth(gravityVector[1], y, GRAVITY_SMOOTHING)
        gravityVector[2] = SpeedMath.smooth(gravityVector[2], z, GRAVITY_SMOOTHING)
    }

    /** Feeds a gyroscope sample in rad/s, device axes. */
    fun updateGyroscopeSample(x: Float, y: Float, z: Float) {
        val yawRate = MotionMath.yawRate(x, y, z, gravityVector[0], gravityVector[1], gravityVector[2])
        currentYawRateRadiansPerSecond = SpeedMath.smooth(currentYawRateRadiansPerSecond, yawRate, YAW_SMOOTHING)
        if (abs(currentYawRateRadiansPerSecond) > ALIVE_THRESHOLD_RADIANS_PER_SECOND) isProducingReliableSignal = true
    }

    override fun resetEstimate() {
        currentYawRateRadiansPerSecond = 0f
        isProducingReliableSignal = false
        gravityVector.fill(0f)
    }

    private companion object {
        const val GRAVITY_SMOOTHING = 0.1f
        const val YAW_SMOOTHING = 0.2f

        /** A rotation no idle sensor reports: the glow's dead zone for turns. */
        const val ALIVE_THRESHOLD_RADIANS_PER_SECOND = 0.05f
    }
}
