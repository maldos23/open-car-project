package org.opencarproject.launcher.data.source

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.SystemClock
import org.opencarproject.launcher.core.pure.SpeedMath
import org.opencarproject.launcher.core.pure.turn.GpsBearingYawEstimator
import org.opencarproject.launcher.core.pure.turn.GyroscopeYawEstimator
import org.opencarproject.launcher.core.pure.turn.TurnDetector
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How the vehicle is moving: yaw rate (rad/s, + = left) and longitudinal acceleration (m/s², + = speeding up). */
data class VehicleMotion(val yawRate: Float = 0f, val acceleration: Float = 0f)

/**
 * Vehicle motion for the edge glow. Turns come from [TurnDetector]: the gyroscope once it proves
 * to be alive, otherwise the GPS bearing. Acceleration and braking come from the GPS speed (the
 * screen's forward axis is unknown), so they need the location permission.
 */
object MotionReader {

    /** Below this distance between fixes a bearing derived from the positions is GPS noise. */
    private const val MIN_DISTANCE_FOR_DERIVED_BEARING_METERS = 3f

    fun motion(context: Context): Flow<VehicleMotion> = callbackFlow {
        var current = VehicleMotion()
        val gyroscopeEstimator = GyroscopeYawEstimator()
        val gpsBearingEstimator = GpsBearingYawEstimator()
        val turnDetector = TurnDetector(listOf(gyroscopeEstimator, gpsBearingEstimator))

        fun publish(acceleration: Float = current.acceleration) {
            val next = VehicleMotion(turnDetector.currentYawRateRadiansPerSecond, acceleration)
            if (next != current) {
                current = next
                trySend(next)
            }
        }

        val sm = context.getSystemService(SensorManager::class.java)
        val sensorListener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                if (e.sensor.type == Sensor.TYPE_GYROSCOPE) {
                    gyroscopeEstimator.updateGyroscopeSample(e.values[0], e.values[1], e.values[2])
                    publish()
                } else {
                    gyroscopeEstimator.updateGravityVector(e.values[0], e.values[1], e.values[2])
                }
            }

            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) = Unit
        }
        val gravitySensor = sm?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        listOfNotNull(gravitySensor, sm?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)).forEach {
            sm?.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI)
        }

        var last: Location? = null
        var lastSpeed: Float? = null
        fun onFix(fix: Location) {
            val prev = last
            last = fix
            val dt = prev?.let { (fix.elapsedRealtimeNanos - it.elapsedRealtimeNanos) / 1e9f } ?: 0f
            // Some receivers (and mock locations) report no speed or bearing: derived from the positions.
            val distance = prev?.distanceTo(fix) ?: 0f
            val speed = if (fix.hasSpeed() || dt <= 0f) fix.speed else distance / dt
            val bearing = when {
                fix.hasBearing() -> fix.bearing
                prev != null && distance >= MIN_DISTANCE_FOR_DERIVED_BEARING_METERS -> prev.bearingTo(fix)
                else -> null
            }
            gpsBearingEstimator.updateGpsFix(fix.elapsedRealtimeNanos, speed, bearing)
            if (prev == null || dt <= 0f || dt > 5f) {
                lastSpeed = null
                return publish()
            }
            val acceleration = lastSpeed?.let { SpeedMath.smooth(current.acceleration, (speed - it) / dt, 0.5f) }
            lastSpeed = speed
            publish(acceleration ?: current.acceleration)
        }
        // No fix for a while (tunnel, signal lost): the last acceleration and GPS turn must not stay lit.
        launch {
            while (true) {
                delay(1000)
                val lastFix = last ?: continue
                val now = SystemClock.elapsedRealtimeNanos()
                gpsBearingEstimator.markSignalStaleIfOlderThan(now)
                publish(if (now - lastFix.elapsedRealtimeNanos > 2_500_000_000L) 0f else current.acceleration)
            }
        }
        launch { GpsFixes.get(context).collect(::onFix) }

        awaitClose { sm?.unregisterListener(sensorListener) }
    }
}
