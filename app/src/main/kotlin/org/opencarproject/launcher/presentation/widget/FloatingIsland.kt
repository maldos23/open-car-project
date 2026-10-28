package org.opencarproject.launcher.presentation.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.opencarproject.launcher.R
import org.opencarproject.launcher.core.model.LauncherSettings
import org.opencarproject.launcher.core.pure.FloatingPosition
import org.opencarproject.launcher.core.ui.theme.LauncherTheme
import org.opencarproject.launcher.core.util.appIcon
import org.opencarproject.launcher.data.source.MediaSessionSource
import org.opencarproject.launcher.data.source.SettingsDataSource
import org.opencarproject.launcher.presentation.common.AudioBars
import org.opencarproject.launcher.presentation.common.OverlayOwner
import org.opencarproject.launcher.presentation.common.localized
import kotlin.math.roundToInt

/** A pause keeps the island this long, so it can be resumed from it before it goes away. */
private const val PAUSE_GRACE_MS = 15_000L

/**
 * The music island floating over other apps (a map, typically) while something plays and Home is
 * not in front: cover, title and controls in a draggable black pill that only covers itself, so
 * the app below keeps every touch around it. Only the cover opens the player app; if allowed in
 * settings, tapping the rest shrinks it to a small bubble (and back). It
 * stays where it was dropped, also across restarts. Needs "display over other apps".
 */
object FloatingIsland {

    /** Home is in front (it has its own island there); set by the launcher Activity. */
    val launcherVisible = MutableStateFlow(false)

    private val settings = MutableStateFlow(LauncherSettings())
    /** Shrunk to the small bubble; kept for the whole process, like the position. */
    private val collapsed = MutableStateFlow(false)
    private var scope: CoroutineScope? = null
    private var view: ComposeView? = null
    private var owner: OverlayOwner? = null
    private var params: WindowManager.LayoutParams? = null
    private var fx = 0.5f
    private var fy = 0f

    /** Follows the settings: called on every change from process start. */
    fun apply(context: Context, next: LauncherSettings) {
        val app = context.applicationContext
        settings.value = next
        if (!next.floatingIsland) {
            if (scope != null) MediaSessionSource.stop()
            scope?.cancel(); scope = null
            hide(app)
            return
        }
        if (scope != null) return
        fx = next.floatingIslandX; fy = next.floatingIslandY
        // Its own user of the sessions: the island must follow the music even with Home closed.
        MediaSessionSource.start(app)
        scope = MainScope().apply {
            launch {
                val playing = MediaSessionSource.nowPlaying.map { it?.isPlaying == true && it.title.isNotBlank() }.distinctUntilChanged()
                combine(launcherVisible, playing) { home, music -> home to music }.collectLatest { (home, music) ->
                    when {
                        home -> hide(app)
                        music -> show(app)
                        else -> { delay(PAUSE_GRACE_MS); hide(app) }
                    }
                }
            }
        }
    }

    fun canDraw(context: Context) = Settings.canDrawOverlays(context)

    @SuppressLint("ClickableViewAccessibility")
    private fun show(app: Context) {
        if (view != null || !canDraw(app)) return
        val wm = app.getSystemService(WindowManager::class.java)
        val context = app.localized(settings.value.appLanguage)
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // Not focusable and only as big as the pill: the app below keeps the keyboard and touches.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
        params = p
        val lifecycle = OverlayOwner().also { owner = it }
        val compose = ComposeView(context).apply {
            setViewTreeLifecycleOwner(lifecycle)
            setViewTreeSavedStateRegistryOwner(lifecycle)
            setContent {
                val current by settings.collectAsStateWithLifecycle()
                LauncherTheme(themeMode = current.themeMode, accent = current.accentColor) {
                    FloatingIslandPill(
                        shrinkable = current.floatingIslandShrink,
                        onDrag = { dx, dy -> drag(wm, dx, dy) },
                        onDragEnd = { save(app) },
                    )
                }
            }
            // Collapsing or expanding changes the size: keep the same relative place on screen.
            addOnLayoutChangeListener { v, l, t, r, b, ol, ot, or, ob ->
                if (r - l != or - ol || b - t != ob - ot) place(wm, v)
            }
        }
        if (runCatching { wm.addView(compose, p) }.isFailure) return lifecycle.destroy()
        view = compose
        lifecycle.resume()
    }

