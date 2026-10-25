package org.opencarproject.launcher.presentation.common

import org.opencarproject.launcher.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.opencarproject.launcher.core.model.DockIconSize

/** Width each shortcut takes in the bar (cell + spacing). */
fun DockIconSize.slotWidth(): Dp = (slotDp + 8).dp

/**
 * App shortcuts on one side of the bottom bar. Shows at most [capacity]
 * (what fits on that side); in edit mode, each one has an ✕ and free spaces appear
 * as outlined slots with "+". [plusFirst] puts the slots on the center side.
 */
@Composable
fun DockFavorites(
    apps: List<String>,
    capacity: Int,
    size: DockIconSize,
    editing: Boolean,
    plusFirst: Boolean,
    onLaunch: (String) -> Unit,
    onRemove: (String) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visible = apps.take(capacity)
    // In edit mode, each free space shows as an outlined slot, making the limit visible.
    val freeSlots = if (editing) (capacity - visible.size).coerceAtLeast(0) else 0
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (plusFirst) repeat(freeSlots) { EmptySlot(size, onAdd) }
        visible.forEach { key -> FavoriteIcon(key, size, editing, onLaunch, onRemove) }
        if (!plusFirst) repeat(freeSlots) { EmptySlot(size, onAdd) }
    }
}

/** Outlined free slot (dashed border + "+"); tapping it opens the app picker. */
@Composable
private fun EmptySlot(iconSize: DockIconSize, onAdd: () -> Unit) {
    val color = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .size(iconSize.slotDp.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onAdd)
            .drawBehind {
                val stroke = 2.dp.toPx()
                drawRoundRect(
                    color = color,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(14.dp.toPx()),
                    style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                )
                drawRoundRect(color = color.copy(alpha = 0.08f), cornerRadius = CornerRadius(14.dp.toPx()))
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.dock_add_shortcut), tint = color)
    }
}

@Composable
private fun FavoriteIcon(
    key: String,
    size: DockIconSize,
    editing: Boolean,
    onLaunch: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    val iconSize = size.iconDp.dp
    // Just the logo, without the adaptive icon's background shape.
    val icon = rememberAppIcon(key, sizePx = with(LocalDensity.current) { iconSize.roundToPx() }, withoutBackground = true)
    Box(Modifier.size(size.slotDp.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(size.slotDp.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable(enabled = !editing) { onLaunch(key) },
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                Image(icon, contentDescription = null, modifier = Modifier.size(iconSize))
            } else {
                Icon(Icons.Rounded.Apps, contentDescription = null, modifier = Modifier.size(iconSize))
            }
        }
        AnimatedVisibility(
            visible = editing,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            // 48 dp touch target around the 26 dp badge; offset so the badge keeps its place.
            modifier = Modifier.align(Alignment.TopEnd).offset(x = 15.dp, y = (-15).dp),
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clickable { onRemove(key) },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(26.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.dock_remove),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
