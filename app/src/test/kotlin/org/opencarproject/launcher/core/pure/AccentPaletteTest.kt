package org.opencarproject.launcher.core.pure

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccentPaletteTest {

    /** Hues of the accents offered in settings. */
    private val hues = listOf(175f, 140f, 270f, 330f, 28f, 5f)

    @Test
    fun hsl_convertsPrimaries() {
        assertEquals(0xFFFF0000.toInt(), AccentPalette.hsl(0f, 1f, 0.5f))
        assertEquals(0xFF00FF00.toInt(), AccentPalette.hsl(120f, 1f, 0.5f))
        assertEquals(0xFF0000FF.toInt(), AccentPalette.hsl(240f, 1f, 0.5f))
    }

    @Test
    fun tones_textOnAccentIsReadable_inBothThemes() {
        for (hue in hues) for (dark in listOf(true, false)) {
            val t = AccentPalette.tones(hue, dark)
            assertTrue("primario h=$hue dark=$dark", AccentPalette.contrast(t.primary, t.onPrimary) >= 4.5)
            assertTrue("contenedor h=$hue dark=$dark", AccentPalette.contrast(t.primaryContainer, t.onPrimaryContainer) >= 4.5)
            assertTrue("tonal h=$hue dark=$dark", AccentPalette.contrast(t.secondaryContainer, t.onSecondaryContainer) >= 4.5)
        }
    }
}
