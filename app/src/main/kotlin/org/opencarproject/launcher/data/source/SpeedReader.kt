package org.opencarproject.launcher.data.source

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import org.opencarproject.launcher.core.model.SpeedSource
import org.opencarproject.launcher.core.pure.SpeedMath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * Location listener for any API level. A Kotlin lambda only implements onLocationChanged; before
 * API 30 the other callbacks are abstract and the system calling them crashes the app.
 */
internal fun locationListener(onFix: (Location) -> Unit): LocationListener = object : LocationListener {
    override fun onLocationChanged(location: Location) = onFix(location)
    override fun onProviderEnabled(provider: String) = Unit
    override fun onProviderDisabled(provider: String) = Unit

    @Deprecated("Deprecated in API 29")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
}

/**
 * GPS fixes (1 s) shared by every reader: the speed scene, the speedometer and the motion glow
 * use a single system listener, which stops when nobody collects (the launcher is stopped).
 * Requires ACCESS_FINE_LOCATION; without it the flow stays silent.
 */
internal object GpsFixes {
    private var shared: SharedFlow<Location>? = null

    // ponytail: process-wide scope, it only hosts this upstream and dies with the process.
    private val scope: CoroutineScope = MainScope()

    /** Without the permission there is nothing to share: callers subscribe again once granted. */
    fun get(context: Context): Flow<Location> {
        if (!SpeedReader.hasGpsPermission(context)) return emptyFlow()
        return shared ?: fixes(context.applicationContext)
            .shareIn(scope, SharingStarted.WhileSubscribed())
            .also { shared = it }
    }

    @SuppressLint("MissingPermission") // checked in get()
    private fun fixes(context: Context): Flow<Location> = callbackFlow {
        val manager = context.getSystemService(LocationManager::class.java) ?: return@callbackFlow awaitClose { }
        val listener = locationListener { trySend(it) }
        runCatching { manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener, Looper.getMainLooper()) }
        awaitClose { manager.removeUpdates(listener) }
    }
}

/** Vehicle speed in km/h from the source chosen in settings. */
object SpeedReader {

    private const val NO_FIX_TIMEOUT_MS = 4_000L

    fun hasGpsPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun speedKmh(context: Context, source: SpeedSource): Flow<Float> = when (source) {
        SpeedSource.GPS -> gps(context)
        SpeedSource.SENSORS -> sensors(context)
    }

    /**
     * GPS: uses the fix speed; if missing (some receivers or mock locations), computes it
     * from distance/time between readings. With no signal for a few seconds, it decays to 0.
     */
    private fun gps(context: Context): Flow<Float> = callbackFlow {
        trySend(0f)
        var last: Location? = null
        var smoothed = 0f
        var lastFixAt = 0L
        // No readings (tunnel, or stopped without changing position): speed decays to 0.
        launch {
            while (true) {
                delay(1000)
                if (lastFixAt != 0L && SystemClock.elapsedRealtime() - lastFixAt > NO_FIX_TIMEOUT_MS && smoothed > 0f) {
                    smoothed = 0f
                    trySend(0f)
                }
            }
        }
        launch {
            GpsFixes.get(context).collect { fix ->
                lastFixAt = SystemClock.elapsedRealtime()
                val raw = when {
                    fix.hasSpeed() -> fix.speed * 3.6f
                    else -> last?.let { SpeedMath.speedKmh(it.latitude, it.longitude, it.time, fix.latitude, fix.longitude, fix.time) }
                }
                last = fix
                if (raw != null) {
                    smoothed = SpeedMath.smooth(smoothed, raw, 0.5f)
                    trySend(if (smoothed < 1.5f) 0f else smoothed)
                }
            }
        }
        awaitClose { }
    }

    /**
     * Sensors (approximate): RMS of linear acceleration over a ~1 s window. The gyroscope
     * does not measure speed; this only distinguishes stopped / slow / fast by the car's vibration.
     */
    private fun sensors(context: Context): Flow<Float> = callbackFlow {
        val sm = context.getSystemService(SensorManager::class.java)
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) ?: sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sm == null || sensor == null) {
            trySend(0f)
            awaitClose { }
            return@callbackFlow
        }
        val linear = sensor.type == Sensor.TYPE_LINEAR_ACCELERATION
        // Seeded with the first reading: starting at 0 would count gravity (~9.8 m/s²) as motion.
        var gravity: FloatArray? = null
        var sumSq = 0f
        var count = 0
        var windowStart = 0L
        var smoothed = 0f
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                // ~50 events per second: no allocations here.
                var magnitudeSq = 0f
                if (linear) {
                    for (i in 0..2) magnitudeSq += e.values[i] * e.values[i]
                } else {
                    // No linear sensor: gravity is removed with a low-pass filter.
                    val g = gravity ?: e.values.copyOf(3).also { gravity = it }
                    for (i in 0..2) {
                        g[i] = 0.9f * g[i] + 0.1f * e.values[i]
                        val d = e.values[i] - g[i]
                        magnitudeSq += d * d
                    }
                }
                sumSq += magnitudeSq
                count++
                if (windowStart == 0L) windowStart = e.timestamp
                if (e.timestamp - windowStart > 1_000_000_000L) {
                    val rms = sqrt(sumSq / count)
                    smoothed = SpeedMath.smooth(smoothed, SpeedMath.estimateKmhFromMotion(rms), 0.3f)
                    trySend(if (smoothed < 2f) 0f else smoothed)
                    sumSq = 0f
                    count = 0
                    windowStart = e.timestamp
                }
            }

            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) = Unit
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        awaitClose { sm.unregisterListener(listener) }
    }
}
