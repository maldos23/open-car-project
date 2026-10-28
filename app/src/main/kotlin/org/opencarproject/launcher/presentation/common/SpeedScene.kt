package org.opencarproject.launcher.presentation.common

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.first
import org.opencarproject.launcher.core.model.WeatherCondition
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.opencarproject.launcher.R
import org.opencarproject.launcher.core.model.SceneTheme
import org.opencarproject.launcher.core.model.SpeedDialStyle
import org.opencarproject.launcher.core.model.SpeedSource
import org.opencarproject.launcher.core.model.VehicleShape
import org.opencarproject.launcher.core.pure.LaunchMath
import org.opencarproject.launcher.core.pure.SpeedUnits
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Top bar speed scene: a vehicle (or pet) in front of a [theme] scenery that scrolls according
 * to the speed; above [rocketKmh] it shakes, turns into a rocket and lifts off among the stars
 * (and lands again when slowing down). Everything is drawn on a Canvas with a frame clock (no images).
 */
@Composable
fun SpeedScene(
    source: SpeedSource,
    rocketKmh: Int,
    vehicle: VehicleShape,
    color: Color,
    theme: SceneTheme = SceneTheme.CITY,
    modifier: Modifier = Modifier,
    showDial: Boolean = true,
    dialStyle: SpeedDialStyle = SpeedDialStyle.CIRCLE,
    mph: Boolean = false,
) {
    val vehicleSpeed = rememberVehicleSpeed(source)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val needsGps = vehicleSpeed.needsGps
    // Only the switch recomposes; the speed itself is read while drawing.
    val rocket by remember(vehicleSpeed, rocketKmh) { derivedStateOf { vehicleSpeed.rawKmh >= rocketKmh } }
    // Sky based on local time and current weather (recomputed every minute).
    val weather by CurrentWeather.collectAsStateWithLifecycle()
    val hour by produceState(currentHour()) {
        while (true) {
            kotlinx.coroutines.delay(60_000)
            value = currentHour()
        }
    }
    val sky = remember(hour, weather) { SkyState(TimeOfDay.from(hour, weather?.isDayNow()), weather?.condition) }
    // 0 = on the road, 1 = cruising in space; the stages in between are the launch.
    val launch = animateFloatAsState(if (rocket) 1f else 0f, tween(1800, easing = LinearEasing), label = "launch")

    // Distance traveled in "scene units": the city/stars scroll with it.
    var travel by remember { mutableFloatStateOf(0f) }
    var time by remember { mutableFloatStateOf(0f) }
    val currentSky by rememberUpdatedState(sky)
    LaunchedEffect(vehicleSpeed) {
        var last = 0L
        while (true) {
            // Parked under a dry sky nothing needs to move: wait instead of redrawing every frame
            // (any animation redraws the whole launcher window, which is costly on head units).
            if (!sceneMoves(vehicleSpeed, launch.value, currentSky)) {
                snapshotFlow { sceneMoves(vehicleSpeed, launch.value, currentSky) }.first { it }
                last = 0L
            }
            withFrameNanos { now ->
                // A long gap (launcher was hidden) would teleport the scenery: skip that frame.
                if (last != 0L && now - last < 500_000_000L) {
                    val dt = (now - last) / 1e9f
                    time += dt
                    travel += dt * vehicleSpeed.kmh
                }
                last = now
            }
        }
    }

    Box(
        modifier
            .size(width = 320.dp, height = SceneHeight)
            .clip(RoundedCornerShape(50))
            .clickable(enabled = needsGps) { permission.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val kmh = vehicleSpeed.kmh
            val progress = launch.value
            val lift = LaunchMath.lift(progress)
            if (lift < 1f) backdrop(theme, travel, time, sky)
            withAlpha(lift) { space(travel, kmh) }
            launchingActor(
                vehicle,
                progress,
                travel,
                time,
                kmh,
                color,
                lights = sky.time == TimeOfDay.NIGHT || sky.time == TimeOfDay.DUSK,
                braking = vehicleSpeed.rawKmh < kmh - 3f,
            )
            withAlpha(1f - lift) { precipitation(sky, time) }
        }
        // Speedometer inside a circle; in rocket mode its outline burns.
        if (showDial) SpeedReadout(
            style = dialStyle,
            kmh = { vehicleSpeed.kmh },
            mph = mph,
            rocketKmh = rocketKmh,
            onFire = rocket,
            time = { time },
            needsGps = needsGps,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp),
        )
    }
}

/** Shared height of the top bar pills (weather and speed scene). */
val SceneHeight = 64.dp

