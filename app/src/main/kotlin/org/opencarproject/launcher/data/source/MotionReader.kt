package org.opencarproject.launcher.data.source

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.SystemClock
import org.opencarproject.launcher.core.pure.MotionMath
import org.opencarproject.launcher.core.pure.SpeedMath
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How the vehicle is moving: yaw rate (rad/s, + = left) and longitudinal acceleration (m/s², + = speeding up). */
data class VehicleMotion(val yawRate: Float = 0f, val acceleration: Float = 0f)

/**
 * Vehicle motion for the edge glow. Turns come from the gyroscope projected on gravity, so the
 * mount angle doesn't matter; without a gyroscope, from the GPS bearing. Acceleration and braking
 * come from the GPS speed (the screen's forward axis is unknown), so they need the location
 * permission; without it only turns are reported.
 */
object MotionReader {

    fun motion(context: Context): Flow<VehicleMotion> = callbackFlow {
        var current = VehicleMotion()
        val sm = context.getSystemService(SensorManager::class.java)
        val gyro = sm?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        val gravitySensor = sm?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val up = floatArrayOf(0f, 0f, 0f)

        val sensorListener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                if (e.sensor.type == Sensor.TYPE_GYROSCOPE) {
                    val yaw = MotionMath.yawRate(e.values[0], e.values[1], e.values[2], up[0], up[1], up[2])
                    current = current.copy(yawRate = SpeedMath.smooth(current.yawRate, yaw, 0.2f))
                    trySend(current)
                } else {
                    // Low-pass: a raw accelerometer also carries the car's own acceleration.
                    for (i in 0..2) up[i] = 0.9f * up[i] + 0.1f * e.values[i]
                }
            }

            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) = Unit
        }
        if (gyro != null && gravitySensor != null) {
            sm.registerListener(sensorListener, gravitySensor, SensorManager.SENSOR_DELAY_UI)
            sm.registerListener(sensorListener, gyro, SensorManager.SENSOR_DELAY_UI)
        }

        var last: Location? = null
        var lastSpeed: Float? = null
        fun onFix(fix: Location) {
            val prev = last
            last = fix
            val dt = prev?.let { (fix.elapsedRealtimeNanos - it.elapsedRealtimeNanos) / 1e9f } ?: 0f
            if (prev == null || dt <= 0f || dt > 5f) {
                lastSpeed = null
                return
            }
            // Some receivers (and mock locations) report no speed: derived from distance/time.
            val speed = if (fix.hasSpeed()) fix.speed else prev.distanceTo(fix) / dt
            var next = current
            lastSpeed?.let { before ->
                next = next.copy(acceleration = SpeedMath.smooth(current.acceleration, (speed - before) / dt, 0.5f))
            }
            lastSpeed = speed
            // ponytail: bearing is meaningless when crawling; 2 m/s ≈ 7 km/h is the cut-off.
            if (gyro == null && fix.hasBearing() && prev.hasBearing() && speed > 2f) {
                val yaw = -Math.toRadians(MotionMath.bearingDelta(prev.bearing, fix.bearing).toDouble()).toFloat() / dt
                next = next.copy(yawRate = SpeedMath.smooth(current.yawRate, yaw, 0.5f))
            }
            current = next
            trySend(current)
        }
        // No fix for a while (tunnel, signal lost): the last acceleration must not stay lit.
        launch {
            while (true) {
                delay(1000)
                val lastFix = last ?: continue
                if (SystemClock.elapsedRealtimeNanos() - lastFix.elapsedRealtimeNanos > 2_500_000_000L) {
                    // Without a gyroscope the turn also comes from GPS: it goes dark too.
                    val stale = current.copy(acceleration = 0f, yawRate = if (gyro == null) 0f else current.yawRate)
                    if (stale != current) {
                        current = stale
                        trySend(current)
                    }
                }
            }
        }
        launch { GpsFixes.get(context).collect(::onFix) }

        awaitClose { sm?.unregisterListener(sensorListener) }
    }
}
