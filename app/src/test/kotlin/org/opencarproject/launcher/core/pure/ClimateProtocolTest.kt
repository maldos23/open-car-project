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
        val frame = byteArrayOf(0x98.toByte(), 1, 25) + data + byteArrayOf(0xFF.toByte())
        val s = ClimateProtocol.parseAcFrame(frame, frameType = 1)!!
        assertTrue(s.power && s.ac && s.auto && s.frontDefrost && s.airFace && s.airFeet)
        assertFalse(s.recirculation || s.rearDefrost || s.airWindshield)
        assertEquals(3, s.fanSpeed)
        assertEquals(ClimateTemp.Value(22.5f, fahrenheit = false), s.leftTemp)
        assertEquals(ClimateTemp.Low, s.rightTemp)
        assertEquals("face+feet goes to feet", "WindDown", ClimateProtocol.nextAirMode(s))
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
