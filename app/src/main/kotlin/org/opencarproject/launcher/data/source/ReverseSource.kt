package org.opencarproject.launcher.data.source

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.opencarproject.launcher.core.model.RadarState
import org.opencarproject.launcher.core.pure.RadarProtocol

/**
 * Reverse gear and parking radar of the head unit. Reverse comes from the QF firmware's
 * `BACKCAR_START` / `BACKCAR_STOP` broadcasts; [radar] from the CAN app's radar callback.
 * Binding the same vendor service as [ClimateSource] shares its connection (one per app).
 */
// Holds only the application context, which outlives any screen: nothing leaks.
@SuppressLint("StaticFieldLeak")
object ReverseSource {

    private val _reversing = MutableStateFlow(false)
    val reversing: StateFlow<Boolean> = _reversing.asStateFlow()

    private val _radar = MutableStateFlow<RadarState?>(null)
    val radar: StateFlow<RadarState?> = _radar.asStateFlow()

    private var app: Context? = null
    private var receiver: BroadcastReceiver? = null
    private var connection: ServiceConnection? = null
    private var scope: CoroutineScope? = null
    @Volatile private var bridge: QfVehicleBridge? = null
    @Volatile private var callback: IBinder? = null

    /** Starts listening; idempotent. */
    fun start(context: Context) {
        if (receiver != null) return
        val appContext = context.applicationContext
        app = appContext
        receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.action == ACTION_VENDOR_VIEW) {
                    if (hideVendorView && intent.getBooleanExtra("show", false)) closeVendorView(c)
                    return
                }
                _reversing.value = intent.action == ACTION_START
            }
        }.also {
            val filter = IntentFilter().apply { addAction(ACTION_START); addAction(ACTION_STOP); addAction(ACTION_VENDOR_VIEW) }
            // Sent by the system firmware, so the receiver must be exported.
            ContextCompat.registerReceiver(appContext, it, filter, ContextCompat.RECEIVER_EXPORTED)
        }
        // A reverse that began before this receiver existed (process just started) sent its
        // broadcast to nobody: take the current gear state from the firmware property instead.
        if (systemProperty("sys.qf.backcar_state") == "true") _reversing.value = true
        bindRadar(appContext)
    }

    /** (flipX, flipY) the vendor applies to the reverse video, so ours looks the same. */
    fun cameraMirror(): Pair<Boolean, Boolean> = RadarProtocol.cameraMirror(
        systemProperty("persist.sys.backcar_mirror")?.toIntOrNull() ?: 0,
        systemProperty("ro.qf.platform").orEmpty(),
    )

    @SuppressLint("PrivateApi")
    private fun systemProperty(name: String): String? = runCatching {
        Class.forName("android.os.SystemProperties").getMethod("get", String::class.java).invoke(null, name) as String
    }.getOrNull()

    fun stop() {
        val context = app ?: return
        receiver?.let { runCatching { context.unregisterReceiver(it) } }
        receiver = null
        val b = bridge
        val cb = callback
        val s = scope
        bridge = null; callback = null; scope = null
        // Unregister off the main thread, then drop the scope.
        s?.launch { if (b != null && cb != null) b.removeRadarCallback(cb) }?.invokeOnCompletion { s.cancel() }
        connection?.let { runCatching { context.unbindService(it) } }
        connection = null
        _reversing.value = false
        _radar.value = null
    }

    private fun bindRadar(context: Context) {
        val vendor = ClimateSource.vendor(context) ?: return
        val s = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scope = it }
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                s.launch {
                    val b = QfVehicleBridge.load(context, vendor, service) ?: return@launch
                    val cb = b.newCallback { frame ->
                        val state = RadarProtocol.parseRadar(frame) ?: return@newCallback
                        // Zones per sensor, for calibrating colors against the car (only on change).
                        if (state != _radar.value) Log.d(TAG, "front=${state.front.map { it.zone }} rear=${state.rear.map { it.zone }}")
                        _radar.value = state
                    }
                    if (scope !== s) return@launch // stopped while loading
                    bridge = b; callback = cb
                    b.addRadarCallback(cb)
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                // The vendor process died; the system rebinds by itself.
                bridge = null; callback = null
                _radar.value = null
            }
        }
        val intent = Intent(vendor.serviceAction).setPackage(vendor.packageName)
        if (runCatching { context.bindService(intent, conn, Context.BIND_AUTO_CREATE) }.getOrDefault(false)) connection = conn
    }

    /** Close the vendor's own reverse view whenever it appears ("Hide the system camera"). */
    @Volatile var hideVendorView = false

    /**
     * Closes the vendor's reverse view the way its camera key does (`USER_BACK_CAM`, data 0), as
     * soon as the vendor announces it is showing (`BACKCAR_VIEW`, show = true).
     * ponytail: one flash frame remains (the vendor draws before announcing); only a system app could prevent it.
     */
    private fun closeVendorView(context: Context) {
        Log.d(TAG, "vendor reverse view shown, closing it")
        context.sendBroadcast(Intent(ACTION_USER_BACK_CAM).setPackage(VENDOR_CAMERA_APP).putExtra("data", 0))
    }

    /** Debug builds only: radar frames injected without a car. */
    internal fun debugRadar(state: RadarState?) { _radar.value = state }

    private const val ACTION_USER_BACK_CAM = "com.qf.action.USER_BACK_CAM"
    private const val VENDOR_CAMERA_APP = "com.qf.backcar"
    private const val TAG = "ReverseSource"
    private const val ACTION_START = "com.qf.action.BACKCAR_START"
    private const val ACTION_STOP = "com.qf.action.BACKCAR_STOP"
    /** Sent by the vendor camera app when its full-screen view shows or hides. */
    private const val ACTION_VENDOR_VIEW = "com.qf.action.BACKCAR_VIEW"
}
