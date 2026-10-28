package org.opencarproject.launcher.presentation.widget

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.opencarproject.launcher.R
import org.opencarproject.launcher.core.model.DoorState
import org.opencarproject.launcher.core.model.LauncherSettings
import org.opencarproject.launcher.core.ui.theme.LauncherTheme
import org.opencarproject.launcher.data.source.DoorSource
import org.opencarproject.launcher.presentation.common.OverlayOwner
import org.opencarproject.launcher.presentation.common.localized
import org.opencarproject.launcher.presentation.reverse.drawCarSilhouette
import kotlin.math.cos
import kotlin.math.sin

/**
 * The launcher's door alert over every app, replacing the stereo's own: slides down from the top
 * while a door, the trunk or the hood is open. The vendor view is switched off only once this
 * window can be shown ("display over other apps"), so an alert is never lost.
 */
object DoorOverlay {

    private val settings = MutableStateFlow(LauncherSettings())
    private var scope: CoroutineScope? = null
    private var view: ComposeView? = null
    private var owner: OverlayOwner? = null

    /** Follows the settings: called on every change from process start. */
    fun apply(context: Context, next: LauncherSettings) {
        val app = context.applicationContext
        settings.value = next
        if (!next.launcherDoorAlert) {
            if (scope == null) return
            scope?.cancel(); scope = null
            hide(app)
            DoorSource.stop()
            DoorSource.setVendorView(app, show = true)
            return
        }
        if (scope != null) return
        DoorSource.start(app)
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
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL }
        val lifecycle = OverlayOwner().also { owner = it }
        val compose = ComposeView(app.localized(settings.value.appLanguage)).apply {
            setViewTreeLifecycleOwner(lifecycle)
            setViewTreeSavedStateRegistryOwner(lifecycle)
            setContent {
                val current by settings.collectAsStateWithLifecycle()
                val doors by DoorSource.doors.collectAsStateWithLifecycle()
                LauncherTheme(themeMode = current.themeMode, accent = current.accentColor) {
                    DoorAlert(doors)
                }
            }
        }
        if (runCatching { wm.addView(compose, p) }.isFailure) return lifecycle.destroy()
        view = compose
        lifecycle.resume()
        DoorSource.setVendorView(app, show = false)
    }

    private fun hide(app: Context) {
        view?.let { runCatching { app.getSystemService(WindowManager::class.java).removeView(it) } }
        owner?.destroy()
        view = null; owner = null
    }
}

/** The card: a car from above with what's open in the error color, and the list of open parts. */
@Composable
internal fun DoorAlert(doors: DoorState?) {
    // The last open state stays drawn while the card slides out.
    val shown = remember { mutableListOf(DoorState()) }
    if (doors?.anyOpen == true) shown[0] = doors
    AnimatedVisibility(
        visible = doors?.anyOpen == true,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
    ) {
        val state = shown[0]
        Surface(
            // Room for the shadow inside the wrap-content window.
            Modifier.padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 12.dp,
        ) {
            Row(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                DoorCar(state, Modifier.size(130.dp, 150.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.doors_open), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                    openParts(state).forEach {
                        Text(stringResource(it), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

private fun openParts(d: DoorState) = listOfNotNull(
    R.string.door_front_left.takeIf { d.frontLeft },
    R.string.door_front_right.takeIf { d.frontRight },
    R.string.door_rear_left.takeIf { d.rearLeft },
    R.string.door_rear_right.takeIf { d.rearRight },
    R.string.door_trunk.takeIf { d.trunk },
    R.string.door_hood.takeIf { d.hood },
)

@Composable
private fun DoorCar(d: DoorState, modifier: Modifier) {
    val body = MaterialTheme.colorScheme.onSurfaceVariant
    val open = MaterialTheme.colorScheme.error
    // Offscreen: the silhouette's windows are cut out with BlendMode.Clear.
    Canvas(modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        val w = size.height * 0.40f
        val h = size.height * 0.86f
        val cx = size.width / 2
        val top = (size.height - h) / 2
        drawCarSilhouette(cx, top, w, h, body)
        val stroke = w * 0.09f
        // A door swung out 40° from its front hinge.
        fun door(left: Boolean, hingeY: Float) {
            val x = if (left) cx - w / 2 else cx + w / 2
            val len = h * 0.22f
            val a = Math.toRadians(40.0)
            val end = Offset(x + (if (left) -1 else 1) * (len * sin(a)).toFloat(), hingeY + (len * cos(a)).toFloat())
            drawLine(open, Offset(x, hingeY), end, stroke, StrokeCap.Round)
        }
        if (d.frontLeft) door(left = true, hingeY = top + h * 0.30f)
        if (d.frontRight) door(left = false, hingeY = top + h * 0.30f)
        if (d.rearLeft) door(left = true, hingeY = top + h * 0.54f)
        if (d.rearRight) door(left = false, hingeY = top + h * 0.54f)
        if (d.hood) drawLine(open, Offset(cx - w * 0.34f, top + h * 0.06f), Offset(cx + w * 0.34f, top + h * 0.06f), stroke, StrokeCap.Round)
        if (d.trunk) drawLine(open, Offset(cx - w * 0.34f, top + h * 0.97f), Offset(cx + w * 0.34f, top + h * 0.97f), stroke, StrokeCap.Round)
    }
}
