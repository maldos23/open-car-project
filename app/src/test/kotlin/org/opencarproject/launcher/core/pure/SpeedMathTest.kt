package org.opencarproject.launcher.core.pure

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeedMathTest {

    @Test
    fun distance_oneHundredthOfDegreeOfLatitudeIsAbout1112m() {
        assertEquals(1112.0, SpeedMath.distanceMeters(19.43, -99.13, 19.44, -99.13), 5.0)
    }

    @Test
    fun speed_fromTwoFixes() {
        // ~111 m en 5 s = ~80 km/h
        val kmh = SpeedMath.speedKmh(19.4300, -99.13, 0, 19.4310, -99.13, 5_000)!!
        assertEquals(80f, kmh, 1.5f)
    }

    @Test
    fun speed_ignoresNoisyOrStaleIntervals() {
        assertNull(SpeedMath.speedKmh(19.43, -99.13, 0, 19.44, -99.13, 100))
        assertNull(SpeedMath.speedKmh(19.43, -99.13, 0, 19.44, -99.13, 60_000))
    }

    @Test
    fun motionEstimate_isZeroWhenStill() {
        assertEquals(0f, SpeedMath.estimateKmhFromMotion(0.05f), 0f)
        assertEquals(true, SpeedMath.estimateKmhFromMotion(1.0f) > 30f)
    }
}
