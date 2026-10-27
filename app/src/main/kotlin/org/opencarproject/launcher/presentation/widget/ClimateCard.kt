package org.opencarproject.launcher.presentation.widget

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.opencarproject.launcher.R
import org.opencarproject.launcher.core.model.ClimateState
import org.opencarproject.launcher.core.model.ClimateStatus
import org.opencarproject.launcher.core.model.ClimateTemp
import org.opencarproject.launcher.core.pure.ClimateProtocol
import org.opencarproject.launcher.data.source.ClimateSource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Climate module: cabin temperatures and power over a wind of ice flakes that blows harder with
 * the fan; when the climate is off the flakes settle and fade and the card dims. Taller cards
 * add A/C, AUTO, recirculation, air direction and defroster chips. Only the commands the vendor
 * accepts are shown.
 */
@Composable
fun ClimateCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    DisposableEffect(context) {
        ClimateSource.start(context)
        onDispose { ClimateSource.stop() }
    }
    val status by ClimateSource.status.collectAsStateWithLifecycle()
    val state = (status as? ClimateStatus.Ready)?.state
    // 1 = blowing, 0 = off (or no state yet): drives the flakes and the dimming.
    val activity by animateFloatAsState(if (state?.power == true) 1f else 0f, tween(1200), label = "activity")
    Box(modifier.fillMaxSize().iceWind(activity, state?.fanSpeed ?: 0), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
            when (val s = status) {
                ClimateStatus.Connecting -> CircularProgressIndicator()
                ClimateStatus.Unavailable -> Message(stringResource(R.string.climate_unavailable))
                is ClimateStatus.Ready -> ClimateControls(s, activity)
            }
        }
    }
}

@Composable
private fun ClimateControls(status: ClimateStatus.Ready, activity: Float) {
    val state = status.state
    val can = status.commands
    val send: (String) -> Unit = ClimateSource::send
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxHeight < 170.dp
        val chips = maxHeight >= 220.dp && maxWidth >= 420.dp
        // Off: the temperatures fade toward the secondary text color.
        val tempColor = lerp(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), MaterialTheme.colorScheme.onSurface, activity)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                TempText(state?.leftTemp, tempColor, compact, Modifier.weight(1f))
                val power = @Composable {
                    if ("Power" in can) {
                        FilledIconToggleButton(
                            checked = state?.power == true,
                            onCheckedChange = { send("Power") },
                            modifier = Modifier.size(if (compact) 48.dp else 64.dp),
                        ) {
                            Icon(Icons.Rounded.PowerSettingsNew, stringResource(R.string.climate_power), Modifier.size(if (compact) 26.dp else 32.dp))
                        }
                    }
                }
                // Center column: power on top, then a slim strip with the air direction and the
                // fan bars ("Off" while the climate is off). Tapping the air icon cycles the
                // direction only on taller cards, where it gets a full 48 dp target.
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 6.dp)) {
                    power()
                    if (state != null) {
                        if (state.power) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                val next = ClimateProtocol.nextAirMode(state)
                                if (compact) {
                                    AirModeIcon(state, size = 22.dp)
                                } else {
                                    Box(
                                        Modifier.size(48.dp).clickable(enabled = next in can) { send(next) },
                                        contentAlignment = Alignment.Center,
                                    ) { AirModeIcon(state, size = 32.dp) }
                                }
                                FanBars(state, barHeight = if (compact) 14.dp else 24.dp)
                            }
                        } else {
                            Text(
                                stringResource(R.string.climate_off),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
                TempText(state?.rightTemp, tempColor, compact, Modifier.weight(1f))
            }
            if (chips) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Toggle("Ac", stringResource(R.string.climate_ac), state?.ac, can, send)
                    Toggle("Auto", stringResource(R.string.climate_auto), state?.auto, can, send)
                    Toggle("Cycle", stringResource(R.string.climate_recirculation), state?.recirculation, can, send)
                    Toggle(ClimateProtocol.nextAirMode(state), airModeLabel(state), selected = null, can = can, send = send)
                    Toggle("FrontWindowFogClear", stringResource(R.string.climate_defrost_front), state?.frontDefrost, can, send)
                    Toggle("BackWindowFogClear", stringResource(R.string.climate_defrost_rear), state?.rearDefrost, can, send)
                }
            }
            val note = when {
                can.isEmpty() -> stringResource(R.string.climate_read_only, status.vehicle?.label ?: "?")
                state == null -> stringResource(R.string.climate_waiting)
                else -> null
            }
            if (note != null) {
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = if (compact) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TempText(temp: ClimateTemp?, color: Color, compact: Boolean, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(
            text = tempText(temp),
            style = if (compact) MaterialTheme.typography.displaySmall else MaterialTheme.typography.displayMedium,
            color = color,
            maxLines = 1,
        )
    }
}

