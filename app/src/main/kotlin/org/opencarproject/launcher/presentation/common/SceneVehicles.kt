package org.opencarproject.launcher.presentation.common

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import org.opencarproject.launcher.core.model.VehicleShape
import org.opencarproject.launcher.core.pure.LaunchMath
import org.opencarproject.launcher.core.pure.PixelSprite
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Size unit of the vehicles and pets (all geometry is in multiples of it). */
private val ActorUnit = 6.dp

/** Length of each silhouette in [ActorUnit]s (to center it in thumbnails and launch the rocket from its middle). */
internal fun actorLength(shape: VehicleShape): Float = when (shape) {
    VehicleShape.CAR -> 8.6f
    VehicleShape.SUV -> 8.8f
    VehicleShape.PICKUP -> 9.4f
    VehicleShape.SPORTS, VehicleShape.VAN -> 9f
    VehicleShape.BUS -> 12.5f
    VehicleShape.MOTORBIKE -> 6.4f
    VehicleShape.CAT, VehicleShape.DOG -> 7.6f
    VehicleShape.PIXEL_CAT -> PixelSprite.CAT_FRAMES[0].width * CatPixel.value / ActorUnit.value
}

/**
 * Draws the silhouette standing on the road at [base] (left end, ground line).
 * [lights]: headlight beam (night); [braking]: bright taillight.
 */
internal fun DrawScope.actor(
    shape: VehicleShape,
    base: Offset,
    travel: Float,
    time: Float,
    kmh: Float,
    color: Color,
    lights: Boolean = false,
    braking: Boolean = false,
) {
    when (shape) {
        VehicleShape.CAR -> car(base, travel, time, kmh, color, VehicleBody.SEDAN, lights, braking)
        VehicleShape.SUV -> car(base, travel, time, kmh, color, VehicleBody.SUV, lights, braking)
        VehicleShape.PICKUP -> car(base, travel, time, kmh, color, VehicleBody.PICKUP, lights, braking)
        VehicleShape.SPORTS -> car(base, travel, time, kmh, color, VehicleBody.SPORTS, lights, braking)
        VehicleShape.VAN -> car(base, travel, time, kmh, color, VehicleBody.VAN, lights, braking)
        VehicleShape.BUS -> car(base, travel, time, kmh, color, VehicleBody.BUS, lights, braking)
        VehicleShape.MOTORBIKE -> motorbike(base, travel, time, kmh, color, lights, braking)
        VehicleShape.CAT -> pet(base, time, kmh, color, cat = true)
        VehicleShape.DOG -> pet(base, time, kmh, color, cat = false)
        VehicleShape.PIXEL_CAT -> pixelCat(base, time, kmh, boost = 0f)
    }
}

/**
 * The silhouette with its launch: at [launch] 0 it drives on the road; while it grows to 1
 * it shakes and sparks, a rocket grows out of it (painted with its color) and lifts off to
 * the cruise spot among the stars. The pixel cat flies with its rainbow instead of a rocket.
 */
internal fun DrawScope.launchingActor(
    shape: VehicleShape,
    launch: Float,
    travel: Float,
    time: Float,
    kmh: Float,
    color: Color,
    lights: Boolean,
    braking: Boolean,
) {
    val u = ActorUnit.toPx()
    val base = Offset(size.width * 0.18f, size.height * RoadLevel)
    val charge = LaunchMath.charge(launch)
    val morph = LaunchMath.morph(launch)
    val lift = LaunchMath.lift(launch)
    val eased = lift * lift * (3f - 2f * lift)
    val shake = charge * (1f - lift) * 1.2.dp.toPx()
    val jitter = Offset(sin(time * 61f) * shake, sin(time * 47f) * shake * 0.6f)
    val cruise = Offset(size.width * 0.22f, size.height / 2 + sin(time * 2.5f) * 2.dp.toPx())

    if (shape == VehicleShape.PIXEL_CAT) {
        val sprite = PixelSprite.CAT_FRAMES[0]
        val cruiseBase = cruise + Offset(-sprite.width * CatPixel.toPx() / 2, sprite.height * CatPixel.toPx() / 2)
        pixelCat(androidx.compose.ui.geometry.lerp(base, cruiseBase, eased) + jitter, time, kmh, boost = morph)
        return
    }

    val start = base + Offset(actorLength(shape) * u / 2, -1.8f * u)
    val pos = androidx.compose.ui.geometry.lerp(start, cruise, eased) + jitter
    if (morph < 1f) {
        withAlpha(1f - morph) {
            translate(jitter.x, jitter.y) { actor(shape, base, travel, time, kmh, color, lights, braking) }
            if (charge > 0f) sparks(base + Offset(0f, -0.6f * u), time, charge)
        }
    }
    if (morph > 0f) {
        // Nose up while climbing, level again when cruising.
        rotate(-18f * sin(PI.toFloat() * lift), pivot = pos) {
            scale(0.4f + 0.6f * morph, pivot = pos) { rocket(pos, time, fins = color, flame = morph) }
        }
    }
}

