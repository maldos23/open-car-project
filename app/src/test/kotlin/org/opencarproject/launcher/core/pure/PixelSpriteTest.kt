package org.opencarproject.launcher.core.pure

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PixelSpriteTest {

    @Test
    fun catFrames_parseWithTheSameSize() {
        val (a, b) = PixelSprite.CAT_FRAMES
        assertEquals(22, a.width)
        assertEquals(13, a.height)
        assertEquals(a.width, b.width)
        assertEquals(a.height, b.height)
    }

    @Test
    fun transparentPixelIsNull() {
        val sprite = PixelSprite.parse(listOf("k.", ".g"), "kg")
        assertEquals('k', sprite.at(0, 0))
        assertNull(sprite.at(1, 0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun unevenRowsAreRejected() {
        PixelSprite.parse(listOf("kk", "k"), "k")
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownPixelIsRejected() {
        PixelSprite.parse(listOf("kx"), "k")
    }
}
