package org.opencarproject.launcher.presentation.widget

import org.opencarproject.launcher.presentation.common.rememberAmbientFloat
import org.opencarproject.launcher.R
import androidx.compose.ui.res.stringResource
import android.content.Context
import android.graphics.Bitmap
import android.media.session.MediaController
import android.os.SystemClock
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.cos
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.scale
import org.opencarproject.launcher.core.pure.VibrantColor
import org.opencarproject.launcher.data.source.MediaSessionSource
import org.opencarproject.launcher.data.source.NowPlaying
import org.opencarproject.launcher.presentation.common.rememberAppIcon
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.LocalContentColor
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/**
 * Player module for the active MediaSession (Spotify, radio, BT…): artwork, title, artist,
 * album and live progress, over a background using the artwork's vibrant tones. An
 * equalizer shows whether it's playing (dancing bars) or paused (still bars). Tapping it
 * opens the app that is playing.
 */
@Composable
fun MediaPlayerCard(modifier: Modifier = Modifier) {
    if (!rememberNotificationAccess()) {
        NotificationAccessPrompt(stringResource(R.string.media_access_prompt), modifier)
        return
    }
    val context = LocalContext.current
    DisposableEffect(context) {
        MediaSessionSource.start(context)
        onDispose { MediaSessionSource.stop() }
    }
    val current by MediaSessionSource.nowPlaying.collectAsStateWithLifecycle()

    // Background: an animated gradient of the artwork's colors (or the theme primary); each color
    // blends smoothly into the next track's.
    val fallback = MaterialTheme.colorScheme.primaryContainer
    val art = current?.art
    val palette = remember(art) { art?.vibrantPalette().orEmpty().map { Color(it) } }
    val base = palette.getOrNull(0) ?: fallback
    val first by animateColorAsState(base, tween(900), label = "c0")
    val second by animateColorAsState(palette.getOrNull(1) ?: base.darker(), tween(900), label = "c1")
    val third by animateColorAsState(palette.getOrNull(2) ?: lerp(base, Color.White, 0.25f), tween(900), label = "c2")
    val surface = MaterialTheme.colorScheme.surfaceContainerHighest
    val phase = rememberAmbientFloat(0f, (2 * PI).toFloat(), 24_000)
    val playingAlpha by animateFloatAsState(if (current?.isPlaying == true) 1f else 0.55f, tween(600), label = "alpha")
    val tint = 0.85f * playingAlpha
    val a = lerp(surface, first, tint)
    val b = lerp(surface, second, tint)
    val c = lerp(surface, third, tint)
    val bottom = lerp(surface, first.darker(), 0.6f * playingAlpha)
    val onBg = if (lerp(lerp(a, b, 0.5f), c, 0.33f).luminance() > 0.5f) Color(0xFF111111) else Color.White

    Box(
        modifier
            .fillMaxSize()
            // The phase is read while drawing: the card redraws, it doesn't recompose 60 times a second.
            .drawBehind {
                val t = phase.value
                drawRect(Brush.linearGradient(listOf(a, bottom), start = Offset.Zero, end = Offset(size.width, size.height)))
                // Soft color blobs drifting on closed loops (integer frequencies: seamless repeat).
                drawBlob(b, 0.5f + 0.4f * cos(t), 0.5f + 0.4f * sin(2 * t))
                drawBlob(c, 0.5f + 0.45f * cos(2 * t + 2.1f), 0.5f + 0.4f * sin(t + 2.1f))
                drawBlob(a, 0.5f + 0.35f * cos(t + 4.2f), 0.5f + 0.45f * sin(t + 4.2f))
            }
            .clickable { openPlayer(context, current) },
    ) {
        CompositionLocalProvider(LocalContentColor provides onBg) {
            AnimatedContent(
                targetState = current == null,
                transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.95f)) togetherWith fadeOut() },
                label = "state",
            ) { idle ->
                if (idle) IdleState() else current?.let { PlayerLayouts(it, onBg) }
            }
        }
    }
}

