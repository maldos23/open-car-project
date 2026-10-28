package org.opencarproject.launcher.data.source

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.opencarproject.launcher.core.model.ClimateState
import org.opencarproject.launcher.core.model.ClimateStatus
import org.opencarproject.launcher.core.model.ClimateVendor
import org.opencarproject.launcher.core.pure.ClimateProtocol

/**
 * Climate control of the head unit's CAN app. The system decides everything: the car comes from
 * the vendor's car-mode property and the vendor turns
 * each command into frames for its CAN box; the launcher only listens to the AC events and sends
 * the commands the vendor already accepts. [start]/[stop] are reference-counted like
 * [MediaSessionSource].
 */
// Holds only the application context, which outlives any screen: nothing leaks.
@SuppressLint("StaticFieldLeak")
object ClimateSource {

    private const val TAG = "ClimateSource"
    private const val VENDOR_ASSET = "climate/qf.json"

    private val _status = MutableStateFlow<ClimateStatus>(ClimateStatus.Connecting)
    val status: StateFlow<ClimateStatus> = _status.asStateFlow()

    @Volatile private var users = 0
    private var app: Context? = null
    private var scope: CoroutineScope? = null
    private var connection: ServiceConnection? = null

    // Set while connected.
    @Volatile private var bridge: QfVehicleBridge? = null
    @Volatile private var callback: IBinder? = null

    fun start(context: Context) {
        if (users++ > 0) return
        val appContext = context.applicationContext
        app = appContext
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        _status.value = ClimateStatus.Connecting
        val vendor = vendor(appContext)
        if (vendor == null) {
            _status.value = ClimateStatus.Unavailable
            return
        }
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                scope?.launch { connect(appContext, vendor, service) }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                // The vendor process died; the system rebinds by itself.
                release()
                _status.value = ClimateStatus.Connecting
            }
        }
        val intent = Intent(vendor.serviceAction).setPackage(vendor.packageName)
        val bound = runCatching { appContext.bindService(intent, conn, Context.BIND_AUTO_CREATE) }.getOrDefault(false)
        if (bound) connection = conn else _status.value = ClimateStatus.Unavailable
    }

    /** Debug builds: a fake reading, for the emulator (no CAN service there). */
    internal fun debugClimate(state: ClimateState) { _status.value = ClimateStatus.Ready(null, state, emptySet()) }

    fun stop() {
        if (--users > 0) return
        users = 0
        val b = bridge
        val cb = callback
        val s = scope
        scope = null
        release()
        // Unregister before unbinding, off the main thread; then drop the scope.
        s?.launch { if (b != null && cb != null) b.removeAcCallback(cb) }?.invokeOnCompletion { s.cancel() }
        connection?.let { conn -> runCatching { app?.unbindService(conn) } }
        connection = null
        _status.value = ClimateStatus.Connecting
    }

    @Volatile private var cachedVendor: ClimateVendor? = null

    /** The vendor integration points from the asset (read once); also used by [ReverseSource]. */
    fun vendor(context: Context): ClimateVendor? = cachedVendor ?: runCatching {
        context.assets.open(VENDOR_ASSET).bufferedReader().use { it.readText() }
    }.getOrNull()?.let(ClimateProtocol::decodeVendor)?.also { cachedVendor = it }

    /** True only when this head unit has the vendor CAN app and its service can be bound. */
    fun isAvailable(context: Context): Boolean {
        val app = context.applicationContext
        val vendor = vendor(app) ?: return false
        return runCatching {
            app.packageManager.queryIntentServices(Intent(vendor.serviceAction).setPackage(vendor.packageName), 0).isNotEmpty()
        }.getOrDefault(false)
    }

    /**
     * Whether the vendor shows its own AC popup over every screen, read live from the system
     * setting it keeps; null when this head unit has no vendor CAN app.
     */
    fun nativePopup(context: Context): Flow<Boolean?> = callbackFlow {
        val app = context.applicationContext
        val vendor = vendor(app)
        if (vendor == null || !isAvailable(app)) {
            trySend(null)
            awaitClose()
            return@callbackFlow
        }
        // A unit whose vendor app never wrote the setting shows its popup (the vendor's default).
        fun read() = Settings.System.getInt(app.contentResolver, vendor.acPopupSetting, 0) == 0
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { trySend(read()) }
        }
        app.contentResolver.registerContentObserver(Settings.System.getUriFor(vendor.acPopupSetting), false, observer)
        trySend(read())
        awaitClose { app.contentResolver.unregisterContentObserver(observer) }
    }.distinctUntilChanged()

    /** Asks the vendor to show or hide its AC popup, with the same broadcast its SDK sends. */
    fun setNativePopup(context: Context, enabled: Boolean) {
        val vendor = vendor(context.applicationContext) ?: return
        context.sendBroadcast(
            Intent(vendor.popupAction)
                .setPackage(vendor.packageName)
                .putExtra(vendor.popupTypeExtra, vendor.acPopupType.toByte())
                .putExtra(vendor.popupEnableExtra, enabled),
        )
    }

    /** Sends a vendor `SettingType` (e.g. `"Power"`); ignored when not connected. */
    fun send(settingType: String) {
        val context = app ?: return
        val sent = bridge?.send(context, settingType) ?: return
        if (!sent) Log.w(TAG, "vendor has no SettingType $settingType")
    }

    private fun release() {
        bridge = null
        callback = null
    }

    @SuppressLint("PrivateApi")
    private fun connect(context: Context, vendor: ClimateVendor, service: IBinder) {
        val b = QfVehicleBridge.load(context, vendor, service)
        if (b == null) {
            _status.value = ClimateStatus.Unavailable
            return
        }
        if (users == 0) return // stopped while loading the vendor classes
        // Read again on every connection: the user may change the car in the vendor app.
        val carMode = runCatching {
            Class.forName("android.os.SystemProperties").getMethod("get", String::class.java)
                .invoke(null, vendor.carModeProperty) as String
        }.getOrNull()
        val vehicle = ClimateProtocol.parseCarMode(carMode)
        val commands = CARD_COMMANDS.filterTo(mutableSetOf()) { it in b.settingTypes }
        Log.i(TAG, "vehicle=$carMode settingTypes=${b.settingTypes.size} commands=${commands.size}")
        bridge = b

        val cb = b.newCallback { frame ->
            if (Log.isLoggable(TAG, Log.DEBUG)) Log.d(TAG, "frame " + frame.joinToString(" ") { "%02x".format(it) })
            val state = ClimateProtocol.parseAcFrame(frame, vendor.acFrameType) ?: return@newCallback
            _status.update { if (it is ClimateStatus.Ready) it.copy(state = state) else it }
        }
        callback = cb
        _status.value = ClimateStatus.Ready(vehicle, state = null, commands = commands)
        b.addAcCallback(cb)
    }

    /** Vendor `SettingType` names the card has buttons for. */
    private val CARD_COMMANDS = listOf(
        "Power", "Ac", "Auto", "Cycle", "FrontWindowFogClear", "BackWindowFogClear",
    ) + ClimateProtocol.AirModeCycle
}
