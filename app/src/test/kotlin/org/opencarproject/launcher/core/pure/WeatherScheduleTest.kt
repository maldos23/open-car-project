package org.opencarproject.launcher.core.pure

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherScheduleTest {

    private val hour = 3600L
    private val sunrise = 1_000_000L * hour // any day, 07:00 local for the example
    private val sunset = sunrise + 11 * hour // 18:00

    @Test fun dayBetweenSunriseAndSunset() {
        assertTrue(WeatherSchedule.isDay(sunrise + 5 * hour, sunrise, sunset, fallback = false))
        assertFalse(WeatherSchedule.isDay(sunset + 2 * hour, sunrise, sunset, fallback = true))
        assertFalse(WeatherSchedule.isDay(sunrise - 1, sunrise, sunset, fallback = true))
    }

    @Test fun staleTimesStillWorkOnLaterDays() {
        // Fetched yesterday, network lost since: tomorrow 10:00 is day, tomorrow 22:00 is night.
        assertTrue(WeatherSchedule.isDay(sunrise + 24 * hour + 3 * hour, sunrise, sunset, fallback = false))
        assertFalse(WeatherSchedule.isDay(sunrise + 48 * hour + 15 * hour, sunrise, sunset, fallback = true))
    }

    @Test fun unknownTimesUseFallback() {
        assertTrue(WeatherSchedule.isDay(0L, null, sunset, fallback = true))
        assertFalse(WeatherSchedule.isDay(0L, sunset, sunrise, fallback = false)) // nonsense order
    }

    @Test fun failureRetriesSooner() {
        assertTrue(WeatherSchedule.nextWaitMs(lastAttemptFailed = true) < WeatherSchedule.nextWaitMs(lastAttemptFailed = false))
        assertEquals(WeatherSchedule.REFRESH_MS, WeatherSchedule.nextWaitMs(lastAttemptFailed = false))
    }

    @Test fun onlyAFewKilometersCountAsMoving() {
        assertFalse(WeatherSchedule.hasMovedFarEnough(800f))
        assertTrue(WeatherSchedule.hasMovedFarEnough(6_000f))
    }
}
