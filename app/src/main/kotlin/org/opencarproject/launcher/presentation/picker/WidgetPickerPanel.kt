package org.opencarproject.launcher.presentation.picker

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.opencarproject.launcher.core.model.CatalogItem
import org.opencarproject.launcher.core.model.CatalogItemKind
import org.opencarproject.launcher.presentation.common.rememberAppIcon
import org.opencarproject.launcher.presentation.common.rememberWidgetPreview

/**
 * Bottom picker panel (native Material 3): shows the catalog (widgets + apps)
 * so they can be added to the home screen.
 */
@Composable
fun WidgetPickerPanel(
    catalog: List<CatalogItem>,
    onSelect: (CatalogItem) -> Unit,
    onClose: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(320.dp),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
    ) {
        Column(Modifier.fillMaxSize().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Agregar al escritorio",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onClose) {
                    Icon(imageVector = Icons.Rounded.Close, contentDescription = "Cerrar")
                }
            }
            Spacer(Modifier.height(12.dp))

            if (catalog.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No hay elementos disponibles",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(catalog, key = { it.key }) { item ->
                        CatalogTile(item = item, onClick = { onSelect(item) })
                    }
                }
            }
        }
    }
}

/** Tile for a catalog item (native Card with ripple). */
@Composable
private fun CatalogTile(item: CatalogItem, onClick: () -> Unit) {
    val appIcon = if (item.kind == CatalogItemKind.APP) rememberAppIcon(item.key) else null
    val widgetPreview = if (item.kind == CatalogItemKind.WIDGET) rememberWidgetPreview(item.key) else null

    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxSize().padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when {
                widgetPreview != null -> Image(
                    bitmap = widgetPreview,
                    contentDescription = item.title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                )
                appIcon != null -> Image(
                    bitmap = appIcon,
                    contentDescription = item.title,
                    modifier = Modifier.size(34.dp),
                )
                else -> Icon(
                    imageVector = when (item.kind) {
                        CatalogItemKind.MAP -> Icons.Rounded.Navigation
                        CatalogItemKind.MEDIA -> Icons.Rounded.MusicNote
                        CatalogItemKind.NOTIFICATIONS -> Icons.Rounded.Notifications
                        CatalogItemKind.WIDGET -> Icons.Rounded.Widgets
                        else -> Icons.Rounded.Apps
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(34.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = item.title,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}
