package org.opencarproject.launcher.presentation.common

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.opencarproject.launcher.core.model.SceneTheme
import org.opencarproject.launcher.core.model.WeatherCondition
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/** Height of the road surface, as a fraction of the scene height. */
internal const val RoadLevel = 0.82f

/** Scenery behind the vehicle; everything scrolls with [travel] (parallax by depth). */
internal fun DrawScope.backdrop(theme: SceneTheme, travel: Float, time: Float, sky: SkyState) {
    when (theme) {
        SceneTheme.CITY -> city(travel, time, sky)
        SceneTheme.PIXEL -> pixelCity(travel, time, sky)
        SceneTheme.COUNTRYSIDE -> countryside(travel, time, sky)
        SceneTheme.BEACH -> beach(travel, time, sky)
        SceneTheme.DESERT -> desert(travel, time, sky)
        SceneTheme.SNOW -> snow(travel, time, sky)
        SceneTheme.JUNGLE -> jungle(travel, time, sky)
    }
}

/** Draws [block] with [alpha] as a whole (one layer), so overlapping parts don't show through. */
internal inline fun DrawScope.withAlpha(alpha: Float, crossinline block: DrawScope.() -> Unit) {
    when {
        alpha <= 0f -> Unit
        alpha >= 1f -> block()
        else -> drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(Offset.Zero, size), Paint().apply { this.alpha = alpha })
            block()
            canvas.restore()
        }
    }
}

/** Stable pseudorandom 0..1 by index (same index → same building/star). */
internal fun hash(i: Int, seed: Int): Float {
    var x = i * 374761393 + seed * 668265263
    x = (x xor (x ushr 13)) * 1274126177
    return ((x xor (x ushr 16)) and 0x7FFFFFFF) / 2147483647f
}

// ---- Shared pieces ----

private fun DrawScope.sky(sky: SkyState, travel: Float, time: Float, colors: List<Color> = sky.colors()) {
    drawRect(Brush.verticalGradient(colors))
    celestial(sky, time)
    clouds(sky, travel, time)
}

private val SkyState.clearDay get() = time == TimeOfDay.DAY && condition !in SkyState.OVERCAST