/** Nothing playing: a "breathing" note. */
@Composable
private fun IdleState() {
    val breathe by rememberAmbientFloat(0.85f, 1.1f, 1600, FastOutSlowInEasing, reverse = true)
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(
            Icons.Rounded.MusicNote,
            contentDescription = null,
            modifier = Modifier.size(44.dp).graphicsLayer { scaleX = breathe; scaleY = breathe; alpha = 0.5f + 0.4f * (breathe - 0.85f) / 0.25f },
        )
        Text(stringResource(R.string.media_nothing_playing), style = MaterialTheme.typography.titleMedium, color = LocalContentColor.current.copy(alpha = 0.75f))
    }
}

@Composable
private fun PlayerLayouts(current: NowPlaying, onBg: Color) {
    val controls = MediaSessionSource.controller?.transportControls
    var showQueue by remember { mutableStateOf(false) }
    val hasQueue = current.queue.isNotEmpty()
    BoxWithConstraints(Modifier.fillMaxSize().padding(14.dp)) {
        val w = maxWidth
        val h = maxHeight
        val mini = h < 90.dp || (w < 180.dp && h < 200.dp)
        // Mini (play over the artwork), compact (text + controls), tall (artwork on top), wide
        // (artwork on the left). Every size keeps the artwork; beside the text it takes at most
        // 40% of the width.
        val sideCover = Modifier.widthIn(max = w * 0.4f).aspectRatio(1f)
        when {
            showQueue && hasQueue && h >= 170.dp -> QueueList(current, controls, onClose = { showQueue = false })
            mini -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Cover(current, Modifier.fillMaxSize())
                PlayPause(current.isPlaying, controls, big = h >= 72.dp, onBg)
            }
            h < 170.dp -> Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Cover(current, sideCover)
                Column(Modifier.weight(1f).fillMaxHeight().padding(start = 12.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TrackText(current, center = false, titleStyle = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Equalizer(current.isPlaying, Modifier.size(24.dp, 20.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Controls(current.isPlaying, controls, compact = true, onBg)
                        // End padding leaves room for the app logo in the corner.
                        Progress(current, Modifier.weight(1f).padding(start = 8.dp, end = 36.dp), showTimes = w > 360.dp)
                    }
                }
            }
            h > w * 1.1f -> Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            ) {
                Cover(current, Modifier.weight(1f, fill = false).fillMaxWidth().aspectRatio(1f, matchHeightConstraintsFirst = true))
                TrackText(current, center = true, titleStyle = MaterialTheme.typography.titleLarge)
                Progress(current, Modifier.fillMaxWidth(), showTimes = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Controls(current.isPlaying, controls, compact = w < 260.dp, onBg)
                    if (hasQueue) QueueButton { showQueue = true }
                }
            }
            else -> Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Cover(current, sideCover)
                Column(
                    Modifier.weight(1f).padding(start = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TrackText(current, center = false, titleStyle = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                        Equalizer(current.isPlaying, Modifier.size(30.dp, 26.dp))
                    }
                    Progress(current, Modifier.fillMaxWidth(), showTimes = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // The artwork takes up to 40%: big buttons only when the rest still fits them.
                        Controls(current.isPlaying, controls, compact = w < 480.dp, onBg)
                        if (hasQueue) QueueButton { showQueue = true }
                    }
                }
            }
        }
        // The app that is playing, in the card's bottom-right corner.
        if (!mini) AppLogo(current.packageName, Modifier.align(Alignment.BottomEnd).size(28.dp))
    }
}

/** Artwork; on song change it enters with a fade and a slight zoom. */
@Composable
private fun Cover(current: NowPlaying, modifier: Modifier) {
    AnimatedContent(
        targetState = current.art,
        transitionSpec = { (fadeIn(tween(500)) + scaleIn(initialScale = 0.92f)) togetherWith fadeOut(tween(300)) },
        label = "cover",
        modifier = modifier,
    ) { art ->
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).background(LocalContentColor.current.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            if (art != null) {
                Image(art.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(Icons.Rounded.MusicNote, contentDescription = null, modifier = Modifier.size(40.dp))
            }
        }
    }
}

