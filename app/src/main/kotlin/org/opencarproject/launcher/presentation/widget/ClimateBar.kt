package org.opencarproject.launcher.presentation.widget

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.opencarproject.launcher.R
import org.opencarproject.launcher.core.model.ClimateState
import org.opencarproject.launcher.core.model.ClimateStatus
import org.opencarproject.launcher.core.model.ClimateTemp
import org.opencarproject.launcher.core.pure.ClimateProtocol
import org.opencarproject.launcher.data.source.ClimateSource

/** Seconds the bar stays after the last climate change. */
private const val SHOW_MS = 5_000L
private const val TEMP_BARS = 8

/** Cold-to-hot scale of the temperature bars (data colors, not theme text colors). */
private val Cold = Color(0xFF2F80ED)
private val Hot = Color(0xFFE53935)

/**
 * The launcher's own climate popup: when the climate changes it slides up at the bottom of the
 * home screen for a few seconds, with each side's temperature over blue-to-red bars and the fan.
 * Meant to replace the vendor's popup when that one is switched off.
 */
@Composable
fun ClimateBar(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    DisposableEffect(context) {
        ClimateSource.start(context)
        onDispose { ClimateSource.stop() }
    }
    val status by ClimateSource.status.collectAsStateWithLifecycle()
    val state = (status as? ClimateStatus.Ready)?.state
    var visible by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf<ClimateState?>(null) }
    LaunchedEffect(state) {
        // Only real changes: not the first reading after (re)connecting.
        val previous = shown
        shown = state ?: return@LaunchedEffect
        if (previous == null || previous == state) return@LaunchedEffect
        visible = true
        delay(SHOW_MS)
        visible = false
    }
    AnimatedVisibility(
        visible = visible && shown != null,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier,
    ) {
        shown?.let { BarContent(it) }
    }
}

@Composable
private fun BarContent(state: ClimateState) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
    ) {
        Row(
            Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            TempWithBars(state.leftTemp, state.power)
            if (state.power) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AirModeIcon(state, size = 32.dp)
                    Spacer(Modifier.width(12.dp))
                    FanBars(state)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.climate_off), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            TempWithBars(state.rightTemp, state.power)
        }
    }
}

/** "22°" next to [TEMP_BARS] rising bars lit up to the temperature, blue (cold) to red (hot). */
@Composable
private fun TempWithBars(temp: ClimateTemp, on: Boolean) {
    val warmth = ClimateProtocol.warmth(temp)
    val lit = warmth?.let { (it * (TEMP_BARS - 1)).toInt() + 1 } ?: 0
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = when (temp) {
                ClimateTemp.Low -> stringResource(R.string.climate_temp_low)
                ClimateTemp.High -> stringResource(R.string.climate_temp_high)
                ClimateTemp.Unknown -> "--"
                is ClimateTemp.Value -> (if (temp.degrees % 1f == 0f) temp.degrees.toInt().toString() else temp.degrees.toString()) + "°"
            },
            style = MaterialTheme.typography.headlineMedium,
            color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            val off = MaterialTheme.colorScheme.outlineVariant
            repeat(TEMP_BARS) { i ->
                val target = if (on && i < lit) lerp(Cold, Hot, i / (TEMP_BARS - 1f)) else off
                val color by animateColorAsState(target, tween(400), label = "bar$i")
                Box(
                    Modifier
                        .width(6.dp)
                        .height((10 + i * 3).dp)
                        .background(color, RoundedCornerShape(3.dp)),
                )
            }
        }
    }
}