/** A chip for one command; [selected] null means it just triggers (e.g. the air mode cycle). */
@Composable
private fun Toggle(command: String, label: String, selected: Boolean?, can: Set<String>, send: (String) -> Unit) {
    if (command !in can) return
    FilterChip(
        selected = selected == true,
        onClick = { send(command) },
        label = { Text(label) },
        leadingIcon = when (command) {
            "Ac" -> ({ Icon(Icons.Rounded.AcUnit, contentDescription = null, Modifier.size(18.dp)) })
            in ClimateProtocol.AirModeCycle -> ({ Icon(Icons.Rounded.Air, contentDescription = null, Modifier.size(18.dp)) })
            else -> null
        },
        modifier = Modifier.heightIn(min = 48.dp),
    )
}

/** One ice flake of the wind: fixed lane and look, position computed from the animation phase. */
private class Flake(val x0: Float, val y: Float, val laps: Int, val size: Float, val wobble: Float, val spin: Float)

private const val FLAKES = 28

/**
 * Wind of ice flakes behind the content, drawn (not recomposed) every frame. [activity] fades it
 * in and out — while turning off, the flakes drop and melt away; [fan] (0–8+) shows more flakes.
 */
@Composable
private fun Modifier.iceWind(activity: Float, fan: Int): Modifier {
    val flakes = remember {
        val r = Random(7)
        List(FLAKES) { Flake(r.nextFloat(), r.nextFloat(), 1 + r.nextInt(3), 0.6f + r.nextFloat() * 0.8f, r.nextFloat() * 2f * PI.toFloat(), r.nextFloat()) }
    }
    val phase = rememberInfiniteTransition(label = "wind")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(9_000, easing = LinearEasing)), label = "phase")
    val color = MaterialTheme.colorScheme.primary
    // Always a breeze when on; each fan step adds flakes.
    val visible = (8 + fan * 3).coerceAtMost(FLAKES)
    return drawBehind {
        if (activity <= 0.01f) return@drawBehind
        val t = phase.value
        flakes.forEachIndexed { i, f ->
            if (i >= visible) return@forEachIndexed
            // Integer laps per cycle keep the loop seamless.
            val x = ((f.x0 + t * f.laps) % 1f) * (size.width + 40f) - 20f
            val sway = sin(t * 2f * PI.toFloat() * f.laps + f.wobble) * size.height * 0.05f
            // Turning off: the flakes sink and melt.
            val fall = (1f - activity) * size.height * 0.35f
            val y = f.y * size.height + sway + fall
            drawFlake(Offset(x, y), 5.dp.toPx() * f.size, t * 2f * PI.toFloat() * (f.spin - 0.5f) * 2f, color.copy(alpha = 0.35f * activity))
        }
    }
}

/** Six-armed ice crystal with small side barbs. */
private fun DrawScope.drawFlake(center: Offset, radius: Float, rotation: Float, color: Color) {
    val stroke = radius * 0.22f
    repeat(6) { k ->
        val a = rotation + k * PI.toFloat() / 3f
        val dx = cos(a)
        val dy = sin(a)
        val tip = Offset(center.x + dx * radius, center.y + dy * radius)
        drawLine(color, center, tip, stroke, StrokeCap.Round)
        // Barbs at 60% of the arm.
        val mid = Offset(center.x + dx * radius * 0.6f, center.y + dy * radius * 0.6f)
        for (side in listOf(-1f, 1f)) {
            val b = a + side * PI.toFloat() / 4f
            drawLine(color, mid, Offset(mid.x + cos(b) * radius * 0.35f, mid.y + sin(b) * radius * 0.35f), stroke * 0.8f, StrokeCap.Round)
        }
    }
}

@Composable
internal fun airModeLabel(state: ClimateState?): String = when {
    state == null -> stringResource(R.string.climate_mode)
    state.frontDefrost && !state.airFace && !state.airFeet -> stringResource(R.string.climate_defrost_front)
    state.airWindshield && state.airFeet -> stringResource(R.string.climate_mode_windshield_feet)
    state.airFace && state.airFeet -> stringResource(R.string.climate_mode_face_feet)
    state.airFeet -> stringResource(R.string.climate_mode_feet)
    state.airFace -> stringResource(R.string.climate_mode_face)
    else -> stringResource(R.string.climate_mode)
}

@Composable
private fun tempText(temp: ClimateTemp?): String = when (temp) {
    null, ClimateTemp.Unknown -> "--"
    ClimateTemp.Low -> stringResource(R.string.climate_temp_low)
    ClimateTemp.High -> stringResource(R.string.climate_temp_high)
    is ClimateTemp.Value -> {
        val d = temp.degrees
        (if (d % 1f == 0f) d.toInt().toString() else d.toString()) + "°"
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}