/** Sparks and glow under the rear while the vehicle charges for the launch. */
private fun DrawScope.sparks(rear: Offset, time: Float, amount: Float) {
    val u = ActorUnit.toPx()
    drawCircle(
        Brush.radialGradient(listOf(Color(0xAAFF9800).copy(alpha = 0.6f * amount), Color.Transparent), center = rear, radius = 2.5f * u),
        radius = 2.5f * u,
        center = rear,
    )
    for (i in 0 until 10) {
        val p = (time * (2.5f + hash(i, 3)) + hash(i, 5)) % 1f
        val at = rear + Offset(-p * 3f * u, (hash(i, 7) - 0.5f) * u - p * 0.8f * u)
        drawCircle(Color(0xFFFFD54F).copy(alpha = amount * (1f - p)), radius = 0.18f * u * (1f - p) + 1f, center = at)
    }
}

private enum class VehicleBody(val length: Float, val roof: Float, val doors: List<Float>, val mirror: Offset, val wheels: Pair<Float, Float>) {
    SEDAN(8.6f, 3.3f, listOf(2.6f, 4.4f), Offset(5.9f, 1.9f), 1.9f to 6.6f),
    SUV(8.8f, 3.3f, listOf(2.6f, 4.6f), Offset(6.5f, 2.0f), 1.9f to 6.8f),
    PICKUP(9.4f, 3.3f, listOf(4.0f), Offset(7.0f, 2.0f), 1.9f to 7.4f),
    SPORTS(9f, 2.7f, listOf(3.6f), Offset(6.5f, 1.5f), 1.8f to 7.1f),
    VAN(9f, 3.7f, listOf(2.4f, 5.4f), Offset(7.4f, 2.1f), 1.9f to 7f),
    BUS(12.5f, 4.1f, emptyList(), Offset(12.5f, 3.0f), 2.2f to 9.9f),
}

/**
 * Car-like bodies with a modern profile: continuous curved body, tinted windows with
 * reflection, door seams and handles, side mirror, glowing LED headlight (with a beam at
 * night), taillight that brightens when braking, exhaust puffs and 5-spoke rims.
 */
