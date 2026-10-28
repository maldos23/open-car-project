package org.opencarproject.launcher.presentation.common

import org.opencarproject.launcher.R
import androidx.compose.ui.res.stringResource
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import org.opencarproject.launcher.data.source.StereoVolume
import kotlin.math.roundToInt

/** `VOLUME_CHANGED_ACTION` is a hidden SDK constant, but the broadcast is receivable. */
private const val ACTION_VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"

/** The floating control closes itself after this long without being touched. */
private const val AUTO_HIDE_MS = 4_000L

/**
 * Dock volume button: the badge shows the audio level (%) and updates live (the stereo's own
 * volume on QF head units, where it drives the speakers; otherwise Android's music stream), including when changed with the physical or steering-wheel buttons. Tapping it opens a
 * floating control with mute, −, bar and +.
 */
@Composable
fun VolumeButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(AudioManager::class.java) }
    val stereo = remember { StereoVolume.get() != null }
    val max = remember { if (stereo) StereoVolume.MAX else audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    fun read() = if (stereo) StereoVolume.get() ?: 0 else audio.getStreamVolume(AudioManager.STREAM_MUSIC)
    var level by remember { mutableIntStateOf(read()) }
    // Last level before muting, to restore it when unmuting.
    var beforeMute by remember { mutableIntStateOf(max / 2) }
    val popup = remember { MutableTransitionState(false) }
    var lastTouch by remember { mutableStateOf(0L) }

    DisposableEffect(audio) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                level = read()
            }
        }
        // The stereo's broadcast comes from its firmware app, so that receiver must be exported.
        if (stereo) ContextCompat.registerReceiver(context, receiver, IntentFilter(StereoVolume.ACTION_CHANGED), ContextCompat.RECEIVER_EXPORTED)
        else ContextCompat.registerReceiver(context, receiver, IntentFilter(ACTION_VOLUME_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }

    fun setLevel(value: Int) {
        val clamped = value.coerceIn(0, max)
        // No FLAG_SHOW_UI: our own control replaces the system panel.
        if (stereo) StereoVolume.set(clamped) else runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, clamped, 0) }
        level = read()
        lastTouch = System.currentTimeMillis()
    }

    // Auto-close: every interaction restarts the countdown.
    LaunchedEffect(popup.targetState, lastTouch) {
        if (popup.targetState) {
            delay(AUTO_HIDE_MS)
            popup.targetState = false
        }
    }

    val percent = (level * 100f / max).roundToInt()
    IconButton(
        onClick = {
            lastTouch = System.currentTimeMillis()
            popup.targetState = !popup.targetState
        },
        modifier = modifier.size(56.dp),
    ) {
        BadgedBox(badge = { Badge { Text("$percent") } }) {
            Icon(
                imageVector = volumeIcon(percent),
                contentDescription = stringResource(R.string.volume_level, percent),
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(30.dp),
            )
        }
    }

    if (popup.currentState || popup.targetState) {
        // Above the button, aligned to its right edge (the dock is at the bottom right).
        Popup(
            alignment = Alignment.BottomEnd,
            offset = with(androidx.compose.ui.platform.LocalDensity.current) { IntOffset(0, -72.dp.roundToPx()) },
            onDismissRequest = { popup.targetState = false },
            properties = PopupProperties(focusable = true),
        ) {
            AnimatedVisibility(
                visibleState = popup,
                enter = fadeIn() + scaleIn(spring(dampingRatio = 0.7f), initialScale = 0.85f, transformOrigin = TransformOrigin(1f, 1f)),
                exit = fadeOut() + scaleOut(targetScale = 0.9f, transformOrigin = TransformOrigin(1f, 1f)),
            ) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = 12.dp,
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        IconButton(
                            onClick = {
                                if (level > 0) {
                                    beforeMute = level
                                    setLevel(0)
                                } else {
                                    setLevel(beforeMute.coerceAtLeast(1))
                                }
                            },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(volumeIcon(percent), contentDescription = if (level > 0) stringResource(R.string.volume_mute) else stringResource(R.string.volume_unmute))
                        }
                        FilledTonalIconButton(onClick = { setLevel(level - 1) }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Rounded.Remove, contentDescription = stringResource(R.string.volume_down))
                        }
                        Slider(
                            value = level.toFloat(),
                            onValueChange = { setLevel(it.roundToInt()) },
                            valueRange = 0f..max.toFloat(),
                            steps = (max - 1).coerceAtLeast(0),
                            modifier = Modifier.width(280.dp),
                        )
                        FilledTonalIconButton(onClick = { setLevel(level + 1) }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.volume_up))
                        }
                        Text(
                            text = stringResource(R.string.percent, percent),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(56.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun volumeIcon(percent: Int): ImageVector = when {
    percent == 0 -> Icons.Rounded.VolumeOff
    percent < 50 -> Icons.Rounded.VolumeDown
    else -> Icons.Rounded.VolumeUp
}
