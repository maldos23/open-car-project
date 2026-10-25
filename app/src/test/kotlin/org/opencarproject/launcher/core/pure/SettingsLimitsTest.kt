package org.opencarproject.launcher.core.pure

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsLimitsTest {

    @Test
    fun snap_roundsToStepAndClamps() {
        assertEquals(1.05f, snap(1.04f, 20, UiScaleRange), 0.0001f)
        assertEquals(1.3f, snap(2f, 20, UiScaleRange), 0.0001f)
        assertEquals(0.5f, snap(0.1f, 10, MotionGlowSensitivityRange), 0.0001f)
    }
}