private fun DrawScope.car(base: Offset, travel: Float, time: Float, kmh: Float, color: Color, body: VehicleBody, lights: Boolean, braking: Boolean) {
    val u = ActorUnit.toPx()
    val moving = kmh > 1f
    val bob = if (moving) sin(time * 18f) * 0.5.dp.toPx() else 0f
    val y0 = base.y - 0.6f * u + bob // bottom line of the body
    val length = body.length * u
    val x0 = base.x
    val shade = lerp(color, Color.Black, 0.35f)
    val shine = lerp(color, Color.White, 0.35f)
    // Shadow on the ground.
    drawOval(Color(0x55000000), topLeft = Offset(x0 + 0.3f * u, base.y - 0.25f * u), size = Size(length - 0.6f * u, 0.5f * u))
    if (moving) exhaust(Offset(x0, y0 - 0.3f * u), time)

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
            // Roof rails.
            drawLine(Color(0xFF37474F), Offset(x0 + 1.2f * u, y0 - 3.45f * u), Offset(x0 + 5f * u, y0 - 3.45f * u), strokeWidth = 0.18f * u, cap = StrokeCap.Round)
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
        VehicleBody.SPORTS -> {
            shell.apply {
                moveTo(x0, y0 - 0.5f * u)
                lineTo(x0, y0 - 1.5f * u)
                lineTo(x0 + 0.8f * u, y0 - 1.6f * u)
                cubicTo(x0 + 2.6f * u, y0 - 1.7f * u, x0 + 3.4f * u, y0 - 2.7f * u, x0 + 4.6f * u, y0 - 2.7f * u) // low roof
                cubicTo(x0 + 5.6f * u, y0 - 2.7f * u, x0 + 6.2f * u, y0 - 1.6f * u, x0 + 7f * u, y0 - 1.4f * u)
                quadraticTo(x0 + 8.5f * u, y0 - 1.1f * u, x0 + length, y0 - 0.6f * u) // long, low nose
                lineTo(x0 + length, y0 - 0.2f * u)
                lineTo(x0, y0)
                close()
            }
            glass.apply {
                moveTo(x0 + 3f * u, y0 - 1.8f * u)
                cubicTo(x0 + 3.6f * u, y0 - 2.5f * u, x0 + 5.4f * u, y0 - 2.6f * u, x0 + 6.4f * u, y0 - 1.6f * u)
                close()
            }
            // Rear spoiler on two struts.
            drawLine(Color(0xFF212121), Offset(x0 + 0.5f * u, y0 - 1.6f * u), Offset(x0 + 0.5f * u, y0 - 2.1f * u), strokeWidth = 0.15f * u)
            drawRoundRect(shade, topLeft = Offset(x0 - 0.2f * u, y0 - 2.3f * u), size = Size(1.3f * u, 0.28f * u), cornerRadius = CornerRadius(0.14f * u))
        }
        VehicleBody.VAN -> {
            shell.apply {
                moveTo(x0, y0 - 0.3f * u)
                lineTo(x0, y0 - 3.4f * u)
                quadraticTo(x0, y0 - 3.7f * u, x0 + 0.4f * u, y0 - 3.7f * u)
                lineTo(x0 + 6.4f * u, y0 - 3.7f * u)
                quadraticTo(x0 + 7.1f * u, y0 - 3.6f * u, x0 + 7.6f * u, y0 - 2.1f * u) // steep windshield
                quadraticTo(x0 + 8.7f * u, y0 - 1.9f * u, x0 + length, y0 - 1.2f * u)
                lineTo(x0 + length, y0 - 0.2f * u)
                lineTo(x0, y0)
                close()
            }
            glass.apply {
                moveTo(x0 + 5.7f * u, y0 - 2.2f * u)
                lineTo(x0 + 5.8f * u, y0 - 3.3f * u)
                lineTo(x0 + 6.4f * u, y0 - 3.3f * u)
                quadraticTo(x0 + 6.9f * u, y0 - 3.2f * u, x0 + 7.2f * u, y0 - 2.2f * u)
                close()
                addRoundRect(RoundRect(x0 + 0.5f * u, y0 - 3.3f * u, x0 + 5.2f * u, y0 - 2.4f * u, CornerRadius(0.2f * u)))
            }
        }
        VehicleBody.BUS -> {
            shell.apply {
                moveTo(x0, y0 - 0.3f * u)
                lineTo(x0, y0 - 3.8f * u)
                quadraticTo(x0, y0 - 4.1f * u, x0 + 0.4f * u, y0 - 4.1f * u)
                lineTo(x0 + length - 0.6f * u, y0 - 4.1f * u)
                quadraticTo(x0 + length, y0 - 4f * u, x0 + length, y0 - 3.4f * u)
                lineTo(x0 + length, y0 - 0.2f * u)
                lineTo(x0, y0)
                close()
            }
            glass.apply {
                for (i in 0 until 6) {
                    val left = x0 + 0.5f * u + i * 1.65f * u
                    addRoundRect(RoundRect(left, y0 - 3.7f * u, left + 1.35f * u, y0 - 2.4f * u, CornerRadius(0.2f * u)))
                }
                addRoundRect(RoundRect(x0 + length - 0.9f * u, y0 - 3.7f * u, x0 + length - 0.15f * u, y0 - 1.6f * u, CornerRadius(0.2f * u)))
            }
        }
    }
    // Body with gradient (highlight on top, shadow below) and belt line.
    drawPath(shell, Brush.verticalGradient(listOf(shine, color, shade), startY = y0 - body.roof * u, endY = y0))
    drawLine(shade.copy(alpha = 0.6f), Offset(x0 + 0.4f * u, y0 - 1.1f * u), Offset(x0 + length - 0.6f * u, y0 - 1.1f * u), strokeWidth = 1.2f)
    if (body == VehicleBody.BUS) {
        // Light stripe and folding front door.
        drawLine(Color.White.copy(alpha = 0.7f), Offset(x0 + 0.3f * u, y0 - 2f * u), Offset(x0 + length - 2.6f * u, y0 - 2f * u), strokeWidth = 0.3f * u)
        drawRect(shade, topLeft = Offset(x0 + length - 2.3f * u, y0 - 3.7f * u), size = Size(1.1f * u, 3.3f * u), style = Stroke(1.5f))
        drawLine(shade, Offset(x0 + length - 1.75f * u, y0 - 3.7f * u), Offset(x0 + length - 1.75f * u, y0 - 0.4f * u), strokeWidth = 1.2f)
    }
    // Tinted windows with a diagonal reflection.
    drawPath(glass, Brush.linearGradient(listOf(Color(0xFF1C2833), Color(0xFF34495E)), start = Offset(x0 + 2f * u, y0 - 3f * u), end = Offset(x0 + 6f * u, y0 - 1.8f * u)))
    drawPath(glass, Brush.linearGradient(listOf(Color(0x00FFFFFF), Color(0x55FFFFFF), Color(0x00FFFFFF)), start = Offset(x0 + 3f * u, y0 - 3f * u), end = Offset(x0 + 4.4f * u, y0 - 1.8f * u)))
    // Door seams with their handles, and the side mirror.
    body.doors.forEach { d ->
        drawLine(shade.copy(alpha = 0.8f), Offset(x0 + d * u, y0 - 1.9f * u), Offset(x0 + d * u, y0 - 0.35f * u), strokeWidth = 1.2f)
        drawLine(shade, Offset(x0 + (d + 0.35f) * u, y0 - 1.5f * u), Offset(x0 + (d + 0.85f) * u, y0 - 1.5f * u), strokeWidth = 0.16f * u, cap = StrokeCap.Round)
    }
    drawRoundRect(shade, topLeft = Offset(x0 + body.mirror.x * u - 0.3f * u, y0 - body.mirror.y * u - 0.35f * u), size = Size(0.55f * u, 0.38f * u), cornerRadius = CornerRadius(0.15f * u))
    // LED headlight (strip) with glow, beam at night, and taillight (brighter when braking).
    val headlight = Offset(x0 + length - 0.5f * u, y0 - 1.0f * u)
    if (lights) beam(headlight)
    drawCircle(Brush.radialGradient(listOf(Color(0x88FFFDE7), Color.Transparent), center = headlight, radius = 1.6f * u), radius = 1.6f * u, center = headlight)
    drawLine(Color(0xFFFFFDE7), headlight - Offset(0.6f * u, 0f), headlight + Offset(0.3f * u, 0f), strokeWidth = 0.35f * u, cap = StrokeCap.Round)
    taillight(Offset(x0 + 0.35f * u, y0 - 1.2f * u), braking)
    wheels(listOf(Offset(x0 + body.wheels.first * u, base.y - 0.85f * u), Offset(x0 + body.wheels.second * u, base.y - 0.85f * u)), travel)
}

