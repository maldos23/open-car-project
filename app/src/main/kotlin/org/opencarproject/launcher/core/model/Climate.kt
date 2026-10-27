package org.opencarproject.launcher.core.model

import kotlinx.serialization.Serializable

/**
 * Vehicle as configured by the head unit's own CAN app (`persist.sys.car_mode`, e.g.
 * `[Raise-3]-[Kia]-[Sportage]-[16(Import)]`). The launcher only reads it, never sets it.
 */
data class VehicleIdentity(
    val canProvider: String,
    val brand: String,
    val model: String,
    val year: String,
) {
    /** Human-readable form for messages ("Raise-3 · Kia Sportage 16(Import)"). */
    val label: String get() = listOf(canProvider, "·", brand, model, year).filter { it.isNotBlank() }.joinToString(" ")
}

/** One temperature reading of the climate control. */
sealed interface ClimateTemp {
    data object Low : ClimateTemp
    data object High : ClimateTemp
    data object Unknown : ClimateTemp
    data class Value(val degrees: Float, val fahrenheit: Boolean) : ClimateTemp
}

/** Climate state reported by the CAN service; air directions may be combined. */
data class ClimateState(
    val power: Boolean,
    val ac: Boolean,
    val auto: Boolean,
    val recirculation: Boolean,
    val dual: Boolean,
    val frontDefrost: Boolean,
    val rearDefrost: Boolean,
    val airWindshield: Boolean,
    val airFace: Boolean,
    val airFeet: Boolean,
    val fanSpeed: Int,
    val leftTemp: ClimateTemp,
    val rightTemp: ClimateTemp,
)

/**
 * Integration points of the head unit's CAN app, all by name (content of
 * `assets/climate/qf.json`): the launcher reads the vendor's classes and sends its own broadcast,
 * so the vendor maps each command to whatever CAN box and car the system is configured for.
 */
@Serializable
data class ClimateVendor(
    val packageName: String,
    val serviceAction: String,
    val featureStub: String,
    val callbackStub: String,
    val settingTypeClass: String,
    val controlAction: String,
    val controlExtra: String,
    val carModeProperty: String,
    val acFrameType: Int,
)

/** What the climate card can show. */
sealed interface ClimateStatus {
    /** Binding to the vendor service. */
    data object Connecting : ClimateStatus

    /** This head unit has no compatible CAN service. */
    data object Unavailable : ClimateStatus

    /**
     * Connected. [state] is null until the service reports one; [commands] are the vendor
     * `SettingType` names the card may send.
     */
    data class Ready(
        val vehicle: VehicleIdentity?,
        val state: ClimateState?,
        val commands: Set<String>,
    ) : ClimateStatus
}
