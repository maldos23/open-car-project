package org.opencarproject.launcher.core.pure

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TouchLineTest {

    @Test
    fun roundTripsMultiTouch() {
        val line = TouchLine(5, 1, listOf(TouchPointer(0, 10.5f, 20f), TouchPointer(1, 300f, 400.25f)))
        assertEquals(line, TouchLine.parse(line.encode()))
    }

    @Test
    fun rejectsMalformedLines() {
        assertNull(TouchLine.parse(""))
        assertNull(TouchLine.parse("0 0 1 2"))
        assertNull(TouchLine.parse("0 0 1 x 2"))
        assertNull(TouchLine.parse("0 0 1 2 3 4"))
    }
}
