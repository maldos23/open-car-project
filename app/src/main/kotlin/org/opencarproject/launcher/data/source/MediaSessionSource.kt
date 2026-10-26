package org.opencarproject.launcher.data.source

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
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
    /** What the app says comes next (empty if it doesn't publish its queue). */
    val queue: List<QueueEntry> = emptyList(),
    val queueTitle: String = "",
    /** [QueueEntry.id] of the item playing now, or -1 if unknown. */
    val activeQueueId: Long = -1,
)

/** One item of the player app's queue; [id] is what `skipToQueueItem` takes. */
data class QueueEntry(val id: Long, val title: String, val subtitle: String, val art: Bitmap?)

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

    /** Registers the listener; if notification access wasn't granted yet, retries on every call. */
    fun start(context: Context) {
        users++
        register(context.applicationContext)
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

    private fun register(app: Context) {
        if (registered) return
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

    /**
     * Last track announced on [trackChanged]. Compared instead of the previous snapshot: players
     * load the next track's metadata while skipping (not yet playing), so by the time it plays the
     * snapshot already has the new title and the change would never be announced.
     */
    private var lastAnnouncedTrack: Triple<String, String, String>? = null

    private fun publish() {
        val next = controller?.toNowPlaying()
        _nowPlaying.value = next
        if (next == null || next.title.isBlank() || !next.isPlaying) return
        val track = Triple(next.packageName, next.title, next.artist)
        if (track != lastAnnouncedTrack) {
            lastAnnouncedTrack = track
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
            queue = queue.orEmpty().map {
                QueueEntry(it.queueId, it.description.title?.toString().orEmpty(), it.description.subtitle?.toString().orEmpty(), it.description.iconBitmap)
            },
            queueTitle = queueTitle?.toString().orEmpty(),
            activeQueueId = state?.activeQueueItemId ?: -1,
        )
    }
}
