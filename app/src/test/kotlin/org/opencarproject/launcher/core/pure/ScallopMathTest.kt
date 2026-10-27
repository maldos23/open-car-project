package org.opencarproject.launcher.core.pure

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.math.min

class ScallopMathTest {

    @Test
    fun pointsStayInsideBounds() {
        val p = scallopedOutline(300f, 180f, corner = 24f, depth = 10f, waveLength = 40f)
        assertTrue(p.isNotEmpty())
        for (i in p.indices step 2) {
            assertTrue(p[i] in -1e-3f..300.001f)
            assertTrue(p[i + 1] in -1e-3f..180.001f)
        }
    }

    @Test
    fun startsAtTopEdgeAndClosesSmoothly() {
        val p = scallopedOutline(200f, 100f, corner = 20f, depth = 8f, waveLength = 30f)
        // Wave depth is 0 at the start, and the last sample is close to it again.
        assertEquals(20f, p[0], 1e-3f)
        assertEquals(0f, p[1], 1e-3f)
        assertTrue(hypot(p[p.size - 2] - p[0], p[p.size - 1] - p[1]) < 4f)
    }

    @Test
    fun zeroDepthIsTheRoundedRect() {
        val w = 160f; val h = 90f; val r = 16f
        val p = scallopedOutline(w, h, r, depth = 0f, waveLength = 30f)
        for (i in p.indices step 2) {
            val x = p[i]; val y = p[i + 1]
            // Distance to the rounded rect border is ~0.
            val qx = (x - x.coerceIn(r, w - r)); val qy = (y - y.coerceIn(r, h - r))
            val onArc = qx != 0f && qy != 0f
            val dist = if (onArc) hypot(qx, qy) - r else min(min(x, w - x), min(y, h - y))
            assertEquals(0f, dist, 1e-2f)
        }
    }

    @Test
    fun degenerateSizeIsEmpty() {
        assertEquals(0, scallopedOutline(0f, 0f, 10f, 5f, 20f).size)
    }
}
