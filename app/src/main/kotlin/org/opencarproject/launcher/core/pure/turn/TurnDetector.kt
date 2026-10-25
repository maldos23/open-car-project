package org.opencarproject.launcher.core.pure.turn

/**
 * Detects how the vehicle is turning by picking, from [estimatorsInPriorityOrder], the first
 * estimator whose signal is reliable (Strategy with a priority fallback). A new source (e.g. the
 * CAN bus) is one more [YawRateEstimator] in the list, without touching this class.
 */
class TurnDetector(private val estimatorsInPriorityOrder: List<YawRateEstimator>) {

    /** Estimator currently in use, or null when none is reliable. */
    val activeEstimator: YawRateEstimator?
        get() = estimatorsInPriorityOrder.firstOrNull { it.isProducingReliableSignal }

    /** Yaw rate in rad/s, positive = turning left; 0 when no source is reliable. */
    val currentYawRateRadiansPerSecond: Float
        get() = activeEstimator?.currentYawRateRadiansPerSecond ?: 0f
}
