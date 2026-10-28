package org.opencarproject.launcher.presentation.grid

import org.opencarproject.launcher.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Shape
import org.opencarproject.launcher.core.model.CatalogItemKind
import org.opencarproject.launcher.core.model.Cell
import org.opencarproject.launcher.core.model.GridSpec
import org.opencarproject.launcher.core.model.LayoutMode
import org.opencarproject.launcher.core.model.ModuleShape
import org.opencarproject.launcher.core.model.PlacedItem
import org.opencarproject.launcher.core.pure.GridMath
import org.opencarproject.launcher.core.ui.ScallopedShape
import org.opencarproject.launcher.core.ui.theme.Dimens
import org.opencarproject.launcher.data.widget.LauncherAppWidgetHost
import org.opencarproject.launcher.presentation.common.rememberAppIcon
import org.opencarproject.launcher.presentation.launcher.LauncherViewModel
import org.opencarproject.launcher.presentation.widget.GlassWidgetHostView
import org.opencarproject.launcher.presentation.widget.MediaPlayerCard
import org.opencarproject.launcher.presentation.widget.NavigationCard
import org.opencarproject.launcher.presentation.widget.EmbeddedMapCard
import org.opencarproject.launcher.presentation.widget.NotificationsCard
import org.opencarproject.launcher.presentation.widget.ClimateCard
import kotlinx.coroutines.launch

/**
 * Card for a placed item (native Material 3 Card) with its gestures and micro-interactions:
 *
 * - NORMAL: tap launches the app (sinks on press). The long-press to edit is detected
 *   by [WidgetGrid] so it works anywhere.
 * - EDIT: cards shrink slightly (they are "loose"); dragging lifts it (scale +
 *   shadow) and follows the finger, with haptics on pickup, on every cell change and on drop
 *   (or a rejection buzz if it doesn't fit). The handle resizes while showing the resulting size;
 *   the "x" fades it out before removing it. On appearing, it enters with a small "pop".
 *
 * Gestures read the current state via [rememberUpdatedState]: `pointerInput` doesn't restart
 * when the card moves, so capturing `item` directly would use its original cell.
 */
