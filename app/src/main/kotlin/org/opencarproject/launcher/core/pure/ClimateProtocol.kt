package org.opencarproject.launcher.core.pure

import kotlinx.serialization.json.Json
import org.opencarproject.launcher.core.model.ClimateVendor
import org.opencarproject.launcher.core.model.ClimateState
import org.opencarproject.launcher.core.model.ClimateTemp
import org.opencarproject.launcher.core.model.VehicleIdentity

/** Pure parts of the climate integration: vendor config, vehicle identity and AC frames. */
object ClimateProtocol {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Decodes the vendor asset; null when it's broken (the card then shows "unavailable"). */
    fun decodeVendor(text: String): ClimateVendor? =
        runCatching { json.decodeFromString(ClimateVendor.serializer(), text) }.getOrNull()

    /** `[Raise-3]-[Kia]-[Sportage]-[16(Import)]` → identity; null if it has no bracketed parts. */
    fun parseCarMode(value: String?): VehicleIdentity? {
        val parts = Regex("""\[([^\[\]]*)]""").findAll(value.orEmpty()).map { it.groupValues[1].trim() }.toList()
        if (parts.isEmpty() || parts[0].isEmpty()) return null
        return VehicleIdentity(parts[0], parts.getOrElse(1) { "" }, parts.getOrElse(2) { "" }, parts.getOrElse(3) { "" })
    }

    /**
     * Parses a QF SDK AC frame `[0x98, type, len, data…, 0xFF]` (layout of the vendor's
     * `QfSdkDataPack.packAcState`). Returns null for any other frame.
     */
    fun parseAcFrame(frame: ByteArray, frameType: Int): ClimateState? {
        if (frame.size < DATA + 7 || frame[0] != HEADER || frame[1].toInt() != frameType) return null
        fun bit(index: Int, bit: Int) = (frame[DATA + index].toInt() shr bit) and 1 == 1
        val fahrenheit = bit(4, 7)
        return ClimateState(
            power = bit(0, 7),
            ac = bit(0, 6),
            auto = bit(0, 2),
            recirculation = bit(0, 4),
            dual = bit(0, 0),
            frontDefrost = bit(1, 7),
            rearDefrost = bit(1, 6),
            airWindshield = bit(2, 7),
            airFace = bit(2, 6),
            airFeet = bit(2, 5),
            fanSpeed = frame[DATA + 2].toInt() and 0x0F,
            maxFanSpeed = frame.getOrNull(DATA + 19)?.let { (it.toInt() shr 4) and 0x0F } ?: 0,
            leftTemp = temp(frame[DATA + 5], fahrenheit),
            rightTemp = temp(frame[DATA + 6], fahrenheit),
        )
    }

    /** Vendor encoding (`QfSdkDataPack.getTemp`): 0 Low, 0xFF High, 0xFE none, else degrees × 2. */
    private fun temp(raw: Byte, fahrenheit: Boolean): ClimateTemp = when (val v = raw.toInt() and 0xFF) {
        0 -> ClimateTemp.Low
        0xFF -> ClimateTemp.High
        0xFE -> ClimateTemp.Unknown
        else -> ClimateTemp.Value(v / 2f, fahrenheit)
    }

    /**
     * Where a temperature sits between cold (0) and hot (1) for the blue-to-red bars: the
     * usual car range is 16–32 °C (60–90 °F); Low and High are the ends, unknown is null.
     */
    fun warmth(temp: ClimateTemp): Float? = when (temp) {
        ClimateTemp.Low -> 0f
        ClimateTemp.High -> 1f
        ClimateTemp.Unknown -> null
        is ClimateTemp.Value ->
            if (temp.fahrenheit) ((temp.degrees - 60f) / 30f).coerceIn(0f, 1f) else ((temp.degrees - 16f) / 16f).coerceIn(0f, 1f)
    }

    /**
     * Air direction as the vendor names it (`up` windshield, `parallel` face, `down` feet), e.g.
     * `parallel_down`; null when no direction is set.
     */
    fun airModeName(state: ClimateState): String? =
        listOfNotNull("up".takeIf { blowsWindshield(state) }, "parallel".takeIf { state.airFace }, "down".takeIf { state.airFeet })
            .takeIf { it.isNotEmpty() }?.joinToString("_")

    /**
     * Air goes to the windshield: its direction bit, or the front defroster when no other
     * direction is reported (some cars send "FRONT" mode that way).
     */
    fun blowsWindshield(state: ClimateState): Boolean =
        state.airWindshield || (state.frontDefrost && !state.airFace && !state.airFeet)

    /** Air direction cycle used by the card: face → face+feet → feet → windshield+feet. */
    val AirModeCycle = listOf("WindParallel", "WindParallelDown", "WindDown", "WindUpDown")

    /** The `SettingType` that comes after the current air direction. */
    fun nextAirMode(state: ClimateState?): String {
        val current = when {
            state == null -> -1
            state.airWindshield && state.airFeet -> 3
            state.airFace && state.airFeet -> 1
            state.airFeet -> 2
            state.airFace -> 0
            else -> -1
        }
        return AirModeCycle[(current + 1) % AirModeCycle.size]
    }

    private const val HEADER = 0x98.toByte()
    private const val DATA = 3
}
