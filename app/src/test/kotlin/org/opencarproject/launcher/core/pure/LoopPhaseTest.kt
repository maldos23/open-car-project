package org.opencarproject.launcher.core.pure

import org.junit.Assert.assertEquals
import org.junit.Test

class LoopPhaseTest {
    @Test fun forwardLoopRestartsEachPeriod() {
        assertEquals(0f, LoopPhase.fraction(0, 1000, reverse = false), 0f)
        assertEquals(0.5f, LoopPhase.fraction(500, 1000, reverse = false), 0f)
        assertEquals(0f, LoopPhase.fraction(1000, 1000, reverse = false), 0f)
    }

    @Test fun reverseLoopGoesThereAndBack() {
        assertEquals(1f, LoopPhase.fraction(1000, 1000, reverse = true), 0f)
        assertEquals(0.5f, LoopPhase.fraction(1500, 1000, reverse = true), 0f)
        assertEquals(0f, LoopPhase.fraction(2000, 1000, reverse = true), 0f)
    }
}
