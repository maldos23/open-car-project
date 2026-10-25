package org.opencarproject.launcher.core.pure

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NetworkLabelsTest {

    @Test
    fun cellular_mapsGenerations() {
        assertEquals("E", NetworkLabels.cellular(2))      // EDGE
        assertEquals("3G", NetworkLabels.cellular(3))     // UMTS
        assertEquals("H+", NetworkLabels.cellular(15))    // HSPA+
        assertEquals("4G", NetworkLabels.cellular(13))    // LTE
        assertEquals("5G", NetworkLabels.cellular(20))    // NR SA
        assertNull(NetworkLabels.cellular(0))             // unknown
    }

    @Test
    fun cellular_overrideWins() {
        assertEquals("5G", NetworkLabels.cellular(13, override = 3))  // LTE + NR NSA
        assertEquals("5G+", NetworkLabels.cellular(13, override = 5)) // NR advanced
        assertEquals("4G+", NetworkLabels.cellular(13, override = 1)) // LTE CA
    }

    @Test
    fun wifi_mapsStandardAndBand() {
        assertEquals("Wi-Fi 5", NetworkLabels.wifi(5, 5180))
        assertEquals("Wi-Fi 6", NetworkLabels.wifi(6, 5180))
        assertEquals("Wi-Fi 6E", NetworkLabels.wifi(6, 5955))
        assertEquals("Wi-Fi 7", NetworkLabels.wifi(8, 6115))
        assertEquals("2.4 GHz", NetworkLabels.wifi(1, 2437)) // legacy: the band is shown
        assertEquals("5 GHz", NetworkLabels.wifi(0, 5180))
        assertNull(NetworkLabels.wifi(0, -1))
    }
}