/** Motorbike with its rider leaning forward: helmet in the chosen color. */
private fun DrawScope.motorbike(base: Offset, travel: Float, time: Float, kmh: Float, color: Color, lights: Boolean, braking: Boolean) {
    val u = ActorUnit.toPx()
    val moving = kmh > 1f
    val y = base.y + (if (moving) sin(time * 20f) * 0.4.dp.toPx() else 0f)
    val x0 = base.x
    val shade = lerp(color, Color.Black, 0.35f)
    val shine = lerp(color, Color.White, 0.35f)
    val rear = Offset(x0 + 1.1f * u, base.y - 0.95f * u)
    val front = Offset(x0 + 5.3f * u, base.y - 0.95f * u)
    drawOval(Color(0x55000000), topLeft = Offset(x0 + 0.2f * u, base.y - 0.25f * u), size = Size(6f * u, 0.5f * u))
    if (moving) exhaust(Offset(x0 + 0.4f * u, y - 1.4f * u), time)
    // Swing arm, fork and exhaust pipe.
    drawLine(Color(0xFF37474F), rear, Offset(x0 + 2.8f * u, y - 1.6f * u), strokeWidth = 0.3f * u, cap = StrokeCap.Round)
    drawLine(Color(0xFFB0BEC5), front, Offset(x0 + 4.7f * u, y - 2.7f * u), strokeWidth = 0.28f * u, cap = StrokeCap.Round)
    drawLine(Color(0xFF90A4AE), Offset(x0 + 2.8f * u, y - 1.1f * u), Offset(x0 + 0.5f * u, y - 1.45f * u), strokeWidth = 0.28f * u, cap = StrokeCap.Round)
    // Engine, tank/body and seat.
    drawRoundRect(Color(0xFF455A64), topLeft = Offset(x0 + 2.4f * u, y - 1.8f * u), size = Size(1.5f * u, 0.8f * u), cornerRadius = CornerRadius(0.2f * u))
    val tank = Path().apply {
        moveTo(x0 + 1.2f * u, y - 2.0f * u)
        lineTo(x0 + 2.2f * u, y - 2.4f * u)
        quadraticTo(x0 + 3.5f * u, y - 3.0f * u, x0 + 4.7f * u, y - 2.5f * u)
        lineTo(x0 + 4.3f * u, y - 1.7f * u)
        lineTo(x0 + 1.6f * u, y - 1.7f * u)
        close()
    }
    drawPath(tank, Brush.verticalGradient(listOf(shine, color, shade), startY = y - 3f * u, endY = y - 1.7f * u))
    drawRoundRect(Color(0xFF212121), topLeft = Offset(x0 + 1.3f * u, y - 2.45f * u), size = Size(1.5f * u, 0.35f * u), cornerRadius = CornerRadius(0.17f * u))
    // Rider: leg, torso, arm and helmet with visor.
    val jacket = Color(0xFF263238)
    val hip = Offset(x0 + 2.3f * u, y - 2.6f * u)
    val shoulder = Offset(x0 + 3.4f * u, y - 4.0f * u)
    drawLine(jacket, hip, Offset(x0 + 3.4f * u, y - 2.0f * u), strokeWidth = 0.55f * u, cap = StrokeCap.Round)
    drawLine(jacket, Offset(x0 + 3.4f * u, y - 2.0f * u), Offset(x0 + 2.9f * u, y - 1.2f * u), strokeWidth = 0.5f * u, cap = StrokeCap.Round)
    drawLine(jacket, hip, shoulder, strokeWidth = 0.85f * u, cap = StrokeCap.Round)
    drawLine(Color(0xFF37474F), shoulder, Offset(x0 + 4.6f * u, y - 2.9f * u), strokeWidth = 0.4f * u, cap = StrokeCap.Round)
    val helmet = Offset(x0 + 3.8f * u, y - 4.6f * u)
    drawCircle(Brush.radialGradient(listOf(shine, color, shade), center = helmet - Offset(0.2f * u, 0.3f * u), radius = 0.9f * u), radius = 0.68f * u, center = helmet)
    drawRoundRect(Color(0xFF1C2833), topLeft = Offset(helmet.x + 0.05f * u, helmet.y - 0.25f * u), size = Size(0.62f * u, 0.38f * u), cornerRadius = CornerRadius(0.18f * u))
    // Lights and wheels.
    val headlight = Offset(x0 + 4.95f * u, y - 2.5f * u)
    if (lights) beam(headlight)
    drawCircle(Color(0xFFFFFDE7), radius = 0.22f * u, center = headlight)
    taillight(Offset(x0 + 1.25f * u, y - 2.1f * u), braking)
    wheels(listOf(rear, front), travel)
}

