package org.opencarproject.launcher.presentation.reverse

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.TextureView
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.opencarproject.launcher.R
import org.opencarproject.launcher.core.model.AppLanguage
import org.opencarproject.launcher.core.model.LauncherSettings
import org.opencarproject.launcher.core.model.RadarSensor
import org.opencarproject.launcher.core.model.RadarState
import org.opencarproject.launcher.core.model.closest
import org.opencarproject.launcher.core.pure.RadarProtocol
import org.opencarproject.launcher.core.ui.theme.LauncherTheme
import org.opencarproject.launcher.data.source.ReverseSource
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Floating window with the reverse camera and the parking radar, shown over any app while the
 * car is in reverse (or while previewed from settings). Needs "display over other apps".
 */
object ReverseOverlay {

    private var settings = LauncherSettings()
    private var scope: CoroutineScope? = null
    private var view: ComposeView? = null
    private var owner: OverlayOwner? = null
    private val preview = MutableStateFlow(false)
    /** Closed by the user during this reverse; reopens on the next one. */
    private val dismissed = MutableStateFlow(false)

    /** Follows the settings: called on every change from the launcher Activity. */
    fun apply(context: Context, next: LauncherSettings) {
        val app = context.applicationContext
        settings = next
        if (!next.reverseOverlay) {
            scope?.cancel(); scope = null
            ReverseSource.stop()
            hide(app)
            return
        }
        if (scope != null) return
        ReverseSource.start(app)
        scope = MainScope().apply {
            launch {
                ReverseSource.reversing.collectLatest {
                    if (!it) return@collectLatest
                    dismissed.value = false
                    if (settings.hideSystemReverseView) ReverseSource.closeVendorView(app)
                }
            }
            launch {
                combine(ReverseSource.reversing, preview, dismissed) { reversing, test, closed -> test || (reversing && !closed) }
                    .collect { if (it) show(app) else hide(app) }
            }
        }
    }

    /** Settings "Test" button: shows the window without reverse gear. */
    fun preview(context: Context) {
        apply(context, settings.copy(reverseOverlay = true))
        preview.value = true
    }

    fun canDraw(context: Context) = Settings.canDrawOverlays(context)

    private fun show(app: Context) {
        if (view != null || !canDraw(app)) return
        val wm = app.getSystemService(WindowManager::class.java)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 32; y = 32 }
        val lifecycle = OverlayOwner().also { owner = it }
        val compose = ComposeView(localized(app)).apply {
            setViewTreeLifecycleOwner(lifecycle)
            setViewTreeSavedStateRegistryOwner(lifecycle)
            val current = settings
            setContent {
                LauncherTheme(themeMode = current.themeMode, accent = current.accentColor) {
                    val radar by ReverseSource.radar.collectAsStateWithLifecycle()
                    ReversePanel(
                        cameraId = current.reverseCameraId,
                        radar = radar,
                        onDrag = { dx, dy ->
                            params.x += dx.roundToInt(); params.y += dy.roundToInt()
                            view?.let { runCatching { wm.updateViewLayout(it, params) } }
                        },
                        onClose = { if (preview.value) preview.value = false else dismissed.value = true },
                    )
                }
            }
        }
        if (runCatching { wm.addView(compose, params) }.isFailure) return lifecycle.destroy()
        view = compose
        lifecycle.resume()
    }

    private fun hide(app: Context) {
        view?.let { runCatching { app.getSystemService(WindowManager::class.java).removeView(it) } }
        owner?.destroy()
        view = null; owner = null
    }

    /** The overlay lives outside the Activity, so it applies the in-app language itself. */
    private fun localized(app: Context): Context {
        if (settings.appLanguage == AppLanguage.SYSTEM) return app
        val config = Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(settings.appLanguage.tag)) }
        return app.createConfigurationContext(config)
    }

    /** Minimal lifecycle so Compose can run in a window that no Activity owns. */
    private class OverlayOwner : SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val controller = SavedStateRegistryController.create(this).apply { performRestore(null) }
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry
        fun resume() { registry.currentState = Lifecycle.State.RESUMED }
        fun destroy() { registry.currentState = Lifecycle.State.DESTROYED }
    }
}

@Composable
private fun ReversePanel(
    cameraId: String,
    radar: RadarState?,
    onDrag: (Float, Float) -> Unit,
    onClose: () -> Unit,
) {
    val cameraWidth = (LocalConfiguration.current.screenWidthDp * 0.5f).dp
    // Margin inside the window: the shadow would otherwise be cut square at the window edge.
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 12.dp,
        modifier = Modifier
            .padding(16.dp)
            .pointerInput(Unit) { detectDragGestures { change, drag -> change.consume(); onDrag(drag.x, drag.y) } },
    ) {
        Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // CVBS frames are 720x480 with non-square pixels: a 4:3 box restores the real proportions.
            Box(Modifier.width(cameraWidth).aspectRatio(4f / 3f).clip(RoundedCornerShape(14.dp))) {
                CameraPreview(cameraId)
                FilledTonalIconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(48.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.reverse_close))
                }
            }
            RadarPanel(radar, Modifier.width(150.dp).height(cameraWidth * 0.75f))
        }
    }
}

