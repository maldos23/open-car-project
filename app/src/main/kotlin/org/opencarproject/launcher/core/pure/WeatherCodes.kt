package org.opencarproject.launcher.core.pure

import org.opencarproject.launcher.core.model.WeatherCondition

/** Maps the WMO codes returned by Open-Meteo (`weather_code`) to a condition. */
object WeatherCodes {

    fun condition(wmoCode: Int): WeatherCondition = when (wmoCode) {
        0 -> WeatherCondition.CLEAR
        1, 2 -> WeatherCondition.PARTLY_CLOUDY
        3 -> WeatherCondition.CLOUDY
        45, 48 -> WeatherCondition.FOG
        in 51..57 -> WeatherCondition.DRIZZLE
        in 61..67, in 80..82 -> WeatherCondition.RAIN
        in 71..77, 85, 86 -> WeatherCondition.SNOW
        in 95..99 -> WeatherCondition.THUNDERSTORM
        else -> WeatherCondition.UNKNOWN
    }

    /** Short name to show when the city is unknown. */
    fun label(condition: WeatherCondition): String = when (condition) {
        WeatherCondition.CLEAR -> "Despejado"
        WeatherCondition.PARTLY_CLOUDY -> "Parcialmente nublado"
        WeatherCondition.CLOUDY -> "Nublado"
        WeatherCondition.FOG -> "Niebla"
        WeatherCondition.DRIZZLE -> "Llovizna"
        WeatherCondition.RAIN -> "Lluvia"
        WeatherCondition.SNOW -> "Nieve"
        WeatherCondition.THUNDERSTORM -> "Tormenta"
        WeatherCondition.UNKNOWN -> "Clima"
    }
}
