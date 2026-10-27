package org.opencarproject.launcher.core.pure

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Outline of a [w]×[h] rectangle with [corner] radius whose edge waves inward like a flower
 * ("scalloped"). Returns x,y pairs clockwise from the top-left end of the top edge; the path is
 * closed implicitly. The wave count is rounded so the last wave meets the first one.
 */
fun scallopedOutline(w: Float, h: Float, corner: Float, depth: Float, waveLength: Float, step: Float = 2f): FloatArray {
    val r = corner.coerceIn(0f, min(w, h) / 2)
    val sw = max(0f, w - 2 * r)
    val sh = max(0f, h - 2 * r)
    val arc = (PI / 2 * r).toFloat()
    val perimeter = 2 * sw + 2 * sh + 4 * arc
    if (perimeter <= 0f) return FloatArray(0)
    val waves = max(1, (perimeter / waveLength).roundToInt())
    // At least 8 samples per wave so the scallops stay smooth on small cards.
    val count = max(8 * waves, (perimeter / step).roundToInt())
    val segments = floatArrayOf(sw, arc, sh, arc, sw, arc, sh, arc)
    val out = FloatArray(count * 2)
    for (i in 0 until count) {
        var s = perimeter * i / count
        val d = depth * (1 - cos(2 * PI * waves * s / perimeter)).toFloat() / 2
        var seg = 0
        while (seg < 7 && s > segments[seg]) s -= segments[seg++]
        // Base point on the rounded rect and its inward normal.
        val x: Float
        val y: Float
        val nx: Float
        val ny: Float
        when (seg) {
            0 -> { x = r + s; y = 0f; nx = 0f; ny = 1f }
            2 -> { x = w; y = r + s; nx = -1f; ny = 0f }
            4 -> { x = w - r - s; y = h; nx = 0f; ny = -1f }
            6 -> { x = 0f; y = h - r - s; nx = 1f; ny = 0f }
            else -> {
                // Corner arcs: TR, BR, BL, TL, each starting 90° after the previous one.
                val cx = if (seg == 1 || seg == 3) w - r else r
                val cy = if (seg == 3 || seg == 5) h - r else r
                val a = (PI / 2 * (seg / 2) - PI / 2 + if (r > 0f) s / r else 0f)
                val ca = cos(a).toFloat()
                val sa = sin(a).toFloat()
                x = cx + r * ca; y = cy + r * sa; nx = -ca; ny = -sa
            }
        }
        out[2 * i] = x + nx * d
        out[2 * i + 1] = y + ny * d
    }
    return out
}
