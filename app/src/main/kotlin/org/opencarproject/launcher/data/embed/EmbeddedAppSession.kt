package org.opencarproject.launcher.data.embed

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.SurfaceHolder
import dadb.AdbStream
import org.opencarproject.launcher.core.pure.StackList
import org.opencarproject.launcher.core.pure.TouchLine
import org.opencarproject.launcher.core.pure.TouchPointer
import java.util.concurrent.Executors
import kotlin.math.roundToInt
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/** Lifecycle of an [EmbeddedAppSession] as shown by the card. */
enum class EmbedState { CONNECTING, RUNNING, UNAVAILABLE }

/**
 * Runs [packageName] inside a SurfaceView: its surface backs a VirtualDisplay owned by the
 * launcher, the root adb shell moves the app's stack onto that display, and [TouchInjector] (as root)
 * replays the card's touches there. All adb I/O happens on one background thread, in order.
 *
 * Sessions outlive the card's composition (see [of]): the pager or a sheet may dispose the card,
 * and releasing the display would close the app with it.
 *
 * A SurfaceView (not a TextureView) keeps the app's frames out of the launcher's window: the
 * system composes them as their own layer, so the launcher doesn't redraw for every map frame.
 * Its surface also goes away while the home screen is hidden, which turns the display off and
 * stops the app drawing frames nobody sees.
 */
class EmbeddedAppSession private constructor(
    private val context: Context,
    private val packageName: String,
) : SurfaceHolder.Callback {

    /** Last state, read by a card that (re)attaches; [listener] hears the changes. Main thread. */
    var state = EmbedState.CONNECTING
        private set
    var listener: ((EmbedState) -> Unit)? = null

    private val io = Executors.newSingleThreadScheduledExecutor()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var display: VirtualDisplay? = null
    private var watch: ScheduledFuture<*>? = null
    // Written on the io thread: the app was last seen running on the display.
    @Volatile private var running = false
    private var touch: AdbStream? = null

    /**
     * Fraction of the card's resolution the app draws at; the system scales its frames up to the
     * card. The app draws fewer pixels, the map is softer. Main thread.
     */
    var renderScale = 1f
        set(value) {
            if (field == value) return
            field = value
            sized?.let { (holder, width, height) -> fit(holder, width, height) }
        }

    // The card's view and its size in px, to re-fit when the resolution changes (main thread).
    private var sized: Triple<SurfaceHolder, Int, Int>? = null

    /** Sizes [holder]'s buffer for a card of [width]×[height] px at [renderScale]. */
    fun fit(holder: SurfaceHolder, width: Int, height: Int) {
        sized = Triple(holder, width, height)
        if (renderScale >= 1f) holder.setSizeFromLayout()
        else holder.setFixedSize((width * renderScale).roundToInt(), (height * renderScale).roundToInt())
    }

    override fun surfaceCreated(holder: SurfaceHolder) = Unit

    // Always follows surfaceCreated with the size.
    @SuppressLint("WrongConstant") // FLAGS includes the hidden DESTROY_CONTENT_ON_REMOVAL.
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // Same layout in dp at any resolution: the density scales with the buffer.
        val dpi = (context.resources.displayMetrics.densityDpi * renderScale).roundToInt()
        display?.let {
            val reattached = it.surface !== holder.surface
            it.surface = holder.surface
            it.resize(width, height, dpi)
            // The app may have moved or been closed while the card was hidden: a quiet check
            // that never cold-starts an app the user closed or retries an unavailable card.
            if (reattached && !io.isShutdown) io.execute(::keepOnCard)
            return
        }
        display = context.getSystemService(DisplayManager::class.java)
            .createVirtualDisplay(NAME, width, height, dpi, holder.surface, FLAGS)
        start()
    }

    // Without a surface the display turns off; it survives until the card shows again.
    // A recomposed card may attach its new view before the old one lets go: only detach our own.
    override fun surfaceDestroyed(holder: SurfaceHolder) {
        if (sized?.first === holder) sized = null
        display?.takeIf { it.surface === holder.surface }?.surface = null
    }

    /** Connects (again) and puts the app on the display, starting it if it isn't running. */
    fun start(showProgress: Boolean = true) {
        if (io.isShutdown) return
        val displayId = display?.display?.displayId ?: return
        if (showProgress) post(EmbedState.CONNECTING)
        io.execute { connect(displayId) }
    }

    /**
     * While the home screen is visible, checks every few seconds that the app is still on the
     * card: opening it full screen moves its task to the main display, and the vendor's task
     * killer or "clear recents" closes it. Either way the card would freeze on its last frame.
     */
    fun resume() {
        if (io.isShutdown || watch != null) return
        watch = io.scheduleWithFixedDelay(::keepOnCard, 0, WATCH_SECONDS, TimeUnit.SECONDS)
    }

    fun pause() {
        watch?.cancel(false)
        watch = null
    }

    // Unavailable stays unavailable until the user retries: no silent reconnect loops.
    private fun keepOnCard() {
        val displayId = display?.display?.displayId ?: return
        if (!running) return
        runCatching {
            val stacks = StackList.findAll(RootAdb.connect(context).shell("am stack list").output, packageName)
            if (stacks.isEmpty() || stacks.any { it.displayId != displayId }) connect(displayId)
        }
    }

    // Runs on the io thread.
    private fun connect(displayId: Int) {
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
                // A splash activity hands off to a new task a moment later, so a cold start
                // re-checks for a few seconds; later stray tasks are caught by [keepOnCard].
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
            running = true
            post(EmbedState.RUNNING)
        } catch (e: Exception) {
            running = false
            RootAdb.reset()
            post(EmbedState.UNAVAILABLE)
        }
    }

    /** Forwards a touch on the card, scaled from card pixels to the display's. */
    fun onTouch(event: MotionEvent) {
        if (io.isShutdown) return
        val line = TouchLine(
            action = event.actionMasked,
            index = event.actionIndex,
            pointers = (0 until event.pointerCount).map { TouchPointer(event.getPointerId(it), event.getX(it) * renderScale, event.getY(it) * renderScale) },
        ).encode()
        io.execute {
            runCatching { touch?.sink?.apply { writeUtf8(line).writeByte('\n'.code).flush() } }
        }
    }

    /** Closes the helper and the display; the app's task on it is destroyed with it. */
    fun release() {
        pause()
        display?.release()
        display = null
        io.execute { runCatching { touch?.close() } }
        io.shutdown()
    }

    private fun post(state: EmbedState) = main.post {
        this.state = state
        listener?.invoke(state)
    }

    companion object {
        private val sessions = mutableMapOf<String, EmbeddedAppSession>()

        /** The running session for [packageName]; sessions of other apps are released. Main thread. */
        fun of(context: Context, packageName: String): EmbeddedAppSession {
            sessions.keys.filter { it != packageName }.forEach { sessions.remove(it)?.release() }
            return sessions.getOrPut(packageName) { EmbeddedAppSession(context.applicationContext, packageName) }
        }

        /** Closes every session (no embedded card left on the home screen). Main thread. */
        fun releaseAll() {
            sessions.values.forEach { it.release() }
            sessions.clear()
        }

        private const val NAME = "OpenCarEmbed"
        private const val WATCH_SECONDS = 2L

        // Hidden DisplayManager flag: removing the display finishes its activities instead of
        // moving them to the main screen.
        private const val DESTROY_CONTENT_ON_REMOVAL = 1 shl 8
        private const val FLAGS = DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or DESTROY_CONTENT_ON_REMOVAL
    }
}
