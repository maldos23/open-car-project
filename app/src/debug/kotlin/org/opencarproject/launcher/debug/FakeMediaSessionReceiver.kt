package org.opencarproject.launcher.debug

import android.content.BroadcastReceiver
import androidx.core.graphics.createBitmap
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.SystemClock

/**
 * Debug only. Publishes a fake MediaSession to test the player and the island:
 *
 * Test wallpaper: ... -a org.opencarproject.launcher.DEBUG_WALLPAPER
 *
 * adb shell am broadcast -n org.opencarproject.launcher/.debug.FakeMediaSessionReceiver \
 *   -a org.opencarproject.launcher.DEBUG_MEDIA --es title "Canción" --es artist "Artista" \
 *   --es album "Álbum" --ei color 0xE53935 --es state play|pause|stop --el duration 210000 --el position 30000
 */
class FakeMediaSessionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
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
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, intent.getStringExtra("title") ?: "Canción de prueba")
                .putString(MediaMetadata.METADATA_KEY_ARTIST, intent.getStringExtra("artist") ?: "Artista")
                .putString(MediaMetadata.METADATA_KEY_ALBUM, intent.getStringExtra("album") ?: "Álbum")
                .putLong(MediaMetadata.METADATA_KEY_DURATION, duration)
                .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, cover(intent.getIntExtra("color", 0xE53935)))
                .build(),
        )
        val playing = intent.getStringExtra("state") != "pause"
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS)
                .setState(
                    if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    intent.getLongExtra("position", 30_000),
                    if (playing) 1f else 0f,
                    SystemClock.elapsedRealtime(),
                )
                .build(),
        )
        session.isActive = true
    }

    /** Synthetic cover art: gradient of the requested color (to see the vibrant background). */
    private fun cover(rgb: Int): Bitmap {
        val bmp = createBitmap(256, 256)
        val color = 0xFF000000.toInt() or rgb
        val paint = Paint().apply { shader = LinearGradient(0f, 0f, 256f, 256f, color, 0xFF101010.toInt(), Shader.TileMode.CLAMP) }
        Canvas(bmp).drawRect(0f, 0f, 256f, 256f, paint)
        return bmp
    }

    companion object {
        private var session: MediaSession? = null
    }
}
