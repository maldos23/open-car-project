package org.opencarproject.launcher.presentation.common

import android.view.View
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** Whether full screen is active (provided by the Activity from the settings). */
val LocalFullscreen = staticCompositionLocalOf { false }

/**
 * "Immersive" full screen: hides the status and navigation bars; a swipe
 * from the edge shows them temporarily.
 */
fun Window.setSystemBarsHidden(hidden: Boolean) {
    val controller = WindowCompat.getInsetsController(this, decorView)
    if (hidden) {
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    } else {
        controller.show(WindowInsetsCompat.Type.systemBars())
    }
}

/**
 * Call inside the content of a ModalBottomSheet/Dialog: those live in their own window,
 * which doesn't inherit the Activity's full screen and would show the bars again.
 */
@Composable
fun MatchSystemBarsInDialog() {
    val fullscreen = LocalFullscreen.current
    val view = LocalView.current
    LaunchedEffect(fullscreen) {
        view.dialogWindow()?.setSystemBarsHidden(fullscreen)
    }
}

private fun View.dialogWindow(): Window? {
    var current: Any? = this
    while (current != null) {
        if (current is DialogWindowProvider) return current.window
        current = (current as? View)?.parent
    }
    return null
}
