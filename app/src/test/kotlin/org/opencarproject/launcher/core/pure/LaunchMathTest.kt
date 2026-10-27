package org.opencarproject.launcher.core.pure

import org.junit.Assert.assertEquals
import org.junit.Test

class LaunchMathTest {

    @Test
    fun stagesRunInOrder() {
        assertEquals(0f, LaunchMath.charge(0f))
        assertEquals(1f, LaunchMath.charge(LaunchMath.CHARGE_END))
        assertEquals(0f, LaunchMath.morph(LaunchMath.CHARGE_END))
        assertEquals(0.5f, LaunchMath.morph((LaunchMath.CHARGE_END + LaunchMath.MORPH_END) / 2), 1e-4f)
        assertEquals(0f, LaunchMath.lift(LaunchMath.MORPH_END))
        assertEquals(1f, LaunchMath.lift(LaunchMath.LIFT_END))
    }

    @Test
    fun stagesAreClamped() {
        assertEquals(1f, LaunchMath.charge(1f))
        assertEquals(1f, LaunchMath.lift(1.2f))
        assertEquals(0f, LaunchMath.lift(-0.1f))
    }
}
