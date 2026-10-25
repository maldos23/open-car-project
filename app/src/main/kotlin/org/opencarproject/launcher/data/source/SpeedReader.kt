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
import android.os.Looper
import android.os.SystemClock
import org.opencarproject.launcher.core.model.SpeedSource
import org.opencarproject.launcher.core.pure.SpeedMath
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlin.math.sqrt

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
    @SuppressLint("MissingPermission") // checked with hasGpsPermission
    private fun gps(context: Context): Flow<Float> = callbackFlow {
        val manager = context.getSystemService(LocationManager::class.java)
        if (manager == null || !hasGpsPermission(context)) {
            trySend(0f)
            awaitClose { }
            return@callbackFlow
        }
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
        val listener = LocationListener { fix ->
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
        runCatching { manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener, Looper.getMainLooper()) }
        awaitClose { manager.removeUpdates(listener) }
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
        var gravity = floatArrayOf(0f, 0f, 0f)
        var sumSq = 0f
        var count = 0
        var windowStart = 0L
        var smoothed = 0f
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val (x, y, z) = if (linear) {
                    Triple(e.values[0], e.values[1], e.values[2])
                } else {
                    // No linear sensor: gravity is removed with a low-pass filter.
                    gravity = FloatArray(3) { i -> 0.9f * gravity[i] + 0.1f * e.values[i] }
                    Triple(e.values[0] - gravity[0], e.values[1] - gravity[1], e.values[2] - gravity[2])
                }
                sumSq += x * x + y * y + z * z
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
