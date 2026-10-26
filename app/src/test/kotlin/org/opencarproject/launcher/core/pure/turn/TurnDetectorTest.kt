package org.opencarproject.launcher.core.pure.turn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnDetectorTest {

    private val gyroscope = GyroscopeYawEstimator()
    private val gps = GpsBearingYawEstimator()
    private val detector = TurnDetector(listOf(gyroscope, gps))

    /** One fix per second at [speed] m/s, the bearing turning [degreesPerFix] each time. */
    private fun driveGps(fixes: Int, startBearing: Float, degreesPerFix: Float, speed: Float = 10f, startSecond: Int = 0) {
        for (i in 0 until fixes) {
            gps.updateGpsFix((startSecond + i) * SECOND, speed, (startBearing + i * degreesPerFix + 360f) % 360f)
        }
    }

    private fun feedGyroscope(samples: Int, yawRate: Float) {
        repeat(50) { gyroscope.updateGravityVector(0f, 0f, 9.81f) } // lying flat: up = +z
        repeat(samples) { gyroscope.updateGyroscopeSample(0f, 0f, yawRate) }
    }

    @Test fun idleGyroscopeFallsBackToGpsTurn() {
        // The emulator case: a gyroscope is present but never moves while GPS goes round a curve.
        feedGyroscope(samples = 100, yawRate = 0f)
        driveGps(fixes = 6, startBearing = 0f, degreesPerFix = 15f) // turning right
        assertSame(gps, detector.activeEstimator)
        assertTrue(detector.currentYawRateRadiansPerSecond < -0.2f)
    }

    @Test fun liveGyroscopeTakesPriorityOverGps() {
        driveGps(fixes = 6, startBearing = 0f, degreesPerFix = 15f)
        feedGyroscope(samples = 30, yawRate = 0.3f) // turning left
        assertSame(gyroscope, detector.activeEstimator)
        assertEquals(0.3f, detector.currentYawRateRadiansPerSecond, 0.01f)
    }

    @Test fun gpsTurnAcrossNorthHasNoJump() {
        driveGps(fixes = 6, startBearing = 350f, degreesPerFix = -10f) // 350 → 300: turning left
        assertTrue(detector.currentYawRateRadiansPerSecond > 0.15f)
        assertTrue(detector.currentYawRateRadiansPerSecond < 0.2f)
    }

    @Test fun gpsTurnShowsWithinOneFix() {
        // 15°/s ≈ 0.26 rad/s: the first bearing change must already give most of it (no extra lag).
        driveGps(fixes = 2, startBearing = 0f, degreesPerFix = -15f)
        assertTrue(detector.currentYawRateRadiansPerSecond > 0.2f)
    }

    @Test fun crawlingIsNotATurn() {
        driveGps(fixes = 6, startBearing = 0f, degreesPerFix = 30f, speed = 1f)
        assertEquals(0f, detector.currentYawRateRadiansPerSecond, 0f)
    }

    @Test fun missingBearingIsNotATurn() {
        driveGps(fixes = 3, startBearing = 0f, degreesPerFix = 20f)
        repeat(8) { gps.updateGpsFix((3 + it) * SECOND, 10f, null) }
        assertEquals(0f, detector.currentYawRateRadiansPerSecond, 0.01f)
    }

    @Test fun staleGpsGoesDark() {
        driveGps(fixes = 6, startBearing = 0f, degreesPerFix = 15f)
        gps.markSignalStaleIfOlderThan(nowNanos = 9 * SECOND)
        assertNull(detector.activeEstimator)
        assertEquals(0f, detector.currentYawRateRadiansPerSecond, 0f)
    }

    @Test fun noSourceMeansNoTurn() {
        assertNull(detector.activeEstimator)
        assertEquals(0f, detector.currentYawRateRadiansPerSecond, 0f)
    }

    private companion object {
        const val SECOND = 1_000_000_000L
    }
}
