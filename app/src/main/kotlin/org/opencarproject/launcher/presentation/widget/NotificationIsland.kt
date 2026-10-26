package org.opencarproject.launcher.presentation.widget

import org.opencarproject.launcher.R
import androidx.compose.ui.res.stringResource
import android.content.Context
import android.service.notification.StatusBarNotification
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.opencarproject.launcher.core.pure.IslandIndicator
import org.opencarproject.launcher.core.pure.IslandResting
import org.opencarproject.launcher.core.util.appIcon
import org.opencarproject.launcher.presentation.common.AudioBars
import org.opencarproject.launcher.core.util.appLabel
import org.opencarproject.launcher.data.source.LauncherNotificationListener
import org.opencarproject.launcher.data.source.MediaSessionSource
import org.opencarproject.launcher.data.source.NowPlaying
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
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

/** How long a preview opened by a tap stays open if not touched again. */
private const val DETAIL_MS = 10_000L

/** Tallest the preview body grows below the header, so it never covers the road view. */
private val DETAIL_MAX_HEIGHT = 320.dp

/** Narrowest the island gets when the top bar leaves little room, so its text stays readable. */
private val MIN_WIDTH = 240.dp

/** Music keeps its bubble this long after it stops playing: skipping tracks pauses briefly. */
private const val MEDIA_GRACE_MS = 3_000L

/** Ongoing-call green, on the always-black island. */
private val CallGreen = Color(0xFF34C759)

/**
 * Notification "island" (Dynamic Island style): each new notification appears at the top
 * as a black pill, springs open showing its preview and then collapses back to a bubble that
 * stays with the newest active notification (icon + one line) until none are left. Tapping
 * grows it into the full preview the notifications card shows (native view for media, calls…;
 * player controls for the music bubble) and tapping again opens it; swiping up closes the
 * preview or dismisses the bubble until a newer one arrives. Notifications arriving back to
 * back wait in a queue. Only listens while the launcher is visible.
 *
 * The resting bubble is state, not an event, so it is back as soon as the screen wakes even if
 * the alert was missed: an ongoing call (pulsing green dot), else the music playing (level
 * meter), else the newest notification (pulsing dot).
 */
