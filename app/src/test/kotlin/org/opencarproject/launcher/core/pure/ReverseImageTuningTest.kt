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
    fun valuesAreClamped() {
        assertEquals(ReverseImageTuning.of(1f, 1f), ReverseImageTuning.of(5f, 9f))
        assertEquals(ReverseImageTuning.of(0f, 0f), ReverseImageTuning.of(-1f, -1f))
    }
}
