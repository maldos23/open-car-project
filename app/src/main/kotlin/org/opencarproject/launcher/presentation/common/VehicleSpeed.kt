package org.opencarproject.launcher.presentation.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.opencarproject.launcher.core.model.SpeedSource
import org.opencarproject.launcher.data.source.SpeedReader
import org.opencarproject.launcher.presentation.widget.rememberOnResume

/** Vehicle speed (km/h, smoothed) and whether the GPS permission to read it is missing. */
class VehicleSpeed(val kmh: Float, val rawKmh: Float, val needsGps: Boolean)

/** Reads the speed from the chosen source; the scene and the bar's speedometer share it. */
@Composable
fun rememberVehicleSpeed(source: SpeedSource): VehicleSpeed {
    val context = LocalContext.current
    val hasGps = rememberOnResume { SpeedReader.hasGpsPermission(this) }
    // Lifecycle-aware: GPS / accelerometer stop while the launcher is in the background.
    val raw by remember(source, hasGps) { SpeedReader.speedKmh(context, source) }
        .collectAsStateWithLifecycle(0f)
    // Visual smoothing so needles and scenes don't jerk.
    val smooth by animateFloatAsState(raw, tween(900), label = "speed")
    return VehicleSpeed(smooth, raw, source == SpeedSource.GPS && !hasGps)
}
