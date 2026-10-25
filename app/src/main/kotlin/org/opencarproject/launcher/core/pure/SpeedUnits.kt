package org.opencarproject.launcher.core.pure

import org.opencarproject.launcher.core.model.SpeedUnit

/** Speedometer units. Everything is km/h internally; converted only for display. */
object SpeedUnits {

    private const val KMH_PER_MPH = 1.609344f

    /** Countries that measure speed in mph (for [SpeedUnit.AUTO]). */
    private val MPH_COUNTRIES = setOf("US", "GB", "LR", "MM", "BS", "BZ", "PR", "VI", "GU", "AS", "MP")

    fun usesMph(unit: SpeedUnit, country: String): Boolean = when (unit) {
        SpeedUnit.AUTO -> country.uppercase() in MPH_COUNTRIES
        SpeedUnit.KMH -> false
        SpeedUnit.MPH -> true
    }

    fun display(kmh: Float, mph: Boolean): Float = if (mph) kmh / KMH_PER_MPH else kmh

    fun toKmh(value: Float, mph: Boolean): Float = if (mph) value * KMH_PER_MPH else value

    fun label(mph: Boolean): String = if (mph) "mph" else "km/h"
}
