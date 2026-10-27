package org.opencarproject.launcher.core.pure

import org.opencarproject.launcher.core.model.ClimateTemp
import org.opencarproject.launcher.core.model.VehicleIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ClimateProtocolTest {

    @Test
    fun parseCarMode_keepsDashesInsideBrackets() {
        val v = ClimateProtocol.parseCarMode("[Raise-3]-[Kia]-[Sportage]-[16(Import)]")
        assertEquals(VehicleIdentity("Raise-3", "Kia", "Sportage", "16(Import)"), v)
        assertNull(ClimateProtocol.parseCarMode(""))
        assertNull(ClimateProtocol.parseCarMode(null))
    }

    @Test
    fun parseAcFrame_readsQfSdkLayout() {
        val data = ByteArray(25)
        data[0] = 0b1100_0100.toByte() // power, ac, auto
        data[1] = 0b1000_0000.toByte() // front defrost
        data[2] = (0b0110_0000 or 3).toByte() // face + feet, fan 3
        data[5] = 45 // 22.5
        data[6] = 0 // Low
        data[19] = (8 shl 4).toByte() // 8 fan levels
        val frame = byteArrayOf(0x98.toByte(), 1, 25) + data + byteArrayOf(0xFF.toByte())
        val s = ClimateProtocol.parseAcFrame(frame, frameType = 1)!!
        assertTrue(s.power && s.ac && s.auto && s.frontDefrost && s.airFace && s.airFeet)
        assertFalse(s.recirculation || s.rearDefrost || s.airWindshield)
        assertEquals(3, s.fanSpeed)
        assertEquals(ClimateTemp.Value(22.5f, fahrenheit = false), s.leftTemp)
        assertEquals(ClimateTemp.Low, s.rightTemp)
        assertEquals("face+feet goes to feet", "WindDown", ClimateProtocol.nextAirMode(s))
        assertEquals("parallel_down", ClimateProtocol.airModeName(s))
        assertEquals(8, s.maxFanSpeed)
        assertEquals(0.40625f, ClimateProtocol.warmth(s.leftTemp)!!, 1e-4f)
        assertEquals(0f, ClimateProtocol.warmth(s.rightTemp)!!, 0f)
    }

    @Test
    fun parseAcFrame_realKiaFrontDefrostFrame() {
        // Captured from the vendor service on a Raise/Kia head unit with the panel in "FRONT".
        val hex = "98 01 19 c2 80 17 00 00 26 26 00 00 00 00 00 fe fe 00 00 00 ff 00 80 00 00 00 00 00 ff"
        val frame = hex.split(" ").map { it.toInt(16).toByte() }.toByteArray()
        val s = ClimateProtocol.parseAcFrame(frame, frameType = 1)!!
        assertTrue(s.power && s.ac && s.frontDefrost)
        assertFalse(s.airFace || s.airFeet || s.airWindshield)
        assertTrue(ClimateProtocol.blowsWindshield(s))
        assertEquals("up", ClimateProtocol.airModeName(s))
        assertEquals(7, s.fanSpeed)
        assertEquals(8, s.maxFanSpeed)
        assertEquals(ClimateTemp.Value(19f, fahrenheit = false), s.leftTemp)
    }

    @Test
    fun parseAcFrame_rejectsOtherFrames() {
        val frame = byteArrayOf(0x98.toByte(), 3, 25) + ByteArray(26)
        assertNull(ClimateProtocol.parseAcFrame(frame, frameType = 1))
        assertNull(ClimateProtocol.parseAcFrame(byteArrayOf(0x98.toByte(), 1), frameType = 1))
    }

    @Test
    fun bundledVendor_decodes() {
        val vendor = ClimateProtocol.decodeVendor(File("src/main/assets/climate/qf.json").readText())
        assertNotNull(vendor)
        assertEquals("persist.sys.car_mode", vendor!!.carModeProperty)
        assertNull(ClimateProtocol.decodeVendor("{not json"))
        assertNull("missing fields", ClimateProtocol.decodeVendor("{}"))
    }
}
