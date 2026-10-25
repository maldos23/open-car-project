package org.opencarproject.launcher.data.source

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import org.opencarproject.launcher.core.model.HotspotClient
import java.lang.reflect.Proxy
import java.util.concurrent.Executor

/**
 * Device hotspot state.
 *
 * - On/off: `WIFI_AP_STATE_CHANGED` broadcast, receivable by any app.
 * - Connected devices: `TetheringManager` is a system API; it only responds if the launcher
 *   is installed as a priv-app with `TETHER_PRIVILEGED` (see docs/HOTSPOT.md). Used via
 *   reflection because the public SDK doesn't expose it.
 */
object HotspotSource {

    private const val ACTION_AP_STATE = "android.net.wifi.WIFI_AP_STATE_CHANGED"
    private const val EXTRA_AP_STATE = "wifi_state"
    private const val AP_STATE_ENABLED = 13
    private const val TETHER_PRIVILEGED = "android.permission.TETHER_PRIVILEGED"

    /** Reports every on/off change; the broadcast is sticky, so the current state arrives on registration. */
    fun observeEnabled(context: Context, onChange: (Boolean) -> Unit): () -> Unit {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                onChange(intent.getIntExtra(EXTRA_AP_STATE, -1) == AP_STATE_ENABLED)
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(ACTION_AP_STATE),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        return { context.unregisterReceiver(receiver) }
    }

    /** `true` if the system granted the launcher access to the device list. */
    fun canListClients(context: Context): Boolean =
        context.checkSelfPermission(TETHER_PRIVILEGED) == PackageManager.PERMISSION_GRANTED

    /**
     * Reports every change in the connected-device list. Returns how to unregister,
     * or `null` if the launcher lacks system permissions (or the API doesn't exist on this version).
     */
    fun observeClients(context: Context, onChange: (List<HotspotClient>) -> Unit): (() -> Unit)? {
        if (!canListClients(context)) return null
        return runCatching {
            val manager = context.getSystemService("tethering") ?: return null
            val callbackClass = Class.forName("android.net.TetheringManager\$TetheringEventCallback")
            val callback = Proxy.newProxyInstance(callbackClass.classLoader, arrayOf(callbackClass)) { proxy, method, args ->
                when (method.name) {
                    "onClientsChanged" -> onChange((args?.firstOrNull() as? Collection<*>).orEmpty().mapNotNull(::toClient))
                    "hashCode" -> return@newProxyInstance System.identityHashCode(proxy)
                    "equals" -> return@newProxyInstance proxy === args?.firstOrNull()
                    "toString" -> return@newProxyInstance "LauncherTetheringCallback"
                }
                null // the remaining callbacks are `void`
            }
            manager.javaClass
                .getMethod("registerTetheringEventCallback", Executor::class.java, callbackClass)
                .invoke(manager, ContextCompat.getMainExecutor(context), callback)
            val unregister: () -> Unit = {
                runCatching {
                    manager.javaClass.getMethod("unregisterTetheringEventCallback", callbackClass).invoke(manager, callback)
                }
            }
            unregister
        }.getOrNull()
    }

    /** `TetheredClient` → [HotspotClient] (MAC + first IP/hostname reported by DHCP). */
    private fun toClient(raw: Any?): HotspotClient? = runCatching {
        raw ?: return null
        val mac = raw.javaClass.getMethod("getMacAddress").invoke(raw)?.toString()
        val address = (raw.javaClass.getMethod("getAddresses").invoke(raw) as? List<*>)?.firstOrNull()
        val hostname = address?.let { it.javaClass.getMethod("getHostname").invoke(it) as? String }
        val ip = address?.let { it.javaClass.getMethod("getAddress").invoke(it)?.toString()?.substringBefore('/') }
        HotspotClient(name = hostname?.takeIf { it.isNotBlank() }, ip = ip, mac = mac)
    }.getOrNull()
}
