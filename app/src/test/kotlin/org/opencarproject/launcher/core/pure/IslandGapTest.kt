package org.opencarproject.launcher.core.pure

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IslandGapTest {

    @Test fun nearestSideElementLimitsTheWidth() {
        // Bar 0..1000, weather on the left up to 300 (200 from center), scene on the right from 750.
        assertEquals(380f, IslandGap.width(500f, listOf(0f to 300f, 750f to 1000f), 10f)!!, 0f)
    }

    @Test fun hiddenAndCenteredElementsAreIgnored() {
        assertNull(IslandGap.width(500f, listOf(0f to 0f, 400f to 600f), 10f))
        assertEquals(780f, IslandGap.width(500f, listOf(400f to 600f, 900f to 1000f), 10f)!!, 0f)
    }

    @Test fun neverNegative() {
        assertEquals(0f, IslandGap.width(500f, listOf(0f to 499f), 10f)!!, 0f)
    }
}
