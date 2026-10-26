package org.opencarproject.launcher.core.pure

/** When the weather is refetched, and whether it is day there right now. */
object WeatherSchedule {

    /** Regular refresh: Open-Meteo updates its current conditions every 15 minutes. */
    const val REFRESH_MS = 15 * 60 * 1000L

    /** Retry after a failed fetch (no network, no location fix yet). */
    const val RETRY_MS = 2 * 60 * 1000L

    /** On returning to the launcher, data younger than this is kept. */
    const val FRESH_MS = 10 * 60 * 1000L

    /** Driving this far from where the weather was fetched means another town or area. */
    const val MOVED_REFRESH_METERS = 5_000f

    /** Wait before the next attempt: short after a failure, so the data comes back soon. */
    fun nextWaitMs(lastAttemptFailed: Boolean): Long = if (lastAttemptFailed) RETRY_MS else REFRESH_MS

    fun hasMovedFarEnough(metersFromFetch: Float): Boolean = metersFromFetch >= MOVED_REFRESH_METERS

    /**
     * Whether it is day at [nowSeconds], from one day's [sunriseSeconds] and [sunsetSeconds]
     * (Unix time). Compared by time of day, so the times stay valid on the following days too
     * (they shift only a minute or two a day): a fetch from last night still knows it's morning.
     * [fallback] is used when the times are unknown.
     */
    fun isDay(nowSeconds: Long, sunriseSeconds: Long?, sunsetSeconds: Long?, fallback: Boolean): Boolean {
        if (sunriseSeconds == null || sunsetSeconds == null || sunsetSeconds <= sunriseSeconds) return fallback
        val sinceSunrise = Math.floorMod(nowSeconds - sunriseSeconds, DAY_SECONDS)
        return sinceSunrise < sunsetSeconds - sunriseSeconds
    }

    private const val DAY_SECONDS = 24 * 60 * 60L
}