/** Spinning 5-spoke rims. */
private fun DrawScope.wheels(centers: List<Offset>, travel: Float) {
    val u = ActorUnit.toPx()
    val angle = travel * 40f
    centers.forEach { c ->
        drawCircle(Color(0xFF101010), radius = 0.95f * u, center = c)
        drawCircle(Color(0xFFB0BEC5), radius = 0.6f * u, center = c)
        rotate(angle, pivot = c) {
            for (k in 0 until 5) {
                val a = k * 72.0 * PI / 180
                drawLine(Color(0xFF455A64), c, c + Offset((cos(a) * 0.55f * u).toFloat(), (sin(a) * 0.55f * u).toFloat()), strokeWidth = 0.18f * u, cap = StrokeCap.Round)
            }
        }
        drawCircle(Color(0xFF263238), radius = 0.15f * u, center = c)
    }
}

/** Grey puffs drifting back from the exhaust. */
private fun DrawScope.exhaust(pipe: Offset, time: Float) {
    val u = ActorUnit.toPx()
    for (k in 0 until 3) {
        val p = (time * 1.5f + k / 3f) % 1f
        drawCircle(Color(0xFFB0BEC5).copy(alpha = 0.35f * (1f - p)), radius = (0.25f + 0.5f * p) * u, center = pipe + Offset(-p * 2.2f * u, -p * 0.6f * u))
    }
}

/** Headlight beam cone toward the front. */
private fun DrawScope.beam(from: Offset) {
    val u = ActorUnit.toPx()
    val cone = Path().apply {
        moveTo(from.x, from.y - 0.2f * u)
        lineTo(from.x + 5f * u, from.y - 0.9f * u)
        lineTo(from.x + 5f * u, from.y + 1.1f * u)
        lineTo(from.x, from.y + 0.2f * u)
        close()
    }
    drawPath(cone, Brush.horizontalGradient(listOf(Color(0x66FFF8E1), Color.Transparent), startX = from.x, endX = from.x + 5f * u))
}

private fun DrawScope.taillight(at: Offset, braking: Boolean) {
    val u = ActorUnit.toPx()
    if (braking) drawCircle(Brush.radialGradient(listOf(Color(0xAAFF1744), Color.Transparent), center = at, radius = 1.4f * u), radius = 1.4f * u, center = at)
    drawLine(if (braking) Color(0xFFFF5252) else Color(0xFFD50000), at - Offset(0.25f * u, 0f), at + Offset(0.25f * u, 0f), strokeWidth = 0.35f * u, cap = StrokeCap.Round)
}

/**
 * Running cat or dog: body, head, tail and legs that swing in time with the speed (still
 * when stopped). The cat has pointy ears that twitch, whiskers, stripes and blinks; the
 * dog has spots, a collar with a tag, a floppy ear that flaps and its tongue out when fast.
 */
