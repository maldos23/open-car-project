package org.opencarproject.launcher.data.source

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import org.opencarproject.launcher.core.model.Weather
import org.opencarproject.launcher.core.pure.WeatherCodes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Current weather at the device's approximate location.
 *
 * - Data: Open-Meteo (free, no key; non-commercial use, CC BY 4.0 attribution).
 * - Location: approximate (ACCESS_COARSE_LOCATION), enough for weather.
 * - City: Android's `Geocoder`; on devices without Google services it may not exist, in
 *   which case the condition is shown instead.
 */
class WeatherSource(private val context: Context) {

    fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** `null` if there's no permission, location or network. */
    suspend fun current(): Weather? = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext null
        val location = location() ?: return@withContext null
        runCatching {
            // Locale.US: with a Spanish system locale the decimal would come out with a comma.
            val url = String.format(
                Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f" +
                    "&current=temperature_2m,weather_code,is_day&timezone=auto",
                location.latitude,
                location.longitude,
            )
            val current = JSONObject(get(url)).getJSONObject("current")
            Weather(
                celsius = current.getDouble("temperature_2m"),
                condition = WeatherCodes.condition(current.getInt("weather_code")),
                isDay = current.optInt("is_day", 1) == 1,
                city = city(location),
            )
        }.getOrNull()
    }

    private fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        return try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /** Last known location if recent; otherwise requests a new one (max 15 s). */
    @SuppressLint("MissingPermission") // checked in current()
    private suspend fun location(): Location? {
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = manager.getProviders(true).filter { it != LocationManager.PASSIVE_PROVIDER }
        val last = providers
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        if (last != null && System.currentTimeMillis() - last.time < MAX_LOCATION_AGE_MS) return last

        for (provider in providers) {
            val fresh = withTimeoutOrNull(15_000) {
                suspendCancellableCoroutine { cont ->
                    val signal = androidx.core.os.CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    runCatching {
                        LocationManagerCompat.getCurrentLocation(manager, provider, signal, ContextCompat.getMainExecutor(context)) {
                            if (cont.isActive) cont.resume(it)
                        }
                    }.onFailure { if (cont.isActive) cont.resume(null) }
                }
            }
            if (fresh != null) return fresh
        }
        return last
    }

    private suspend fun city(location: Location): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.getDefault())
        val address: Address? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            withTimeoutOrNull(10_000) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(
                        location.latitude,
                        location.longitude,
                        1,
                        object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                if (cont.isActive) cont.resume(addresses.firstOrNull())
                            }

                            override fun onError(errorMessage: String?) {
                                if (cont.isActive) cont.resume(null)
                            }
                        },
                    )
                }
            }
        } else {
            @Suppress("DEPRECATION")
            runCatching { geocoder.getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull() }.getOrNull()
        }
        return address?.locality ?: address?.subLocality ?: address?.subAdminArea ?: address?.adminArea
    }

    private companion object {
        const val MAX_LOCATION_AGE_MS = 60 * 60 * 1000L
    }
}
