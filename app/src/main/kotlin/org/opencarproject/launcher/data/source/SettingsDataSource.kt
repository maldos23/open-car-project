package org.opencarproject.launcher.data.source

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import org.opencarproject.launcher.core.model.AccentColor
import org.opencarproject.launcher.core.model.AppLanguage
import org.opencarproject.launcher.core.model.AssistantStyle
import org.opencarproject.launcher.core.model.BackgroundStyle
import org.opencarproject.launcher.core.model.AppMenuStyle
import org.opencarproject.launcher.core.model.DockElement
import org.opencarproject.launcher.core.model.DockIconSize
import org.opencarproject.launcher.core.model.GlowColor
import org.opencarproject.launcher.core.model.ModuleShape
import org.opencarproject.launcher.core.model.MotionSource
import org.opencarproject.launcher.core.model.LauncherSettings
import org.opencarproject.launcher.core.model.SceneTheme
import org.opencarproject.launcher.core.model.SpeedDialStyle
import org.opencarproject.launcher.core.model.SpeedSource
import org.opencarproject.launcher.core.model.SpeedUnit
import org.opencarproject.launcher.core.model.TemperatureUnit
import org.opencarproject.launcher.core.model.ThemeMode
import org.opencarproject.launcher.core.model.TimeFormat
import org.opencarproject.launcher.core.model.VehicleColor
import org.opencarproject.launcher.core.model.VehicleShape
import org.opencarproject.launcher.core.model.WeatherPosition
import org.opencarproject.launcher.core.pure.ModuleCornerRange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Dedicated DataStore: settings are kept separate from the layout. */
private val Context.settingsDataStore by preferencesDataStore(
    name = "launcher_settings",
    // A corrupt file falls back to defaults instead of crashing the home screen on every start.
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

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
    private val sceneThemeKey = stringPreferencesKey("scene_theme")
    private val hiddenElementsKey = stringSetPreferencesKey("hidden_elements")
    private val appMenuStyleKey = stringPreferencesKey("app_menu_style")
    private val accentColorKey = stringPreferencesKey("accent_color")
    private val dockLeftKey = stringPreferencesKey("dock_left_apps")
    private val dockIconSizeKey = stringPreferencesKey("dock_icon_size")
    private val appLabelsKey = booleanPreferencesKey("show_app_labels")
    private val smoothAnimationsKey = booleanPreferencesKey("smooth_animations")
    private val dockRightKey = stringPreferencesKey("dock_right_apps")
    private val appLanguageKey = stringPreferencesKey("app_language")
    private val uiScaleKey = floatPreferencesKey("ui_scale")
    private val menuIconScaleKey = floatPreferencesKey("menu_icon_scale")
    private val motionGlowKey = booleanPreferencesKey("motion_glow")
    private val motionGlowSensitivityKey = floatPreferencesKey("motion_glow_sensitivity")
    private val motionGlowSmoothnessKey = floatPreferencesKey("motion_glow_smoothness")
    private val motionGlowColorKey = stringPreferencesKey("motion_glow_color")
    private val motionSourceKey = stringPreferencesKey("motion_source")
    private val moduleShapeKey = stringPreferencesKey("module_shape")
    private val moduleCornerKey = intPreferencesKey("module_corner_dp")
    private val assistantStyleKey = stringPreferencesKey("assistant_style")
    private val embeddedMapAppKey = stringPreferencesKey("embedded_map_app")
    private val embeddedMapScaleKey = intPreferencesKey("embedded_map_scale")
    private val assistantGlowKey = booleanPreferencesKey("assistant_glow")
    private val climateBarKey = booleanPreferencesKey("climate_bar")
    private val climateBarEverywhereKey = booleanPreferencesKey("climate_bar_everywhere")
    private val reverseOverlayKey = booleanPreferencesKey("reverse_overlay")
    private val reverseCameraIdKey = stringPreferencesKey("reverse_camera_id")
    private val hideSystemReverseKey = booleanPreferencesKey("hide_system_reverse_view")
    private val reverseCleanImageKey = booleanPreferencesKey("reverse_clean_image")
    private val reverseDenoiseKey = floatPreferencesKey("reverse_denoise")
    private val reverseSharpnessKey = floatPreferencesKey("reverse_sharpness")
    private val reverseBrightnessKey = floatPreferencesKey("reverse_brightness")
    private val reverseContrastKey = floatPreferencesKey("reverse_contrast")
    private val floatingIslandKey = booleanPreferencesKey("floating_island")
    private val floatingIslandShrinkKey = booleanPreferencesKey("floating_island_shrink")
    private val floatingIslandXKey = floatPreferencesKey("floating_island_x")
    private val floatingIslandYKey = floatPreferencesKey("floating_island_y")

    val settingsFlow: Flow<LauncherSettings> = context.settingsDataStore.data.recoverIoErrors().map { it.toSettings() }

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
            prefs[sceneThemeKey] = next.sceneTheme.name
            prefs[hiddenElementsKey] = next.hiddenElements.map { it.name }.toSet()
            prefs[appMenuStyleKey] = next.appMenuStyle.name
            prefs[accentColorKey] = next.accentColor.name
            // One per line: preserves order (a preferences set would not).
            prefs[dockLeftKey] = next.dockLeftApps.joinToString("\n")
            prefs[dockIconSizeKey] = next.dockIconSize.name
            prefs[appLabelsKey] = next.showAppLabels
            prefs[smoothAnimationsKey] = next.smoothAnimations
            prefs[dockRightKey] = next.dockRightApps.joinToString("\n")
            prefs[appLanguageKey] = next.appLanguage.name
            prefs[uiScaleKey] = next.uiScale
            prefs[menuIconScaleKey] = next.menuIconScale
            prefs[motionGlowKey] = next.motionGlow
            prefs[motionGlowSensitivityKey] = next.motionGlowSensitivity
            prefs[motionGlowSmoothnessKey] = next.motionGlowSmoothness
            prefs[motionGlowColorKey] = next.motionGlowColor.name
            prefs[motionSourceKey] = next.motionSource.name
            prefs[moduleShapeKey] = next.moduleShape.name
            prefs[moduleCornerKey] = next.moduleCornerDp
            prefs[assistantStyleKey] = next.assistantStyle.name
            prefs[assistantGlowKey] = next.assistantGlow
            prefs[climateBarKey] = next.climateBar
            prefs[climateBarEverywhereKey] = next.climateBarEverywhere
            prefs[reverseOverlayKey] = next.reverseOverlay
            prefs[reverseCameraIdKey] = next.reverseCameraId
            prefs[hideSystemReverseKey] = next.hideSystemReverseView
            prefs[reverseCleanImageKey] = next.reverseCleanImage
            prefs[reverseDenoiseKey] = next.reverseDenoise
            prefs[reverseSharpnessKey] = next.reverseSharpness
            prefs[reverseBrightnessKey] = next.reverseBrightness
            prefs[reverseContrastKey] = next.reverseContrast
            prefs[floatingIslandKey] = next.floatingIsland
            prefs[floatingIslandShrinkKey] = next.floatingIslandShrink
            prefs[floatingIslandXKey] = next.floatingIslandX
            prefs[floatingIslandYKey] = next.floatingIslandY
            prefs[embeddedMapAppKey] = next.embeddedMapApp
            prefs[embeddedMapScaleKey] = next.embeddedMapScale
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
        sceneTheme = enumOr(this[sceneThemeKey], SceneTheme.CITY),
        appMenuStyle = enumOr(this[appMenuStyleKey], AppMenuStyle.BOTTOM_SHEET),
        accentColor = enumOr(this[accentColorKey], AccentColor.BLUE),
        dockIconSize = enumOr(this[dockIconSizeKey], DockIconSize.MEDIUM),
        showAppLabels = this[appLabelsKey] ?: false,
        smoothAnimations = this[smoothAnimationsKey] ?: false,
        dockLeftApps = this[dockLeftKey].orEmpty().lines().filter { it.isNotBlank() },
        dockRightApps = this[dockRightKey].orEmpty().lines().filter { it.isNotBlank() },
        hiddenElements = this[hiddenElementsKey].orEmpty()
            .mapNotNull { runCatching { DockElement.valueOf(it) }.getOrNull() }
            .toSet(),
        appLanguage = enumOr(this[appLanguageKey], AppLanguage.SYSTEM),
        uiScale = this[uiScaleKey] ?: 1f,
        menuIconScale = this[menuIconScaleKey] ?: 1f,
        motionGlow = this[motionGlowKey] ?: false,
        motionGlowSensitivity = this[motionGlowSensitivityKey] ?: 1f,
        motionGlowSmoothness = this[motionGlowSmoothnessKey] ?: 0.5f,
        motionGlowColor = enumOr(this[motionGlowColorKey], GlowColor.ACCENT),
        motionSource = enumOr(this[motionSourceKey], MotionSource.SENSORS_AND_GPS),
        moduleShape = enumOr(this[moduleShapeKey], ModuleShape.ROUNDED),
        moduleCornerDp = (this[moduleCornerKey] ?: 12).coerceIn(ModuleCornerRange),
        assistantStyle = enumOr(this[assistantStyleKey], AssistantStyle.GOOGLE_ANIMATED),
        assistantGlow = this[assistantGlowKey] ?: true,
        climateBar = this[climateBarKey] ?: false,
        climateBarEverywhere = this[climateBarEverywhereKey] ?: false,
        reverseOverlay = this[reverseOverlayKey] ?: false,
        reverseCameraId = this[reverseCameraIdKey] ?: "1",
        hideSystemReverseView = this[hideSystemReverseKey] ?: false,
        reverseCleanImage = this[reverseCleanImageKey] ?: false,
        reverseDenoise = (this[reverseDenoiseKey] ?: 0.8f).coerceIn(0f, 1f),
        reverseSharpness = (this[reverseSharpnessKey] ?: 0.2f).coerceIn(0f, 1f),
        reverseBrightness = (this[reverseBrightnessKey] ?: 0.5f).coerceIn(0f, 1f),
        reverseContrast = (this[reverseContrastKey] ?: 0.5f).coerceIn(0f, 1f),
        floatingIsland = this[floatingIslandKey] ?: false,
        floatingIslandShrink = this[floatingIslandShrinkKey] ?: false,
        floatingIslandX = (this[floatingIslandXKey] ?: 0.5f).coerceIn(0f, 1f),
        floatingIslandY = (this[floatingIslandYKey] ?: 0f).coerceIn(0f, 1f),
        embeddedMapApp = this[embeddedMapAppKey] ?: "",
        embeddedMapScale = (this[embeddedMapScaleKey] ?: 100).coerceIn(50, 100),
    )

    /** Stored value, or the default if missing or from a version that no longer exists. */
    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default
}
