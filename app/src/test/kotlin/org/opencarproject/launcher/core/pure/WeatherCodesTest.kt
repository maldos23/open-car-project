package org.opencarproject.launcher.core.pure

import org.opencarproject.launcher.core.model.WeatherCondition
import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherCodesTest {

    @Test
    fun condition_mapsWmoRanges() {
        assertEquals(WeatherCondition.CLEAR, WeatherCodes.condition(0))
        assertEquals(WeatherCondition.PARTLY_CLOUDY, WeatherCodes.condition(2))
        assertEquals(WeatherCondition.CLOUDY, WeatherCodes.condition(3))
        assertEquals(WeatherCondition.FOG, WeatherCodes.condition(48))
        assertEquals(WeatherCondition.DRIZZLE, WeatherCodes.condition(53))
        assertEquals(WeatherCondition.RAIN, WeatherCodes.condition(63))
        assertEquals(WeatherCondition.RAIN, WeatherCodes.condition(81)) // chubascos
        assertEquals(WeatherCondition.SNOW, WeatherCodes.condition(86))
        assertEquals(WeatherCondition.THUNDERSTORM, WeatherCodes.condition(95))
        assertEquals(WeatherCondition.UNKNOWN, WeatherCodes.condition(42))
    }
}
