package org.opencarproject.launcher.core.pure

/**
 * Room for the island, centered at the top between the top bar's side elements (weather, speed
 * scene, edit buttons) so it never covers them.
 */
object IslandGap {

    /**
     * Widest width centered on [center] that clears every side element by [margin] on each side.
     * [sides] are the elements' horizontal (left, right) bounds; empty ones (hidden) and ones
     * straddling the center (centered weather, which already shares the middle) are ignored.
     * `null` when nothing limits it.
     */
    fun width(center: Float, sides: Collection<Pair<Float, Float>>, margin: Float): Float? =
        sides
            .filter { (left, right) -> right > left && (right <= center || left >= center) }
            .minOfOrNull { (left, right) -> if (right <= center) center - right else left - center }
            ?.let { (2 * (it - margin)).coerceAtLeast(0f) }
}
