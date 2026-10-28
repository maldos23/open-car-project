package org.opencarproject.launcher.core.pure

/** Position in a looping animation, like an infinite tween (with `RepeatMode.Reverse` when [reverse]). */
object LoopPhase {
    /** Fraction 0..1 of the tween at [millis] for a loop of [periodMs]; reverse loops play back down. */
    fun fraction(millis: Long, periodMs: Int, reverse: Boolean): Float {
        val cycle = if (reverse) periodMs * 2L else periodMs.toLong()
        val f = (millis % cycle) / periodMs.toFloat()
        return if (f > 1f) 2f - f else f
    }
}