    private fun hide(app: Context) {
        view?.let { runCatching { app.getSystemService(WindowManager::class.java).removeView(it) } }
        owner?.destroy()
        view = null; owner = null; params = null
    }

    /** Room the window can move in: the screen below the status bar, minus its own size. */
    private fun free(v: View): Pair<Int, Int> {
        val res = v.context.resources
        val statusId = res.getIdentifier("status_bar_height", "dimen", "android")
        val status = if (statusId != 0) res.getDimensionPixelSize(statusId) else 0
        return (res.displayMetrics.widthPixels - v.width) to (res.displayMetrics.heightPixels - status - v.height)
    }

    private fun place(wm: WindowManager, v: View) {
        val p = params ?: return
        val (w, h) = free(v)
        p.x = FloatingPosition.toPx(fx, w)
        p.y = FloatingPosition.toPx(fy, h)
        runCatching { wm.updateViewLayout(v, p) }
    }

    private fun drag(wm: WindowManager, dx: Float, dy: Float) {
        val v = view ?: return
        val p = params ?: return
        val (w, h) = free(v)
        p.x = (p.x + dx.roundToInt()).coerceIn(0, w.coerceAtLeast(0))
        p.y = (p.y + dy.roundToInt()).coerceIn(0, h.coerceAtLeast(0))
        fx = FloatingPosition.toFraction(p.x, w)
        fy = FloatingPosition.toFraction(p.y, h)
        runCatching { wm.updateViewLayout(v, p) }
    }

    /** Remembers where it was dropped (once per drag, not per move). */
    private fun save(app: Context) {
        val x = fx; val y = fy
        scope?.launch { SettingsDataSource(app).update { it.copy(floatingIslandX = x, floatingIslandY = y) } }
    }

    @Composable
    private fun FloatingIslandPill(shrinkable: Boolean, onDrag: (Float, Float) -> Unit, onDragEnd: () -> Unit) {
        val context = LocalContext.current
        val track by MediaSessionSource.nowPlaying.collectAsStateWithLifecycle()
        val shrunk by collapsed.collectAsStateWithLifecycle()
        val small = shrinkable && shrunk
        val t = track ?: return
        val art = remember(t.art, t.packageName) { t.art?.asImageBitmap() ?: context.appIcon(t.packageName, 48f) }
        val shape = RoundedCornerShape(50)
        val toggle = stringResource(if (small) R.string.floating_island_expand else R.string.floating_island_collapse)
        // Margin inside the window so the shadow isn't cut square at its edge.
        Box(Modifier.padding(16.dp)) {
            Row(
                Modifier
                    .shadow(8.dp, shape)
                    .background(Color.Black, shape)
                    .pointerInput(Unit) {
                        detectDragGestures(onDragEnd = onDragEnd) { change, amount ->
                            change.consume()
                            onDrag(amount.x, amount.y)
                        }
                    }
                    // Shrunk, the whole bubble but the cover expands it: an easy target while driving.
                    .clip(shape)
                    .clickable(enabled = small, onClickLabel = toggle) { collapsed.value = false }
                    // Quick: every frame of it also resizes the overlay window, which is slow on head units.
                    .animateContentSize(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium))
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Only the cover opens the player app; 48 dp or more to hit it while driving.
                Box(
                    Modifier
                        .size(if (small) 48.dp else 64.dp)
                        .clip(shape)
                        .clickable { openPlayer(context, t) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (art != null) {
                        Image(
                            art,
                            contentDescription = stringResource(R.string.floating_island_open),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(if (small) 40.dp else 56.dp).clip(shape),
                        )
                    }
                }
                // Expanded, the title shrinks it (if allowed in settings).
                if (small) {
                    Box(Modifier.padding(end = 4.dp).size(48.dp), contentAlignment = Alignment.Center) {
                        if (t.isPlaying) AudioBars(List(4) { Color.White.copy(alpha = 0.7f) }, Modifier.size(width = 18.dp, height = 14.dp), periodMs = 2000)
                    }
                } else {
                    Column(
                        Modifier
                            .width(200.dp)
                            .clickable(enabled = shrinkable, onClickLabel = toggle) { collapsed.value = true },
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(t.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(t.artist, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Box(Modifier.padding(end = 6.dp)) {
                        CompositionLocalProvider(LocalContentColor provides Color.White) {
                            Controls(t.isPlaying, MediaSessionSource.controller?.transportControls, compact = true, onBg = Color.White)
                        }
                    }
                }
            }
        }
    }
}
