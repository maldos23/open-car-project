package org.opencarproject.launcher.core.model

/**
 * One parking sensor as reported by the CAN app. [zone] follows the vendor scale:
 * -1 = no sensor at this position, 0 = clear, 1..10 = obstacle (1 closest, 10 farthest).
 * [distanceCm] is null when the car only reports zones (most CAN boxes).
 */
data class RadarSensor(val zone: Int, val distanceCm: Int? = null) {
    val present: Boolean get() = zone >= 0
    val detected: Boolean get() = zone > 0
}

/** Open/closed state of each door, the trunk and the hood (true = open). */
data class DoorState(
    val frontLeft: Boolean = false,
    val frontRight: Boolean = false,
    val rearLeft: Boolean = false,
    val rearRight: Boolean = false,
    val trunk: Boolean = false,
    val hood: Boolean = false,
) {
    val anyOpen: Boolean get() = frontLeft || frontRight || rearLeft || rearRight || trunk || hood
}

/** Parking radar state; each zone lists its sensors from the car's left to its right. */
data class RadarState(
    val front: List<RadarSensor>,
    val rear: List<RadarSensor>,
    val left: List<RadarSensor>,
    val right: List<RadarSensor>,
)

/** Closest obstacle among [sensors] (lowest zone), or null when nothing is detected. */
fun closest(sensors: List<RadarSensor>?): RadarSensor? =
    sensors?.filter { it.detected }?.minWithOrNull(compareBy({ it.zone }, { it.distanceCm ?: Int.MAX_VALUE }))