@Composable
fun PlacedItemCard(
    item: PlacedItem,
    span: Cell,
    grid: GridSpec,
    cellWidthPx: Float,
    cellHeightPx: Float,
    layoutMode: LayoutMode,
    lifted: Boolean,
    invalid: Boolean,
    onDragOffset: (Offset) -> Unit,
    widgetHost: LauncherAppWidgetHost?,
    viewModel: LauncherViewModel,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val editing = layoutMode == LayoutMode.EDIT
    val current by rememberUpdatedState(item)
    val currentGrid by rememberUpdatedState(grid)
    val cellW by rememberUpdatedState(cellWidthPx)
    val cellH by rememberUpdatedState(cellHeightPx)
    val isInvalid by rememberUpdatedState(invalid)

    // Enter ("pop") and exit (on removal).
    val presence = remember(item.id) { Animatable(0f) }
    LaunchedEffect(item.id) { presence.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow)) }
    val scope = rememberCoroutineScope()

    val pressSource = remember { MutableInteractionSource() }
    val pressed by pressSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = when {
            lifted -> 1.04f
            pressed -> 0.96f
            editing -> 0.97f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "scale",
    )
    val elevation by animateDpAsState(if (lifted) 18.dp else 0.dp, label = "elevation")
    val errorBorder by animateFloatAsState(if (invalid) 1f else 0f, label = "invalid")

    val moveGesture = Modifier.pointerInput(item.id) {
        var accum = Offset.Zero
        var lastCell: Cell? = null
        detectDragGestures(
            onDragStart = {
                accum = Offset.Zero
                lastCell = current.cell
                view.haptic(EditHaptic.PICK_UP)
                viewModel.onMoveStart(current.id)
            },
            onDrag = { change, amount ->
                change.consume()
                accum += amount
                onDragOffset(accum)
                val target = GridMath.targetCell(
                    start = current.cell,
                    span = current.span,
                    deltaX = accum.x,
                    deltaY = accum.y,
                    cellWidth = cellW,
                    cellHeight = cellH,
                    grid = currentGrid,
                )
                if (target != lastCell) {
                    lastCell = target
                    view.haptic(EditHaptic.CELL_TICK)
                    viewModel.onMovePreview(target)
                }
            },
            onDragEnd = {
                view.haptic(if (isInvalid) EditHaptic.REJECT else EditHaptic.DROP)
                viewModel.onMoveEnd()
            },
            onDragCancel = { viewModel.onMoveCancel() },
        )
    }

    // In EDIT the drag lives in an upper layer: AndroidViews (system widgets) consume the
    // touch and would cancel detectDragGestures if the gesture were on the container.
    val tapGesture = if (editing) {
        Modifier
    } else {
        // Long-press is detected by the grid (also works over buttons and widgets).
        Modifier.clickable(
            interactionSource = pressSource,
            indication = null,
            onClick = { viewModel.onTapItem(item) },
        )
    }

    // Built-in modules take the shape chosen in Settings; widgets and apps keep their own.
    val cardShape = when (item.kind) {
        CatalogItemKind.WIDGET, CatalogItemKind.APP -> RoundedCornerShape(Dimens.ItemCorner)
        else -> LocalModuleShape.current
    }
    Box(
        modifier
            .graphicsLayer {
                val p = presence.value
                scaleX = scale * (0.85f + 0.15f * p)
                scaleY = scale * (0.85f + 0.15f * p)
                // A SurfaceView (embedded map) is its own system layer: fading the card would hide
                // it until the fade ends, so that card only scales in.
                if (item.kind != CatalogItemKind.EMBEDDED_MAP) alpha = p
                shadowElevation = elevation.toPx()
                shape = cardShape
                clip = false
            }
            .then(tapGesture),
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .border(
                    width = 3.dp,
                    color = MaterialTheme.colorScheme.error.copy(alpha = errorBorder),
                    shape = cardShape,
                ),
            shape = cardShape,
            // System widgets bring their own design: no launcher background card.
            // Apps: just icon and name (no card), like a regular launcher.
            colors = if (item.kind == CatalogItemKind.WIDGET || item.kind == CatalogItemKind.APP) {
                CardDefaults.cardColors(containerColor = Color.Transparent)
            } else {
                CardDefaults.cardColors()
            },
        ) {
            when (item.kind) {
                CatalogItemKind.WIDGET -> if (widgetHost != null) {
                    GlassWidgetHostView(
                        host = widgetHost,
                        appWidgetId = item.widgetId,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                CatalogItemKind.APP -> AppContent(item, span)
                CatalogItemKind.MAP -> NavigationCard()
                CatalogItemKind.MEDIA -> MediaPlayerCard()
                CatalogItemKind.NOTIFICATIONS -> NotificationsCard()
                CatalogItemKind.CLIMATE -> ClimateCard()
                CatalogItemKind.EMBEDDED_MAP -> {
                    val settings by viewModel.settings.collectAsStateWithLifecycle()
                    EmbeddedMapCard(settings.embeddedMapApp)
                }
            }
        }

        if (editing) {
            Box(Modifier.matchParentSize().then(moveGesture))
        }

        // Resulting size while resizing ("3 × 2" = columns × rows).
        AnimatedVisibility(
            visible = lifted && layoutMode == LayoutMode.EDIT && span != item.span,
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Text(
                text = stringResource(R.string.span_size, span.column, span.row),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }

        AnimatedVisibility(
            visible = editing,
            enter = fadeIn() + scaleIn(spring(dampingRatio = 0.55f)),
            exit = fadeOut() + scaleOut(),
            // 48 dp touch target centered on the corner (half outside the card) with a small
            // badge, so even 1×1 cards keep most of their surface free for dragging.
            modifier = Modifier.align(Alignment.TopEnd).offset(x = 20.dp, y = (-20).dp),
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                    ) {
                        view.haptic(EditHaptic.DELETE)
                        scope.launch {
                            presence.animateTo(0f, tween(180))
                            viewModel.onDeleteItem(item.id)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(24.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.delete),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = editing,
            enter = fadeIn() + scaleIn(spring(dampingRatio = 0.55f)),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.BottomEnd),
        ) {
            var lastSpan by remember { mutableStateOf<Cell?>(null) }
            ResizeHandle(
                active = lifted,
                onResizeStart = {
                    lastSpan = current.span
                    view.haptic(EditHaptic.PICK_UP)
                    viewModel.onResizeStart(current.id)
                },
                onResize = { accum ->
                    val target = GridMath.targetSpan(
                        start = current.cell,
                        startSpan = current.span,
                        deltaX = accum.x,
                        deltaY = accum.y,
                        cellWidth = cellW,
                        cellHeight = cellH,
                        grid = currentGrid,
                    )
                    if (target != lastSpan) {
                        lastSpan = target
                        view.haptic(EditHaptic.CELL_TICK)
                        viewModel.onResizePreview(target)
                    }
                },
                onResizeEnd = {
                    view.haptic(if (isInvalid) EditHaptic.REJECT else EditHaptic.DROP)
                    viewModel.onResizeEnd()
                },
                onResizeCancel = { viewModel.onResizeCancel() },
            )
        }
    }
}

/**
 * Resize handle: 48 dp touch target (for in-car use) with a corner arc that grows while
 * dragging. Reports the accumulated offset since the gesture started.
 */
@Composable
private fun ResizeHandle(
    active: Boolean,
    onResizeStart: () -> Unit,
    onResize: (Offset) -> Unit,
    onResizeEnd: () -> Unit,
    onResizeCancel: () -> Unit,
) {
    val start by rememberUpdatedState(onResizeStart)
    val move by rememberUpdatedState(onResize)
    val end by rememberUpdatedState(onResizeEnd)
    val cancel by rememberUpdatedState(onResizeCancel)
    val grow by animateFloatAsState(if (active) 1.25f else 1f, spring(dampingRatio = 0.5f), label = "handle")
    val color = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            // Centered on the corner (half outside the card), like the delete badge.
            .offset(x = 20.dp, y = 20.dp)
            .size(48.dp)
            .pointerInput(Unit) {
                var accum = Offset.Zero
                detectDragGestures(
                    onDragStart = {
                        accum = Offset.Zero
                        start()
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        accum += amount
                        move(accum)
                    },
                    onDragEnd = { end() },
                    onDragCancel = { cancel() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(16.dp).graphicsLayer { scaleX = grow; scaleY = grow }) {
            // Bottom-right corner arc (like the card's edge).
            drawArc(
                color = color,
                startAngle = 0f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(-size.width, -size.height),
                size = Size(size.width * 2, size.height * 2),
                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
            )
        }
    }
}

/** App content: icon + label. */
@Composable
private fun AppContent(item: PlacedItem, span: Cell) {
    val showLabel = LocalShowAppLabels.current
    BoxWithConstraints(Modifier.fillMaxSize().padding(4.dp), contentAlignment = Alignment.Center) {
        // The icon grows with the available space (half cell ≈ 64 dp), leaving room for the name if shown.
        val iconSize = minOf(maxWidth, maxHeight - if (showLabel) 24.dp else 0.dp).coerceIn(36.dp, 96.dp)
        // Just the logo (no background shape), like the bar shortcuts.
        val icon = rememberAppIcon(item.componentKey, sizePx = with(LocalDensity.current) { iconSize.roundToPx() }, withoutBackground = true)
        AppIconAndLabel(item, icon, iconSize, showLabel)
    }
}

/** Outline of the built-in module cards (Settings). */
val LocalModuleShape = staticCompositionLocalOf<Shape> { RoundedCornerShape(Dimens.ItemCorner) }

/** Shape for the module cards: rounded or scalloped ("flower") with [cornerDp] corners. */
fun moduleShapeOf(shape: ModuleShape, cornerDp: Int): Shape = when (shape) {
    ModuleShape.ROUNDED -> RoundedCornerShape(cornerDp.dp)
    ModuleShape.SCALLOPED -> ScallopedShape(cornerDp.dp)
}

/** Whether home screen apps show their name below the icon (Settings). */
val LocalShowAppLabels = staticCompositionLocalOf { false }

@Composable
private fun AppIconAndLabel(item: PlacedItem, icon: ImageBitmap?, iconSize: Dp, showLabel: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Image(bitmap = icon, contentDescription = item.title, modifier = Modifier.size(iconSize))
        }
        if (!showLabel) return@Column
        Spacer(Modifier.height(4.dp))
        Text(
            text = item.title,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
