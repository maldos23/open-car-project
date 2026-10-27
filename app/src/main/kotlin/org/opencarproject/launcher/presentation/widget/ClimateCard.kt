package org.opencarproject.launcher.presentation.widget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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

/**
 * Climate module: cabin temperatures, power, A/C, AUTO, recirculation, air direction, defrosters
 * and fan, as reported and accepted by the head unit's CAN service. Only the commands the
 * vehicle's key map supports are shown.
 */
@Composable
fun ClimateCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    DisposableEffect(context) {
        ClimateSource.start(context)
        onDispose { ClimateSource.stop() }
    }
    val status by ClimateSource.status.collectAsStateWithLifecycle()
    Box(modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
        when (val s = status) {
            ClimateStatus.Connecting -> CircularProgressIndicator()
            ClimateStatus.Unavailable -> Message(stringResource(R.string.climate_unavailable))
            is ClimateStatus.Ready -> ClimateControls(s)
        }
    }
}

@Composable
private fun ClimateControls(status: ClimateStatus.Ready) {
    val state = status.state
    val can = status.commands
    val send: (String) -> Unit = ClimateSource::send
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Short cards put each temperature in one row (− 22° +) and leave out the chips.
        val compact = maxHeight < 170.dp
        val chips = maxHeight >= 220.dp && maxWidth >= 420.dp
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                TempColumn(
                    temp = state?.leftTemp,
                    up = "LeftTempIncrease".takeIf { it in can },
                    down = "LeftTempDecrease".takeIf { it in can },
                    upLabel = stringResource(R.string.climate_left_temp_up),
                    downLabel = stringResource(R.string.climate_left_temp_down),
                    send = send,
                    compact = compact,
                    modifier = Modifier.weight(1f),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if ("Power" in can) {
                        FilledIconToggleButton(
                            checked = state?.power == true,
                            onCheckedChange = { send("Power") },
                            modifier = Modifier.size(if (compact) 48.dp else 64.dp),
                        ) {
                            Icon(Icons.Rounded.PowerSettingsNew, stringResource(R.string.climate_power), Modifier.size(if (compact) 26.dp else 32.dp))
                        }
                    }
                    if (!compact) FanRow(state, can, send)
                }
                TempColumn(
                    temp = state?.rightTemp,
                    up = "RightTempIncrease".takeIf { it in can },
                    down = "RightTempDecrease".takeIf { it in can },
                    upLabel = stringResource(R.string.climate_right_temp_up),
                    downLabel = stringResource(R.string.climate_right_temp_down),
                    send = send,
                    compact = compact,
                    modifier = Modifier.weight(1f),
                )
            }
            if (chips) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Toggle("Ac", stringResource(R.string.climate_ac), state?.ac, can, send)
                    Toggle("Auto", stringResource(R.string.climate_auto), state?.auto, can, send)
                    Toggle("Cycle", stringResource(R.string.climate_recirculation), state?.recirculation, can, send)
                    val nextMode = ClimateProtocol.nextAirMode(state)
                    Toggle(nextMode, airModeLabel(state), selected = null, can = can, send = send)
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
private fun TempColumn(
    temp: ClimateTemp?,
    up: String?,
    down: String?,
    upLabel: String,
    downLabel: String,
    send: (String) -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val text = @Composable {
        Text(
            text = tempText(temp),
            style = if (compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
    if (compact) {
        Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
            if (down != null) StepButton(Icons.Rounded.Remove, downLabel) { send(down) }
            text()
            if (up != null) StepButton(Icons.Rounded.Add, upLabel) { send(up) }
        }
    } else {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            if (up != null) StepButton(Icons.Rounded.KeyboardArrowUp, upLabel) { send(up) }
            text()
            if (down != null) StepButton(Icons.Rounded.KeyboardArrowDown, downLabel) { send(down) }
        }
    }
}

@Composable
private fun FanRow(state: ClimateState?, can: Set<String>, send: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if ("WindLevelDecrease" in can) StepButton(Icons.Rounded.Remove, stringResource(R.string.climate_fan_down)) { send("WindLevelDecrease") }
        Icon(Icons.Rounded.Air, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = state?.fanSpeed?.toString() ?: "–",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if ("WindLevelIncrease" in can) StepButton(Icons.Rounded.Add, stringResource(R.string.climate_fan_up)) { send("WindLevelIncrease") }
    }
}

@Composable
private fun StepButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(icon, contentDescription = label)
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
        leadingIcon = if (command == "Ac") ({ Icon(Icons.Rounded.AcUnit, contentDescription = null, Modifier.size(18.dp)) }) else null,
        modifier = Modifier.heightIn(min = 48.dp),
    )
}

@Composable
private fun airModeLabel(state: ClimateState?): String = when {
    state == null -> stringResource(R.string.climate_mode)
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
