package org.opencarproject.launcher.data.source

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import androidx.core.content.edit
import java.io.File
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** What's playing now, read from the active MediaSession. */
data class NowPlaying(
    val packageName: String,
    val title: String,
    val artist: String,
    val album: String,
    val art: Bitmap?,
    val isPlaying: Boolean,
    val durationMs: Long,
    /** Position at [positionUpdatedAt] (`elapsedRealtime` clock); extrapolated using [speed]. */
    val positionMs: Long,
    val positionUpdatedAt: Long,
    val speed: Float,
    val sessionActivity: PendingIntent?,
    /** What the app says comes next (empty if it doesn't publish its queue). */
    val queue: List<QueueEntry> = emptyList(),
    val queueTitle: String = "",
    /** [QueueEntry.id] of the item playing now, or -1 if unknown. */
    val activeQueueId: Long = -1,
    /** Remembered from an earlier session that is gone: play wakes its app up again. */
    val restored: Boolean = false,
)

/** One item of the player app's queue; [id] is what `skipToQueueItem` takes. */
data class QueueEntry(val id: Long, val title: String, val subtitle: String, val art: Bitmap?)

/**
 * The system's active media session, shared by the player and the island (which announces
 * track changes even when the card isn't on the home screen). Requires notification
 * access. [start]/[stop] are reference-counted: the first registers, the last releases.
 */
object MediaSessionSource {

    private const val TAG = "OCP.Media"

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    /** Emitted when another track starts (not on pause/resume of the same one). */
    private val _trackChanged = MutableSharedFlow<NowPlaying>(extraBufferCapacity = 4)
    val trackChanged: SharedFlow<NowPlaying> = _trackChanged.asSharedFlow()

    var controller: MediaController? = null
        private set