private fun DrawScope.pet(base: Offset, time: Float, kmh: Float, color: Color, cat: Boolean) {
    val u = ActorUnit.toPx()
    val moving = kmh > 1f
    val stride = if (moving) sin(time * (8f + kmh / 6f)) else 0f
    val bob = if (moving) abs(stride) * 0.5f * u else 0f
    val bodyY = base.y - 2.6f * u - bob
    val dark = lerp(color, Color.Black, 0.3f)
    val shine = lerp(color, Color.White, 0.3f)
    drawOval(Color(0x44000000), topLeft = Offset(base.x + 0.8f * u, base.y - 0.25f * u), size = Size(5.4f * u, 0.5f * u))
    // Legs: front and back in antiphase, with paws.
    val legTop = bodyY + 0.9f * u
    listOf(1.2f to stride, 2.0f to -stride, 4.6f to -stride, 5.4f to stride).forEach { (lx, phase) ->
        val top = Offset(base.x + lx * u, legTop)
        val foot = Offset(top.x + phase * 0.8f * u, base.y - 0.2f * u)
        drawLine(dark, top, foot, strokeWidth = 0.55f * u, cap = StrokeCap.Round)
        drawCircle(dark, radius = 0.32f * u, center = foot)
    }
    // Tail: waves while running; the cat's curls at the tip.
    val tailBase = Offset(base.x + 0.4f * u, bodyY + 0.2f * u)
    val tailWave = if (moving) sin(time * 12f) * 0.6f * u else sin(time * 2f) * 0.2f * u
    val tipX = tailBase.x - (if (cat) 1.0f else 1.6f) * u
    val tipY = tailBase.y - (if (cat) 2.2f else 1.2f) * u + tailWave
    val tail = Path().apply {
        moveTo(tailBase.x, tailBase.y)
        quadraticTo(tailBase.x - 1.2f * u, tailBase.y - 1f * u + tailWave, tipX, tipY)
        if (cat) quadraticTo(tipX - 0.1f * u, tipY - 0.6f * u, tipX + 0.4f * u, tipY - 0.5f * u)
    }
    drawPath(tail, color, style = Stroke(width = 0.5f * u, cap = StrokeCap.Round))
    // Oval body with gradient (volume) and chest higher than the hips.
    val torso = Path().apply {
        moveTo(base.x + 0.6f * u, bodyY + 0.6f * u)
        cubicTo(base.x + 0.2f * u, bodyY - 0.9f * u, base.x + 2.4f * u, bodyY - 1.0f * u, base.x + 3.6f * u, bodyY - 0.8f * u)
        cubicTo(base.x + 5.2f * u, bodyY - 1.4f * u, base.x + 6.4f * u, bodyY - 0.6f * u, base.x + 6.0f * u, bodyY + 0.6f * u)
        cubicTo(base.x + 4.4f * u, bodyY + 1.2f * u, base.x + 2.0f * u, bodyY + 1.2f * u, base.x + 0.6f * u, bodyY + 0.6f * u)
        close()
    }
    drawPath(torso, Brush.verticalGradient(listOf(shine, color, dark), startY = bodyY - 1.4f * u, endY = bodyY + 1.2f * u))
    if (cat) {
        // Tabby stripes.
        for (sx in listOf(1.6f, 2.6f, 3.6f)) {
            drawLine(dark.copy(alpha = 0.7f), Offset(base.x + sx * u, bodyY - 0.8f * u), Offset(base.x + (sx + 0.3f) * u, bodyY + 0.1f * u), strokeWidth = 0.22f * u, cap = StrokeCap.Round)
        }
    } else {
        // Spots.
        drawOval(dark.copy(alpha = 0.75f), topLeft = Offset(base.x + 1.5f * u, bodyY - 0.6f * u), size = Size(1.3f * u, 0.8f * u))
        drawOval(dark.copy(alpha = 0.75f), topLeft = Offset(base.x + 3.6f * u, bodyY - 0.3f * u), size = Size(0.9f * u, 0.6f * u))
    }
    val head = Offset(base.x + 6.5f * u, bodyY - 1.2f * u)
    val face = Offset(head.x + 0.45f * u, head.y - 0.25f * u)
    if (cat) {
        // Pointy ears with pink inside; one twitches now and then.
        val twitch = if ((time % 5f) < 0.18f) -0.3f * u else 0f
        val ears = Path().apply {
            moveTo(head.x - 0.9f * u, head.y - 0.5f * u); lineTo(head.x - 0.6f * u, head.y - 1.8f * u); lineTo(head.x - 0.1f * u, head.y - 0.9f * u); close()
            moveTo(head.x + 0.1f * u, head.y - 0.9f * u); lineTo(head.x + 0.6f * u + twitch, head.y - 1.8f * u); lineTo(head.x + 0.9f * u, head.y - 0.5f * u); close()
        }
        drawPath(ears, color)
        val inner = Path().apply {
            moveTo(head.x - 0.7f * u, head.y - 0.7f * u); lineTo(head.x - 0.55f * u, head.y - 1.4f * u); lineTo(head.x - 0.3f * u, head.y - 0.9f * u); close()
            moveTo(head.x + 0.3f * u, head.y - 0.9f * u); lineTo(head.x + 0.55f * u + twitch * 0.8f, head.y - 1.4f * u); lineTo(head.x + 0.7f * u, head.y - 0.7f * u); close()
        }
        drawPath(inner, Color(0xFFF8BBD0))
        drawCircle(Brush.radialGradient(listOf(shine, color), center = head - Offset(0.3f * u, 0.3f * u), radius = 1.3f * u), radius = 1.05f * u, center = head)
        // Pink nose and whiskers.
        val nose = Offset(head.x + 0.95f * u, head.y + 0.1f * u)
        drawCircle(Color(0xFFFF80AB), radius = 0.14f * u, center = nose)
        for (k in -1..1) {
            drawLine(Color.White.copy(alpha = 0.8f), nose + Offset(0.05f * u, 0.1f * u), nose + Offset(0.9f * u, (0.1f + k * 0.3f) * u), strokeWidth = 1f)
        }
    } else {
        drawCircle(Brush.radialGradient(listOf(shine, color), center = head - Offset(0.3f * u, 0.3f * u), radius = 1.3f * u), radius = 1.05f * u, center = head)
        // Snout, tongue when running fast, nose and floppy ear flapping with the stride.
        drawRoundRect(color, topLeft = Offset(head.x + 0.4f * u, head.y - 0.1f * u), size = Size(1.2f * u, 0.8f * u), cornerRadius = CornerRadius(0.4f * u))
        if (kmh > 40f) {
            drawRoundRect(Color(0xFFFF6F91), topLeft = Offset(head.x + 0.9f * u, head.y + 0.55f * u), size = Size(0.35f * u, 0.7f * u + abs(stride) * 0.15f * u), cornerRadius = CornerRadius(0.18f * u))
        }
        drawCircle(Color(0xFF111111), radius = 0.2f * u, center = Offset(head.x + 1.6f * u, head.y + 0.1f * u))
        val earTop = Offset(head.x - 0.6f * u, head.y - 0.7f * u)
        rotate(if (moving) stride * 25f else 0f, pivot = earTop) {
            drawRoundRect(dark, topLeft = Offset(earTop.x - 0.3f * u, earTop.y), size = Size(0.6f * u, 1.4f * u), cornerRadius = CornerRadius(0.3f * u))
        }
        // Collar with its tag.
        drawLine(Color(0xFFE53935), Offset(head.x - 0.8f * u, head.y + 0.7f * u), Offset(head.x - 0.1f * u, head.y + 1.05f * u), strokeWidth = 0.3f * u, cap = StrokeCap.Round)
        drawCircle(Color(0xFFFFD54F), radius = 0.17f * u, center = Offset(head.x - 0.35f * u, head.y + 1.3f * u))
    }
    // Eye with a highlight; blinks every few seconds.
    if ((time % 4f) < 0.12f) {
        drawLine(Color(0xFF111111), face - Offset(0.18f * u, 0f), face + Offset(0.18f * u, 0f), strokeWidth = 0.1f * u, cap = StrokeCap.Round)
    } else {
        drawCircle(Color(0xFF111111), radius = 0.18f * u, center = face)
        drawCircle(Color.White, radius = 0.06f * u, center = face + Offset(0.06f * u, -0.06f * u))
    }
}

