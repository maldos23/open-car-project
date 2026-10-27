package org.opencarproject.launcher.core.pure

import org.opencarproject.launcher.core.model.RadarSensor
import org.opencarproject.launcher.core.model.closest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class RadarProtocolTest {

    private fun frame(type: Int = 4, data: ByteArray = ByteArray(40) { -1 }) =
        byteArrayOf(0x98.toByte(), type.toByte(), data.size.toByte()) + data + byteArrayOf(0xFF.toByte())

    /** Frame logged on a Kia (Raise CAN box): corners absent, inner sensors report zones only. */
    @Test
    fun parseRadar_readsZonesAndMissingSensors() {
        val data = ByteArray(40) { -1 }
        listOf(1, 2, 3, 4, 8, 9, 10).forEach { data[it] = 0 } // inner sensors present, clear
        data[7] = 1 // LB2: closest zone
        data[10] = 10 // RB2: far
        data[19] = 45 // LB2 distance reported
        val r = RadarProtocol.parseRadar(frame(data = data))!!
        assertFalse(r.rear[0].present) // LB1 has no sensor
        assertEquals(RadarSensor(1, 45), r.rear[1])
        assertEquals(RadarSensor(10, null), r.rear[4])
        assertEquals(RadarSensor(0, null), r.front[1])
        assertEquals(listOf(6, 6, 4, 4), listOf(r.front.size, r.rear.size, r.left.size, r.right.size))
        assertEquals(RadarSensor(1, 45), closest(r.rear))
        assertNull(closest(r.front))
    }

    @Test
    fun proximity_lowerZoneIsCloser() {
        assertNull(RadarProtocol.proximity(-1))
        assertNull(RadarProtocol.proximity(0))
        assertEquals(RadarProtocol.Proximity.CLOSE, RadarProtocol.proximity(1))
        assertEquals(RadarProtocol.Proximity.MEDIUM, RadarProtocol.proximity(5))
        assertEquals(RadarProtocol.Proximity.FAR, RadarProtocol.proximity(10))
    }

    @Test
    fun parseRadar_rejectsOtherFrames() {
        assertNull(RadarProtocol.parseRadar(frame(type = 3)))
        assertNull(RadarProtocol.parseRadar(frame(data = ByteArray(20))))
        assertNull(RadarProtocol.parseRadar(ByteArray(0)))
    }
}
