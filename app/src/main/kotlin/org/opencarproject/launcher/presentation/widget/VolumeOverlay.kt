package org.opencarproject.launcher.presentation.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.media.AudioManager
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.opencarproject.launcher.R
import org.opencarproject.launcher.core.model.LauncherSettings
import org.opencarproject.launcher.core.ui.theme.LauncherTheme
import org.opencarproject.launcher.data.source.StereoVolume
import org.opencarproject.launcher.presentation.common.OverlayOwner
import org.opencarproject.launcher.presentation.common.localized
import kotlin.math.roundToInt

/**
 * The launcher's volume indicator over every app: a pill near the bottom while the volume changes
 * (knob, steering wheel, dock). The stereo's own bar can't be switched off by a regular app (a
 * system window with no switch in the firmware), so this sits away from it, at the bottom.
 */
object VolumeOverlay {

    private const val SHOW_MS = 2_500L
    /** Hidden SDK constant; the broadcast is receivable. */
    private const val ACTION_ANDROID_VOLUME = "android.media.VOLUME_CHANGED_ACTION"

    private val settings = MutableStateFlow(LauncherSettings())
    /** Percent and a counter that grows on every change, so the same level shown twice re-arms the timer. */
    private val change = MutableStateFlow<Pair<Int, Long>?>(null)
    private var scope: CoroutineScope? = null
    private var receiver: BroadcastReceiver? = null
    private var view: ComposeView? = null
    private var owner: OverlayOwner? = null

    /** Follows the settings: called on every change from process start. */
    fun apply(context: Context, next: LauncherSettings) {
        val app = context.applicationContext
        settings.value = next
        if (!next.launcherVolumeIndicator) {
            scope?.cancel(); scope = null
            receiver?.let { runCatching { app.unregisterReceiver(it) } }
            receiver = null
            hide(app)
            return
        }
        if (scope != null) return
        val stereo = StereoVolume.get() != null
        val audio = app.getSystemService(AudioManager::class.java)
        receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val percent = if (stereo) {
                    (StereoVolume.get() ?: return) * 100f / StereoVolume.MAX
                } else {
                    audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f / audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                }
                change.value = percent.roundToInt() to System.nanoTime()
            }
        }.also {
            // The stereo's broadcast comes from its firmware app, so that receiver must be exported.
            if (stereo) ContextCompat.registerReceiver(app, it, IntentFilter(StereoVolume.ACTION_CHANGED), ContextCompat.RECEIVER_EXPORTED)
            else ContextCompat.registerReceiver(app, it, IntentFilter(ACTION_ANDROID_VOLUME), ContextCompat.RECEIVER_NOT_EXPORTED)
        }
        // Retry each time Home comes or goes: the permission may have been granted meanwhile.
        scope = MainScope().apply { launch { FloatingIsland.launcherVisible.collect { show(app) } } }
    }

    private fun show(app: Context) {
        if (view != null || !Settings.canDrawOverlays(app)) return
        val wm = app.getSystemService(WindowManager::class.java)
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // Not FLAG_NOT_TOUCHABLE: Android 12+ fades such windows to 80% (touch occlusion rules).
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL }
        val lifecycle = OverlayOwner().also { owner = it }
        val compose = ComposeView(app.localized(settings.value.appLanguage)).apply {
            setViewTreeLifecycleOwner(lifecycle)
            setViewTreeSavedStateRegistryOwner(lifecycle)
            setContent {
                val current by settings.collectAsStateWithLifecycle()
                val last by change.collectAsStateWithLifecycle()
                LauncherTheme(themeMode = current.themeMode, accent = current.accentColor) {
                    VolumePill(last)
                }
            }
        }
        if (runCatching { wm.addView(compose, p) }.isFailure) return lifecycle.destroy()
        view = compose
        lifecycle.resume()
    }

    private fun hide(app: Context) {
        view?.let { runCatching { app.getSystemService(WindowManager::class.java).removeView(it) } }
        owner?.destroy()
        view = null; owner = null
    }

    @Composable
    private fun VolumePill(last: Pair<Int, Long>?) {
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(last) {
            if (last == null) return@LaunchedEffect
            shown = true
            delay(SHOW_MS)
            shown = false
        }
        val percent = last?.first ?: 0
        val fraction by animateFloatAsState(percent / 100f, label = "volume")
        AnimatedVisibility(
            visible = shown,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            Surface(
                // Above the dock, where Home keeps its own controls.
                Modifier.padding(bottom = 120.dp),
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 12.dp,
            ) {
                Row(
                    Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(
                        when {
                            percent == 0 -> Icons.Rounded.VolumeOff
                            percent < 50 -> Icons.Rounded.VolumeDown
                            else -> Icons.Rounded.VolumeUp
                        },
                        contentDescription = stringResource(R.string.volume_level, percent),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp),
                    )
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.width(360.dp), drawStopIndicator = {})
                    Text(
                        stringResource(R.string.percent, percent),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(72.dp),
                    )
                }
            }
        }
    }
}