/** Asphalt with an edge strip and (optionally) dashed center line moving with [travel]. */
private fun DrawScope.road(travel: Float, asphalt: Color, dashes: Color?, edge: Color? = null) {
    val roadY = size.height * RoadLevel
    drawRect(asphalt, topLeft = Offset(0f, roadY), size = Size(size.width, size.height - roadY))
    if (edge != null) drawRect(edge, topLeft = Offset(0f, roadY - 1.5.dp.toPx()), size = Size(size.width, 1.5.dp.toPx()))
    if (dashes == null) return
    val dash = 26.dp.toPx()
    var x = -((travel * 3f) % dash)
    while (x < size.width) {
        drawLine(dashes, Offset(x, roadY + 5.dp.toPx()), Offset(x + dash * 0.45f, roadY + 5.dp.toPx()), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
        x += dash
    }
}

/** Calls [item] for every slot of a row that repeats every [spacing] and scrolls by [offset]. */
private inline fun DrawScope.row(offset: Float, spacing: Dp, item: (x: Float, index: Int) -> Unit) {
    val step = spacing.toPx()
    val first = floor(offset / step).toInt()
    val shift = offset - first * step
    for (i in -1..(size.width / step).toInt() + 1) item(i * step - shift, first + i)
}

/** Rolling ridge (hills, dunes, canopy) filled down to the bottom. */
private fun DrawScope.ridge(offset: Float, baseY: Float, amplitude: Float, wave: Dp, color: Color, seed: Int) {
    val w = wave.toPx()
    val path = Path().apply {
        moveTo(0f, size.height)
        var x = 0f
        while (x <= size.width + 3f) {
            val k = (x + offset) / w
            lineTo(x, baseY - amplitude * (0.55f + 0.3f * sin(k + seed) + 0.15f * sin(k * 2.7f + seed * 1.3f)))
            x += 3.dp.toPx()
        }
        lineTo(size.width, size.height)
        close()
    }
    drawPath(path, color)
}

/** Flapping birds crossing the sky. */
private fun DrawScope.birds(travel: Float, time: Float, color: Color, count: Int = 3) {
    val span = size.width + 40.dp.toPx()
    val wing = 4.dp.toPx()
    for (i in 0 until count) {
        val x = span - ((hash(i, 51) * span + travel * 0.2f + time * (12f + 6f * hash(i, 53))) % span) - 20.dp.toPx()
        val y = size.height * (0.12f + 0.22f * hash(i, 57))
        val flap = sin(time * 10f + i * 2f) * wing * 0.6f
        drawLine(color, Offset(x - wing, y - flap), Offset(x, y), strokeWidth = 1.3.dp.toPx(), cap = StrokeCap.Round)
        drawLine(color, Offset(x, y), Offset(x + wing, y - flap), strokeWidth = 1.3.dp.toPx(), cap = StrokeCap.Round)
    }
}

/** Darkens scenery colors at night and dusk so they match the sky. */
private fun SkyState.shade(c: Color): Color = when (time) {
    TimeOfDay.DAY -> c
    TimeOfDay.DAWN, TimeOfDay.DUSK -> lerp(c, Color(0xFF2B2D5C), 0.3f)
    TimeOfDay.NIGHT -> lerp(c, Color(0xFF0B1026), 0.6f)
}

private val SkyState.dark get() = time == TimeOfDay.NIGHT || time == TimeOfDay.DUSK

// ---- City ----

private fun DrawScope.city(travel: Float, time: Float, sky: SkyState) {
    sky(sky, travel, time)
    if (sky.clearDay) birds(travel, time, Color(0xFF34495E))
    // Two building layers with parallax (the far one moves slower).
    val (far, near) = sky.buildingColors()
    skyline(offset = travel * 0.6f, baseY = size.height * 0.78f, maxH = size.height * 0.45f, color = far, seed = 7)
    skyline(offset = travel * 1.4f, baseY = size.height * RoadLevel, maxH = size.height * 0.6f, color = near, seed = 3, windows = sky.time != TimeOfDay.DAY, time = time)
    road(travel, Color(0xFF15172A), Color(0x99FFFFFF))
}

/** Repeating "random but stable" row of buildings (pseudorandom by index). */
private fun DrawScope.skyline(offset: Float, baseY: Float, maxH: Float, color: Color, seed: Int, windows: Boolean = false, time: Float = 0f) {
    row(offset, 22.dp) { left, idx ->
        val h = maxH * (0.35f + 0.65f * hash(idx, seed))
        drawRect(color, topLeft = Offset(left, baseY - h), size = Size(20.dp.toPx(), h))
        if (windows) {
            // Some lit windows that blink slowly.
            val lit = hash(idx, seed + 11) > 0.45f && sin(time * 0.7f + idx) > -0.6f
            if (lit) drawRect(Color(0xFFFFD27A).copy(alpha = 0.7f), topLeft = Offset(left + 5.dp.toPx(), baseY - h + 5.dp.toPx()), size = Size(4.dp.toPx(), 4.dp.toPx()))
        }
    }
}

// ---- Pixel art ----

/** 8-bit city: banded sky, blocky sun/moon, twinkling cross stars, stepping clouds and blinking windows. */
private fun DrawScope.pixelCity(travel: Float, time: Float, sky: SkyState) {
    val p = 3.dp.toPx().roundToInt().toFloat()
    fun snap(v: Float) = (v / p).roundToInt() * p
    val roadY = snap(size.height * RoadLevel)
    // Sky in flat bands.
    val colors = sky.colors()
    val bands = 6
    val bandH = snap(roadY / bands) + p
    for (i in 0 until bands) drawRect(gradientAt(colors, i / (bands - 1f)), topLeft = Offset(0f, i * bandH), size = Size(size.width, bandH))
    // Blocky sun or moon (crescent).
    if (sky.showSun || sky.showMoon) {
        val c = Offset(snap(size.width * 0.62f), snap(size.height * if (sky.time == TimeOfDay.DAY) 0.28f else 0.5f))
        val r = 3
        for (dy in -r..r) for (dx in -r..r) {
            if (dx * dx + dy * dy > r * r + 1) continue
            if (sky.showMoon && (dx - 2) * (dx - 2) + (dy + 1) * (dy + 1) <= r * r - 2) continue
            drawRect(if (sky.showMoon) Color(0xFFE8EAF6) else Color(0xFFFFE066), topLeft = c + Offset(dx * p, dy * p), size = Size(p, p))
        }
    }
    if (sky.dark) {
        // Stars cycle dot → plus → big plus → off.
        for (i in 0 until 12) {
            val stage = ((time * 3f).toInt() + i * 3) % 5
            if (stage > 2) continue
            val s = Offset(snap(size.width * hash(i, 41)), snap(size.height * 0.55f * hash(i, 43)))
            drawRect(Color.White, topLeft = s, size = Size(p, p))
            for (arm in 1..stage) {
                listOf(Offset(arm * p, 0f), Offset(-arm * p, 0f), Offset(0f, arm * p), Offset(0f, -arm * p)).forEach {
                    drawRect(Color.White.copy(alpha = 0.8f), topLeft = s + it, size = Size(p, p))
                }
            }
        }
    }
    // Clouds that move one pixel at a time.
    val cloud = if (sky.dark) Color(0xFF4A5470) else Color.White
    val span = size.width + 40f * p
    for (i in 0 until if (sky.condition in SkyState.OVERCAST) 5 else 3) {
        val x = snap(span - ((hash(i, 61) * span + travel * 0.3f + time * 6f) % span) - 20f * p)
        val y = snap(size.height * (0.1f + 0.25f * hash(i, 67)))
        drawRect(cloud, topLeft = Offset(x + 2 * p, y), size = Size(4 * p, p))
        drawRect(cloud, topLeft = Offset(x + p, y + p), size = Size(7 * p, p))
        drawRect(cloud, topLeft = Offset(x, y + 2 * p), size = Size(9 * p, p))
    }
    // Two-frame birds.
    if (sky.clearDay) for (i in 0 until 3) {
        val x = snap(span - ((hash(i, 51) * span + time * 14f) % span) - 10f * p)
        val y = snap(size.height * (0.12f + 0.2f * hash(i, 57)))
        val up = ((time * 6f).toInt() + i) % 2 == 0
        drawRect(Color(0xFF263238), topLeft = Offset(x, y), size = Size(p, p))
        drawRect(Color(0xFF263238), topLeft = Offset(x - p, y + if (up) -p else p * 0), size = Size(p, p))
        drawRect(Color(0xFF263238), topLeft = Offset(x + p, y + if (up) -p else p * 0), size = Size(p, p))
    }
    // Blocky skylines; the near one has windows that blink at night.
    val (far, near) = sky.buildingColors()
    for ((layer, color) in listOf(0.6f to far, 1.4f to near)) {
        val seed = if (layer < 1f) 7 else 3
        val maxH = size.height * if (layer < 1f) 0.45f else 0.58f
        row(travel * layer, 24.dp) { left, idx ->
            val x = snap(left)
            val h = snap(maxH * (0.35f + 0.65f * hash(idx, seed)))
            val w = 7 * p
            drawRect(color, topLeft = Offset(x, roadY - h), size = Size(w, h))
            if (layer > 1f) {
                var wy = roadY - h + p
                while (wy < roadY - 2 * p) {
                    for (wx in listOf(x + p, x + 3 * p, x + 5 * p)) {
                        val n = idx * 31 + (wy / p).toInt() * 7 + (wx / p).toInt()
                        val lit = if (sky.dark) hash(n, 13) > 0.5f && ((time * 0.5f).toInt() + n) % 7 != 0 else false
                        drawRect(if (lit) Color(0xFFFFD27A) else lerp(color, Color.Black, 0.25f), topLeft = Offset(wx, wy), size = Size(p, p))
                    }
                    wy += 2 * p
                }
            }
        }
    }
    // Flat road with blocky dashes.
    drawRect(Color(0xFF2B2B3B), topLeft = Offset(0f, roadY), size = Size(size.width, size.height - roadY))
    drawRect(Color(0xFF4A4A60), topLeft = Offset(0f, roadY), size = Size(size.width, p))
    val dash = 8 * p
    var x = -snap((travel * 3f) % dash)
    while (x < size.width) {
        drawRect(Color(0xFFFFE066), topLeft = Offset(x, roadY + 2 * p), size = Size(3 * p, p))
        x += dash
    }
}

/** Color at [t] (0..1) along an evenly spaced gradient. */
private fun gradientAt(colors: List<Color>, t: Float): Color {
    val pos = t.coerceIn(0f, 1f) * (colors.size - 1)
    val i = pos.toInt().coerceAtMost(colors.size - 2)
    return lerp(colors[i], colors[i + 1], pos - i)
}

// ---- Countryside ----

private fun DrawScope.countryside(travel: Float, time: Float, sky: SkyState) {
    sky(sky, travel, time)
    if (sky.clearDay) birds(travel, time, Color(0xFF34495E))
    ridge(travel * 0.3f, size.height * 0.62f, size.height * 0.25f, 60.dp, sky.shade(Color(0xFF8DB580)), seed = 1)
    windmill(travel * 0.3f, time, sky)
    ridge(travel * 0.8f, size.height * 0.76f, size.height * 0.2f, 40.dp, sky.shade(Color(0xFF5E9B4F)), seed = 2)
    val roadY = size.height * RoadLevel
    // Round trees swaying gently.
    row(travel * 1.6f, 34.dp) { x, idx ->
        if (hash(idx, 5) < 0.35f) return@row
        val r = (4.5f + 2.5f * hash(idx, 6)).dp.toPx()
        val sway = sin(time * 1.5f + idx) * 0.6.dp.toPx()
        drawRect(sky.shade(Color(0xFF6D4C41)), topLeft = Offset(x - 1.dp.toPx(), roadY - 2 * r), size = Size(2.dp.toPx(), 2 * r))
        val crown = Offset(x + sway, roadY - 2 * r - r * 0.4f)
        drawCircle(sky.shade(Color(0xFF2E7D32)), radius = r, center = crown)
        drawCircle(sky.shade(Color(0xFF43A047)), radius = r * 0.6f, center = crown - Offset(r * 0.3f, r * 0.3f))
    }
    // Wooden fence along the road.
    val fence = sky.shade(Color(0xFF8D6E63))
    drawLine(fence, Offset(0f, roadY - 2.dp.toPx()), Offset(size.width, roadY - 2.dp.toPx()), strokeWidth = 1.dp.toPx())
    drawLine(fence, Offset(0f, roadY - 4.dp.toPx()), Offset(size.width, roadY - 4.dp.toPx()), strokeWidth = 1.dp.toPx())
    row(travel * 3f, 12.dp) { x, _ -> drawLine(fence, Offset(x, roadY), Offset(x, roadY - 5.5.dp.toPx()), strokeWidth = 1.5.dp.toPx()) }
    road(travel, Color(0xFF3A3D45), Color(0x99FFFFFF), edge = sky.shade(Color(0xFF7CB342)))
}

/** Windmill on the far hills with spinning blades. */
private fun DrawScope.windmill(offset: Float, time: Float, sky: SkyState) {
    val span = size.width + 80.dp.toPx()
    val x = span - ((offset + span * 0.3f) % span) - 40.dp.toPx()
    val base = size.height * 0.62f
    val hub = Offset(x, base - 16.dp.toPx())
    val tower = Path().apply {
        moveTo(x - 3.dp.toPx(), base)
        lineTo(x - 1.5.dp.toPx(), hub.y)
        lineTo(x + 1.5.dp.toPx(), hub.y)
        lineTo(x + 3.dp.toPx(), base)
        close()
    }
    drawPath(tower, sky.shade(Color(0xFFF5F5F5)))
    rotate(time * 70f, pivot = hub) {
        for (k in 0 until 4) {
            val a = k * (Math.PI / 2).toFloat()
            drawLine(sky.shade(Color(0xFFECEFF1)), hub, hub + Offset(cos(a), sin(a)) * 9.dp.toPx(), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
        }
    }
    drawCircle(sky.shade(Color(0xFF90A4AE)), radius = 1.2.dp.toPx(), center = hub)
}

// ---- Beach ----

private fun DrawScope.beach(travel: Float, time: Float, sky: SkyState) {
    sky(sky, travel, time)
    val seaTop = size.height * 0.56f
    val roadY = size.height * RoadLevel
    drawRect(Brush.verticalGradient(listOf(sky.shade(Color(0xFF1E88E5)), sky.shade(Color(0xFF4FC3F7))), startY = seaTop, endY = roadY), topLeft = Offset(0f, seaTop), size = Size(size.width, roadY - seaTop))
    // Sun glitter and rolling wave lines.
    for (i in 0 until 10) {
        val a = 0.5f + 0.5f * sin(time * 3f + i * 1.7f)
        val gx = size.width * (0.5f + 0.25f * hash(i, 71))
        val gy = seaTop + (roadY - seaTop) * 0.6f * hash(i, 73)
        drawLine(Color.White.copy(alpha = 0.5f * a), Offset(gx, gy), Offset(gx + 4.dp.toPx(), gy), strokeWidth = 1.dp.toPx())
    }
    for (r in 0..2) {
        val y = seaTop + (r + 1) * (roadY - seaTop) / 4.5f
        val wave = Path().apply {
            var x = 0f
            moveTo(0f, y)
            while (x <= size.width) {
                lineTo(x, y + sin((x + travel * (0.4f + r * 0.3f)) / 10.dp.toPx() + time * 2f + r * 1.7f) * 1.dp.toPx())
                x += 3.dp.toPx()
            }
        }
        drawPath(wave, Color.White.copy(alpha = 0.35f), style = Stroke(1.dp.toPx()))
    }
    // Sailboat on the horizon.
    val span = size.width + 60.dp.toPx()
    val bx = span - ((travel * 0.2f + time * 4f) % span) - 30.dp.toPx()
    val by = seaTop + 2.dp.toPx() + sin(time * 2f) * 0.5.dp.toPx()
    val boat = Path().apply {
        moveTo(bx - 6.dp.toPx(), by); lineTo(bx + 6.dp.toPx(), by); lineTo(bx + 4.dp.toPx(), by + 2.5.dp.toPx()); lineTo(bx - 4.dp.toPx(), by + 2.5.dp.toPx()); close()
        moveTo(bx, by - 0.5.dp.toPx()); lineTo(bx, by - 11.dp.toPx()); lineTo(bx + 6.dp.toPx(), by - 1.dp.toPx()); close()
    }
    drawPath(boat, sky.shade(Color(0xFFFAFAFA)))
    if (sky.clearDay) birds(travel, time, Color.White, count = 4)
    // Sand and palms swaying in the breeze.
    drawRect(sky.shade(Color(0xFFF2D49B)), topLeft = Offset(0f, roadY - 4.dp.toPx()), size = Size(size.width, 4.dp.toPx()))
    row(travel * 1.8f, 48.dp) { x, idx ->
        if (hash(idx, 9) < 0.3f) return@row
        val foot = Offset(x, roadY - 2.dp.toPx())
        val top = Offset(x + 4.dp.toPx(), roadY - (20f + 5f * hash(idx, 10)).dp.toPx())
        val trunk = Path().apply { moveTo(foot.x, foot.y); quadraticTo(foot.x - 1.dp.toPx(), (foot.y + top.y) / 2, top.x, top.y) }
        drawPath(trunk, sky.shade(Color(0xFF8D6E63)), style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round))
        val sway = sin(time * 1.6f + idx) * 1.5.dp.toPx()
        for (k in 0 until 6) {
            val a = (-160f + k * 28f) * (Math.PI / 180).toFloat()
            val len = 9.dp.toPx()
            val tip = top + Offset(cos(a) * len + sway, sin(a) * len * 0.6f + len * 0.35f)
            val frond = Path().apply { moveTo(top.x, top.y); quadraticTo(top.x + cos(a) * len * 0.5f + sway * 0.5f, top.y + sin(a) * len * 0.7f - 2.dp.toPx(), tip.x, tip.y) }
            drawPath(frond, sky.shade(Color(0xFF388E3C)), style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round))
        }
        drawCircle(sky.shade(Color(0xFF5D4037)), radius = 1.2.dp.toPx(), center = top + Offset(1.dp.toPx(), 1.5.dp.toPx()))
    }
    road(travel, Color(0xFF3B3F4A), Color(0x99FFFFFF))
}

