package org.opencarproject.launcher.core.pure

import kotlin.math.sin

/** Level meter that rises and falls like sound, without reading any audio. */
object AudioBarsMath {

    /**
     * Level in 0..1 of bar [index] at phase [t] (radians). Mixes two integer-frequency sines
     * with a different phase per bar: irregular pattern, and seamless when [t] loops over 2π.
     */
    fun level(t: Float, index: Int): Float =
        0.6f * (0.5f + 0.5f * sin(2 * t + index * 1.3f)) +
            0.4f * (0.5f + 0.5f * sin(3 * t + index * 2.1f))
}