    private var users = 0
    private var manager: MediaSessionManager? = null
    private val main = Handler(Looper.getMainLooper())

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onQueueChanged(queue: MutableList<MediaSession.QueueItem>?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            // If another session started playing or this one became unusable, re-select.
            if (controller?.isUsable() == false) attach(manager?.activeSessionsOrEmpty()) else publish()
        }
        override fun onSessionDestroyed() = attach(manager?.activeSessionsOrEmpty())
    }
    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { attach(it) }
    private var listenerComponent: ComponentName? = null

    private var registered = false
    private var app: Context? = null

    /** Registers the listener; if notification access wasn't granted yet, retries on every call. */
    fun start(context: Context) {
        users++
        app = context.applicationContext
        register(context.applicationContext)
    }

    /**
     * Safety net for missed `OnActiveSessionsChanged` callbacks: some head-unit ROMs (seen on a
     * Unisoc K706) never deliver them after the car wakes from sleep, so a player that starts a
     * new session leaves the card on "Nothing playing". Re-reads the sessions and, if the one we
     * would pick isn't the one we follow, registers the listener again and switches to it.
     */
    fun resync() {
        if (users == 0) return
        val context = app ?: return
        if (!registered) return register(context)
        val m = manager ?: return
        val sessions = m.activeSessionsOrEmpty()
        if (pick(sessions)?.sessionToken == controller?.sessionToken) return
        Log.i(TAG, "missed session change, resyncing")
        runCatching {
            m.removeOnActiveSessionsChangedListener(sessionsListener)
            m.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent, main)
        }.onFailure { Log.w(TAG, "can't re-register sessions listener", it) }
        attach(sessions)
    }

    /**
     * The notification listener just connected: access may have been granted after [start] (first
     * install, reinstall), so register now instead of waiting for the next [start], and re-read
     * the sessions that were already playing.
     */
    fun onListenerConnected(context: Context) {
        if (users == 0) return
        if (registered) attach(manager?.activeSessionsOrEmpty()) else register(context.applicationContext)
    }

    /**
     * The listener was unbound: the system may drop the sessions listener with it, so the next
     * [onListenerConnected] registers from scratch instead of only re-reading the sessions.
     */
    fun onListenerDisconnected() {
        if (!registered) return
        registered = false
        runCatching { manager?.removeOnActiveSessionsChangedListener(sessionsListener) }
    }

    private fun register(app: Context) {
        if (registered) return
        val m = app.getSystemService(MediaSessionManager::class.java) ?: return
        manager = m
        listenerComponent = ComponentName(app, LauncherNotificationListener::class.java)
        registered = runCatching {
            m.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent, main)
            attach(m.activeSessionsOrEmpty())
        }.onFailure { Log.w(TAG, "can't read media sessions", it) }.isSuccess
    }

    fun stop() {
        if (--users > 0) return
        users = 0
        registered = false
        runCatching { manager?.removeOnActiveSessionsChangedListener(sessionsListener) }
        controller?.unregisterCallback(callback)
        controller = null
        manager = null
        _nowPlaying.value = null
    }

    private fun MediaSessionManager.activeSessionsOrEmpty(): List<MediaController> =
        runCatching { getActiveSessions(listenerComponent) }.getOrDefault(emptyList())

    /**
     * Prefers the session that's playing; otherwise, the most recent one with something loaded.
     * Ignores those in error/stopped with no track (e.g. "Bluetooth disconnected").
     */
    private fun attach(sessions: List<MediaController>?) {
        val pick = pick(sessions)
        if (pick?.sessionToken == controller?.sessionToken) return publish()
        controller?.unregisterCallback(callback)
        controller = pick?.also { it.registerCallback(callback, main) }
        publish()
    }

    private fun pick(sessions: List<MediaController>?): MediaController? {
        val usable = sessions.orEmpty().filter { it.isUsable() }
        return usable.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: usable.firstOrNull()
    }

    private fun MediaController.isUsable(): Boolean {
        val state = playbackState?.state ?: PlaybackState.STATE_NONE
        val hasTrack = !metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).isNullOrBlank()
        val dead = state == PlaybackState.STATE_ERROR || state == PlaybackState.STATE_NONE || state == PlaybackState.STATE_STOPPED
        return hasTrack || !dead
    }

    /**
     * Last track announced on [trackChanged]. Compared instead of the previous snapshot: players
     * load the next track's metadata while skipping (not yet playing), so by the time it plays the
     * snapshot already has the new title and the change would never be announced.
     */
    private var lastAnnouncedTrack: Triple<String, String, String>? = null

    private fun publish() {
        val live = controller?.toNowPlaying()
        live?.let(::remember)
        val next = live ?: lastTrack()
        _nowPlaying.value = next
        if (next == null || next.title.isBlank() || !next.isPlaying) return
        val track = Triple(next.packageName, next.title, next.artist)
        if (track != lastAnnouncedTrack) {
            lastAnnouncedTrack = track
            _trackChanged.tryEmit(next)
        }
    }

    /**
     * Plays the live session or, when it's gone (car restarted, app killed), wakes the app of the
     * remembered track with a media-button press, which players handle by resuming where they were.
     * Apps without a media-button receiver are just opened.
     */
    fun play(context: Context) {
        controller?.let { return it.transportControls.play() }
        val pkg = last?.packageName ?: return
        val button = Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(pkg)
        if (context.packageManager.queryBroadcastReceivers(button, 0).isEmpty()) {
            context.packageManager.getLaunchIntentForPackage(pkg)?.let {
                runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            return
        }
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            context.sendBroadcast(Intent(button).putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(action, KeyEvent.KEYCODE_MEDIA_PLAY)))
        }
    }

    /** The track last seen playing, persisted in the app's data so it survives restarts and updates. */
    private var last: NowPlaying? = null
    private var lastLoaded = false

    private fun remember(track: NowPlaying) {
        if (track.title.isBlank()) return
        val old = last
        if (old != null && old.packageName == track.packageName && old.title == track.title && old.artist == track.artist && old.art === track.art) return
        last = track.copy(isPlaying = false, durationMs = 0, positionMs = 0, sessionActivity = null, queue = emptyList(), restored = true)
        lastLoaded = true
        val context = app ?: return
        thread(name = "last-track") {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
                putString("package", track.packageName).putString("title", track.title)
                putString("artist", track.artist).putString("album", track.album)
            }
            val file = File(context.filesDir, ART_FILE)
            runCatching { if (track.art == null) file.delete() else file.outputStream().use { track.art.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        }
    }

    private fun lastTrack(): NowPlaying? {
        if (!lastLoaded) {
            lastLoaded = true
            val context = app ?: return null
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val pkg = prefs.getString("package", null) ?: return null
            last = NowPlaying(
                packageName = pkg,
                title = prefs.getString("title", "").orEmpty(),
                artist = prefs.getString("artist", "").orEmpty(),
                album = prefs.getString("album", "").orEmpty(),
                art = runCatching { BitmapFactory.decodeFile(File(context.filesDir, ART_FILE).path) }.getOrNull(),
                isPlaying = false, durationMs = 0, positionMs = 0, positionUpdatedAt = 0, speed = 0f,
                sessionActivity = null, restored = true,
            )
        }
        // An app that was uninstalled can't be resumed.
        return last?.takeIf { t -> app?.packageManager?.getLaunchIntentForPackage(t.packageName) != null }
    }

    private const val PREFS = "last_track"
    private const val ART_FILE = "last_track_art.png"

    private fun MediaController.toNowPlaying(): NowPlaying {
        val meta = metadata
        val state = playbackState
        return NowPlaying(
            packageName = packageName,
            title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE).orEmpty(),
            artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE).orEmpty(),
            album = meta?.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty(),
            art = meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON),
            isPlaying = state?.state == PlaybackState.STATE_PLAYING || state?.state == PlaybackState.STATE_BUFFERING,
            durationMs = meta?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0 } ?: 0L,
            positionMs = state?.position ?: 0L,
            positionUpdatedAt = state?.lastPositionUpdateTime ?: 0L,
            speed = state?.playbackSpeed ?: 0f,
            sessionActivity = sessionActivity,
            queue = queue.orEmpty().map {
                QueueEntry(it.queueId, it.description.title?.toString().orEmpty(), it.description.subtitle?.toString().orEmpty(), it.description.iconBitmap)
            },
            queueTitle = queueTitle?.toString().orEmpty(),
            activeQueueId = state?.activeQueueItemId ?: -1,
        )
    }
}
