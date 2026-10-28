package org.opencarproject.launcher.core.pure

import org.opencarproject.launcher.core.model.RadarSensor
import org.opencarproject.launcher.core.model.RadarState

/** Pure decoding of the QF SDK parking radar frame. */
object RadarProtocol {

    /** Proximity of a detected obstacle on the vendor zone scale (1 closest .. 10 farthest). */
    enum class Proximity { CLOSE, MEDIUM, FAR }

    /** ponytail: thresholds match the Hyundai/Kia mapping (raw 3→1, 2→5, 1→10); tune per car if needed. */
    fun proximity(zone: Int): Proximity? = when {
        zone <= 0 -> null
        zone <= 3 -> Proximity.CLOSE
        zone <= 6 -> Proximity.MEDIUM
        else -> Proximity.FAR
    }

    /** Zone as 0..1 closeness for drawing: clear or absent → 0, zone 1 → 1.0, zone 10 → 0.1. */
    fun proximity01(zone: Int): Float = if (zone <= 0) 0f else ((11 - zone) / 10f).coerceIn(0f, 1f)

    /**
     * Closeness along a bumper, [samples] points from the car's left to its right. [sensors] are the
     * present sensors only (any count), spread evenly at `(i + 0.5) / n`; between two sensors the
     * value is linearly interpolated and beyond the outer ones it holds their value.
     */
    fun profile(sensors: List<Float>, samples: Int): FloatArray {
        val n = sensors.size
        if (n == 0 || samples <= 0) return FloatArray(samples.coerceAtLeast(0))
        return FloatArray(samples) { s ->
            // Position in sensor units: sensor i sits at i.
            val x = ((s + 0.5f) / samples) * n - 0.5f
            val i = x.toInt().coerceIn(0, n - 1)
            if (x <= 0f) sensors[0]
            else if (i >= n - 1) sensors[n - 1]
            else sensors[i] + (sensors[i + 1] - sensors[i]) * (x - i)
        }
    }

    /**
     * Parses `[0x98, 0x04, len, 40 data bytes, 0xFF]`, laid out as the vendor's
     * `QfSdkDataPack.packRadarState`: 12 zones (LF1 LF2 LF3 RF3 RF2 RF1 LB1 LB2 LB3 RB3 RB2 RB1),
     * 12 distances in the same order, then left side L1..L4 zones + distances and right side
     * R1..R4 zones + distances. Zones are signed (-1 = no sensor); distance 0xFF = not reported.
     * Returns null for any other frame.
     */
    fun parseRadar(frame: ByteArray): RadarState? {
        if (frame.size < DATA + SIZE || frame[0] != HEADER || frame[1].toInt() != TYPE) return null
        fun zone(i: Int) = frame[DATA + i].toInt()
        fun distance(i: Int) = (frame[DATA + i].toInt() and 0xFF).takeIf { it != 0xFF }
        fun sensors(zones: Int, distances: Int, count: Int) =
            List(count) { RadarSensor(zone = zone(zones + it), distanceCm = distance(distances + it)) }
        return RadarState(
            front = sensors(0, 12, 6),
            rear = sensors(6, 18, 6),
            left = sensors(24, 28, 4),
            right = sensors(32, 36, 4),
        )
    }

    /**
     * How the vendor camera app flips the reverse video (`BaseBackCarView.AdjustMirror`):
     * `persist.sys.backcar_mirror` 0..3 picks none / horizontal / vertical / both, except that on
     * some platforms (`ro.qf.platform` 7861, 7862, 7870, 8581) 0↔1 and 2↔3 are swapped, so the
     * default 0 flips horizontally there. Returns (flipX, flipY).
     */
    fun cameraMirror(mirrorProperty: Int, platform: String): Pair<Boolean, Boolean> {
        val swapped = listOf("7862", "8581", "7861", "7870").any { it in platform }
        val mode = if (!swapped) mirrorProperty else when (mirrorProperty) {
            0 -> 1
            1 -> 0
            2 -> 3
            3 -> 2
            else -> mirrorProperty
        }
        return when (mode) {
            1 -> true to false
            2 -> false to true
            3 -> true to true
            else -> false to false
        }
    }

    private const val HEADER = 0x98.toByte()
    private const val TYPE = 0x04
    private const val DATA = 3
    private const val SIZE = 40
}
