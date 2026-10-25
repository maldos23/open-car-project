package org.opencarproject.launcher.presentation.widget

import org.opencarproject.launcher.R
import android.content.Context
import android.service.notification.StatusBarNotification
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.opencarproject.launcher.core.util.dpInt
import org.opencarproject.launcher.core.util.toImageBitmap
import org.opencarproject.launcher.data.source.LauncherNotificationListener
import org.opencarproject.launcher.data.source.MediaSessionSource
import org.opencarproject.launcher.data.source.NowPlaying
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Island phases: appears as a pill, expands, returns to a pill and hides. */
private enum class IslandPhase { HIDDEN, COMPACT, EXPANDED }

/** Spring with a slight bounce, like the iOS island. */
private val IslandSpring = spring<Dp>(dampingRatio = 0.72f, stiffness = 380f)

/** How long the notification stays expanded if not touched. */
private const val EXPANDED_MS = 4_500L

/**
 * Notification "island" (Dynamic Island style): each new notification appears at the top
 * as a black pill, springs open showing its preview and then collapses back to a bubble that
 * stays with the newest active notification (icon + one line) until none are left. Tapping
 * opens it; swiping up dismisses it until a newer one arrives. Notifications arriving back to
 * back wait in a queue. Only listens while the launcher is visible.
 */
@Composable
fun NotificationIsland(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val queue = remember { mutableStateListOf<IslandItem>() }
    var current by remember { mutableStateOf<IslandItem?>(null) }
    var phase by remember { mutableStateOf(IslandPhase.HIDDEN) }
    // Completes when the user taps or dismisses: cuts short the expanded-phase wait.
    var dismissed by remember { mutableStateOf(CompletableDeferred<Unit>()) }
    val scope = rememberCoroutineScope()

    // Resting bubble: the newest active notification, unless the user swiped it away.
    val active by LauncherNotificationListener.notifications.collectAsStateWithLifecycle()
    val newest = active.firstOrNull()
    var swipedAwayId by remember { mutableStateOf<String?>(null) }
    val resting = remember(newest?.key, newest?.postTime) { newest?.let { context.toIslandItem(it) } }
        ?.takeIf { it.id != swipedAwayId }
    val restingNow by rememberUpdatedState(resting)

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            fun enqueue(item: IslandItem) {
                queue.removeAll { it.key == item.key } // an update replaces the previous one
                queue += item
            }
            launch { LauncherNotificationListener.posted.collect { enqueue(context.toIslandItem(it)) } }
            // Song change: the island shows the new one with its cover art.
            launch { MediaSessionSource.trackChanged.collect { enqueue(context.toIslandItem(it)) } }
        }
    }

    // Choreography: pill → expanded → pill → hidden, one notification after another.
    LaunchedEffect(Unit) {
        while (true) {
            // Waits (without polling) until something is in the queue.
            val next = snapshotFlow { queue.firstOrNull() }.filterNotNull().first()
            queue.remove(next)
            current = next
            dismissed = CompletableDeferred()
            phase = IslandPhase.COMPACT
            delay(220)
            phase = IslandPhase.EXPANDED
            withTimeoutOrNull(EXPANDED_MS) { dismissed.await() }
            phase = IslandPhase.COMPACT
            delay(260)
            // Hides only when there is no bubble to rest on.
            if (restingNow == null) {
                phase = IslandPhase.HIDDEN
                delay(320)
            }
            current = null
        }
    }

    val content = current ?: resting ?: return
    val shownPhase = if (current != null) phase else IslandPhase.COMPACT
    val expanded = shownPhase == IslandPhase.EXPANDED

    val width by animateDpAsState(if (expanded) 560.dp else 340.dp, IslandSpring, label = "w")
    val height by animateDpAsState(if (expanded) 104.dp else 48.dp, IslandSpring, label = "h")
    val corner by animateDpAsState(if (expanded) 34.dp else 24.dp, IslandSpring, label = "r")
    // Content appears once the island has grown and leaves before it shrinks.
    val contentAlpha by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = if (expanded) tween(220, delayMillis = 140) else tween(120),
        label = "content",
    )
    val presence = remember(content.id) { Animatable(0f) }
    // Also keyed on presence: an alert replacing the resting bubble starts a new one at 0.
    LaunchedEffect(shownPhase, presence) {
        presence.animateTo(if (shownPhase == IslandPhase.HIDDEN) 0f else 1f, spring(dampingRatio = 0.65f, stiffness = 420f))
    }
    var dragY by remember(content.id) { mutableStateOf(0f) }

    Box(
        modifier
            .width(width)
            .graphicsLayer {
                val p = presence.value
                scaleX = 0.5f + 0.5f * p
                scaleY = 0.5f + 0.5f * p
                alpha = p
                translationY = dragY.coerceAtMost(0f)
                shadowElevation = 24.dp.toPx() * p
                shape = RoundedCornerShape(corner)
                clip = true
            }
            .background(Color.Black, RoundedCornerShape(corner))
            .pointerInput(content.id) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (dragY < -24.dp.toPx()) {
                            swipedAwayId = content.id
                            dismissed.complete(Unit)
                        }
                        dragY = 0f
                    },
                    onVerticalDrag = { _, amount -> dragY += amount },
                )
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                scope.launch { content.onOpen() }
                dismissed.complete(Unit)
            },
    ) {
        Box(Modifier.size(width, height)) {
            // Compact: app icon, a one-line summary and an alert dot.
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp)
                    .graphicsLayer { alpha = 1f - contentAlpha },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (content.appIcon != null) Image(content.appIcon, contentDescription = null, modifier = Modifier.size(24.dp))
                Text(
                    text = listOf(content.title, content.text).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { content.appName },
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
            }

            // Expanded: full preview.
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 14.dp)
                    .graphicsLayer { alpha = contentAlpha },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                val leading = content.avatar ?: content.appIcon
                if (leading != null) {
                    Image(
                        bitmap = leading,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(52.dp).clip(CircleShape),
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "${content.appName} · ${content.label}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1,
                    )
                    Text(
                        text = content.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = content.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.75f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (content.picture != null) {
                    Image(
                        bitmap = content.picture,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(60.dp).clip(RoundedCornerShape(14.dp)),
                    )
                }
            }
        }
    }
}

