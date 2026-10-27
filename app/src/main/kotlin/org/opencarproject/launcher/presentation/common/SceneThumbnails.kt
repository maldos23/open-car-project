package org.opencarproject.launcher.presentation.common

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import org.opencarproject.launcher.core.model.SceneTheme
import org.opencarproject.launcher.core.model.VehicleShape

/** Still preview of a silhouette for the settings catalog, centered on a thin ground line. */
@Composable
fun VehicleThumbnail(shape: VehicleShape, color: Color, ground: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val groundY = size.height * RoadLevel
        drawLine(ground, Offset(0f, groundY), Offset(size.width, groundY), strokeWidth = 1.dp.toPx())
        // Enlarged to fill the card (1.4x still fits the tallest pets).
        val length = actorLength(shape) * 6.dp.toPx()
        val zoom = (size.width * 0.88f / length).coerceAtMost(1.4f)
        scale(zoom, pivot = Offset(size.width / 2, groundY)) {
            actor(shape, Offset((size.width - length) / 2, groundY), travel = 0f, time = 0f, kmh = 0f, color = color)
        }
    }
}

/** Still preview of a scenery for the settings catalog, under a clear daytime sky. */
@Composable
fun SceneThumbnail(theme: SceneTheme, modifier: Modifier = Modifier) {
    Canvas(modifier) { backdrop(theme, travel = 0f, time = 0f, sky = PreviewSky) }
}

private val PreviewSky = SkyState(TimeOfDay.DAY, null)