// ---- Desert ----

private fun DrawScope.desert(travel: Float, time: Float, sky: SkyState) {
    val warm = sky.colors().map { if (sky.time == TimeOfDay.NIGHT) it else lerp(it, Color(0xFFFFB74D), 0.18f) }
    sky(sky, travel, time, warm)
    val horizon = size.height * 0.66f
    // Flat-topped mesas far away.
    row(travel * 0.25f, 90.dp) { x, idx ->
        if (hash(idx, 13) < 0.4f) return@row
        val w = (40f + 20f * hash(idx, 14)).dp.toPx()
        val h = (12f + 8f * hash(idx, 15)).dp.toPx()
        val mesa = Path().apply { moveTo(x, horizon); lineTo(x + w * 0.2f, horizon - h); lineTo(x + w * 0.8f, horizon - h); lineTo(x + w, horizon); close() }
        drawPath(mesa, sky.shade(Color(0xFFB5653F)))
    }
    ridge(travel * 0.5f, size.height * 0.72f, size.height * 0.12f, 50.dp, sky.shade(Color(0xFFE0B877)), seed = 3)
    // Heat haze shimmering over the dunes in daylight.
    if (sky.clearDay) for (k in 0..2) {
        val y = horizon + k * 2.dp.toPx()
        val haze = Path().apply {
            var x = 0f
            moveTo(0f, y)
            while (x <= size.width) {
                lineTo(x, y + sin(x / 6.dp.toPx() + time * 6f + k) * 0.8.dp.toPx())
                x += 3.dp.toPx()
            }
        }
        drawPath(haze, Color.White.copy(alpha = 0.15f), style = Stroke(1.dp.toPx()))
    }
    ridge(travel * 1.0f, size.height * 0.8f, size.height * 0.1f, 36.dp, sky.shade(Color(0xFFD9A55B)), seed = 4)
    val roadY = size.height * RoadLevel
    // Saguaro cacti.
    val cactus = sky.shade(Color(0xFF4E8D4A))
    row(travel * 1.6f, 44.dp) { x, idx ->
        if (hash(idx, 17) < 0.45f) return@row
        val h = (14f + 5f * hash(idx, 18)).dp.toPx()
        val w = 3.5.dp.toPx()
        val r = CornerRadius(w / 2)
        drawRoundRect(cactus, topLeft = Offset(x, roadY - h), size = Size(w, h), cornerRadius = r)
        drawRoundRect(cactus, topLeft = Offset(x - 3.dp.toPx(), roadY - h * 0.55f), size = Size(4.dp.toPx(), w * 0.8f), cornerRadius = r)
        drawRoundRect(cactus, topLeft = Offset(x - 3.dp.toPx(), roadY - h * 0.85f), size = Size(w * 0.8f, h * 0.35f), cornerRadius = r)
        drawRoundRect(cactus, topLeft = Offset(x + w - 1.dp.toPx(), roadY - h * 0.45f), size = Size(4.dp.toPx(), w * 0.8f), cornerRadius = r)
        drawRoundRect(cactus, topLeft = Offset(x + w + 0.5.dp.toPx(), roadY - h * 0.7f), size = Size(w * 0.8f, h * 0.3f), cornerRadius = r)
    }
    road(travel, Color(0xFF6D5A48), Color(0x88FFE0B2))
    // Tumbleweed bouncing along the road.
    val span = size.width + 40.dp.toPx()
    val roll = travel * 3.4f + time * 25f
    val c = Offset(span - (roll % span) - 10.dp.toPx(), roadY - 3.dp.toPx() - abs(sin(time * 5f)) * 3.dp.toPx())
    val weed = sky.shade(Color(0xFFA1887F))
    rotate(-roll * 4f, pivot = c) {
        drawCircle(weed, radius = 3.dp.toPx(), center = c, style = Stroke(1.dp.toPx()))
        for (k in 0 until 3) {
            val a = k * (Math.PI / 3).toFloat()
            val d = Offset(cos(a), sin(a)) * 3.dp.toPx()
            drawLine(weed, c - d, c + d, strokeWidth = 0.8.dp.toPx())
        }
    }
}