@Composable
private fun TrackText(current: NowPlaying, center: Boolean, titleStyle: TextStyle, modifier: Modifier = Modifier) {
    val align = if (center) TextAlign.Center else TextAlign.Start
    val secondary = LocalContentColor.current.copy(alpha = 0.75f)
    // Artist · album on one line; the title changes with a fade.
    val subtitle = listOf(current.artist, current.album).filter { it.isNotBlank() }.joinToString(stringResource(R.string.separator))
    Column(modifier.fillMaxWidth()) {
        AnimatedContent(targetState = current.title, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "title") {
            Text(
                text = it.ifBlank { stringResource(R.string.media_unknown) },
                style = titleStyle,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = align,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (subtitle.isNotEmpty()) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = secondary, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = align, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Progress bar that advances live (extrapolates the position using the playback speed). */
@Composable
private fun Progress(current: NowPlaying, modifier: Modifier, showTimes: Boolean) {
    if (current.durationMs <= 0) return
    val position by produceState(current.positionAt(), current) {
        while (true) {
            value = current.positionAt()
            delay(if (current.isPlaying) 500 else 2000)
        }
    }
    val fraction by animateFloatAsState((position.toFloat() / current.durationMs).coerceIn(0f, 1f), tween(500, easing = LinearEasing), label = "progress")
    val color = LocalContentColor.current
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(6.dp)) {
            val r = CornerRadius(size.height / 2)
            drawRoundRect(color.copy(alpha = 0.25f), cornerRadius = r)
            drawRoundRect(color, size = Size(size.width * fraction, size.height), cornerRadius = r)
        }
        if (showTimes) {
            Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                Text(DateUtils.formatElapsedTime(position / 1000), style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.75f), modifier = Modifier.weight(1f))
                Text(DateUtils.formatElapsedTime(current.durationMs / 1000), style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.75f))
            }
        }
    }
}

@Composable
private fun QueueButton(onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(56.dp)) {
        Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = stringResource(R.string.media_queue_open), modifier = Modifier.size(30.dp))
    }
}

/** The app's queue ("up next"): the current item is highlighted; tapping one plays it. */
@Composable
private fun QueueList(current: NowPlaying, controls: MediaController.TransportControls?, onClose: () -> Unit) {
    val active = current.queue.indexOfFirst { it.id == current.activeQueueId }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = active.coerceAtLeast(0))
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.media_queue_close))
            }
            Text(
                current.queueTitle.ifBlank { stringResource(R.string.media_queue) },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Bottom padding keeps the last item clear of the app logo in the corner.
        LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 36.dp)) {
            itemsIndexed(current.queue, key = { _, item -> item.id }) { index, item ->
                val playing = index == active
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (playing) LocalContentColor.current.copy(alpha = 0.15f) else Color.Transparent)
                        .clickable {
                            controls?.skipToQueueItem(item.id)
                            onClose()
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(LocalContentColor.current.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                        val art = item.art
                        if (art != null) {
                            Image(art.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        } else {
                            Icon(Icons.Rounded.MusicNote, contentDescription = null, modifier = Modifier.size(22.dp))
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            item.title.ifBlank { stringResource(R.string.media_unknown) },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (playing) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (item.subtitle.isNotBlank()) {
                            Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = LocalContentColor.current.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (playing) Equalizer(current.isPlaying, Modifier.size(20.dp, 16.dp))
                }
            }
        }
    }
}

/** Logo (no background) of the app that is playing. */
@Composable
private fun AppLogo(packageName: String, modifier: Modifier) {
    val icon = rememberAppIcon(packageName, sizePx = with(LocalDensity.current) { 28.dp.roundToPx() }, withoutBackground = true) ?: return
    Image(icon, contentDescription = null, modifier = modifier)
}

/** Equalizer: bars that dance while playing and flatten (motionless) when paused. */
@Composable
private fun Equalizer(playing: Boolean, modifier: Modifier) {
    val phase by rememberAmbientFloat(0f, (2 * PI).toFloat(), 1100)
    val energy by animateFloatAsState(if (playing) 1f else 0f, tween(500), label = "energy")
    val color = LocalContentColor.current
    Canvas(modifier) {
        val bars = 4
        val gap = size.width * 0.12f
        val barW = (size.width - gap * (bars - 1)) / bars
        for (i in 0 until bars) {
            val wave = 0.5f + 0.5f * sin(phase * (1 + i % 2) + i * 1.7f)
            val hFrac = 0.18f + energy * 0.82f * wave
            val bh = size.height * hFrac
            drawRoundRect(
                color = color.copy(alpha = 0.55f + 0.45f * energy),
                topLeft = Offset(i * (barW + gap), size.height - bh),
                size = Size(barW, bh),
                cornerRadius = CornerRadius(barW / 2),
            )
        }
    }
}

/** Previous · play/pause · next; [compact] shrinks the buttons (still ≥ 48 dp). */
@Composable
internal fun Controls(isPlaying: Boolean, controls: MediaController.TransportControls?, compact: Boolean, onBg: Color) {
    val side = if (compact) 48.dp else 56.dp
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { controls?.skipToPrevious() }, modifier = Modifier.size(side)) {
            Icon(Icons.Rounded.SkipPrevious, contentDescription = stringResource(R.string.media_previous), modifier = Modifier.size(side * 0.55f))
        }
        PlayPause(isPlaying, controls, big = !compact, onBg)
        IconButton(onClick = { controls?.skipToNext() }, modifier = Modifier.size(side)) {
            Icon(Icons.Rounded.SkipNext, contentDescription = stringResource(R.string.media_next), modifier = Modifier.size(side * 0.55f))
        }
    }
}