/** Side of one pixel of the pixel cat. */
private val CatPixel = 2.dp

private val CatPalette = mapOf(
    'k' to Color(0xFF111111),
    'g' to Color(0xFF9E9E9E),
    'l' to Color(0xFFD7DCE0),
    'd' to Color(0xFF6D6D6D),
    'w' to Color.White,
    'p' to Color(0xFFFF8FAB),
)

private val Rainbow = listOf(Color(0xFFFF1744), Color(0xFFFF9100), Color(0xFFFFEA00), Color(0xFF00E676), Color(0xFF2979FF), Color(0xFF7C4DFF))

/**
 * Original 8-bit cat with a waving rainbow trail and twinkling sparkles, snapped to its pixel
 * grid. [boost] (0..1) thickens the rainbow and adds sparkles during the launch.
 */
private fun DrawScope.pixelCat(base: Offset, time: Float, kmh: Float, boost: Float) {
    val p = CatPixel.toPx().roundToInt().coerceAtLeast(1).toFloat()
    val moving = kmh > 1f || boost > 0f
    val tick = (time * 8f).toInt()
    val frame = if (moving) tick % 2 else 0
    val sprite = PixelSprite.CAT_FRAMES[frame]
    val left = (base.x / p).roundToInt() * p
    val top = ((base.y - sprite.height * p) / p).roundToInt() * p - (if (frame == 1) p else 0f)
    // Rainbow: six stepped bands that wave up and down, from the screen edge to the tail.
    val band = p * (1f + boost)
    val segment = 4 * p
    val trailTop = top + 3 * p
    var x = left + 2 * p
    var seg = 0
    while (x > -segment) {
        val lift = if ((seg + tick / 2) % 2 == 0) 0f else p
        Rainbow.forEachIndexed { i, c -> drawRect(c, topLeft = Offset(x - segment, trailTop + lift + i * band), size = Size(segment, band)) }
        x -= segment
        seg++
    }
    // Sparkles over the trail: dot → small plus → big plus → off.
    val count = 6 + (8 * boost).toInt()
    for (i in 0 until count) {
        val stage = (tick / 2 + i) % 4
        if (stage == 3) continue
        val cx = ((left * hash(i, 83)) / p).roundToInt() * p
        val cy = ((size.height * hash(i, 89)) / p).roundToInt() * p
        drawRect(Color.White, topLeft = Offset(cx, cy), size = Size(p, p))
        for (arm in 1..stage) {
            listOf(Offset(arm * p, 0f), Offset(-arm * p, 0f), Offset(0f, arm * p), Offset(0f, -arm * p)).forEach {
                drawRect(Color.White, topLeft = Offset(cx, cy) + it, size = Size(p, p))
            }
        }
    }
    for (py in 0 until sprite.height) for (px in 0 until sprite.width) {
        val key = sprite.at(px, py) ?: continue
        drawRect(CatPalette.getValue(key), topLeft = Offset(left + px * p, top + py * p), size = Size(p, p))
    }
}

