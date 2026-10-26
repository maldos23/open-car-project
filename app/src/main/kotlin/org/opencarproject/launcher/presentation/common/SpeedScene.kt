package org.opencarproject.launcher.presentation.common

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.withFrameNanos
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
import org.opencarproject.launcher.core.model.SpeedDialStyle
import org.opencarproject.launcher.core.model.SpeedSource
import org.opencarproject.launcher.core.model.VehicleShape
import org.opencarproject.launcher.core.pure.SpeedUnits
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Top bar speed scene: a vehicle (or pet) in front of a city that scrolls according to the
 * speed; above [rocketKmh] it turns into a rocket traveling among the stars, with
 * fire and glow. Everything is drawn on a Canvas with a frame clock (no images).
 */
@Composable
fun SpeedScene(
    source: SpeedSource,
    rocketKmh: Int,
    vehicle: VehicleShape,
    color: Color,
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

    // Distance traveled in "scene units": the city/stars scroll with it.
    var travel by remember { mutableFloatStateOf(0f) }
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(vehicleSpeed) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) {
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
        AnimatedContent(
            targetState = rocket,
            transitionSpec = { (fadeIn(tween(600)) + scaleIn(initialScale = 0.9f)) togetherWith (fadeOut(tween(400)) + scaleOut(targetScale = 1.1f)) },
            label = "mode",
        ) { isRocket ->
            Canvas(Modifier.fillMaxSize()) {
                val kmh = vehicleSpeed.kmh
                if (isRocket) space(travel, time, kmh) else city(travel, time, kmh, vehicle, color, sky)
            }
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

// ---- City + car ----

private fun DrawScope.city(travel: Float, time: Float, kmh: Float, vehicle: VehicleShape, color: Color, sky: SkyState) {
    drawRect(Brush.verticalGradient(sky.colors()))
    celestial(sky, time)
    clouds(sky, travel, time)
    // Two building layers with parallax (the far one moves slower).
    val (far, near) = sky.buildingColors()
    skyline(offset = travel * 0.6f, baseY = size.height * 0.78f, maxH = size.height * 0.45f, color = far, seed = 7)
    skyline(offset = travel * 1.4f, baseY = size.height * 0.82f, maxH = size.height * 0.6f, color = near, seed = 3, windows = sky.time != TimeOfDay.DAY, time = time)
    // Road with moving lines.
    val roadY = size.height * 0.82f
    drawRect(Color(0xFF15172A), topLeft = Offset(0f, roadY), size = Size(size.width, size.height - roadY))
    val dash = 26.dp.toPx()
    val shift = (travel * 3f) % dash
    var x = -shift
    while (x < size.width) {
        drawLine(Color(0x99FFFFFF), Offset(x, roadY + 5.dp.toPx()), Offset(x + dash * 0.45f, roadY + 5.dp.toPx()), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
        x += dash
    }
    val base = Offset(size.width * 0.18f, roadY)
    when (vehicle) {
        VehicleShape.CAR -> car(base, travel, time, kmh, color, VehicleBody.SEDAN)
        VehicleShape.SUV -> car(base, travel, time, kmh, color, VehicleBody.SUV)
        VehicleShape.PICKUP -> car(base, travel, time, kmh, color, VehicleBody.PICKUP)
        VehicleShape.CAT -> pet(base, time, kmh, color, cat = true)
        VehicleShape.DOG -> pet(base, time, kmh, color, cat = false)
    }
    precipitation(sky, time)
}

/** Repeating "random but stable" row of buildings (pseudorandom by index). */
private fun DrawScope.skyline(offset: Float, baseY: Float, maxH: Float, color: Color, seed: Int, windows: Boolean = false, time: Float = 0f) {
    val w = 22.dp.toPx()
    val first = (offset / w).toInt()
    val shift = offset % w
    for (i in 0..(size.width / w).toInt() + 1) {
        val idx = first + i
        val h = maxH * (0.35f + 0.65f * hash(idx, seed))
        val left = i * w - shift
        drawRect(color, topLeft = Offset(left, baseY - h), size = Size(w - 2.dp.toPx(), h))
        if (windows) {
            // Some lit windows that blink slowly.
            val lit = hash(idx, seed + 11) > 0.45f && sin(time * 0.7f + idx) > -0.6f
            if (lit) drawRect(Color(0xFFFFD27A).copy(alpha = 0.7f), topLeft = Offset(left + 5.dp.toPx(), baseY - h + 5.dp.toPx()), size = Size(4.dp.toPx(), 4.dp.toPx()))
        }
    }
}

private enum class VehicleBody { SEDAN, SUV, PICKUP }

/**
 * Car / SUV / pickup with a modern profile: continuous curved body, tinted windows
 * with reflection, glowing LED headlight, red taillight and 5-spoke rims.
 */
private fun DrawScope.car(base: Offset, travel: Float, time: Float, kmh: Float, color: Color, body: VehicleBody) {
    val u = 6.dp.toPx()
    val bob = if (kmh > 1f) sin(time * 18f) * 0.5.dp.toPx() else 0f
    val y0 = base.y - 0.6f * u + bob // bottom line of the body
    val length = when (body) { VehicleBody.SEDAN -> 8.6f; VehicleBody.SUV -> 8.8f; VehicleBody.PICKUP -> 9.4f } * u
    val x0 = base.x
    val shade = androidx.compose.ui.graphics.lerp(color, Color.Black, 0.35f)
    val shine = androidx.compose.ui.graphics.lerp(color, Color.White, 0.35f)
    // Shadow on the ground.
    drawOval(Color(0x55000000), topLeft = Offset(x0 + 0.3f * u, base.y - 0.25f * u), size = Size(length - 0.6f * u, 0.5f * u))

    val shell = Path()
    val glass = Path()
    when (body) {
        VehicleBody.SEDAN -> {
            shell.apply {
                moveTo(x0, y0 - 0.4f * u)
                quadraticTo(x0, y0 - 1.6f * u, x0 + 0.9f * u, y0 - 1.7f * u) // trunk
                lineTo(x0 + 2.2f * u, y0 - 1.8f * u)
                cubicTo(x0 + 3f * u, y0 - 3.3f * u, x0 + 5.3f * u, y0 - 3.3f * u, x0 + 6.2f * u, y0 - 1.9f * u) // fastback-style roof
                quadraticTo(x0 + 7.9f * u, y0 - 1.7f * u, x0 + length, y0 - 0.9f * u) // hood
                lineTo(x0 + length, y0 - 0.2f * u)
                lineTo(x0, y0)
                close()
            }
            glass.apply {
                moveTo(x0 + 2.6f * u, y0 - 1.9f * u)
                cubicTo(x0 + 3.2f * u, y0 - 2.9f * u, x0 + 5f * u, y0 - 2.95f * u, x0 + 5.7f * u, y0 - 1.95f * u)
                close()
            }
        }
        VehicleBody.SUV -> {
            shell.apply {
                moveTo(x0, y0 - 0.3f * u)
                lineTo(x0 + 0.1f * u, y0 - 2.8f * u)
                quadraticTo(x0 + 0.2f * u, y0 - 3.3f * u, x0 + 0.9f * u, y0 - 3.3f * u) // long roof
                lineTo(x0 + 5.4f * u, y0 - 3.3f * u)
                quadraticTo(x0 + 6.3f * u, y0 - 3.2f * u, x0 + 6.9f * u, y0 - 2f * u) // raked windshield
                quadraticTo(x0 + 8.3f * u, y0 - 1.8f * u, x0 + length, y0 - 1.1f * u)
                lineTo(x0 + length, y0 - 0.2f * u)
                lineTo(x0, y0)
                close()
            }
            glass.apply {
                moveTo(x0 + 0.6f * u, y0 - 2f * u)
                lineTo(x0 + 0.7f * u, y0 - 2.9f * u)
                lineTo(x0 + 5.3f * u, y0 - 2.9f * u)
                quadraticTo(x0 + 6f * u, y0 - 2.8f * u, x0 + 6.4f * u, y0 - 2f * u)
                close()
            }
        }
        VehicleBody.PICKUP -> {
            shell.apply {
                moveTo(x0, y0 - 0.3f * u)
                lineTo(x0, y0 - 1.9f * u) // bed
                lineTo(x0 + 3.6f * u, y0 - 1.9f * u)
                lineTo(x0 + 3.8f * u, y0 - 3.3f * u) // cab
                lineTo(x0 + 6.1f * u, y0 - 3.3f * u)
                quadraticTo(x0 + 6.8f * u, y0 - 3.1f * u, x0 + 7.3f * u, y0 - 2f * u)
                quadraticTo(x0 + 8.8f * u, y0 - 1.8f * u, x0 + length, y0 - 1.2f * u)
                lineTo(x0 + length, y0 - 0.2f * u)
                lineTo(x0, y0)
                close()
            }
            glass.apply {
                moveTo(x0 + 4.2f * u, y0 - 2f * u)
                lineTo(x0 + 4.3f * u, y0 - 2.95f * u)
                lineTo(x0 + 6f * u, y0 - 2.95f * u)
                quadraticTo(x0 + 6.5f * u, y0 - 2.8f * u, x0 + 6.8f * u, y0 - 2f * u)
                close()
            }
        }
    }
    // Body with gradient (highlight on top, shadow below) and belt line.
    drawPath(shell, Brush.verticalGradient(listOf(shine, color, shade), startY = y0 - 3.3f * u, endY = y0))
    drawLine(shade.copy(alpha = 0.6f), Offset(x0 + 0.4f * u, y0 - 1.1f * u), Offset(x0 + length - 0.6f * u, y0 - 1.1f * u), strokeWidth = 1.2f)
    // Tinted windows with a diagonal reflection.
    drawPath(glass, Brush.linearGradient(listOf(Color(0xFF1C2833), Color(0xFF34495E)), start = Offset(x0 + 2f * u, y0 - 3f * u), end = Offset(x0 + 6f * u, y0 - 1.8f * u)))
    drawPath(glass, Brush.linearGradient(listOf(Color(0x00FFFFFF), Color(0x55FFFFFF), Color(0x00FFFFFF)), start = Offset(x0 + 3f * u, y0 - 3f * u), end = Offset(x0 + 4.4f * u, y0 - 1.8f * u)))
    // LED headlight (strip) with glow and red taillight.
    val headlight = Offset(x0 + length - 0.5f * u, y0 - 1.0f * u)
    drawCircle(Brush.radialGradient(listOf(Color(0x88FFFDE7), Color.Transparent), center = headlight, radius = 1.6f * u), radius = 1.6f * u, center = headlight)
    drawLine(Color(0xFFFFFDE7), headlight - Offset(0.6f * u, 0f), headlight + Offset(0.3f * u, 0f), strokeWidth = 0.35f * u, cap = StrokeCap.Round)
    drawLine(Color(0xFFFF1744), Offset(x0 + 0.1f * u, y0 - 1.2f * u), Offset(x0 + 0.6f * u, y0 - 1.2f * u), strokeWidth = 0.35f * u, cap = StrokeCap.Round)
    // Spinning 5-spoke rims.
    val angle = travel * 40f
    listOf(1.9f * u, length - 2f * u).forEach { wx ->
        val c = Offset(x0 + wx, base.y - 0.85f * u)
        drawCircle(Color(0xFF101010), radius = 0.95f * u, center = c)
        drawCircle(Color(0xFFB0BEC5), radius = 0.6f * u, center = c)
        rotate(angle, pivot = c) {
            for (k in 0 until 5) {
                val a = k * 72.0 * PI / 180
                drawLine(Color(0xFF455A64), c, c + Offset((kotlin.math.cos(a) * 0.55f * u).toFloat(), (sin(a) * 0.55f * u).toFloat()), strokeWidth = 0.18f * u, cap = StrokeCap.Round)
            }
        }
        drawCircle(Color(0xFF263238), radius = 0.15f * u, center = c)
    }
}

/**
 * Running cat or dog: body, head, tail and legs that swing in time with the
 * speed (still when stopped). The cat has pointy ears; the dog, floppy ones.
 */
private fun DrawScope.pet(base: Offset, time: Float, kmh: Float, color: Color, cat: Boolean) {
    val u = 6.dp.toPx()
    val moving = kmh > 1f
    val stride = if (moving) sin(time * (8f + kmh / 6f)) else 0f
    val bob = if (moving) kotlin.math.abs(stride) * 0.5f * u else 0f
    val bodyY = base.y - 2.6f * u - bob
    val dark = androidx.compose.ui.graphics.lerp(color, Color.Black, 0.3f)
    // Legs: front and back in antiphase.
    val legTop = bodyY + 0.9f * u
    listOf(1.2f to stride, 2.0f to -stride, 4.6f to -stride, 5.4f to stride).forEach { (lx, phase) ->
        val top = Offset(base.x + lx * u, legTop)
        val foot = Offset(top.x + phase * 0.8f * u, base.y - 0.2f * u)
        drawLine(dark, top, foot, strokeWidth = 0.55f * u, cap = StrokeCap.Round)
    }
    // Tail: waves while running.
    val tailBase = Offset(base.x + 0.4f * u, bodyY + 0.2f * u)
    val tailWave = if (moving) sin(time * 12f) * 0.6f * u else 0f
    val tail = Path().apply {
        moveTo(tailBase.x, tailBase.y)
        quadraticTo(tailBase.x - 1.2f * u, tailBase.y - 1f * u + tailWave, tailBase.x - (if (cat) 1.0f else 1.6f) * u, tailBase.y - (if (cat) 2.2f else 1.2f) * u + tailWave)
    }
    drawPath(tail, color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.5f * u, cap = StrokeCap.Round))
    // Body and head.
    // Oval body with gradient (volume) and chest higher than the hips.
    val shine = androidx.compose.ui.graphics.lerp(color, Color.White, 0.3f)
    val torso = Path().apply {
        moveTo(base.x + 0.6f * u, bodyY + 0.6f * u)
        cubicTo(base.x + 0.2f * u, bodyY - 0.9f * u, base.x + 2.4f * u, bodyY - 1.0f * u, base.x + 3.6f * u, bodyY - 0.8f * u)
        cubicTo(base.x + 5.2f * u, bodyY - 1.4f * u, base.x + 6.4f * u, bodyY - 0.6f * u, base.x + 6.0f * u, bodyY + 0.6f * u)
        cubicTo(base.x + 4.4f * u, bodyY + 1.2f * u, base.x + 2.0f * u, bodyY + 1.2f * u, base.x + 0.6f * u, bodyY + 0.6f * u)
        close()
    }
    drawPath(torso, Brush.verticalGradient(listOf(shine, color, dark), startY = bodyY - 1.4f * u, endY = bodyY + 1.2f * u))
    val head = Offset(base.x + 6.5f * u, bodyY - 1.2f * u)
    drawCircle(Brush.radialGradient(listOf(shine, color), center = head - Offset(0.3f * u, 0.3f * u), radius = 1.3f * u), radius = 1.05f * u, center = head)
    if (cat) {
        // Pointy ears.
        val ears = Path().apply {
            moveTo(head.x - 0.9f * u, head.y - 0.5f * u); lineTo(head.x - 0.6f * u, head.y - 1.8f * u); lineTo(head.x - 0.1f * u, head.y - 0.9f * u); close()
            moveTo(head.x + 0.1f * u, head.y - 0.9f * u); lineTo(head.x + 0.6f * u, head.y - 1.8f * u); lineTo(head.x + 0.9f * u, head.y - 0.5f * u); close()
        }
        drawPath(ears, color)
    } else {
        // Snout and floppy ear.
        drawRoundRect(color, topLeft = Offset(head.x + 0.4f * u, head.y - 0.1f * u), size = Size(1.2f * u, 0.8f * u), cornerRadius = CornerRadius(0.4f * u))
        drawRoundRect(dark, topLeft = Offset(head.x - 0.9f * u, head.y - 0.7f * u), size = Size(0.6f * u, 1.4f * u), cornerRadius = CornerRadius(0.3f * u))
        drawCircle(Color(0xFF111111), radius = 0.2f * u, center = Offset(head.x + 1.6f * u, head.y + 0.1f * u))
    }
    // Eye.
    drawCircle(Color(0xFF111111), radius = 0.18f * u, center = Offset(head.x + 0.45f * u, head.y - 0.25f * u))
}

// ---- Space + rocket ----

private fun DrawScope.space(travel: Float, time: Float, kmh: Float) {
    drawRect(Brush.horizontalGradient(listOf(Color(0xFF05061A), Color(0xFF1A0B3D), Color(0xFF070A26))))
    // Stars at three depths; the faster, the longer the trails ("warp" effect).
    val streak = (kmh / 20f).coerceIn(1f, 10f)
    for (layer in 1..3) {
        val speedFactor = layer * 2.2f
        for (i in 0 until 26) {
            val y = size.height * hash(i, layer * 17)
            val span = size.width + 40f
            val x = span - ((hash(i, layer * 29) * span + travel * speedFactor) % span) - 20f
            val len = streak * layer * 1.6f
            drawLine(
                Color.White.copy(alpha = 0.25f + 0.25f * layer),
                Offset(x, y),
                Offset(x + len, y),
                strokeWidth = (0.6f + 0.4f * layer).dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
    rocket(Offset(size.width * 0.22f, size.height / 2 + sin(time * 2.5f) * 2.dp.toPx()), time)
}

private fun DrawScope.rocket(center: Offset, time: Float) {
    val u = 5.dp.toPx()
    // Fire: flickering core + radial glow behind.
    val flicker = 0.85f + 0.15f * sin(time * 40f) + 0.08f * sin(time * 23f)
    val flameLen = 4.2f * u * flicker
    val tail = Offset(center.x - 3.2f * u, center.y)
    drawCircle(
        Brush.radialGradient(listOf(Color(0xCCFF9800), Color(0x55FF5722), Color.Transparent), center = tail - Offset(flameLen * 0.4f, 0f), radius = flameLen * 1.1f),
        radius = flameLen * 1.1f,
        center = tail - Offset(flameLen * 0.4f, 0f),
    )
    val flame = Path().apply {
        moveTo(tail.x, tail.y - 0.9f * u)
        quadraticTo(tail.x - flameLen * 0.6f, tail.y - 0.6f * u, tail.x - flameLen, tail.y)
        quadraticTo(tail.x - flameLen * 0.6f, tail.y + 0.6f * u, tail.x, tail.y + 0.9f * u)
        close()
    }
    drawPath(flame, Brush.horizontalGradient(listOf(Color(0xFFFF5722), Color(0xFFFFC107), Color(0xFFFFF8E1)), startX = tail.x - flameLen, endX = tail.x))
    // Body, nose, fins and window.
    val body = Path().apply {
        moveTo(center.x - 3.2f * u, center.y - 1.1f * u)
        lineTo(center.x + 1.8f * u, center.y - 1.1f * u)
        quadraticTo(center.x + 4f * u, center.y, center.x + 1.8f * u, center.y + 1.1f * u)
        lineTo(center.x - 3.2f * u, center.y + 1.1f * u)
        close()
    }
    drawPath(body, Color(0xFFECEFF1))
    val fins = Path().apply {
        moveTo(center.x - 2.4f * u, center.y - 1.1f * u); lineTo(center.x - 3.6f * u, center.y - 2.2f * u); lineTo(center.x - 3.4f * u, center.y - 1.1f * u); close()
        moveTo(center.x - 2.4f * u, center.y + 1.1f * u); lineTo(center.x - 3.6f * u, center.y + 2.2f * u); lineTo(center.x - 3.4f * u, center.y + 1.1f * u); close()
    }
    drawPath(fins, Color(0xFFE53935))
    drawCircle(Color(0xFF4FC3F7), radius = 0.55f * u, center = Offset(center.x + 0.6f * u, center.y))
    drawCircle(Color(0xFF263238), radius = 0.55f * u, center = Offset(center.x + 0.6f * u, center.y), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f))
}

/** Stable pseudorandom 0..1 by index (same index → same building/star). */
private fun hash(i: Int, seed: Int): Float {
    var x = i * 374761393 + seed * 668265263
    x = (x xor (x ushr 13)) * 1274126177
    return ((x xor (x ushr 16)) and 0x7FFFFFFF) / 2147483647f
}

// ---- Sky by time and weather ----

private enum class TimeOfDay {
    DAWN, DAY, DUSK, NIGHT;

    companion object {
        /** By local time; if the weather says it's night/day, that wins at the extremes. */
        fun from(hour: Int, isDay: Boolean?): TimeOfDay = when {
            hour in 6..7 -> DAWN
            hour in 18..19 -> DUSK
            isDay == false -> NIGHT
            hour in 8..17 -> DAY
            else -> NIGHT
        }
    }
}

private data class SkyState(val time: TimeOfDay, val condition: org.opencarproject.launcher.core.model.WeatherCondition?) {
    private val overcast get() = condition in OVERCAST

    fun colors(): List<Color> {
        val base = when (time) {
            TimeOfDay.DAWN -> listOf(Color(0xFF3D5A80), Color(0xFFE29578), Color(0xFFFFD6A5))
            TimeOfDay.DAY -> listOf(Color(0xFF2E86DE), Color(0xFF54A0FF), Color(0xFFA3D5FF))
            TimeOfDay.DUSK -> listOf(Color(0xFF2B2D5C), Color(0xFF8E4585), Color(0xFFF28C6B))
            TimeOfDay.NIGHT -> listOf(Color(0xFF0B1026), Color(0xFF1B2A4A), Color(0xFF2C3E63))
        }
        // Cloudy/rain/storm: the sky fades toward gray.
        val gray = if (time == TimeOfDay.NIGHT) Color(0xFF1E2430) else Color(0xFF7F8C9A)
        val amount = when (condition) {
            org.opencarproject.launcher.core.model.WeatherCondition.THUNDERSTORM -> 0.75f
            org.opencarproject.launcher.core.model.WeatherCondition.RAIN -> 0.6f
            org.opencarproject.launcher.core.model.WeatherCondition.CLOUDY, org.opencarproject.launcher.core.model.WeatherCondition.FOG -> 0.5f
            org.opencarproject.launcher.core.model.WeatherCondition.DRIZZLE, org.opencarproject.launcher.core.model.WeatherCondition.SNOW -> 0.4f
            org.opencarproject.launcher.core.model.WeatherCondition.PARTLY_CLOUDY -> 0.15f
            else -> 0f
        }
        return base.map { androidx.compose.ui.graphics.lerp(it, gray, amount) }
    }

    fun buildingColors(): Pair<Color, Color> = when (time) {
        TimeOfDay.DAY -> Color(0xFF6C7A93) to Color(0xFF4B5873)
        TimeOfDay.DAWN, TimeOfDay.DUSK -> Color(0xFF3A3557) to Color(0xFF26233F)
        TimeOfDay.NIGHT -> Color(0xFF2B2F52) to Color(0xFF1E213D)
    }

    val showSun get() = (time == TimeOfDay.DAY || time == TimeOfDay.DAWN || time == TimeOfDay.DUSK) && !overcast
    val showMoon get() = time == TimeOfDay.NIGHT && !overcast

    companion object {
        val OVERCAST = setOf(
            org.opencarproject.launcher.core.model.WeatherCondition.CLOUDY,
            org.opencarproject.launcher.core.model.WeatherCondition.FOG,
            org.opencarproject.launcher.core.model.WeatherCondition.RAIN,
            org.opencarproject.launcher.core.model.WeatherCondition.THUNDERSTORM,
            org.opencarproject.launcher.core.model.WeatherCondition.SNOW,
            org.opencarproject.launcher.core.model.WeatherCondition.DRIZZLE,
        )
    }
}

private fun currentHour(): Int = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)

/** Sun (low at sunrise/sunset) or moon with stars. */
private fun DrawScope.celestial(sky: SkyState, time: Float) {
    if (sky.showSun) {
        val low = sky.time != TimeOfDay.DAY
        val c = Offset(size.width * 0.62f, size.height * if (low) 0.62f else 0.28f)
        val r = size.height * 0.14f
        drawCircle(Brush.radialGradient(listOf(Color(0x88FFF3B0), Color.Transparent), center = c, radius = r * 3f), radius = r * 3f, center = c)
        drawCircle(if (low) Color(0xFFFFB74D) else Color(0xFFFFF59D), radius = r, center = c)
    }
    if (sky.showMoon) {
        val c = Offset(size.width * 0.62f, size.height * 0.26f)
        drawCircle(Color(0xFFE8EAF6), radius = size.height * 0.1f, center = c)
        for (i in 0 until 14) {
            val twinkle = 0.4f + 0.6f * (0.5f + 0.5f * sin(time * 2f + i))
            drawCircle(Color.White.copy(alpha = twinkle * 0.8f), radius = 1.2f, center = Offset(size.width * hash(i, 41), size.height * 0.5f * hash(i, 43)))
        }
    }
}

/** Slowly passing clouds; more and darker the worse the weather. */
private fun DrawScope.clouds(sky: SkyState, travel: Float, time: Float) {
    val c = sky.condition ?: return
    val count = when (c) {
        org.opencarproject.launcher.core.model.WeatherCondition.PARTLY_CLOUDY -> 2
        org.opencarproject.launcher.core.model.WeatherCondition.CLEAR -> 0
        else -> 4
    }
    if (count == 0) return
    val stormy = c == org.opencarproject.launcher.core.model.WeatherCondition.THUNDERSTORM || c == org.opencarproject.launcher.core.model.WeatherCondition.RAIN
    // At night clouds barely stand out from the sky (not white).
    val tint = when {
        sky.time == TimeOfDay.NIGHT -> if (stormy) Color(0xFF2A3140) else Color(0xFF3A4458)
        stormy -> Color(0xFF55606E)
        sky.time == TimeOfDay.DUSK || sky.time == TimeOfDay.DAWN -> Color(0xFFC9B6C8)
        else -> Color(0xFFE3E8EE)
    }
    val span = size.width + 120f
    for (i in 0 until count) {
        val x = span - ((hash(i, 61) * span + travel * 0.3f + time * 6f) % span) - 60f
        val y = size.height * (0.15f + 0.25f * hash(i, 67))
        val w = size.height * (0.5f + 0.3f * hash(i, 71))
        drawOval(tint.copy(alpha = 0.85f), topLeft = Offset(x, y), size = Size(w, w * 0.35f))
        drawOval(tint.copy(alpha = 0.85f), topLeft = Offset(x + w * 0.2f, y - w * 0.15f), size = Size(w * 0.55f, w * 0.4f))
    }
}

/** Rain, snow, lightning or fog over the scene. */
private fun DrawScope.precipitation(sky: SkyState, time: Float) {
    when (sky.condition) {
        org.opencarproject.launcher.core.model.WeatherCondition.RAIN,
        org.opencarproject.launcher.core.model.WeatherCondition.DRIZZLE,
        org.opencarproject.launcher.core.model.WeatherCondition.THUNDERSTORM -> {
            val n = if (sky.condition == org.opencarproject.launcher.core.model.WeatherCondition.DRIZZLE) 18 else 40
            for (i in 0 until n) {
                val p = (time * (1.2f + hash(i, 5)) + hash(i, 9)) % 1f
                val x = size.width * hash(i, 13) - p * 12f
                val y = p * size.height
                drawLine(Color(0x99B3E5FC), Offset(x, y), Offset(x - 4f, y + 10f), strokeWidth = 1.5f, cap = StrokeCap.Round)
            }
            if (sky.condition == org.opencarproject.launcher.core.model.WeatherCondition.THUNDERSTORM && (time % 4.5f) < 0.12f) {
                drawRect(Color.White.copy(alpha = 0.35f))
            }
        }
        org.opencarproject.launcher.core.model.WeatherCondition.SNOW -> for (i in 0 until 30) {
            val p = (time * (0.25f + 0.2f * hash(i, 5)) + hash(i, 9)) % 1f
            val x = size.width * hash(i, 13) + sin(time * 2f + i) * 6f
            drawCircle(Color.White.copy(alpha = 0.9f), radius = 1.8f, center = Offset(x, p * size.height))
        }
        org.opencarproject.launcher.core.model.WeatherCondition.FOG -> drawRect(
            Brush.verticalGradient(listOf(Color(0x00FFFFFF), Color(0x66DDE3EA), Color(0x88DDE3EA))),
        )
        else -> Unit
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
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) withFrameNanos { now ->
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