/** Button contrasted with the background; the icon changes with a smooth rotation. */
@Composable
private fun PlayPause(isPlaying: Boolean, controls: MediaController.TransportControls?, big: Boolean, onBg: Color) {
    val size: Dp = if (big) 64.dp else 52.dp
    FilledIconButton(
        onClick = { if (isPlaying) controls?.pause() else controls?.play() },
        modifier = Modifier.size(size),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = onBg,
            contentColor = if (onBg == Color.White) Color(0xFF111111) else Color.White,
        ),
    ) {
        AnimatedContent(targetState = isPlaying, transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.6f)) togetherWith fadeOut() }, label = "pp") { playing ->
            Icon(
                imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (playing) stringResource(R.string.media_pause) else stringResource(R.string.media_play),
                modifier = Modifier.size(size * 0.56f),
            )
        }
    }
}

private fun NowPlaying.positionAt(): Long {
    if (!isPlaying || positionUpdatedAt <= 0) return positionMs.coerceIn(0, durationMs)
    val elapsed = SystemClock.elapsedRealtime() - positionUpdatedAt
    return (positionMs + (elapsed * speed).toLong()).coerceIn(0, durationMs)
}

/** Opens where playback is happening: the session's activity or, if it doesn't declare one, the app. */
internal fun openPlayer(context: Context, current: NowPlaying?) {
    current ?: return
    val sent = current.sessionActivity?.let { runCatching { it.send() }.isSuccess } == true
    if (!sent) {
        context.packageManager.getLaunchIntentForPackage(current.packageName)?.let {
            runCatching { context.startActivity(it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
}

private fun Bitmap.vibrantPalette(): List<Int> = runCatching {
    val small = scale(24, 24)
    val pixels = IntArray(24 * 24)
    small.getPixels(pixels, 0, 24, 0, 0, 24, 24)
    VibrantColor.palette(pixels)
}.getOrDefault(emptyList())

/** Radial blob of [color] fading to transparent, centered at a fraction ([fx], [fy]) of the size. */
private fun DrawScope.drawBlob(color: Color, fx: Float, fy: Float) {
    val center = Offset(size.width * fx, size.height * fy)
    val radius = maxOf(size.width, size.height) * 0.7f
    drawCircle(Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), center, radius), radius, center)
}

private fun Color.darker(): Color = lerp(this, Color.Black, 0.35f)
