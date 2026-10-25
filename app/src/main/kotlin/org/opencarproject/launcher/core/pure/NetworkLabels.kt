package org.opencarproject.launcher.core.pure

/**
 * Short network-type labels for the signal indicator ("4G", "5G", "Wi-Fi 6"…).
 *
 * Takes the integers from `TelephonyManager.NETWORK_TYPE_*`, `TelephonyDisplayInfo.OVERRIDE_*`
 * and `ScanResult.WIFI_STANDARD_*` (stable SDK values) so it doesn't depend on Android.
 */
object NetworkLabels {

    // TelephonyManager.NETWORK_TYPE_*
    private const val GPRS = 1
    private const val EDGE = 2
    private const val UMTS = 3
    private const val CDMA = 4
    private const val EVDO_0 = 5
    private const val EVDO_A = 6
    private const val RTT_1X = 7
    private const val HSDPA = 8
    private const val HSUPA = 9
    private const val HSPA = 10
    private const val IDEN = 11
    private const val EVDO_B = 12
    private const val LTE = 13
    private const val EHRPD = 14
    private const val HSPAP = 15
    private const val GSM = 16
    private const val TD_SCDMA = 17
    private const val LTE_CA = 19
    private const val NR = 20

    // TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_* (what the status bar shows)
    private const val OVERRIDE_LTE_CA = 1
    private const val OVERRIDE_LTE_ADVANCED_PRO = 2
    private const val OVERRIDE_NR_NSA = 3
    private const val OVERRIDE_NR_NSA_MMWAVE = 4
    private const val OVERRIDE_NR_ADVANCED = 5

    // ScanResult.WIFI_STANDARD_*
    private const val WIFI_11N = 4
    private const val WIFI_11AC = 5
    private const val WIFI_11AX = 6
    private const val WIFI_11BE = 8

    /** 6 GHz band (MHz): Wi-Fi 6 on it is advertised as 6E. */
    private const val BAND_6GHZ_START = 5925
    private const val BAND_5GHZ_START = 4900

    /** Mobile network label; `override` wins because it's what the carrier advertises (5G NSA over LTE). */
    fun cellular(networkType: Int, override: Int = 0): String? = when (override) {
        OVERRIDE_NR_ADVANCED -> "5G+"
        OVERRIDE_NR_NSA, OVERRIDE_NR_NSA_MMWAVE -> "5G"
        OVERRIDE_LTE_CA, OVERRIDE_LTE_ADVANCED_PRO -> "4G+"
        else -> when (networkType) {
            GPRS -> "G"
            EDGE -> "E"
            CDMA, RTT_1X, IDEN, GSM -> "2G"
            UMTS, EVDO_0, EVDO_A, EVDO_B, HSDPA, HSUPA, HSPA, EHRPD, TD_SCDMA -> "3G"
            HSPAP -> "H+"
            LTE -> "4G"
            LTE_CA -> "4G+"
            NR -> "5G"
            else -> null
        }
    }

    /**
     * Commercial Wi-Fi generation; if it's legacy (802.11a/b/g) or unknown, the band
     * ("2.4 GHz"…). `null` only if the frequency is unknown too.
     */
    fun wifi(standard: Int, frequencyMhz: Int): String? = when (standard) {
        WIFI_11N -> "Wi-Fi 4"
        WIFI_11AC -> "Wi-Fi 5"
        WIFI_11AX -> if (frequencyMhz >= BAND_6GHZ_START) "Wi-Fi 6E" else "Wi-Fi 6"
        WIFI_11BE -> "Wi-Fi 7"
        else -> when {
            frequencyMhz >= BAND_6GHZ_START -> "6 GHz"
            frequencyMhz >= BAND_5GHZ_START -> "5 GHz"
            frequencyMhz > 0 -> "2.4 GHz"
            else -> null
        }
    }
}