/**
 * Speedometer circle: large bold number with "km/h" below. In car mode, an arc shows
 * how far it is to the rocket; in rocket mode the edge is a spinning ring of fire, with
 * flickering tongues and glow.
 */
@Composable
private fun SpeedDial(kmh: () -> Float, mph: Boolean, rocketKmh: Int, fire: () -> Float, time: () -> Float, needsGps: Boolean, modifier: Modifier) {
    Box(modifier.size(SceneHeight - 4.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val fire = fire()
            val time = time()
            val r = size.minDimension / 2
            val c = center
            val ring = 3.dp.toPx()
            // Fire glow behind.
            if (fire > 0f) {
                drawCircle(
                    Brush.radialGradient(listOf(Color(0xAAFF6D00).copy(alpha = 0.7f * fire), Color.Transparent), center = c, radius = r * 1.25f),
                    radius = r * 1.25f,
                    center = c,
                )
            }
            drawCircle(Color(0xB3000000), radius = r - ring, center = c)
            // White outline with a progress arc toward the rocket speed.
            if (fire < 1f) {
                drawCircle(Color.White.copy(alpha = 0.25f * (1f - fire)), radius = r - ring, center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(ring))
                drawArc(
                    Color.White.copy(alpha = 1f - fire),
                    startAngle = -90f,
                    sweepAngle = 360f * (kmh() / rocketKmh).coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = Offset(c.x - r + ring, c.y - r + ring),
                    size = Size((r - ring) * 2, (r - ring) * 2),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(ring, cap = StrokeCap.Round),
                )
            }
            if (fire > 0f) {
                // Spinning ring of fire.
                rotate(time * 140f, pivot = c) {
                    drawCircle(
                        Brush.sweepGradient(listOf(Color(0xFFFFEB3B), Color(0xFFFF9800), Color(0xFFFF3D00), Color(0xFFFF9800), Color(0xFFFFEB3B)), center = c),
                        radius = r - ring,
                        center = c,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(ring * 1.6f),
                        alpha = fire,
                    )
                }
                // Outward tongues of fire, each flickering at its own pace.
                for (k in 0 until 14) {
                    val a = (k * 360f / 14 + time * 60f) * (PI / 180).toFloat()
                    val flick = 0.5f + 0.5f * sin(time * (9f + k % 4) + k * 1.7f)
                    val len = r * (0.14f + 0.22f * flick)
                    val start = c + Offset(kotlin.math.cos(a) * (r - ring), sin(a) * (r - ring))
                    val end = c + Offset(kotlin.math.cos(a) * (r - ring + len), sin(a) * (r - ring + len))
                    drawLine(
                        Brush.linearGradient(listOf(Color(0xFFFFC107), Color(0x00FF3D00)), start = start, end = end),
                        start,
                        end,
                        strokeWidth = ring * (1.2f + flick),
                        cap = StrokeCap.Round,
                        alpha = fire,
                    )
                }
            }
        }
        androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
            SpeedText(kmh, mph, needsGps, numberSize = 22.sp, unitSize = 9.sp, gpsSize = 14.sp, unitBeside = false)
        }
    }
}

/** The number and unit (or "GPS" when the permission is missing); recomposes only when the shown number changes. */
@Composable
private fun SpeedText(kmh: () -> Float, mph: Boolean, needsGps: Boolean, numberSize: TextUnit, unitSize: TextUnit, gpsSize: TextUnit, unitBeside: Boolean) {
    if (needsGps) {
        Text(stringResource(R.string.speed_needs_gps), color = Color.White, fontWeight = FontWeight.Bold, fontSize = gpsSize, style = ReadoutShadow)
        return
    }
    val shown by remember(kmh, mph) { derivedStateOf { SpeedUnits.display(kmh(), mph).roundToInt() } }
    Text("$shown", color = Color.White, fontWeight = FontWeight.Black, fontSize = numberSize, lineHeight = numberSize, style = ReadoutShadow)
    Text(SpeedUnits.label(mph), color = Color.White.copy(alpha = 0.85f), fontWeight = FontWeight.Bold, fontSize = unitSize, lineHeight = unitSize, style = ReadoutShadow, modifier = if (unitBeside) Modifier.padding(start = 6.dp, top = 6.dp) else Modifier)
}

/** The scene animates while driving, during the rocket launch or when rain or snow falls. */
private fun sceneMoves(speed: VehicleSpeed, launch: Float, sky: SkyState) =
    speed.kmh >= 0.5f || launch > 0f || sky.condition in FALLING

private val FALLING = setOf(WeatherCondition.RAIN, WeatherCondition.DRIZZLE, WeatherCondition.THUNDERSTORM, WeatherCondition.SNOW)

