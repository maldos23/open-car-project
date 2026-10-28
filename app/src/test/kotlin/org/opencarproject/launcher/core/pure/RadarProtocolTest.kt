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
    fun proximity01_closerIsHigher() {
        assertEquals(0f, RadarProtocol.proximity01(-1), 0f)
        assertEquals(0f, RadarProtocol.proximity01(0), 0f)
        assertEquals(1f, RadarProtocol.proximity01(1), 1e-6f)
        assertEquals(0.6f, RadarProtocol.proximity01(5), 1e-6f)
        assertEquals(0.1f, RadarProtocol.proximity01(10), 1e-6f)
    }

    @Test
    fun profile_interpolatesBetweenSensors() {
        // 4 sensors, obstacle at the second one; 8 samples → sensor i sits between samples 2i and 2i+1.
        val p = RadarProtocol.profile(listOf(0f, 1f, 0f, 0f), 8)
        assertEquals(8, p.size)
        assertEquals(0f, p[0], 1e-6f) // left edge holds sensor 0
        assertEquals(0.75f, p[2], 1e-6f) // near sensor 1, on its left
        assertEquals(0.75f, p[3], 1e-6f) // near sensor 1, on its right
        assertEquals(0.25f, p[4], 1e-6f) // fading towards sensor 2
        assertEquals(0f, p[7], 1e-6f)
        assertEquals(p.max(), p[2], 0f) // peak is around the obstacle
    }

    @Test
    fun profile_edgeCases() {
        assertEquals(listOf(0.4f, 0.4f, 0.4f), RadarProtocol.profile(listOf(0.4f), 3).toList())
        assertEquals(listOf(0f, 0f), RadarProtocol.profile(emptyList(), 2).toList())
        assertEquals(0, RadarProtocol.profile(listOf(1f), 0).size)
    }

    @Test
    fun cameraMirror_followsVendorRules() {
        // K706 (platform 8581) with the default 0: the vendor flips horizontally.
        assertEquals(true to false, RadarProtocol.cameraMirror(0, "8581"))
        assertEquals(false to false, RadarProtocol.cameraMirror(1, "8581"))
        assertEquals(true to true, RadarProtocol.cameraMirror(2, "8581"))
        // Other platforms use the value as is.
        assertEquals(false to false, RadarProtocol.cameraMirror(0, "9863"))
        assertEquals(false to true, RadarProtocol.cameraMirror(2, ""))
    }

    @Test
    fun parseRadar_rejectsOtherFrames() {
        assertNull(RadarProtocol.parseRadar(frame(type = 3)))
        assertNull(RadarProtocol.parseRadar(frame(data = ByteArray(20))))
        assertNull(RadarProtocol.parseRadar(ByteArray(0)))
    }
}
