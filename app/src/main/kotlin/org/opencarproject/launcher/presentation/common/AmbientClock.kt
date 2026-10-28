package org.opencarproject.launcher.presentation.common

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import kotlinx.coroutines.delay
import org.opencarproject.launcher.core.pure.LoopPhase

/** ~12 fps: smooth enough for slow decorative motion. */
private const val AMBIENT_TICK_MS = 80L

private val ambientMillis = mutableLongStateOf(0L)

/**
 * Drives the shared clock of decorative loops (backgrounds, glows, idle icons). Any animation
 * redraws the whole launcher window, which head-unit GPUs struggle with at 60 fps, so these
 * loops tick together at a low rate instead of every frame. Call once from the home screen.
 */
@Composable
fun AmbientClockEffect() {
    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis { ambientMillis.longValue = it }
            delay(AMBIENT_TICK_MS)
        }
    }
}

/**
 * Low-rate stand-in for an infinite tween: goes from [from] to [to] every [periodMs], and back
 * again when [reverse]. Read it in the draw phase so a tick only redraws. It only moves while
 * [AmbientClockEffect] runs (the home screen): elsewhere it stays at [from].
 */
@Composable
fun rememberAmbientFloat(
    from: Float,
    to: Float,
    periodMs: Int,
    easing: Easing = LinearEasing,
    reverse: Boolean = false,
): State<Float> = remember(from, to, periodMs, easing, reverse) {
    derivedStateOf {
        from + (to - from) * easing.transform(LoopPhase.fraction(ambientMillis.longValue, periodMs, reverse))
    }
}