// ---- Snow ----

private fun DrawScope.snow(travel: Float, time: Float, sky: SkyState) {
    sky(sky, travel, time)
    val base = size.height * 0.7f
    // Snow-capped mountains.
    row(travel * 0.25f, 70.dp) { x, idx ->
        val h = size.height * (0.3f + 0.2f * hash(idx, 19))
        val half = 40.dp.toPx()
        val peak = Offset(x + half, base - h)
        drawPath(Path().apply { moveTo(x, base); lineTo(peak.x, peak.y); lineTo(x + 2 * half, base); close() }, sky.shade(Color(0xFF7986A8)))
        val cap = 0.35f
        drawPath(
            Path().apply {
                moveTo(peak.x - half * cap, peak.y + h * cap)
                lineTo(peak.x, peak.y)
                lineTo(peak.x + half * cap, peak.y + h * cap)
                lineTo(peak.x + half * cap * 0.3f, peak.y + h * cap * 0.8f)
                lineTo(peak.x - half * cap * 0.2f, peak.y + h * cap)
                close()
            },
            sky.shade(Color.White),
        )
    }
    ridge(travel * 0.7f, size.height * 0.78f, size.height * 0.14f, 50.dp, sky.shade(Color(0xFFE8F1FA)), seed = 5)
    val roadY = size.height * RoadLevel
    // Pines with snow on every tier.
    row(travel * 1.6f, 26.dp) { x, idx ->
        if (hash(idx, 23) < 0.3f) return@row
        val h = (16f + 6f * hash(idx, 24)).dp.toPx()
        val w = h * 0.55f
        for (t in 0 until 3) {
            val tierTop = roadY - h + t * h * 0.25f
            val tierW = w * (0.5f + 0.25f * t)
            drawPath(Path().apply { moveTo(x, tierTop); lineTo(x + tierW / 2, tierTop + h * 0.4f); lineTo(x - tierW / 2, tierTop + h * 0.4f); close() }, sky.shade(Color(0xFF2E5E4E)))
            drawPath(Path().apply { moveTo(x, tierTop); lineTo(x + tierW / 5, tierTop + h * 0.15f); lineTo(x - tierW / 5, tierTop + h * 0.15f); close() }, sky.shade(Color.White))
        }
        drawRect(sky.shade(Color(0xFF5D4037)), topLeft = Offset(x - 1.dp.toPx(), roadY - h * 0.2f), size = Size(2.dp.toPx(), h * 0.2f))
    }
    road(travel, Color(0xFF4A5060), Color(0x99FFFFFF), edge = sky.shade(Color.White))
    // Light snowfall always (the weather adds its own heavier snow).
    if (sky.condition != WeatherCondition.SNOW) for (i in 0 until 22) {
        val p = (time * (0.18f + 0.12f * hash(i, 25)) + hash(i, 27)) % 1f
        val x = size.width * hash(i, 29) + sin(time * 1.5f + i) * 5.dp.toPx() - travel * 0.5f % size.width
        drawCircle(Color.White.copy(alpha = 0.8f), radius = 1.3f, center = Offset(((x % size.width) + size.width) % size.width, p * size.height))
    }
}

