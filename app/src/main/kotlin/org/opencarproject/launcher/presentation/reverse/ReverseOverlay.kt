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
import kotlin.math.asin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
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
        ReverseSource.hideVendorView = next.reverseOverlay && next.hideSystemReverseView
        if (!next.reverseOverlay) {
            scope?.cancel(); scope = null
            ReverseSource.stop()
            hide(app)
            return
        }
        if (scope != null) return
        ReverseSource.start(app)
        scope = MainScope().apply {
            launch { ReverseSource.reversing.collect { if (it) dismissed.value = false } }
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
        // Explicit width: a WRAP_CONTENT window starts at the platform's preferred dialog width and
        // Compose never asks it to grow, which squeezed the radar panel.
        val context = localized(app)
        val config = context.resources.configuration
        val params = WindowManager.LayoutParams(
            (panelWidthDp(config.screenWidthDp) * context.resources.displayMetrics.density).roundToInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 32; y = 32 }
        val lifecycle = OverlayOwner().also { owner = it }
        val compose = ComposeView(context).apply {
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

// Panel geometry, shared by the composable and the window size.
private const val OUTER_PADDING = 16f // room for the shadow inside the window
private const val INNER_PADDING = 8f
private const val RADAR_WIDTH = 150f
private fun cameraWidthDp(screenWidthDp: Int) = screenWidthDp * 0.5f
private fun panelWidthDp(screenWidthDp: Int) =
    cameraWidthDp(screenWidthDp) + RADAR_WIDTH + 2 * OUTER_PADDING + 3 * INNER_PADDING

@Composable
private fun ReversePanel(
    cameraId: String,
    radar: RadarState?,
    onDrag: (Float, Float) -> Unit,
    onClose: () -> Unit,
) {
    val cameraWidth = cameraWidthDp(LocalConfiguration.current.screenWidthDp).dp
    // Margin inside the window: the shadow would otherwise be cut square at the window edge.
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 12.dp,
        modifier = Modifier
            .padding(OUTER_PADDING.dp)
            .pointerInput(Unit) { detectDragGestures { change, drag -> change.consume(); onDrag(drag.x, drag.y) } },
    ) {
        Row(Modifier.padding(INNER_PADDING.dp), horizontalArrangement = Arrangement.spacedBy(INNER_PADDING.dp)) {
            // CVBS frames are 720x480 with non-square pixels: a 4:3 box restores the real proportions.
            Box(Modifier.width(cameraWidth).aspectRatio(4f / 3f).clip(RoundedCornerShape(14.dp))) {
                CameraPreview(cameraId)
                FilledTonalIconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(48.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.reverse_close))
                }
            }
            RadarPanel(radar, Modifier.width(RADAR_WIDTH.dp).height(cameraWidth * 0.75f))
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

/**
 * Top view of the car (one-color silhouette, front up) with proximity arcs in front and behind:
 * the installed sensors on each bumper are counted, their closeness is interpolated across the
 * bumper, and each arc column grows towards the car (far → close band) as an obstacle approaches.
 */
@Composable
private fun RadarPanel(radar: RadarState?, modifier: Modifier) {
    val description = stringResource(R.string.reverse_radar)
    val body = MaterialTheme.colorScheme.onSurfaceVariant
    val track = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val close = MaterialTheme.colorScheme.error
    // Animated closeness per sensor slot (fixed slot count, so the animations keep their identity).
    val front = animatedCloseness(radar?.front, slots = 6)
    val rear = animatedCloseness(radar?.rear, slots = 6)
    val left = animatedCloseness(radar?.left, slots = 4)
    val right = animatedCloseness(radar?.right, slots = 4)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                // Offscreen so the windows can be cut out of the silhouette.
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .semantics { contentDescription = description },
        ) {
            val cx = size.width / 2
            val carW = size.width * 0.40f
            val carH = (size.height * 0.48f).coerceAtMost(carW * 2.3f)
            val top = (size.height - carH) / 2
            val bottom = top + carH
            drawCarSilhouette(cx, top, carW, carH, body)

            // Arcs: three bands per bumper in the space between the car and the panel edge.
            val zone = top
            val gap = zone * 0.08f
            val band = zone * 0.24f
            val inner = carW * 0.55f
            val outer = inner + 3 * band + 2 * gap
            val half = Math.toDegrees(asin(((size.width / 2 - band / 2) / outer).coerceAtMost(1f).toDouble())).toFloat().coerceAtMost(55f)
            fun arcs(values: List<Float>, centerY: Float, facing: Float) {
                if (values.isEmpty()) return
                val columns = 24
                val profile = RadarProtocol.profile(values, columns)
                val step = 2 * half / columns
                for (c in 0 until columns) {
                    val p = profile[c]
                    // Car's left is screen left: front arcs sweep left→right, rear arcs right←left mirrored.
                    val start = if (facing < 0) -90f - half + c * step else 90f + half - (c + 1) * step
                    val color = closenessColor(p, close)
                    for (b in 0 until 3) { // b = 0 hugs the bumper (close), 2 is the outer (far) band
                        val r = inner + b * (band + gap) + band / 2
                        val lit = p > when (b) { 2 -> 0.02f; 1 -> 0.45f; else -> 0.8f }
                        // Bands fade outward, so a near obstacle reads as a gradient growing towards the car.
                        val tint = if (lit && color != null) color.copy(alpha = 1f - b * 0.22f) else track
                        drawArc(
                            color = tint,
                            startAngle = start + step * 0.08f,
                            sweepAngle = step * 0.84f,
                            useCenter = false,
                            topLeft = Offset(cx - r, centerY - r),
                            size = Size(2 * r, 2 * r),
                            style = Stroke(width = band, cap = StrokeCap.Butt),
                        )
                    }
                }
            }
            val installedFront = front.filterIndexed { i, _ -> radar?.front?.getOrNull(i)?.present ?: (i in 1..4) }
            val installedRear = rear.filterIndexed { i, _ -> radar?.rear?.getOrNull(i)?.present ?: (i in 1..4) }
            arcs(installedFront, centerY = top - gap + inner, facing = -1f)
            arcs(installedRear, centerY = bottom + gap - inner, facing = 1f)

            // Side sensors (most cars have none): short bars along the doors.
            fun side(values: List<Float>, sensors: List<RadarSensor>?, x: Float) {
                val installed = values.filterIndexed { i, _ -> sensors?.getOrNull(i)?.present == true }
                if (installed.isEmpty()) return
                val h = carH * 0.6f / installed.size
                installed.forEachIndexed { i, v ->
                    drawRoundRect(
                        closenessColor(v, close) ?: track,
                        Offset(x, top + carH * 0.2f + i * h + gap / 2),
                        Size(band * 0.5f, h - gap),
                        CornerRadius(band / 4),
                    )
                }
            }
            side(left, radar?.left, cx - carW / 2 - band)
            side(right, radar?.right, cx + carW / 2 + band / 2)
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
            color = nearest?.let { closenessColor(RadarProtocol.proximity01(it.zone), close) } ?: MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Closeness 0..1 per slot, animated so the arcs grow and shrink smoothly. */
@Composable
private fun animatedCloseness(sensors: List<RadarSensor>?, slots: Int): List<Float> = List(slots) { i ->
    animateFloatAsState(RadarProtocol.proximity01(sensors?.getOrNull(i)?.zone ?: 0), tween(250), label = "radar").value
}

/** Close = the theme's error color; medium amber and far green keep their safety meaning. */
private fun closenessColor(p: Float, close: Color): Color? = when {
    p >= 0.8f -> close
    p >= 0.45f -> Color(0xFFFFB020)
    p > 0.02f -> Color(0xFF34C759)
    else -> null
}

/** Car seen from above, nose up, in one color; windshield and rear window are cut out. */
private fun DrawScope.drawCarSilhouette(cx: Float, top: Float, w: Float, h: Float, color: Color) {
    val l = cx - w / 2
    val r = cx + w / 2
    val bottom = top + h
    val body = Path().apply {
        moveTo(cx, top)
        cubicTo(r - w * 0.08f, top, r, top + h * 0.04f, r, top + h * 0.16f)
        lineTo(r, bottom - h * 0.10f)
        cubicTo(r, bottom - h * 0.02f, r - w * 0.12f, bottom, cx, bottom)
        cubicTo(l + w * 0.12f, bottom, l, bottom - h * 0.02f, l, bottom - h * 0.10f)
        lineTo(l, top + h * 0.16f)
        cubicTo(l, top + h * 0.04f, l + w * 0.08f, top, cx, top)
        close()
    }
    drawPath(body, color)
    // Side mirrors.
    val mirror = Size(w * 0.14f, h * 0.05f)
    drawRoundRect(color, Offset(l - mirror.width * 0.8f, top + h * 0.27f), mirror, CornerRadius(mirror.height / 2))
    drawRoundRect(color, Offset(r - mirror.width * 0.2f, top + h * 0.27f), mirror, CornerRadius(mirror.height / 2))
    // Windows as holes, so the silhouette stays a single color.
    val windshield = Path().apply {
        moveTo(l + w * 0.14f, top + h * 0.34f)
        quadraticTo(cx, top + h * 0.24f, r - w * 0.14f, top + h * 0.34f)
        lineTo(r - w * 0.20f, top + h * 0.44f)
        quadraticTo(cx, top + h * 0.40f, l + w * 0.20f, top + h * 0.44f)
        close()
    }
    val rearWindow = Path().apply {
        moveTo(l + w * 0.20f, top + h * 0.78f)
        quadraticTo(cx, top + h * 0.75f, r - w * 0.20f, top + h * 0.78f)
        lineTo(r - w * 0.16f, top + h * 0.85f)
        quadraticTo(cx, top + h * 0.88f, l + w * 0.16f, top + h * 0.85f)
        close()
    }
    drawPath(windshield, Color.Black, blendMode = BlendMode.Clear)
    drawPath(rearWindow, Color.Black, blendMode = BlendMode.Clear)
}