/** What the island shows: a notification or the new song. */
private class IslandItem(
    /** Identity used to replace updates in the queue ("same notification / same player"). */
    val key: String,
    /** Identity of this specific appearance (restarts animations). */
    val id: String,
    val appName: String,
    val label: String,
    val appIcon: ImageBitmap?,
    val avatar: ImageBitmap?,
    val title: String,
    val text: String,
    val picture: ImageBitmap?,
    val onOpen: () -> Unit,
)

private fun Context.toIslandItem(sbn: StatusBarNotification): IslandItem {
    val c = readContent(sbn)
    return IslandItem(
        key = sbn.key,
        id = "${sbn.key}@${sbn.postTime}",
        appName = c.appName,
        label = getString(R.string.island_now),
        appIcon = c.appIcon,
        avatar = c.avatar,
        title = c.title,
        text = c.text,
        picture = c.picture,
        onOpen = { runCatching { sbn.notification.contentIntent?.send() } },
    )
}

private fun Context.toIslandItem(track: NowPlaying): IslandItem {
    val pm = packageManager
    val appName = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(track.packageName, 0)).toString() }.getOrDefault(track.packageName)
    val appIcon = runCatching { pm.getApplicationIcon(track.packageName).toImageBitmap(dpInt(22f)) }.getOrNull()
    return IslandItem(
        key = "media:${track.packageName}",
        id = "media:${track.packageName}:${track.title}:${track.artist}",
        appName = appName,
        label = getString(R.string.island_playing),
        appIcon = appIcon,
        // The cover art takes the avatar's place (rounded).
        avatar = track.art?.asImageBitmap() ?: appIcon,
        title = track.title,
        text = listOf(track.artist, track.album).filter { it.isNotBlank() }.joinToString(" · "),
        picture = null,
        onOpen = {
            val sent = track.sessionActivity?.let { runCatching { it.send() }.isSuccess } ?: false
            if (!sent) pm.getLaunchIntentForPackage(track.packageName)?.let {
                runCatching { startActivity(it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        },
    )
}
