package org.opencarproject.launcher.core.pure

import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingPositionTest {

    @Test
    fun roundTrip_keepsThePosition() {
        assertEquals(300, FloatingPosition.toPx(FloatingPosition.toFraction(300, 1200), 1200))
    }

    @Test
    fun draggedPastTheEdge_staysOnScreen() {
        assertEquals(1f, FloatingPosition.toFraction(1500, 1200), 0f)
        assertEquals(0f, FloatingPosition.toFraction(-40, 1200), 0f)
        assertEquals(1200, FloatingPosition.toPx(1.4f, 1200))
    }

    @Test
    fun windowAsBigAsTheScreen_sitsAtZero() {
        assertEquals(0f, FloatingPosition.toFraction(10, 0), 0f)
        assertEquals(0, FloatingPosition.toPx(0.7f, -50))
    }

    @Test
    fun sizeChange_keepsTheRelativePlace() {
        // Centered: still centered after the window shrinks.
        val f = FloatingPosition.toFraction(400, 800)
        assertEquals(600, FloatingPosition.toPx(f, 1200))
    }
}
