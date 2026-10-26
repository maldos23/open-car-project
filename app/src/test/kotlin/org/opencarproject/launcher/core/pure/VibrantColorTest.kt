package org.opencarproject.launcher.core.pure

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VibrantColorTest {

    @Test
    fun picksDominantSaturatedHue() {
        // 70 % rojo intenso, 30 % gris: debe salir un rojo.
        val pixels = IntArray(100) { if (it < 70) 0xFFE53935.toInt() else 0xFF808080.toInt() }
        val c = VibrantColor.from(pixels)!!
        val r = c shr 16 and 0xFF
        val g = c shr 8 and 0xFF
        assertTrue("r=$r g=$g", r > 180 && g < 100)
    }

    @Test
    fun grayscaleImageHasNoVibrantColor() {
        assertNull(VibrantColor.from(IntArray(100) { 0xFF777777.toInt() }))
    }

    @Test
    fun paletteKeepsDistinctHuesInOrderOfDominance() {
        // 50 % red, 30 % blue, 20 % orange: red, then blue; the orange (next to red) is skipped.
        val pixels = IntArray(100) {
            when {
                it < 50 -> 0xFFE53935.toInt()
                it < 80 -> 0xFF1E3AE5.toInt()
                else -> 0xFFE5A035.toInt()
            }
        }
        val colors = VibrantColor.palette(pixels)
        assertEquals(2, colors.size)
        assertTrue((colors[0] shr 16 and 0xFF) > 180)
        assertTrue((colors[1] and 0xFF) > 180)
    }

    @Test
    fun grayscaleImageHasEmptyPalette() {
        assertTrue(VibrantColor.palette(IntArray(100) { 0xFF777777.toInt() }).isEmpty())
    }
}
