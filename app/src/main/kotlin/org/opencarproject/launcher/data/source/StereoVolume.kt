package org.opencarproject.launcher.data.source

import android.annotation.SuppressLint
import android.os.IBinder
import android.os.Parcel
import android.util.Log

/**
 * The head unit's own volume (QF firmware): its MCU drives the amplifier, so Android's music
 * stream is not what the speakers play nor what the stereo shows. Read and set through the
 * firmware's `mcu_service` (`android.qf.mcu.IMcuManager`, raw transactions); every change, from the
 * knob, the steering wheel or here, is announced with [ACTION_CHANGED].
 */
object StereoVolume {

    /** The firmware clamps its volume to 0..32. */
    const val MAX = 32
    const val ACTION_CHANGED = "com.qf.action.VOLUME_CHANGED"

    @get:SuppressLint("PrivateApi", "DiscouragedPrivateApi")
    private val service: IBinder? by lazy {
        runCatching {
            Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java).invoke(null, "mcu_service") as IBinder?
        }.getOrNull()?.takeIf { runCatching { it.interfaceDescriptor }.getOrNull() == DESCRIPTOR }
            .also { Log.i(TAG, if (it != null) "using the stereo's volume" else "no stereo volume, using Android's") }
    }

    /** Current volume, or null on head units without this firmware. */
    fun get(): Int? = call(GET_VOLUME) {}?.coerceIn(0, MAX)

    fun set(volume: Int) {
        call(SET_VOLUME) { writeByte(volume.coerceIn(0, MAX).toByte()) }
    }

    private fun call(code: Int, args: Parcel.() -> Unit): Int? {
        val binder = service ?: return null
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(DESCRIPTOR)
            data.args()
            binder.transact(code, data, reply, 0)
            reply.readException()
            if (code == GET_VOLUME) reply.readInt() else 0
        } catch (e: Exception) {
            Log.w(TAG, "mcu_service call $code failed", e)
            null
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    private const val TAG = "StereoVolume"
    private const val DESCRIPTOR = "android.qf.mcu.IMcuManager"
    // ponytail: transaction codes read from this firmware's IMcuManager$Stub; another build may differ.
    private const val GET_VOLUME = 12
    private const val SET_VOLUME = 13
}
