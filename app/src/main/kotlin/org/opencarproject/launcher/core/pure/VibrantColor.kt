package org.opencarproject.launcher.core.pure

import kotlin.math.max
import kotlin.math.min

/**
 * Dominant vibrant color of an image (for the player background), dependency-free:
 * takes ARGB pixels (a thumbnail is enough) and picks the most frequent hue among saturated ones.
 * Returns `null` if the image is nearly gray (black-and-white covers).
 */
object VibrantColor {

    fun from(pixels: IntArray): Int? = palette(pixels, 1).firstOrNull()

    /**
     * Up to [max] vibrant colors of the image, most dominant first, each from a different
     * hue family (neighboring hues are skipped so the colors actually differ). Empty if gray.
     */
    fun palette(pixels: IntArray, max: Int = 3): List<Int> {
        // 12 hue buckets; each pixel votes with its "vividness" (saturation × mid brightness).
        val weight = FloatArray(12)
        val sumR = FloatArray(12)
        val sumG = FloatArray(12)
        val sumB = FloatArray(12)
        for (p in pixels) {
            val r = (p shr 16 and 0xFF) / 255f
            val g = (p shr 8 and 0xFF) / 255f
            val b = (p and 0xFF) / 255f
            val mx = max(r, max(g, b))
            val mn = min(r, min(g, b))
            val sat = if (mx == 0f) 0f else (mx - mn) / mx
            if (sat < 0.25f || mx < 0.2f) continue
            val hue = hue(r, g, b, mx, mn)
            val bucket = ((hue / 30f).toInt()).coerceIn(0, 11)
            val w = sat * (1f - kotlin.math.abs(mx - 0.7f))
            weight[bucket] += w
            sumR[bucket] += r * w
            sumG[bucket] += g * w
            sumB[bucket] += b * w
        }
        val picked = mutableListOf<Int>()
        for (b in weight.indices.sortedByDescending { weight[it] }) {
            if (picked.size == max || weight[b] < pixels.size * 0.02f) break
            if (picked.any { (b - it + 12) % 12 <= 1 || (it - b + 12) % 12 <= 1 }) continue
            picked += b
        }
        return picked.map { b ->
            val w = weight[b]
            fun ch(v: Float) = (v / w * 255).toInt().coerceIn(0, 255)
            (0xFF shl 24) or (ch(sumR[b]) shl 16) or (ch(sumG[b]) shl 8) or ch(sumB[b])
        }
    }

    private fun hue(r: Float, g: Float, b: Float, mx: Float, mn: Float): Float {
        val d = mx - mn
        if (d == 0f) return 0f
        val h = when (mx) {
            r -> ((g - b) / d) % 6
            g -> (b - r) / d + 2
            else -> (r - g) / d + 4
        } * 60f
        return if (h < 0) h + 360 else h
    }
}
