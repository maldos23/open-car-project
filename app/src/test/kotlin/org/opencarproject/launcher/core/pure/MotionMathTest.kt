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

    @Test fun borderPositionFollowsTheDirection() {
        val w = 200f
        val h = 100f
        assertEquals(100f, MotionMath.borderPosition(0f, -1f, w, h), 1e-3f) // up: middle of the top
        assertEquals(250f, MotionMath.borderPosition(1f, 0f, w, h), 1e-3f) // right: middle of the right
        assertEquals(400f, MotionMath.borderPosition(0f, 1f, w, h), 1e-3f) // down: middle of the bottom
        assertEquals(550f, MotionMath.borderPosition(-1f, 0f, w, h), 1e-3f) // left: middle of the left
        assertEquals(0f, MotionMath.borderPosition(-2f, -1f, w, h) % MotionMath.perimeter(w, h), 1e-3f) // top-left corner
    }

    @Test fun borderPointIsTheInverse() {
        val (x, y) = MotionMath.borderPoint(MotionMath.borderPosition(-1f, -0.2f, 200f, 100f), 200f, 100f)
        assertEquals(0f, x, 1e-3f)
        assertEquals(30f, y, 1e-3f)
    }

    @Test fun borderDistanceWrapsAround() {
        assertEquals(20f, MotionMath.borderDistance(590f, 10f, 600f), 1e-3f)
        assertEquals(100f, MotionMath.borderDistance(100f, 200f, 600f), 1e-3f)
    }
}