private val ReadoutShadow = TextStyle(shadow = Shadow(Color(0x99000000), Offset(0f, 2f), 6f))

/**
 * Standalone speedometer (without the car scene) for the top bar: the same circle, with its
 * ring of fire when passing the rocket speed.
 */
@Composable
fun StandaloneSpeedometer(style: SpeedDialStyle, source: SpeedSource, rocketKmh: Int, mph: Boolean, modifier: Modifier = Modifier) {
    val speed = rememberVehicleSpeed(source)
    val onFire by remember(speed, rocketKmh) { derivedStateOf { speed.rawKmh >= rocketKmh } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var time by remember { mutableFloatStateOf(0f) }
    // The clock only drives the ring of fire: it runs while the ring is shown.
    LaunchedEffect(onFire) {
        var last = 0L
        while (onFire) withFrameNanos { now ->
            if (last != 0L) time += (now - last) / 1e9f
            last = now
        }
    }
    Box(modifier.clickable(enabled = speed.needsGps) { permission.launch(Manifest.permission.ACCESS_FINE_LOCATION) }) {
        SpeedReadout(
            style = style,
            kmh = { speed.kmh },
            mph = mph,
            rocketKmh = rocketKmh,
            onFire = onFire,
            time = { time },
            needsGps = speed.needsGps,
            modifier = Modifier,
            standalone = true,
        )
    }
}

/** Speedometer in the chosen shape (circle or numbers on one line). */
@Composable
private fun SpeedReadout(
    style: SpeedDialStyle,
    kmh: () -> Float,
    mph: Boolean,
    rocketKmh: Int,
    onFire: Boolean,
    time: () -> Float,
    needsGps: Boolean,
    modifier: Modifier,
    standalone: Boolean = false,
) {
    val fire = animateFloatAsState(if (onFire) 1f else 0f, tween(500), label = "fire")
    when (style) {
        SpeedDialStyle.CIRCLE -> SpeedDial(kmh, mph, rocketKmh, { fire.value }, time, needsGps, if (standalone) modifier.size(SceneHeight) else modifier)
        SpeedDialStyle.INLINE -> SpeedInline(kmh, mph, { fire.value }, time, needsGps, modifier, standalone)
    }
}

/**
 * Single-line speedometer: large bold number with the unit beside it, in a dark
 * pill. Past the rocket speed its edge burns (spinning fire gradient + glow).
 * Inside the scene it is more compact; standalone, it takes the bar's full height.
 */
@Composable
private fun SpeedInline(kmh: () -> Float, mph: Boolean, fire: () -> Float, time: () -> Float, needsGps: Boolean, modifier: Modifier, standalone: Boolean) {
    val height = if (standalone) SceneHeight else SceneHeight - 14.dp
    Row(
        modifier
            .height(height)
            .drawBehind {
                val fire = fire()
                val time = time()
                val r = CornerRadius(size.height / 2)
                val stroke = 3.dp.toPx()
                if (fire > 0f) {
                    drawRoundRect(
                        Brush.radialGradient(listOf(Color(0x99FF6D00).copy(alpha = 0.6f * fire), Color.Transparent), center = center, radius = size.width * 0.7f),
                        topLeft = Offset(-stroke * 2, -stroke * 2),
                        size = Size(size.width + stroke * 4, size.height + stroke * 4),
                        cornerRadius = CornerRadius(size.height / 2 + stroke * 2),
                    )
                }
                drawRoundRect(Color(0xB3000000), cornerRadius = r)
                drawRoundRect(Color.White.copy(alpha = 0.25f * (1f - fire)), cornerRadius = r, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke / 1.5f))
                if (fire > 0f) {
                    // The fire gradient travels around the edge.
                    val shift = (time * 0.8f % 1f) * size.width
                    drawRoundRect(
                        Brush.linearGradient(
                            listOf(Color(0xFFFFEB3B), Color(0xFFFF9800), Color(0xFFFF3D00), Color(0xFFFF9800), Color(0xFFFFEB3B)),
                            start = Offset(shift - size.width, 0f),
                            end = Offset(shift + size.width, size.height),
                            tileMode = androidx.compose.ui.graphics.TileMode.Repeated,
                        ),
                        cornerRadius = r,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(stroke * (1.2f + 0.3f * sin(time * 20f))),
                        alpha = fire,
                    )
                }
            }
            .padding(horizontal = if (standalone) 20.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SpeedText(
            kmh,
            mph,
            needsGps,
            numberSize = if (standalone) 34.sp else 26.sp,
            unitSize = if (standalone) 15.sp else 12.sp,
            gpsSize = 16.sp,
            unitBeside = true,
        )
    }
}
