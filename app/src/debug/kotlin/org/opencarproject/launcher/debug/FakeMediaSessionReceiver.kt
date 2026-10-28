package org.opencarproject.launcher.debug

import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.opencarproject.launcher.core.model.DoorState
import org.opencarproject.launcher.core.model.ClimateState
import org.opencarproject.launcher.core.model.ClimateTemp
import org.opencarproject.launcher.data.source.ClimateSource
import org.opencarproject.launcher.data.source.DoorSource
import org.opencarproject.launcher.presentation.widget.VolumeOverlay
import org.opencarproject.launcher.data.source.SettingsDataSource
import android.content.BroadcastReceiver
import androidx.core.graphics.createBitmap
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.media.MediaDescription
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.SystemClock
import org.opencarproject.launcher.core.model.RadarSensor
import org.opencarproject.launcher.core.model.RadarState
import org.opencarproject.launcher.data.source.ReverseSource

/**
 * Debug only. Publishes a fake MediaSession to test the player and the island:
 *
 * Test wallpaper: ... -a org.opencarproject.launcher.DEBUG_WALLPAPER
 *
 * Fake parking radar (reverse overlay): ... -a org.opencarproject.launcher.DEBUG_RADAR \
 *   --es rear "-1,0,1,5,0,-1" [--es front ...] [--es left "0,0,3,0"] [--ez clear true]
 *
 * Fake climate reading (climate bar): ... -a org.opencarproject.launcher.DEBUG_CLIMATE \
 *   --ei temp 22 --ei fan 3 [--es air face|feet|front] [--ez off true] [--ez everywhere true]
 *   (the bar only shows on a change, so send two different readings)
 *
 * Fake doors (door alert): ... -a org.opencarproject.launcher.DEBUG_DOORS \
 *   --es open "fl,fr,rl,rr,trunk,hood" (any subset; "none" closes all) [--ez enable true]
 *
 * Launcher volume indicator on/off, or a fake change: ... -a org.opencarproject.launcher.DEBUG_VOLUME [--ez enable true] [--ei level 60]
 *
 * adb shell am broadcast -n org.opencarproject.launcher/.debug.FakeMediaSessionReceiver \
 *   -a org.opencarproject.launcher.DEBUG_MEDIA --es title "Canción" --es artist "Artista" \
 *   --es album "Álbum" --ei color 0xE53935 --es state play|pause|stop --el duration 210000 --el position 30000
 *
 * It also publishes a five-track queue with multicolor covers; picking one from the player
 * plays it (`--ez queue false` publishes none).
 */
class FakeMediaSessionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "org.opencarproject.launcher.DEBUG_RADAR") {
            // Zones per sensor, left to right (-1 none, 0 clear, 1 closest .. 10 farthest);
            // `--ez clear true` removes the radar ("No sensors").
            // Bumpers default to this car's layout (corners absent, inner sensors clear); sides to none.
            fun zones(key: String, default: String) =
                (intent.getStringExtra(key) ?: default).split(",").map { RadarSensor(it.trim().toIntOrNull() ?: -1) }
            ReverseSource.debugRadar(
                if (intent.getBooleanExtra("clear", false)) null
                else RadarState(
                    front = zones("front", "-1,0,0,0,0,-1"),
                    rear = zones("rear", "-1,0,0,0,0,-1"),
                    left = zones("left", "-1,-1,-1,-1"),
                    right = zones("right", "-1,-1,-1,-1"),
                ),
            )
            return
        }
        if (intent.action == "org.opencarproject.launcher.DEBUG_VOLUME") {
            // The setting is hidden without a CAN app: switch the indicator from here.
            if (intent.hasExtra("enable")) {
                val enable = intent.getBooleanExtra("enable", true)
                val app = context.applicationContext
                MainScope().launch { SettingsDataSource(app).update { it.copy(launcherVolumeIndicator = enable) } }
            }
            if (intent.hasExtra("level")) VolumeOverlay.debugShow(intent.getIntExtra("level", 0))
            return
        }
        if (intent.action == "org.opencarproject.launcher.DEBUG_DOORS") {
            if (intent.hasExtra("enable")) {
                // The setting is hidden without a CAN app: switch the launcher's alert on from here.
                val enable = intent.getBooleanExtra("enable", false)
                val app = context.applicationContext
                MainScope().launch { SettingsDataSource(app).update { it.copy(launcherDoorAlert = enable) } }
            }
            val open = intent.getStringExtra("open").orEmpty().split(",").map { it.trim() }.toSet()
            DoorSource.debugDoors(DoorState("fl" in open, "fr" in open, "rl" in open, "rr" in open, "trunk" in open, "hood" in open))
            return
        }
        if (intent.action == "org.opencarproject.launcher.DEBUG_CLIMATE") {
            if (intent.hasExtra("everywhere")) {
                // The climate settings are hidden without a CAN app: switch the bar on from here.
                val everywhere = intent.getBooleanExtra("everywhere", false)
                val app = context.applicationContext
                MainScope().launch { SettingsDataSource(app).update { it.copy(climateBar = true, climateBarEverywhere = everywhere) } }
            }
            val air = intent.getStringExtra("air") ?: "face"
            val temp = ClimateTemp.Value(intent.getIntExtra("temp", 22).toFloat(), fahrenheit = false)
            ClimateSource.debugClimate(
                ClimateState(
                    power = !intent.getBooleanExtra("off", false), ac = true, auto = false, recirculation = false, dual = false,
                    frontDefrost = air == "front", rearDefrost = false, airWindshield = air == "front",
                    airFace = air == "face", airFeet = air == "feet", fanSpeed = intent.getIntExtra("fan", 3), maxFanSpeed = 7,
                    leftTemp = temp, rightTemp = temp,
                ),
            )
            return
        }
        if (intent.action == "org.opencarproject.launcher.DEBUG_WALLPAPER") {
            // Test wallpaper: diagonal gradient of vivid colors.
            val bmp = createBitmap(1280, 800)
            val paint = Paint().apply {
                shader = LinearGradient(0f, 0f, 1280f, 800f, intArrayOf(0xFF00897B.toInt(), 0xFF3949AB.toInt(), 0xFFD81B60.toInt()), null, Shader.TileMode.CLAMP)
            }
            Canvas(bmp).drawRect(0f, 0f, 1280f, 800f, paint)
            android.app.WallpaperManager.getInstance(context).setBitmap(bmp)
            return
        }
        val session = session ?: MediaSession(context.applicationContext, "OpenCarProjectDebug").also { session = it }
        when (intent.getStringExtra("state") ?: "play") {
            "stop" -> {
                session.isActive = false
                session.release()
                Companion.session = null
                return
            }
            else -> Unit
        }
        val duration = intent.getLongExtra("duration", 210_000)
        val title = intent.getStringExtra("title") ?: "Canción de prueba"
        val artist = intent.getStringExtra("artist") ?: "Artista"
        val color = intent.getIntExtra("color", 0xE53935)
        session.setMetadata(metadata(title, artist, intent.getStringExtra("album") ?: "Álbum", duration, color))
        val playing = intent.getStringExtra("state") != "pause"
        val withQueue = intent.getBooleanExtra("queue", true)
        session.setQueue(if (withQueue) QUEUE.mapIndexed { i, (t, a, c) -> queueItem(i.toLong(), t, a, c) } else null)
        session.setQueueTitle(if (withQueue) "Road trip" else null)
        session.setCallback(object : MediaSession.Callback() {
            override fun onSkipToQueueItem(id: Long) {
                val (t, a, c) = QUEUE[id.toInt()]
                session.setMetadata(metadata(t, a, "Road trip", duration, c))
                session.setPlaybackState(state(true, 0, id))
            }
        })
        session.setPlaybackState(state(playing, intent.getLongExtra("position", 30_000), if (withQueue) 0 else -1))
        session.isActive = true
    }

    private fun metadata(title: String, artist: String, album: String, duration: Long, color: Int) =
        MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
            .putString(MediaMetadata.METADATA_KEY_ALBUM, album)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, duration)
            .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, cover(color))
            .build()

    private fun queueItem(id: Long, title: String, artist: String, color: Int) = MediaSession.QueueItem(
        MediaDescription.Builder().setMediaId("q$id").setTitle(title).setSubtitle(artist).setIconBitmap(cover(color)).build(),
        id,
    )

    private fun state(playing: Boolean, position: Long, activeId: Long) =
        PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_SKIP_TO_QUEUE_ITEM)
            .setState(if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, position, if (playing) 1f else 0f, SystemClock.elapsedRealtime())
            .setActiveQueueItemId(activeId)
            .build()

    /**
     * Synthetic cover art: the requested color, a second hue (its RGB channels rotated) and a
     * dark corner, so the player's multi-color background has something to pick from.
     */
    private fun cover(rgb: Int): Bitmap {
        val bmp = createBitmap(256, 256)
        val color = 0xFF000000.toInt() or rgb
        val second = 0xFF000000.toInt() or (rgb and 0xFFFF shl 8) or (rgb shr 16 and 0xFF)
        val paint = Paint().apply {
            shader = LinearGradient(0f, 0f, 256f, 256f, intArrayOf(color, second, 0xFF101010.toInt()), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        }
        Canvas(bmp).drawRect(0f, 0f, 256f, 256f, paint)
        return bmp
    }

    companion object {
        private var session: MediaSession? = null

        private val QUEUE = listOf(
            Triple("Canción de prueba", "Artista", 0xE53935),
            Triple("Highway Lights", "The Drivers", 0x1E88E5),
            Triple("Sunset Boulevard", "Coastline", 0xFB8C00),
            Triple("Night Ride", "Neon Avenue", 0x8E24AA),
            Triple("Open Road", "Mile Markers", 0x43A047),
        )
    }
}