@Composable
fun NotificationIsland(modifier: Modifier = Modifier, maxWidth: Dp = Dp.Unspecified) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val queue = remember { mutableStateListOf<IslandItem>() }
    var current by remember { mutableStateOf<IslandItem?>(null) }
    var phase by remember { mutableStateOf(IslandPhase.HIDDEN) }
    // Completes when the user taps or dismisses: cuts short the expanded-phase wait.
    var dismissed by remember { mutableStateOf(CompletableDeferred<Unit>()) }
    // Id of the item opened into its full preview by a tap (null: none).
    var detailId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Resting bubble: call > music playing > newest notification, skipping the one swiped away.
    val active by LauncherNotificationListener.notifications.collectAsStateWithLifecycle()
    val newest = active.firstOrNull()
    val call by LauncherNotificationListener.call.collectAsStateWithLifecycle()
    val playing by produceState<NowPlaying?>(null) {
        // collectLatest: playing again within the grace period cancels the pending hide.
        MediaSessionSource.nowPlaying.collectLatest { track ->
            if (track?.isPlaying == true && track.title.isNotBlank()) {
                value = track
            } else {
                delay(MEDIA_GRACE_MS)
                value = null
            }
        }
    }
    var swipedAwayId by remember { mutableStateOf<String?>(null) }
    val newestItem = remember(newest?.key, newest?.postTime) { newest?.let { context.toIslandItem(it) } }
    val callItem = remember(call?.key, call?.postTime) {
        call?.let { context.toIslandItem(it, IslandIndicator.CALL, R.string.island_call) }
    }
    val mediaItem = remember(playing?.packageName, playing?.title, playing?.artist, playing?.art) { playing?.let { context.toIslandItem(it) } }
    val resting = when (IslandResting.pick(callItem?.id, mediaItem?.id, newestItem?.id, swipedAwayId)) {
        IslandIndicator.CALL -> callItem
        IslandIndicator.MEDIA -> mediaItem
        IslandIndicator.NOTIFICATION -> newestItem
        null -> null
    }
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
            // Waits (without polling) until something is in the queue and no preview is open.
            val next = snapshotFlow { queue.firstOrNull()?.takeIf { detailId == null } }.filterNotNull().first()
            queue.remove(next)
            current = next
            dismissed = CompletableDeferred()
            phase = IslandPhase.COMPACT
            delay(220)
            phase = IslandPhase.EXPANDED
            withTimeoutOrNull(EXPANDED_MS) { dismissed.await() }
            // Opened into its full preview: stays until that closes.
            snapshotFlow { detailId }.first { it != next.id }
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

    // The full preview closes on its own if left untouched.
    LaunchedEffect(detailId) {
        if (detailId != null) {
            delay(DETAIL_MS)
            detailId = null
        }
    }
    val liveTrack by MediaSessionSource.nowPlaying.collectAsStateWithLifecycle()

    val content = current ?: resting ?: return
    val shownPhase = if (current != null) phase else IslandPhase.COMPACT
    val detail = detailId == content.id
    val expanded = shownPhase == IslandPhase.EXPANDED || detail

    // Never wider than the free room between the top bar's side elements (readable minimum aside).
    val fit = { w: Dp -> (if (maxWidth.isSpecified) minOf(w, maxWidth) else w).coerceAtLeast(MIN_WIDTH) }
    val width by animateDpAsState(fit(if (expanded) 560.dp else 340.dp), IslandSpring, label = "w")
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

    if (detail) {
        // A touch anywhere outside the open preview collapses it (and doesn't reach what's below).
        Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onPress = { detailId = null }) })
        BackHandler { detailId = null }
    }

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
            .pointerInput(content.id, detail) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (dragY < -24.dp.toPx()) {
                            // Swiping a preview only closes it; swiping the pill hides it.
                            if (detail) detailId = null else swipedAwayId = content.id
                            dismissed.complete(Unit)
                        }
                        dragY = 0f
                    },
                    onVerticalDrag = { _, amount -> dragY += amount },
                )
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                // First tap opens the full preview; a tap on the preview opens the app.
                if (detail) {
                    scope.launch { content.onOpen() }
                    detailId = null
                    dismissed.complete(Unit)
                } else {
                    detailId = content.id
                }
            },
    ) {
        Column(Modifier.width(width).animateContentSize(spring(dampingRatio = 0.8f, stiffness = 380f))) {
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
                        text = listOf(content.title, content.text).filter { it.isNotBlank() }.joinToString(stringResource(R.string.separator)).ifBlank { content.appName },
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IslandIndicatorMark(content.indicator)
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
                        // The full preview below already shows the text in full.
                        if (!detail || content.sbn == null) {
                            Text(
                                text = content.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.75f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
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

            // Full preview: same body as the notifications card (native view for media, calls,
            // custom views…); the music bubble, with no notification behind it, gets its controls.
            if (detail) {
                CompositionLocalProvider(LocalContentColor provides Color.White) {
                    // Capped and clipped, not scrollable: the full notification is one more tap away.
                    Box(Modifier.fillMaxWidth().heightIn(max = DETAIL_MAX_HEIGHT).padding(start = 18.dp, end = 18.dp, bottom = 16.dp)) {
                        when {
                            content.sbn != null && content.content != null ->
                                NotificationDetail(content.sbn, content.content, Color.White.copy(alpha = 0.75f), dark = true)
                            content.indicator == IslandIndicator.MEDIA -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Controls(liveTrack?.isPlaying == true, MediaSessionSource.controller?.transportControls, compact = true, onBg = Color.White)
                            }
                        }
                    }
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
    /** Animated mark at the end of the compact pill. */
    val indicator: IslandIndicator,
    /** Notification behind it and its content, for the full preview (`null` for music). */
    val sbn: StatusBarNotification? = null,
    val content: NotificationContent? = null,
)

/** Level meter for music; a softly pulsing dot for a call (green) or a notification. */
@Composable
private fun IslandIndicatorMark(indicator: IslandIndicator) {
    if (indicator == IslandIndicator.MEDIA) {
        AudioBars(List(4) { Color.White.copy(alpha = 0.7f) }, Modifier.size(width = 18.dp, height = 14.dp), periodMs = 2000)
        return
    }
    val color = if (indicator == IslandIndicator.CALL) CallGreen else MaterialTheme.colorScheme.primary
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "p",
    )
    // Read in the draw phase only: no recomposition per frame.
    Canvas(Modifier.size(10.dp)) {
        drawCircle(color.copy(alpha = 0.45f + 0.55f * pulse), radius = size.minDimension / 2 * (0.75f + 0.25f * pulse))
    }
}

private fun Context.toIslandItem(
    sbn: StatusBarNotification,
    indicator: IslandIndicator = IslandIndicator.NOTIFICATION,
    label: Int = R.string.island_now,
): IslandItem {
    val c = readContent(sbn)
    return IslandItem(
        key = sbn.key,
        id = "${sbn.key}@${sbn.postTime}",
        appName = c.appName,
        label = getString(label),
        appIcon = c.appIcon,
        avatar = c.avatar,
        title = c.title,
        text = c.text,
        picture = c.picture,
        onOpen = { runCatching { sbn.notification.contentIntent?.send() } },
        indicator = indicator,
        sbn = sbn,
        content = c,
    )
}

private fun Context.toIslandItem(track: NowPlaying): IslandItem {
    val appName = appLabel(track.packageName)
    val appIcon = appIcon(track.packageName, 22f)
    return IslandItem(
        key = "media:${track.packageName}",
        id = "media:${track.packageName}:${track.title}:${track.artist}",
        appName = appName,
        label = getString(R.string.island_playing),
        appIcon = appIcon,
        // The cover art takes the avatar's place (rounded).
        avatar = track.art?.asImageBitmap() ?: appIcon,
        title = track.title,
        text = listOf(track.artist, track.album).filter { it.isNotBlank() }.joinToString(getString(R.string.separator)),
        picture = null,
        onOpen = { openPlayer(this, track) },
        indicator = IslandIndicator.MEDIA,
    )
}
