package org.opencarproject.launcher.data.source

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.opencarproject.launcher.core.model.DoorState
import org.opencarproject.launcher.core.pure.RadarProtocol

/**
 * Doors, trunk and hood from the CAN app's door callback (same vendor service as [ClimateSource]).
 * The vendor only sends a frame when something changes, so [doors] is null until the first one.
 */
// Holds only the application context, which outlives any screen: nothing leaks.
@SuppressLint("StaticFieldLeak")
object DoorSource {

    private val _doors = MutableStateFlow<DoorState?>(null)
    val doors: StateFlow<DoorState?> = _doors.asStateFlow()

    private var app: Context? = null
    private var connection: ServiceConnection? = null
    private var scope: CoroutineScope? = null
    @Volatile private var bridge: QfVehicleBridge? = null
    @Volatile private var callback: IBinder? = null

    /** Starts listening; idempotent. */
    fun start(context: Context) {
        if (scope != null) return
        val appContext = context.applicationContext
        app = appContext
        val vendor = ClimateSource.vendor(appContext) ?: return
        val s = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scope = it }
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                s.launch {
                    val b = QfVehicleBridge.load(appContext, vendor, service) ?: return@launch
                    val cb = b.newCallback { frame -> RadarProtocol.parseDoors(frame)?.let { _doors.value = it } }
                    if (scope !== s) return@launch // stopped while loading
                    bridge = b; callback = cb
                    b.addDoorCallback(cb)
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                // The vendor process died; the system rebinds by itself.
                bridge = null; callback = null
            }
        }
        val intent = Intent(vendor.serviceAction).setPackage(vendor.packageName)
        if (runCatching { appContext.bindService(intent, conn, Context.BIND_AUTO_CREATE) }.getOrDefault(false)) connection = conn
    }

    fun stop() {
        val context = app ?: return
        val b = bridge
        val cb = callback
        val s = scope
        bridge = null; callback = null; scope = null
        // Unregister off the main thread, then drop the scope.
        s?.launch { if (b != null && cb != null) b.removeDoorCallback(cb) }?.invokeOnCompletion { s.cancel() }
        connection?.let { runCatching { context.unbindService(it) } }
        connection = null
        _doors.value = null
    }

    /**
     * Asks the vendor to show or hide its own door view, with the broadcast its SDK sends when
     * another app draws the doors (popup type 3). The vendor keeps the choice across restarts.
     */
    fun setVendorView(context: Context, show: Boolean) {
        val vendor = ClimateSource.vendor(context.applicationContext) ?: return
        context.sendBroadcast(
            Intent(vendor.popupAction)
                .setPackage(vendor.packageName)
                .putExtra(vendor.popupTypeExtra, vendor.doorPopupType.toByte())
                .putExtra(vendor.popupEnableExtra, show),
        )
    }

    /** Debug builds only: door frames injected without a car. */
    internal fun debugDoors(state: DoorState?) { _doors.value = state }
}
