package org.opencarproject.launcher.presentation.grid

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import org.opencarproject.launcher.core.model.Cell
import org.opencarproject.launcher.core.model.DragPhase
import org.opencarproject.launcher.core.model.GridSpec
import org.opencarproject.launcher.core.model.LauncherUiState
import org.opencarproject.launcher.core.model.LayoutMode
import org.opencarproject.launcher.core.model.PlacedItem
import org.opencarproject.launcher.core.pure.GridMath
import org.opencarproject.launcher.core.ui.theme.Dimens
import org.opencarproject.launcher.data.widget.LauncherAppWidgetHost
import org.opencarproject.launcher.presentation.launcher.LauncherViewModel
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/** Repositioning spring: fast but with a slight bounce when settling. */
private val PlacementSpring = spring<Offset>(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)
private val SizeSpring = spring<Dp>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)

/**
 * Home-screen grid. During a gesture it shows the final rearrangement live: neighbors that
 * would be pushed move aside while you drag (same [GridMath.placeWithPush] used by
 * MoveItem/ResizeItem, so what you see is what you get). All positions and sizes are
 * spring-animated; the card you move follows the finger and settles into its cell on release.
 */
@Composable
fun WidgetGrid(
    state: LauncherUiState,
    widgetHost: LauncherAppWidgetHost?,
    viewModel: LauncherViewModel,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("widget-grid")
            .then(
                if (state.layoutMode == LayoutMode.NORMAL) {
                    Modifier.longPressAnywhere {
                        view.haptic(EditHaptic.PICK_UP)
                        viewModel.enterEditMode()
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        val columns = state.grid.columns.coerceAtLeast(1)
        val rows = state.grid.rows.coerceAtLeast(1)
        val cellWidthDp: Dp = maxWidth / columns
        val cellHeightDp: Dp = maxHeight / rows
        val gapDp = Dimens.CellGap
        val density = LocalDensity.current
        val cellWidthPx = with(density) { cellWidthDp.toPx() }
        val cellHeightPx = with(density) { cellHeightDp.toPx() }

        // Finger offset (px) for the card being moved; cleared when done.
        var dragOffset by remember { mutableStateOf(Offset.Zero) }
        LaunchedEffect(state.dragPhase) { if (state.dragPhase == DragPhase.IDLE) dragOffset = Offset.Zero }

        val active = state.items.find { it.id == state.activeItemId }
        val preview: List<PlacedItem>? = remember(state.items, state.dragPhase, state.previewCell, state.previewSpan) {
            when {
                active == null -> state.items
                state.dragPhase == DragPhase.MOVING && state.previewCell != null ->
                    GridMath.placeWithPush(state.items, active.copy(cell = state.previewCell), state.grid)
                state.dragPhase == DragPhase.RESIZING && state.previewSpan != null ->
                    GridMath.placeWithPush(state.items, active.copy(span = state.previewSpan), state.grid)
                else -> state.items
            }
        }
        // No room for the displaced items: the gesture will be rejected on release.
        val invalid = preview == null
        val shown = (preview ?: state.items).associateBy { it.id }

        AnimatedVisibility(state.layoutMode == LayoutMode.EDIT, enter = fadeIn(), exit = fadeOut()) {
            GridDots(state.grid, cellWidthDp, cellHeightDp)
        }

        if (state.dragPhase == DragPhase.MOVING && active != null && state.previewCell != null) {
            DropSlot(state.previewCell, active.span, invalid, cellWidthDp, cellHeightDp, gapDp)
        }

        state.items.forEach { item ->
            val moving = state.dragPhase == DragPhase.MOVING && item.id == state.activeItemId
            val resizing = state.dragPhase == DragPhase.RESIZING && item.id == state.activeItemId
            val placed = shown.getValue(item.id)
            // The moving card stays anchored to its origin + finger; the rest, to the preview.
            val cell = if (moving) item.cell else placed.cell
            val span = if (resizing) state.previewSpan ?: item.span else placed.span

            val target = Offset(cellWidthPx * cell.column, cellHeightPx * cell.row) +
                if (moving) dragOffset else Offset.Zero

            // All animated state inside key(): it follows the card even if the order changes.
            key(item.id) {
                val position = remember { Animatable(target, Offset.VectorConverter) }
                LaunchedEffect(target, moving) {
                    if (moving) position.snapTo(target) else position.animateTo(target, PlacementSpring)
                }
                val width by animateDpAsState(cellWidthDp * span.column - gapDp, SizeSpring, label = "w")
                val height by animateDpAsState(cellHeightDp * span.row - gapDp, SizeSpring, label = "h")

                PlacedItemCard(
                    item = item,
                    span = span,
                    grid = state.grid,
                    cellWidthPx = cellWidthPx,
                    cellHeightPx = cellHeightPx,
                    layoutMode = state.layoutMode,
                    lifted = moving || resizing,
                    invalid = invalid && (moving || resizing),
                    onDragOffset = { dragOffset = it },
                    widgetHost = widgetHost,
                    viewModel = viewModel,
                    modifier = Modifier
                        .testTag("placed-${item.id}")
                        .zIndex(if (moving || resizing) 1f else 0f)
                        .offset { IntOffset(position.value.x.roundToInt(), position.value.y.roundToInt()) }
                        .size(width, height),
                )
            }
        }
    }
}

/** Target slot under the dragged card (red if the rearrangement doesn't fit). */
@Composable
private fun DropSlot(cell: Cell, span: Cell, invalid: Boolean, cellWidthDp: Dp, cellHeightDp: Dp, gapDp: Dp) {
    val x by animateDpAsState(cellWidthDp * cell.column, SizeSpring, label = "slotX")
    val y by animateDpAsState(cellHeightDp * cell.row, SizeSpring, label = "slotY")
    val color = if (invalid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(Dimens.ItemCorner)
    Box(
        Modifier
            .offset(x, y)
            .size(cellWidthDp * span.column - gapDp, cellHeightDp * span.row - gapDp)
            .background(color.copy(alpha = 0.12f), shape)
            .border(2.dp, color.copy(alpha = 0.7f), shape),
    )
}

/** Dots hinting at the grid in edit mode. */
@Composable
private fun GridDots(grid: GridSpec, cellWidthDp: Dp, cellHeightDp: Dp) {
    Box(Modifier.fillMaxSize()) {
        for (row in 0 until grid.rows) {
            for (column in 0 until grid.columns) {
                Box(
                    Modifier
                        .offset(
                            x = cellWidthDp * column + cellWidthDp / 2 - 3.dp,
                            y = cellHeightDp * row + cellHeightDp / 2 - 3.dp,
                        )
                        .size(6.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), CircleShape),
                )
            }
        }
    }
}

/**
 * Long press anywhere (cards, their buttons, system widgets or empty slots).
 * Observes in the `Initial` pass, before the children, without consuming: a normal tap reaches them
 * intact. Only if the finger stays still for the long-press timeout does the action fire and
 * the rest of the gesture get consumed, so the child doesn't also receive a click.
 */
private fun Modifier.longPressAnywhere(onLongPress: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val released = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes
                    .firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull true
                if (!change.pressed) return@withTimeoutOrNull true
                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                    return@withTimeoutOrNull true
                }
            }
            @Suppress("UNREACHABLE_CODE")
            true
        }
        if (released == null) {
            onLongPress()
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        }
    }
}
