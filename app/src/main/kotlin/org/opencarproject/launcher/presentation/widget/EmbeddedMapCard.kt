package org.opencarproject.launcher.presentation.widget

import android.annotation.SuppressLint
import android.view.TextureView
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect
import org.opencarproject.launcher.R
import org.opencarproject.launcher.data.embed.EmbedState
import org.opencarproject.launcher.data.embed.EmbeddedAppSession

/**
 * Navigation app (Google Maps/Waze) running live inside the card. Works only where the device's
 * adbd is root and reachable on 127.0.0.1 (see [org.opencarproject.launcher.data.embed.RootAdb]);
 * elsewhere it explains why and offers to open the app normally.
 */
@Composable
fun EmbeddedMapCard(chosenPackage: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // The app chosen in Settings if still installed, else the default navigation app.
    val packageName = remember(chosenPackage) {
        chosenPackage.takeIf { pkg -> context.installedNavigationApps().any { it.packageName == pkg } }
            ?: context.defaultNavigationApp()?.packageName
    }
    if (packageName == null) {
        EmbedMessage(R.string.embedded_map_no_app, modifier, onOpen = { context.openNavigationApp(null) })
        return
    }
    // A new app means a new display and session: the old app's task closes with its display.
    key(packageName) { EmbeddedApp(packageName, modifier) }
}

// Every touch is replayed in the embedded app, which does its own click handling.
@SuppressLint("ClickableViewAccessibility")
@Composable
private fun EmbeddedApp(packageName: String, modifier: Modifier) {
    val context = LocalContext.current
    val session = remember(packageName) { EmbeddedAppSession.of(context, packageName) }
    var state by remember(session) { mutableStateOf(session.state) }
    // Leaving composition only detaches: the app keeps running on the session's display.
    DisposableEffect(session) {
        session.listener = { state = it }
        onDispose { session.listener = null }
    }
    LifecycleResumeEffect(session) {
        session.resume()
        onPauseOrDispose { session.pause() }
    }

    Box(modifier.fillMaxSize()) {
        AndroidView(
            factory = {
                TextureView(it).apply {
                    surfaceTextureListener = session
                    setOnTouchListener { _, event -> session.onTouch(event); true }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        when (state) {
            EmbedState.RUNNING -> Unit
            EmbedState.CONNECTING -> EmbedMessage(R.string.embedded_map_connecting)
            EmbedState.UNAVAILABLE -> EmbedMessage(
                R.string.embedded_map_unavailable,
                onRetry = { session.start() },
                onOpen = { context.openNavigationApp(packageName) },
            )
        }
    }
}

@Composable
private fun EmbedMessage(
    @StringRes text: Int,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    onOpen: (() -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(text),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            onRetry?.let {
                OutlinedButton(onClick = it, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.embedded_map_retry))
                }
            }
            onOpen?.let {
                Button(onClick = it, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.embedded_map_open))
                }
            }
        }
    }
}
