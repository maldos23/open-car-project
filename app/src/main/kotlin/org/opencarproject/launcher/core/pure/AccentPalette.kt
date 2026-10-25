package org.opencarproject.launcher.core.pure

import kotlin.math.abs
import kotlin.math.pow

/**
 * Tones of an accent color derived from its hue (0..360), for light or dark theme.
 *
 * Approximates Material 3 "tones" with a fixed HSL lightness per role: in dark mode the primary is
 * light on a dark background and vice versa. Returns ARGB (Int) to avoid depending on Compose.
 */
object AccentPalette {

    data class Tones(
        val primary: Int,
        val onPrimary: Int,
        val primaryContainer: Int,
        val onPrimaryContainer: Int,
        val secondaryContainer: Int,
        val onSecondaryContainer: Int,
    )

    fun tones(hue: Float, dark: Boolean): Tones = if (dark) {
        Tones(
            primary = hsl(hue, 0.75f, 0.76f),
            onPrimary = hsl(hue, 0.80f, 0.16f),
            primaryContainer = hsl(hue, 0.60f, 0.28f),
            onPrimaryContainer = hsl(hue, 0.80f, 0.90f),
            secondaryContainer = hsl(hue, 0.25f, 0.27f),
            onSecondaryContainer = hsl(hue, 0.40f, 0.90f),
        )
    } else {
        Tones(
            primary = hsl(hue, 0.70f, 0.27f),
            onPrimary = 0xFFFFFFFF.toInt(),
            primaryContainer = hsl(hue, 0.80f, 0.90f),
            onPrimaryContainer = hsl(hue, 0.80f, 0.12f),
            secondaryContainer = hsl(hue, 0.35f, 0.88f),
            onSecondaryContainer = hsl(hue, 0.50f, 0.12f),
        )
    }

    /** HSL → opaque ARGB. */
    fun hsl(hue: Float, saturation: Float, lightness: Float): Int {
        val c = (1 - abs(2 * lightness - 1)) * saturation
        val h = ((hue % 360) + 360) % 360 / 60f
        val x = c * (1 - abs(h % 2 - 1))
        val (r, g, b) = when {
            h < 1 -> Triple(c, x, 0f)
            h < 2 -> Triple(x, c, 0f)
            h < 3 -> Triple(0f, c, x)
            h < 4 -> Triple(0f, x, c)
            h < 5 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val m = lightness - c / 2
        fun channel(v: Float) = ((v + m) * 255).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
    }

    /** WCAG contrast between two ARGB colors (1..21). */
    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun luminance(argb: Int): Double {
        fun lin(channel: Int): Double {
            val c = channel / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * lin((argb shr 16) and 0xFF) + 0.7152 * lin((argb shr 8) and 0xFF) + 0.0722 * lin(argb and 0xFF)
    }
}
