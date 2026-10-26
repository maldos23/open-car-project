package org.opencarproject.launcher.presentation.drawer

import org.opencarproject.launcher.R
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.opencarproject.launcher.core.model.CatalogItem
import org.opencarproject.launcher.core.pure.TextSearch
import org.opencarproject.launcher.presentation.common.MatchSystemBarsInDialog
import org.opencarproject.launcher.presentation.common.rememberAppIcon

/**
 * "Bottom-up" app menu: a card that slides up (closed by dragging it down or tapping
 * outside) with a search box and grid. Tapping an app opens it; with [onUninstall], long-pressing
 * enters the edit mode described in [AppDrawerContent].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDrawerSheet(
    apps: List<CatalogItem>,
    onLaunch: (CatalogItem) -> Unit,
    onDismiss: () -> Unit,
    title: String? = null,
    iconScale: Float = 1f,
    onUninstall: ((CatalogItem) -> Unit)? = null,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        MatchSystemBarsInDialog()
        if (title != null) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 24.dp, bottom = 12.dp))
        }
        AppDrawerContent(
            apps = apps,
            onLaunch = onLaunch,
            onUninstall = onUninstall,
            iconSize = 56.dp * iconScale,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(horizontal = 24.dp),
        )
    }
}

/**
 * Search box + app grid, shared by the bottom sheet and the side panel
 * (which uses it with larger icons).
 *
 * With [onUninstall], long-pressing an app enters edit mode: icons jiggle and show a trash badge
 * that calls [onUninstall]. Tapping outside an icon, Back or closing the drawer leaves it.
 */
@Composable
fun AppDrawerContent(
    apps: List<CatalogItem>,
    onLaunch: (CatalogItem) -> Unit,
    iconSize: Dp,
    modifier: Modifier = Modifier,
    onUninstall: ((CatalogItem) -> Unit)? = null,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(apps, query) { apps.filter { TextSearch.matches(it.title, query) } }
    val focusManager = LocalFocusManager.current
    // Plain remember: closing the sheet or leaving the swipe page disposes it and ends edit mode.
    var editing by remember { mutableStateOf(false) }
    BackHandler(enabled = editing) { editing = false }

    Column(
        modifier.clickable(
            enabled = editing,
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
        ) { editing = false },
    ) {
        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.drawer_search)) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.drawer_clear_search))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            // Lighter than the container (sheet or card) so the search box stands out.
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceBright,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceBright,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            // "Go" on the keyboard opens the first result.
            keyboardActions = KeyboardActions(onGo = {
                focusManager.clearFocus()
                filtered.firstOrNull()?.let(onLaunch)
            }),
        )
        Spacer(Modifier.height(16.dp))

        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.drawer_no_results), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = iconSize * 2),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filtered, key = { it.key }) { app ->
                    AppTile(
                        app = app,
                        iconSize = iconSize,
                        editing = editing,
                        // While editing, tapping an icon does nothing (like the iOS jiggle mode).
                        onClick = { if (!editing) onLaunch(app) },
                        onLongClick = onUninstall?.let { { editing = true } },
                        onUninstall = { onUninstall?.invoke(app) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppTile(
    app: CatalogItem,
    iconSize: Dp,
    editing: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    onUninstall: () -> Unit,
) {
    val density = LocalDensity.current
    val icon = rememberAppIcon(app.key, sizePx = with(density) { iconSize.roundToPx() })
    // Alternate the swing direction so neighbouring icons don't move in lockstep.
    val swing = if (app.key.hashCode() % 2 == 0) 2f else -2f
    val angle = if (editing) {
        rememberInfiniteTransition(label = "jiggle").animateFloat(
            initialValue = -swing,
            targetValue = swing,
            animationSpec = infiniteRepeatable(tween(130), RepeatMode.Reverse),
            label = "angle",
        ).value
    } else {
        0f
    }
    Column(
        Modifier
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = onLongClick?.let { stringResource(R.string.drawer_uninstall) },
            )
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.graphicsLayer { rotationZ = angle }) {
            if (icon != null) {
                Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(iconSize))
            } else {
                Icon(Icons.Rounded.Apps, contentDescription = null, modifier = Modifier.size(iconSize))
            }
            if (editing) {
                IconButton(
                    onClick = onUninstall,
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = 16.dp, y = (-16).dp).size(48.dp),
                ) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = stringResource(R.string.drawer_uninstall_app, app.title),
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier
                            .size(28.dp)
                            .background(MaterialTheme.colorScheme.errorContainer, CircleShape)
                            .padding(5.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = app.title,
            style = if (iconSize > 56.dp) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
