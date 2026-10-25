package org.opencarproject.launcher.presentation.common

import androidx.compose.ui.res.pluralStringResource
import org.opencarproject.launcher.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.opencarproject.launcher.core.model.HotspotClient
import org.opencarproject.launcher.data.source.HotspotSource

/**
 * Hotspot icon: only shown while the hotspot is on. The badge counts connected
 * devices (if the system allows it) and tapping it lists them.
 */
@Composable
fun HotspotIndicator(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(false) }
    // null = the launcher lacks the system permissions to see the list.
    var clients by remember { mutableStateOf<List<HotspotClient>?>(null) }
    var showList by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val stopState = HotspotSource.observeEnabled(context) { enabled = it }
        val stopClients = HotspotSource.observeClients(context) { clients = it }
        onDispose {
            stopState()
            stopClients?.invoke()
        }
    }
    if (!enabled) return

    val count = clients?.size
    IconButton(onClick = { showList = true }, modifier = modifier.size(56.dp)) {
        BadgedBox(badge = { if (count != null) Badge { Text("$count") } }) {
            Icon(
                imageVector = Icons.Rounded.WifiTethering,
                contentDescription = if (count != null) pluralStringResource(R.plurals.hotspot_devices, count, count) else stringResource(R.string.hotspot_active),
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(30.dp),
            )
        }
    }

    if (showList) {
        AlertDialog(
            onDismissRequest = { showList = false },
            confirmButton = { TextButton(onClick = { showList = false }) { Text(stringResource(R.string.close)) } },
            icon = { Icon(Icons.Rounded.WifiTethering, contentDescription = null) },
            title = { Text(if (count != null) pluralStringResource(R.plurals.hotspot_connected, count, count) else stringResource(R.string.hotspot_active)) },
            text = { ClientList(clients) },
        )
    }
}

@Composable
private fun ClientList(clients: List<HotspotClient>?) {
    when {
        clients == null -> Text(
            stringResource(R.string.hotspot_system_only),
        )
        clients.isEmpty() -> Text(stringResource(R.string.hotspot_none))
        else -> LazyColumn(
            Modifier.heightIn(max = 360.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(clients, key = { it.mac ?: it.hashCode().toString() }) { client ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Devices, contentDescription = null, modifier = Modifier.size(28.dp))
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(client.name ?: stringResource(R.string.hotspot_device), style = MaterialTheme.typography.titleMedium)
                        Text(
                            listOfNotNull(client.ip, client.mac).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
