package org.opencarproject.launcher.presentation.widget

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.opencarproject.launcher.core.model.LauncherSettings
import org.opencarproject.launcher.presentation.common.OverlayOwner
import org.opencarproject.launcher.presentation.common.localized
import org.opencarproject.launcher.core.ui.theme.LauncherTheme

/**
 * The climate bar floating over every app, Home included, with the same slide-up animation.
 * The window wraps the bar (zero-sized while it is hidden) and takes no focus, so the app below
 * keeps the keyboard and every touch outside the bar. Needs "display over other apps"; until then Home shows its own bar.
 */
object ClimateOverlay {

    private val _active = MutableStateFlow(false)
    /** The overlay is up, so Home must not draw a second bar. */
    val active: StateFlow<Boolean> = _active

    private val settings = MutableStateFlow(LauncherSettings())
    private var scope: CoroutineScope? = null
    private var view: ComposeView? = null
    private var owner: OverlayOwner? = null

    /** Follows the settings: called on every change from process start. */
    fun apply(context: Context, next: LauncherSettings) {
        val app = context.applicationContext
        settings.value = next
        if (!(next.climateBar && next.climateBarEverywhere)) {
            scope?.cancel(); scope = null
            hide(app)
            return
        }
        if (scope != null) return
        // Retry each time Home comes or goes: the permission may have been granted meanwhile.
        scope = MainScope().apply { launch { FloatingIsland.launcherVisible.collect { show(app) } } }
    }

    fun canDraw(context: Context) = Settings.canDrawOverlays(context)

    private fun show(app: Context) {
        if (view != null || !canDraw(app)) return
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
                LauncherTheme(themeMode = current.themeMode, accent = current.accentColor) {
                    // Same place as on Home; the padding keeps the slide inside the window.
                    Box(Modifier.padding(bottom = 104.dp)) { ClimateBar() }
                }
            }
        }
        if (runCatching { wm.addView(compose, p) }.isFailure) return lifecycle.destroy()
        view = compose
        lifecycle.resume()
        _active.value = true
    }

    private fun hide(app: Context) {
        view?.let { runCatching { app.getSystemService(WindowManager::class.java).removeView(it) } }
        owner?.destroy()
        view = null; owner = null
        _active.value = false
    }
}