// ---- Jungle ----

private fun DrawScope.jungle(travel: Float, time: Float, sky: SkyState) {
    sky(sky, travel, time)
    if (sky.clearDay) birds(travel, time, Color(0xFFE53935), count = 2)
    ridge(travel * 0.3f, size.height * 0.6f, size.height * 0.22f, 22.dp, sky.shade(Color(0xFF2E7D32)), seed = 6)
    ridge(travel * 0.8f, size.height * 0.74f, size.height * 0.18f, 16.dp, sky.shade(Color(0xFF1B5E20)), seed = 7)
    val roadY = size.height * RoadLevel
    // Fireflies at dusk and night.
    if (sky.dark) for (i in 0 until 14) {
        val glow = 0.5f + 0.5f * sin(time * 3f + i * 1.3f)
        val c = Offset(
            size.width * hash(i, 31) + sin(time * 0.7f + i) * 10.dp.toPx(),
            size.height * (0.3f + 0.4f * hash(i, 33)) + cos(time * 0.9f + i) * 4.dp.toPx(),
        )
        drawCircle(Brush.radialGradient(listOf(Color(0xAAFFF176).copy(alpha = 0.7f * glow), Color.Transparent), center = c, radius = 4.dp.toPx()), radius = 4.dp.toPx(), center = c)
        drawCircle(Color(0xFFFFF59D).copy(alpha = glow), radius = 1.dp.toPx(), center = c)
    }
    // Bushes along the road.
    row(travel * 1.8f, 18.dp) { x, idx ->
        val r = (4f + 2f * hash(idx, 35)).dp.toPx()
        drawCircle(sky.shade(Color(0xFF145A1E)), radius = r, center = Offset(x, roadY - r * 0.4f))
        drawCircle(sky.shade(Color(0xFF2E7D32)), radius = r * 0.5f, center = Offset(x - r * 0.3f, roadY - r * 0.8f))
    }
    road(travel, Color(0xFF5D4037), null)
    drawLine(Color(0x44000000), Offset(0f, roadY + 4.dp.toPx()), Offset(size.width, roadY + 4.dp.toPx()), strokeWidth = 1.dp.toPx())
    drawLine(Color(0x44000000), Offset(0f, roadY + 8.dp.toPx()), Offset(size.width, roadY + 8.dp.toPx()), strokeWidth = 1.dp.toPx())
    // Canopy overhead with swaying vines.
    row(travel * 1.2f, 36.dp) { x, idx ->
        if (hash(idx, 37) < 0.4f) return@row
        val len = (12f + 12f * hash(idx, 38)).dp.toPx()
        val sway = sin(time * 1.3f + idx) * 3.dp.toPx()
        val vine = Path().apply { moveTo(x, 0f); quadraticTo(x + sway * 0.5f, len * 0.5f, x + sway, len) }
        drawPath(vine, sky.shade(Color(0xFF33691E)), style = Stroke(1.2.dp.toPx(), cap = StrokeCap.Round))
        for (k in 1..3) drawCircle(sky.shade(Color(0xFF558B2F)), radius = 1.5.dp.toPx(), center = Offset(x + sway * k / 4f, len * k / 4f))
    }
    row(travel * 1.2f, 14.dp) { x, idx ->
        drawCircle(sky.shade(Color(0xFF0F4D19)), radius = (6f + 3f * hash(idx, 39)).dp.toPx(), center = Offset(x, -2.dp.toPx()))
    }
}

// ---- Space ----

/** Deep space with stars at three depths; the faster, the longer the trails ("warp" effect). */
internal fun DrawScope.space(travel: Float, kmh: Float) {
    drawRect(Brush.horizontalGradient(listOf(Color(0xFF05061A), Color(0xFF1A0B3D), Color(0xFF070A26))))
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
}

// ---- Sky by time and weather ----

internal enum class TimeOfDay {
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

internal data class SkyState(val time: TimeOfDay, val condition: org.opencarproject.launcher.core.model.WeatherCondition?) {
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

internal fun currentHour(): Int = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)

/** Sun (low at sunrise/sunset) or moon with stars. */
internal fun DrawScope.celestial(sky: SkyState, time: Float) {
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
internal fun DrawScope.clouds(sky: SkyState, travel: Float, time: Float) {
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
internal fun DrawScope.precipitation(sky: SkyState, time: Float) {
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

