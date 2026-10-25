package org.opencarproject.launcher.data.source

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
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
)

/**
 * The system's active media session, shared by the player and the island (which announces
 * track changes even when the card isn't on the home screen). Requires notification
 * access. [start]/[stop] are reference-counted: the first registers, the last releases.
 */
object MediaSessionSource {

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
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            // If another session started playing or this one became unusable, re-select.
            if (controller?.isUsable() == false) attach(manager?.activeSessionsOrEmpty()) else publish()
        }
        override fun onSessionDestroyed() = attach(manager?.activeSessionsOrEmpty())
    }
    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { attach(it) }
    private var listenerComponent: ComponentName? = null

    private var registered = false

    /** Registers the listener; if notification access wasn't granted yet, retries on every call. */
    fun start(context: Context) {
        users++
        if (registered) return
        val app = context.applicationContext
        val m = app.getSystemService(MediaSessionManager::class.java) ?: return
        manager = m
        listenerComponent = ComponentName(app, LauncherNotificationListener::class.java)
        registered = runCatching {
            m.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent, main)
            attach(m.activeSessionsOrEmpty())
        }.isSuccess
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
        val usable = sessions.orEmpty().filter { it.isUsable() }
        val pick = usable.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: usable.firstOrNull()
        if (pick?.sessionToken == controller?.sessionToken) return publish()
        controller?.unregisterCallback(callback)
        controller = pick?.also { it.registerCallback(callback, main) }
        publish()
    }

    private fun MediaController.isUsable(): Boolean {
        val state = playbackState?.state ?: PlaybackState.STATE_NONE
        val hasTrack = !metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).isNullOrBlank()
        val dead = state == PlaybackState.STATE_ERROR || state == PlaybackState.STATE_NONE || state == PlaybackState.STATE_STOPPED
        return hasTrack || !dead
    }

    private fun publish() {
        val c = controller
        val previous = _nowPlaying.value
        val next = c?.toNowPlaying()
        _nowPlaying.value = next
        // New track = title/artist changed and something is playing.
        if (next != null && next.title.isNotBlank() && next.isPlaying &&
            (previous == null || previous.title != next.title || previous.artist != next.artist)
        ) {
            _trackChanged.tryEmit(next)
        }
    }

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
        )
    }
}
