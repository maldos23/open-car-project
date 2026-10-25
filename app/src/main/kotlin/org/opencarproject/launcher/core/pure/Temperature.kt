package org.opencarproject.launcher.core.pure

import org.opencarproject.launcher.core.model.TemperatureUnit
import kotlin.math.roundToInt

/** Weather temperature conversion and unit (data always arrives in °C). */
object Temperature {

    /** Countries whose region uses Fahrenheit (for [TemperatureUnit.AUTO]). */
    private val FAHRENHEIT_COUNTRIES = setOf("US", "LR", "MM", "BS", "BZ", "KY", "PW")

    fun usesFahrenheit(unit: TemperatureUnit, country: String): Boolean = when (unit) {
        TemperatureUnit.AUTO -> country.uppercase() in FAHRENHEIT_COUNTRIES
        TemperatureUnit.CELSIUS -> false
        TemperatureUnit.FAHRENHEIT -> true
    }

    /** Whole degrees in the chosen unit. */
    fun display(celsius: Double, fahrenheit: Boolean): Int =
        (if (fahrenheit) celsius * 9 / 5 + 32 else celsius).roundToInt()
}
