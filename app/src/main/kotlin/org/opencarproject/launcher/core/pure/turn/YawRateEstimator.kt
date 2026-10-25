package org.opencarproject.launcher.core.pure.turn

/**
 * One source of the vehicle's yaw rate (how fast it turns), e.g. the gyroscope or the GPS
 * bearing. [TurnDetector] uses the first one, in priority order, whose signal is reliable.
 */
interface YawRateEstimator {

    /** Smoothed yaw rate in rad/s, positive = turning left. */
    val currentYawRateRadiansPerSecond: Float

    /** Whether [currentYawRateRadiansPerSecond] can be trusted right now. */
    val isProducingReliableSignal: Boolean

    /** Forgets every sample, as if the source had just been started. */
    fun resetEstimate()
}
