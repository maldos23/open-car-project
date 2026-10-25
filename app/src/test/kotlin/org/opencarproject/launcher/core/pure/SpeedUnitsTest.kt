package org.opencarproject.launcher.core.pure

import org.opencarproject.launcher.core.model.SpeedUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedUnitsTest {

    @Test
    fun autoFollowsRegion() {
        assertTrue(SpeedUnits.usesMph(SpeedUnit.AUTO, "US"))
        assertTrue(SpeedUnits.usesMph(SpeedUnit.AUTO, "gb"))
        assertFalse(SpeedUnits.usesMph(SpeedUnit.AUTO, "MX"))
    }

    @Test
    fun explicitChoiceWins() {
        assertTrue(SpeedUnits.usesMph(SpeedUnit.MPH, "MX"))
        assertFalse(SpeedUnits.usesMph(SpeedUnit.KMH, "US"))
    }

    @Test
    fun convertsBothWays() {
        assertEquals(62.14f, SpeedUnits.display(100f, mph = true), 0.05f)
        assertEquals(100f, SpeedUnits.display(100f, mph = false), 0f)
        assertEquals(100f, SpeedUnits.toKmh(SpeedUnits.display(100f, true), true), 0.01f)
    }
}