/**
 * Camera preview that survives the vendor app holding the camera: on any failure it waits for
 * the system to report the camera available again (the vendor closed it) and reopens.
 */
@Composable
private fun CameraPreview(cameraId: String) {
    val context = LocalContext.current
    var failed by remember(cameraId) { mutableStateOf(false) }
    val camera = remember { mutableStateOf<ReverseCamera?>(null) }
    val texture = remember { mutableStateOf<SurfaceTexture?>(null) }

    fun open() {
        val target = texture.value ?: return
        camera.value?.close()
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (!granted) { failed = true; return }
        failed = false
        camera.value = ReverseCamera(context, cameraId) { failed = true }.also { it.open(target) }
    }

    DisposableEffect(cameraId) {
        val manager = context.getSystemService(CameraManager::class.java)
        val availability = object : CameraManager.AvailabilityCallback() {
            override fun onCameraAvailable(id: String) { if (id == cameraId && failed) open() }
        }
        manager.registerAvailabilityCallback(availability, Handler(Looper.getMainLooper()))
        onDispose {
            manager.unregisterAvailabilityCallback(availability)
            camera.value?.close(); camera.value = null
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                TextureView(viewContext).apply {
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(t: SurfaceTexture, w: Int, h: Int) {
                            texture.value = t
                            open()
                        }
                        override fun onSurfaceTextureSizeChanged(t: SurfaceTexture, w: Int, h: Int) = Unit
                        override fun onSurfaceTextureDestroyed(t: SurfaceTexture): Boolean {
                            texture.value = null
                            camera.value?.close(); camera.value = null
                            return true
                        }
                        override fun onSurfaceTextureUpdated(t: SurfaceTexture) = Unit
                    }
                }
            },
        )
        if (failed) {
            Text(
                stringResource(R.string.reverse_camera_unavailable),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** Top view of the car with one bar per installed sensor and the closest rear obstacle underneath. */
@Composable
private fun RadarPanel(radar: RadarState?, modifier: Modifier) {
    val description = stringResource(R.string.reverse_radar)
    val idle = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
    val body = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.fillMaxWidth().weight(1f).semantics { contentDescription = description }) {
            val gap = 4.dp.toPx()
            val bar = 10.dp.toPx()
            val r = CornerRadius(bar / 2)
            // Only installed sensors; before the first frame, four idle slots per side.
            fun installed(sensors: List<RadarSensor>?) = sensors?.filter { it.present } ?: List(4) { RadarSensor(0) }
            fun color(s: RadarSensor) = proximityColor(s.zone) ?: idle
            fun row(sensors: List<RadarSensor>?, y: Float) {
                val list = installed(sensors)
                if (list.isEmpty()) return
                val w = (size.width - gap * (list.size + 1)) / list.size
                list.forEachIndexed { i, s -> drawRoundRect(color(s), Offset(gap + i * (w + gap), y), Size(w, bar), r) }
            }
            fun column(sensors: List<RadarSensor>?, x: Float, top: Float, height: Float) {
                val list = sensors?.filter { it.present }.orEmpty()
                if (list.isEmpty()) return
                val h = (height - gap * (list.size - 1)) / list.size
                list.forEachIndexed { i, s -> drawRoundRect(color(s), Offset(x, top + i * (h + gap)), Size(bar, h), r) }
            }
            row(radar?.front, 0f)
            row(radar?.rear, size.height - bar)
            val carTop = bar + gap * 2
            val carHeight = size.height - 2 * carTop
            val carWidth = size.width * 0.45f
            drawRoundRect(body, Offset((size.width - carWidth) / 2, carTop), Size(carWidth, carHeight), CornerRadius(carWidth / 4))
            column(radar?.left, (size.width - carWidth) / 2 - bar - gap * 2, carTop, carHeight)
            column(radar?.right, (size.width + carWidth) / 2 + gap * 2, carTop, carHeight)
        }
        val nearest = closest(radar?.rear)
        val distance = nearest?.distanceCm
        Text(
            text = when {
                radar == null -> stringResource(R.string.reverse_no_radar)
                nearest == null -> stringResource(R.string.reverse_clear)
                distance != null -> stringResource(R.string.reverse_distance_cm, distance)
                else -> when (RadarProtocol.proximity(nearest.zone)) {
                    RadarProtocol.Proximity.CLOSE -> stringResource(R.string.reverse_proximity_close)
                    RadarProtocol.Proximity.MEDIUM -> stringResource(R.string.reverse_proximity_medium)
                    else -> stringResource(R.string.reverse_proximity_far)
                }
            },
            style = if (nearest != null) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            color = nearest?.let { proximityColor(it.zone) } ?: MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Red / amber / green by vendor zone (1 closest .. 10 farthest); null when clear or absent. */
private fun proximityColor(zone: Int): Color? = when (RadarProtocol.proximity(zone)) {
    RadarProtocol.Proximity.CLOSE -> Color(0xFFFF3B30)
    RadarProtocol.Proximity.MEDIUM -> Color(0xFFFFB020)
    RadarProtocol.Proximity.FAR -> Color(0xFF34C759)
    null -> null
}
