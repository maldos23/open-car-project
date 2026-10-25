package org.opencarproject.launcher.presentation.common

import org.opencarproject.launcher.R
import androidx.compose.ui.res.stringResource
import android.annotation.SuppressLint
import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NetworkWifi1Bar
import androidx.compose.material.icons.rounded.NetworkWifi2Bar
import androidx.compose.material.icons.rounded.NetworkWifi3Bar
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.SignalCellularAlt1Bar
import androidx.compose.material.icons.rounded.SignalCellularAlt2Bar
import androidx.compose.material.icons.rounded.SignalCellularOff
import androidx.compose.material.icons.rounded.SignalWifi0Bar
import androidx.compose.material.icons.rounded.SignalWifi4Bar
import androidx.compose.material.icons.rounded.SignalWifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import org.opencarproject.launcher.core.model.TimeFormat
import org.opencarproject.launcher.core.pure.NetworkLabels
import org.opencarproject.launcher.presentation.widget.rememberOnResume
import kotlinx.coroutines.delay
import java.util.Date
import java.util.Locale

/** Time (12/24 h per settings) and short date; updates when the minute changes. */
@Composable
fun ClockText(timeFormat: TimeFormat, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val use24h = when (timeFormat) {
        TimeFormat.SYSTEM -> DateFormat.is24HourFormat(context)
        TimeFormat.H12 -> false
        TimeFormat.H24 -> true
    }
    // Localized pattern ("h:mm a" / "HH:mm" depending on the language).
    val pattern = remember(use24h) { DateFormat.getBestDateTimePattern(Locale.getDefault(), if (use24h) "Hm" else "hma") }
    val now by produceState(Date()) {
        while (true) {
            value = Date()
            delay(60_000L - System.currentTimeMillis() % 60_000L)
        }
    }
    Column(modifier, horizontalAlignment = Alignment.Start) {
        Text(
            text = DateFormat.format(pattern, now).toString(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = DateFormat.format("EEE d MMM", now).toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Active system network: type, level 0..4 and technology label ("5G", "Wi-Fi 6"…). */
private data class Signal(val wifi: Boolean, val connected: Boolean, val level: Int, val type: String? = null)

/** Mobile network type reported by Android (network + 5G NSA / 4G+ override), if known. */
private data class CellDisplay(val networkType: Int, val override: Int)

/** Icon of the active network (Wi-Fi or cellular) with its signal level and technology. */
@Composable
fun SignalIndicator(modifier: Modifier = Modifier) {
    val signal = rememberSignal()
    val label = when {
        !signal.connected -> stringResource(R.string.signal_none)
        signal.wifi -> stringResource(R.string.signal_wifi, signal.type ?: "Wi-Fi", signal.level)
        else -> stringResource(R.string.signal_mobile, signal.type.orEmpty(), signal.level)
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (signal.connected && signal.type != null) {
            Text(
                text = signal.type,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(end = 6.dp),
            )
        }
        Icon(
            imageVector = signal.icon(),
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(32.dp),
        )
    }
}

/**
 * Current signal, updated by Android events (no polling):
 * - default network change (Wi-Fi ↔ cellular ↔ no network),
 * - Wi-Fi RSSI change (`RSSI_CHANGED_ACTION`),
 * - mobile network level and technology change (TelephonyCallback / PhoneStateListener).
 *
 * Mobile technology (4G/5G…) requires READ_PHONE_STATE; without it only the bars are shown.
 */
@Composable
private fun rememberSignal(): Signal {
    val context = LocalContext.current
    val hasPhoneState = rememberOnResume { hasPhoneStatePermission() }
    var cellDisplay by remember { mutableStateOf<CellDisplay?>(null) }
    var signal by remember { mutableStateOf(context.readSignal(cellDisplay, hasPhoneState)) }

    // Re-registers when the permission is granted (the technology listener requires it).
    DisposableEffect(context, hasPhoneState) {
        val refresh = { signal = context.readSignal(cellDisplay, hasPhoneState) }
        refresh()
        val mainHandler = Handler(Looper.getMainLooper())

        val cm = context.getSystemService(ConnectivityManager::class.java)
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = refresh()
            override fun onLost(network: Network) = refresh()
        }
        cm.registerDefaultNetworkCallback(networkCallback, mainHandler)

        val rssiReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) = refresh()
        }
        ContextCompat.registerReceiver(
            context,
            rssiReceiver,
            IntentFilter(WifiManager.RSSI_CHANGED_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        val stopTelephony = context.listenCell(hasPhoneState, onLevel = refresh) {
            cellDisplay = it
            refresh()
        }

        onDispose {
            cm.unregisterNetworkCallback(networkCallback)
            context.unregisterReceiver(rssiReceiver)
            stopTelephony()
        }
    }
    return signal
}

/**
 * Registers the mobile network listeners: level always; technology (incl. 5G NSA) on API 31+ if
 * READ_PHONE_STATE is granted. Returns how to unregister them.
 */
@Suppress("DEPRECATION") // PhoneStateListener: only option before API 31.
private fun Context.listenCell(
    hasPhoneState: Boolean,
    onLevel: () -> Unit,
    onDisplay: (CellDisplay) -> Unit,
): () -> Unit {
    val tm = getSystemService(TelephonyManager::class.java) ?: return {}
    return runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val level = object : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener {
                override fun onSignalStrengthsChanged(strength: SignalStrength) = onLevel()
            }
            tm.registerTelephonyCallback(mainExecutor, level)
            val display = if (hasPhoneState) {
                object : TelephonyCallback(), TelephonyCallback.DisplayInfoListener {
                    override fun onDisplayInfoChanged(info: TelephonyDisplayInfo) =
                        onDisplay(CellDisplay(info.networkType, info.overrideNetworkType))
                }.also { tm.registerTelephonyCallback(mainExecutor, it) }
            } else {
                null
            }
            val stop: () -> Unit = {
                tm.unregisterTelephonyCallback(level)
                display?.let(tm::unregisterTelephonyCallback)
            }
            stop
        } else {
            // Before API 31 the type is read on every refresh (dataNetworkType).
            val listener = object : PhoneStateListener() {
                override fun onSignalStrengthsChanged(strength: SignalStrength?) = onLevel()
            }
            tm.listen(listener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)
            val stop: () -> Unit = { tm.listen(listener, PhoneStateListener.LISTEN_NONE) }
            stop
        }
    }.getOrDefault {} // Head unit without a modem: no cellular level to listen to.
}

private fun Signal.icon(): ImageVector = when {
    wifi && !connected -> Icons.Rounded.SignalWifiOff
    wifi -> when (level) {
        0 -> Icons.Rounded.SignalWifi0Bar
        1 -> Icons.Rounded.NetworkWifi1Bar
        2 -> Icons.Rounded.NetworkWifi2Bar
        3 -> Icons.Rounded.NetworkWifi3Bar
        else -> Icons.Rounded.SignalWifi4Bar
    }
    !connected -> Icons.Rounded.SignalCellularOff
    else -> when (level) {
        0, 1 -> Icons.Rounded.SignalCellularAlt1Bar
        2 -> Icons.Rounded.SignalCellularAlt2Bar
        else -> Icons.Rounded.SignalCellularAlt
    }
}

@Suppress("DEPRECATION") // calculateSignalLevel(rssi, n) and connectionInfo: only options on API 26-30.
@SuppressLint("MissingPermission") // dataNetworkType is only read when hasPhoneState is true
private fun Context.readSignal(cellDisplay: CellDisplay?, hasPhoneState: Boolean): Signal {
    val cm = getSystemService(ConnectivityManager::class.java)
    val caps = runCatching { cm.getNetworkCapabilities(cm.activeNetwork) }.getOrNull()
        ?: return Signal(wifi = true, connected = false, level = 0)

    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
        // API 31+: the WifiInfo travels in the capabilities (no location permission).
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            caps.transportInfo as? WifiInfo
        } else {
            applicationContext.getSystemService(WifiManager::class.java).connectionInfo
        }
        val rssi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) caps.signalStrength else info?.rssi ?: -100
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && info != null) {
            NetworkLabels.wifi(info.wifiStandard, info.frequency)
        } else {
            null
        }
        return Signal(wifi = true, connected = true, level = WifiManager.calculateSignalLevel(rssi, 5), type = type)
    }

    val tm = getSystemService(TelephonyManager::class.java)
    val level = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        runCatching { tm.signalStrength?.level }.getOrNull() ?: 4
    } else {
        4
    }
    val type = when {
        cellDisplay != null -> NetworkLabels.cellular(cellDisplay.networkType, cellDisplay.override)
        hasPhoneState -> runCatching { NetworkLabels.cellular(tm.dataNetworkType) }.getOrNull()
        else -> null
    }
    return Signal(wifi = false, connected = true, level = level, type = type)
}

private fun Context.hasPhoneStatePermission(): Boolean =
    checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
