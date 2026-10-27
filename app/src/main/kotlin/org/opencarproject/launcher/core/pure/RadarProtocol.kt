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

    private const val HEADER = 0x98.toByte()
    private const val TYPE = 0x04
    private const val DATA = 3
    private const val SIZE = 40
}
