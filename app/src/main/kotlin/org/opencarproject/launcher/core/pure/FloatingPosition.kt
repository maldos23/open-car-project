package org.opencarproject.launcher.core.pure

import kotlin.math.roundToInt

/**
 * Position of a floating window stored as a fraction (0..1) of the room it can move in, so it
 * survives size changes (collapsing, expanding, another screen) and always stays fully on screen.
 */
object FloatingPosition {

    /** Window offset in px for a stored fraction; `free` = screen size minus window size. */
    fun toPx(fraction: Float, free: Int): Int = (fraction.coerceIn(0f, 1f) * free.coerceAtLeast(0)).roundToInt()

    /** Fraction to store for a window dragged to `px`. */
    fun toFraction(px: Int, free: Int): Float = if (free <= 0) 0f else (px.toFloat() / free).coerceIn(0f, 1f)
}
