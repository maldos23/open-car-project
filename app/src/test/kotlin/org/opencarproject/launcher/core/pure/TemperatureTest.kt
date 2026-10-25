package org.opencarproject.launcher.core.pure

import org.opencarproject.launcher.core.model.TemperatureUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemperatureTest {

    @Test
    fun usesFahrenheit_autoFollowsRegion() {
        assertTrue(Temperature.usesFahrenheit(TemperatureUnit.AUTO, "US"))
        assertFalse(Temperature.usesFahrenheit(TemperatureUnit.AUTO, "MX"))
    }

    @Test
    fun usesFahrenheit_explicitChoiceWins() {
        assertFalse(Temperature.usesFahrenheit(TemperatureUnit.CELSIUS, "US"))
        assertTrue(Temperature.usesFahrenheit(TemperatureUnit.FAHRENHEIT, "MX"))
    }

    @Test
    fun display_convertsAndRounds() {
        assertEquals(19, Temperature.display(19.4, fahrenheit = false))
        assertEquals(67, Temperature.display(19.4, fahrenheit = true)) // 66.92
        assertEquals(32, Temperature.display(0.0, fahrenheit = true))
    }
}