/** Rocket with flickering fire ([flame] 0..1) and fins in the vehicle's color. */
internal fun DrawScope.rocket(center: Offset, time: Float, fins: Color = Color(0xFFE53935), flame: Float = 1f) {
    val u = 5.dp.toPx()
    // Fire: flickering core + radial glow behind.
    val flicker = 0.85f + 0.15f * sin(time * 40f) + 0.08f * sin(time * 23f)
    val flameLen = 4.2f * u * flicker * flame
    val tail = Offset(center.x - 3.2f * u, center.y)
    if (flameLen > 0f) {
        drawCircle(
            Brush.radialGradient(listOf(Color(0xCCFF9800), Color(0x55FF5722), Color.Transparent), center = tail - Offset(flameLen * 0.4f, 0f), radius = flameLen * 1.1f),
            radius = flameLen * 1.1f,
            center = tail - Offset(flameLen * 0.4f, 0f),
        )
        val fire = Path().apply {
            moveTo(tail.x, tail.y - 0.9f * u)
            quadraticTo(tail.x - flameLen * 0.6f, tail.y - 0.6f * u, tail.x - flameLen, tail.y)
            quadraticTo(tail.x - flameLen * 0.6f, tail.y + 0.6f * u, tail.x, tail.y + 0.9f * u)
            close()
        }
        drawPath(fire, Brush.horizontalGradient(listOf(Color(0xFFFF5722), Color(0xFFFFC107), Color(0xFFFFF8E1)), startX = tail.x - flameLen, endX = tail.x))
    }
    // Body (with shading), nose cone, fins and window.
    val body = Path().apply {
        moveTo(center.x - 3.2f * u, center.y - 1.1f * u)
        lineTo(center.x + 1.8f * u, center.y - 1.1f * u)
        quadraticTo(center.x + 4f * u, center.y, center.x + 1.8f * u, center.y + 1.1f * u)
        lineTo(center.x - 3.2f * u, center.y + 1.1f * u)
        close()
    }
    drawPath(body, Brush.verticalGradient(listOf(Color.White, Color(0xFFECEFF1), Color(0xFFB0BEC5)), startY = center.y - 1.1f * u, endY = center.y + 1.1f * u))
    drawLine(fins, Offset(center.x + 2.4f * u, center.y - 0.85f * u), Offset(center.x + 2.4f * u, center.y + 0.85f * u), strokeWidth = 0.35f * u)
    val finPath = Path().apply {
        moveTo(center.x - 2.4f * u, center.y - 1.1f * u); lineTo(center.x - 3.6f * u, center.y - 2.2f * u); lineTo(center.x - 3.4f * u, center.y - 1.1f * u); close()
        moveTo(center.x - 2.4f * u, center.y + 1.1f * u); lineTo(center.x - 3.6f * u, center.y + 2.2f * u); lineTo(center.x - 3.4f * u, center.y + 1.1f * u); close()
    }
    drawPath(finPath, fins)
    drawCircle(Color(0xFF4FC3F7), radius = 0.55f * u, center = Offset(center.x + 0.6f * u, center.y))
    drawCircle(Color.White.copy(alpha = 0.6f), radius = 0.18f * u, center = Offset(center.x + 0.45f * u, center.y - 0.2f * u))
    drawCircle(Color(0xFF263238), radius = 0.55f * u, center = Offset(center.x + 0.6f * u, center.y), style = Stroke(1.5f))
}
