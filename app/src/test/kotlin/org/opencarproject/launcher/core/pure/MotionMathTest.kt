package org.opencarproject.launcher.core.pure

import org.junit.Assert.assertEquals
import org.junit.Test

class MotionMathTest {

    @Test fun yawIgnoresMountAngle() {
        // Lying flat (up = +z) and standing upright on the dash (up = +y): same left turn.
        assertEquals(0.5f, MotionMath.yawRate(0f, 0f, 0.5f, 0f, 0f, 9.81f), 1e-4f)
        assertEquals(0.5f, MotionMath.yawRate(0f, 0.5f, 0f, 0f, 9.81f, 0f), 1e-4f)
        // Rotation around a horizontal axis (bump, pitch) is not a turn.
        assertEquals(0f, MotionMath.yawRate(0.5f, 0f, 0f, 0f, 9.81f, 0f), 1e-4f)
    }

    @Test fun yawWithoutGravityIsZero() {
        assertEquals(0f, MotionMath.yawRate(1f, 1f, 1f, 0f, 0f, 0f), 0f)
    }

    @Test fun bearingDeltaWrapsAroundNorth() {
        assertEquals(20f, MotionMath.bearingDelta(350f, 10f), 1e-4f)
        assertEquals(-20f, MotionMath.bearingDelta(10f, 350f), 1e-4f)
        assertEquals(180f, MotionMath.bearingDelta(0f, 180f), 1e-4f)
    }

    @Test fun intensityHasDeadZoneAndSaturates() {
        assertEquals(0f, MotionMath.intensity(0.04f, 0.05f, 0.35f), 0f)
        assertEquals(0.5f, MotionMath.intensity(0.2f, 0.05f, 0.35f), 1e-4f)
        assertEquals(-1f, MotionMath.intensity(-2f, 0.05f, 0.35f), 0f)
    }
}
