package org.opencarproject.launcher.data.source

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import org.opencarproject.launcher.core.model.AccentColor
import org.opencarproject.launcher.core.model.BackgroundStyle
import org.opencarproject.launcher.core.model.AppMenuStyle
import org.opencarproject.launcher.core.model.DockElement
import org.opencarproject.launcher.core.model.DockIconSize
import org.opencarproject.launcher.core.model.LauncherSettings
import org.opencarproject.launcher.core.model.SpeedDialStyle
import org.opencarproject.launcher.core.model.SpeedSource
import org.opencarproject.launcher.core.model.SpeedUnit
import org.opencarproject.launcher.core.model.TemperatureUnit
import org.opencarproject.launcher.core.model.ThemeMode
import org.opencarproject.launcher.core.model.TimeFormat
import org.opencarproject.launcher.core.model.VehicleColor
import org.opencarproject.launcher.core.model.VehicleShape
import org.opencarproject.launcher.core.model.WeatherPosition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Dedicated DataStore: settings are kept separate from the layout. */
private val Context.settingsDataStore by preferencesDataStore(name = "launcher_settings")

/** Launcher settings in Preferences DataStore. */
class SettingsDataSource(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme_mode")
    private val fullscreenKey = booleanPreferencesKey("fullscreen")
    private val temperatureUnitKey = stringPreferencesKey("temperature_unit")
    private val timeFormatKey = stringPreferencesKey("time_format")
    private val weatherPositionKey = stringPreferencesKey("weather_position")
    private val backgroundKey = stringPreferencesKey("background_style")
    private val speedSceneKey = booleanPreferencesKey("speed_scene")
    private val particlesKey = booleanPreferencesKey("gradient_particles")
    private val speedSourceKey = stringPreferencesKey("speed_source")
    private val rocketKmhKey = intPreferencesKey("rocket_kmh")
    private val speedDialKey = booleanPreferencesKey("show_speed_dial")
    private val speedUnitKey = stringPreferencesKey("speed_unit")
    private val speedDialStyleKey = stringPreferencesKey("speed_dial_style")
    private val vehicleShapeKey = stringPreferencesKey("vehicle_shape")
    private val vehicleColorKey = stringPreferencesKey("vehicle_color")
    private val hiddenElementsKey = stringSetPreferencesKey("hidden_elements")
    private val appMenuStyleKey = stringPreferencesKey("app_menu_style")
    private val accentColorKey = stringPreferencesKey("accent_color")
    private val dockLeftKey = stringPreferencesKey("dock_left_apps")
    private val dockIconSizeKey = stringPreferencesKey("dock_icon_size")
    private val appLabelsKey = booleanPreferencesKey("show_app_labels")
    private val dockRightKey = stringPreferencesKey("dock_right_apps")

    val settingsFlow: Flow<LauncherSettings> = context.settingsDataStore.data.map { it.toSettings() }

    /** Reads, transforms and saves in a single DataStore transaction. */
    suspend fun update(transform: (LauncherSettings) -> LauncherSettings) {
        context.settingsDataStore.edit { prefs ->
            val next = transform(prefs.toSettings())
            prefs[themeKey] = next.themeMode.name
            prefs[fullscreenKey] = next.fullscreen
            prefs[temperatureUnitKey] = next.temperatureUnit.name
            prefs[timeFormatKey] = next.timeFormat.name
            prefs[weatherPositionKey] = next.weatherPosition.name
            prefs[backgroundKey] = next.background.name
            prefs[speedSceneKey] = next.speedScene
            prefs[particlesKey] = next.gradientParticles
            prefs[speedSourceKey] = next.speedSource.name
            prefs[rocketKmhKey] = next.rocketKmh
            prefs[speedDialKey] = next.showSpeedDial
            prefs[speedUnitKey] = next.speedUnit.name
            prefs[speedDialStyleKey] = next.speedDialStyle.name
            prefs[vehicleShapeKey] = next.vehicleShape.name
            prefs[vehicleColorKey] = next.vehicleColor.name
            prefs[hiddenElementsKey] = next.hiddenElements.map { it.name }.toSet()
            prefs[appMenuStyleKey] = next.appMenuStyle.name
            prefs[accentColorKey] = next.accentColor.name
            // One per line: preserves order (a preferences set would not).
            prefs[dockLeftKey] = next.dockLeftApps.joinToString("\n")
            prefs[dockIconSizeKey] = next.dockIconSize.name
            prefs[appLabelsKey] = next.showAppLabels
            prefs[dockRightKey] = next.dockRightApps.joinToString("\n")
        }
    }

    private fun Preferences.toSettings() = LauncherSettings(
        themeMode = enumOr(this[themeKey], ThemeMode.SYSTEM),
        fullscreen = this[fullscreenKey] ?: false,
        temperatureUnit = enumOr(this[temperatureUnitKey], TemperatureUnit.AUTO),
        timeFormat = enumOr(this[timeFormatKey], TimeFormat.SYSTEM),
        weatherPosition = enumOr(this[weatherPositionKey], WeatherPosition.LEFT),
        background = enumOr(this[backgroundKey], BackgroundStyle.SOLID),
        speedScene = this[speedSceneKey] ?: true,
        gradientParticles = this[particlesKey] ?: true,
        speedSource = enumOr(this[speedSourceKey], SpeedSource.GPS),
        rocketKmh = this[rocketKmhKey] ?: 90,
        showSpeedDial = this[speedDialKey] ?: true,
        speedUnit = enumOr(this[speedUnitKey], SpeedUnit.AUTO),
        speedDialStyle = enumOr(this[speedDialStyleKey], SpeedDialStyle.CIRCLE),
        vehicleShape = enumOr(this[vehicleShapeKey], VehicleShape.CAR),
        vehicleColor = enumOr(this[vehicleColorKey], VehicleColor.RED),
        appMenuStyle = enumOr(this[appMenuStyleKey], AppMenuStyle.BOTTOM_SHEET),
        accentColor = enumOr(this[accentColorKey], AccentColor.BLUE),
        dockIconSize = enumOr(this[dockIconSizeKey], DockIconSize.MEDIUM),
        showAppLabels = this[appLabelsKey] ?: false,
        dockLeftApps = this[dockLeftKey].orEmpty().lines().filter { it.isNotBlank() },
        dockRightApps = this[dockRightKey].orEmpty().lines().filter { it.isNotBlank() },
        hiddenElements = this[hiddenElementsKey].orEmpty()
            .mapNotNull { runCatching { DockElement.valueOf(it) }.getOrNull() }
            .toSet(),
    )

    /** Stored value, or the default if missing or from a version that no longer exists. */
    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default
}
