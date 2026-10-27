package org.opencarproject.launcher.data.embed

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import dadb.AdbStream
import org.opencarproject.launcher.core.pure.StackList
import org.opencarproject.launcher.core.pure.TouchLine
import org.opencarproject.launcher.core.pure.TouchPointer
import java.util.concurrent.Executors

/** Lifecycle of an [EmbeddedAppSession] as shown by the card. */
enum class EmbedState { CONNECTING, RUNNING, UNAVAILABLE }

/**
 * Runs [packageName] inside a TextureView: its surface backs a VirtualDisplay owned by the
 * launcher, the root adb shell moves the app's stack onto that display, and [TouchInjector] (as root)
 * replays the card's touches there. All adb I/O happens on one background thread, in order.
 */
class EmbeddedAppSession(
    private val context: Context,
    private val packageName: String,
    private val onState: (EmbedState) -> Unit,
) : TextureView.SurfaceTextureListener {

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var display: VirtualDisplay? = null
    private var touch: AdbStream? = null
    private var surface: Surface? = null

    @SuppressLint("WrongConstant") // FLAGS includes the hidden DESTROY_CONTENT_ON_REMOVAL.
    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        val surface = Surface(texture).also { this.surface = it }
        val dpi = context.resources.displayMetrics.densityDpi
        display?.let {
            it.surface = surface
            it.resize(width, height, dpi)
            // The vendor may have killed the app while the card was hidden: check (quietly).
            start(showProgress = false)
            return
        }
        display = context.getSystemService(DisplayManager::class.java)
            .createVirtualDisplay(NAME, width, height, dpi, surface, FLAGS)
        start()
    }

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
        display?.resize(width, height, context.resources.displayMetrics.densityDpi)
    }

    // The display survives while the card is off screen; it just stops drawing.
    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        display?.surface = null
        surface?.release()
        surface = null
        return true
    }

    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit

    /** Connects (again) and puts the app on the display, starting it if it isn't running. */
    fun start(showProgress: Boolean = true) {
        if (io.isShutdown) return
        val displayId = display?.display?.displayId ?: return
        if (showProgress) post(EmbedState.CONNECTING)
        io.execute {
            try {
                val adb = RootAdb.connect(context)
                touch?.close()
                // This ROM ignores `am start --display`: start the app (or reuse its running
                // task) on the main screen and move its stacks onto the card's display.
                fun stacks() = StackList.findAll(adb.shell("am stack list").output, packageName)
                fun launch(): Boolean {
                    val cold = stacks().isEmpty()
                    if (cold) {
                        val component = context.packageManager.getLaunchIntentForPackage(packageName)
                            ?.component?.flattenToShortString() ?: error("no launcher activity")
                        adb.shell("am start -W -n $component")
                    }
                    // ponytail: a splash activity hands off to a new task a moment later, so a cold
                    // start re-checks for a few seconds; a watcher would catch later stray tasks too.
                    repeat(if (cold) 5 else 1) { attempt ->
                        if (attempt > 0) Thread.sleep(700)
                        stacks().filter { it.displayId != displayId }
                            .forEach { adb.shell("am display move-stack ${it.id} $displayId") }
                    }
                    return stacks().any { it.displayId == displayId }
                }
                // Some apps (Google Maps) occasionally close right after the move: try once more.
                check(launch() || launch()) { "app did not start" }
                val apk = context.applicationInfo.sourceDir
                touch = adb.open("shell:CLASSPATH=$apk app_process /system/bin ${TouchInjector::class.java.name} $displayId")
                post(EmbedState.RUNNING)
            } catch (e: Exception) {
                RootAdb.reset()
                post(EmbedState.UNAVAILABLE)
            }
        }
    }

    /** Forwards a touch on the card; display and card share the same pixel size. */
    fun onTouch(event: MotionEvent) {
        if (io.isShutdown) return
        val line = TouchLine(
            action = event.actionMasked,
            index = event.actionIndex,
            pointers = (0 until event.pointerCount).map { TouchPointer(event.getPointerId(it), event.getX(it), event.getY(it)) },
        ).encode()
        io.execute {
            runCatching { touch?.sink?.apply { writeUtf8(line).writeByte('\n'.code).flush() } }
        }
    }

    /** Closes the helper and the display; the app's task on it is destroyed with it. */
    fun release() {
        display?.release()
        display = null
        io.execute { runCatching { touch?.close() } }
        io.shutdown()
    }

    private fun post(state: EmbedState) = main.post { onState(state) }

    private companion object {
        const val NAME = "OpenCarEmbed"

        // Hidden DisplayManager flag: removing the display finishes its activities instead of
        // moving them to the main screen.
        const val DESTROY_CONTENT_ON_REMOVAL = 1 shl 8
        const val FLAGS = DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or DESTROY_CONTENT_ON_REMOVAL
    }
}
