package org.opencarproject.launcher.data.source

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** System connectivity as events, for data that must recover as soon as the internet is back. */
object NetworkEvents {

    /** Emits each time the default network gains validated internet access (also on subscribe if already online). */
    fun internetAvailable(context: Context): Flow<Unit> = callbackFlow {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return@callbackFlow awaitClose { }
        var online = false
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                val now = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                // Only the offline → online edge: capabilities change often (signal, bandwidth).
                if (now && !online) trySend(Unit)
                online = now
            }

            override fun onLost(network: Network) {
                online = false
            }
        }
        runCatching { cm.registerDefaultNetworkCallback(callback) }
        awaitClose { runCatching { cm.unregisterNetworkCallback(callback) } }
    }
}
