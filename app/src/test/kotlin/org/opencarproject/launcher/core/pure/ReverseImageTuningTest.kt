package org.opencarproject.launcher.core.pure

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReverseImageTuningTest {

    @Test
    fun zeroDenoise_isPassThrough() {
        val t = ReverseImageTuning.of(denoise = 0f, sharpness = 0f)
        assertEquals(1f, t.stillWeight, 1e-6f) // no temporal averaging
        assertEquals(0f, t.chromaSmoothing, 1e-6f)
        assertEquals(0f, t.sharpen, 1e-6f)
    }

    @Test
    fun strongerDenoise_averagesMoreAndToleratesMoreNoise() {
        val low = ReverseImageTuning.of(0.2f, 0.3f)
        val high = ReverseImageTuning.of(0.9f, 0.3f)
        assertTrue(high.stillWeight < low.stillWeight)
        assertTrue(high.motionThreshold > low.motionThreshold)
        assertTrue(high.edgeFalloff < low.edgeFalloff)
    }

    @Test
    fun brightnessAndContrast_neutralAtHalf() {
        val n = ReverseImageTuning.of(0.5f, 0.5f)
        assertEquals(0f, n.brightness, 1e-6f)
        assertEquals(1f, n.contrast, 1e-6f)
        val t = ReverseImageTuning.of(0.5f, 0.5f, brightness = 1f, contrast = 0f)
        assertEquals(0.25f, t.brightness, 1e-6f)
        assertEquals(0.5f, t.contrast, 1e-6f)
    }

    @Test
    fun valuesAreClamped() {
        assertEquals(ReverseImageTuning.of(1f, 1f), ReverseImageTuning.of(5f, 9f))
        assertEquals(ReverseImageTuning.of(0f, 0f), ReverseImageTuning.of(-1f, -1f))
    }
}
