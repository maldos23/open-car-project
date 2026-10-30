package org.opencarproject.launcher.core.model

import kotlinx.serialization.Serializable

/**
 * Centralized app enums. Grouped here so every module shares the same source of truth
 * (DRY) and there are no stray literals scattered through the code.
 */

/** Launcher interaction modes. */
enum class LayoutMode {
    /** Normal use: tap launches the app; long-press enters edit mode. */
    NORMAL,

    /** Edit: allows dragging, resizing and removing items. */
    EDIT,
}

/** Phases of the drag gesture on the grid. */
enum class DragPhase {
    IDLE,

    /** Moving an item between cells. */
    MOVING,

    /** Resizing an item (changing its span). */
    RESIZING,
}

/** State of the widget/app picker panel. */
enum class PanelState {
    CLOSED,
    OPEN,
}

/**
 * Type of catalog items and placed items.
 * It is serialized (persisted in the layout), hence [Serializable].
 */
@Serializable
enum class CatalogItemKind {
    /** System App Widget hosted via AppWidgetHost. */
    WIDGET,

    /** Launchable activity (app) shown as a card. */
    APP,

    /**
     * Navigation: next turn from Google Maps/Waze, or a button to open them. Keeps the name
     * `MAP` because that is how it is persisted in saved layouts.
     */
    MAP,

    /** Player for the active media session (system MediaSession). */
    MEDIA,

    /** List of active notifications (NotificationListenerService). */
    NOTIFICATIONS,

    /** Climate control of the car through the head unit's CAN service. */
    CLIMATE,

    /** Maps app running inside the card on a virtual display (needs root adb on the device). */
    EMBEDDED_MAP,
}

/** Weather condition (groups Open-Meteo WMO codes). */
enum class WeatherCondition {
    CLEAR,
    PARTLY_CLOUDY,
    CLOUDY,
    FOG,
    DRIZZLE,
    RAIN,
    SNOW,
    THUNDERSTORM,
    UNKNOWN,
}

/**
 * Bar elements the user can show or hide. The settings button is intentionally not
 * here: without it there would be no way to show them again.
 */
enum class DockElement {
    WEATHER,
    CLOCK,
    APPS,
    ASSISTANT,
    HOTSPOT,
    SIGNAL,
    VOLUME,
    NOTIFICATIONS,
}

/** App menu type. */
enum class AppMenuStyle {
    /** Sheet that slides up from the bottom (medium icons). */
    BOTTOM_SHEET,

    /**
     * Page to the right of the home screen: swiping left shifts the whole home screen and
     * shows the apps with large icons.
     */
    SWIPE_PAGE,
}

/** UI accent color. `hue` = hue used to generate the tones; `null` = custom palette. */
enum class AccentColor(val hue: Float?) {
    /** Blue generated from its hue (the upstream launcher's original color). */
    BLUE(205f),
    TEAL(175f),
    GREEN(140f),

    /** The Morado fork's hand-tuned purple: uses the base palette in Theme.kt. */
    PURPLE(null),
    PINK(330f),
    ORANGE(28f),
    RED(5f),

    /** Material You: wallpaper colors (Android 12+; purple on earlier versions). */
    DYNAMIC(null),
}

/** Bottom bar icon size: drawing and touch cell (dp). */
enum class DockIconSize(val iconDp: Int, val slotDp: Int) {
    SMALL(34, 48),
    MEDIUM(42, 56),
    LARGE(52, 64),
}

/** Side of the bottom bar for shortcuts. */
enum class DockSide {
    LEFT,
    RIGHT,
}

/** Launcher background. */
enum class BackgroundStyle {
    /** Flat theme color. */
    SOLID,

    /** System wallpaper behind the launcher. */
    WALLPAPER,

    /** Animated gradient using the theme and accent tones. */
    GRADIENT,
}

/** Silhouette of the speed scene (stored by name: append new ones, never rename). */
enum class VehicleShape { CAR, SUV, PICKUP, CAT, DOG, SPORTS, VAN, BUS, MOTORBIKE, PIXEL_CAT }

/** Scenery the speed scene drives through (the sky still follows time and weather). */
enum class SceneTheme { CITY, PIXEL, COUNTRYSIDE, BEACH, DESERT, SNOW, JUNGLE }

/** Silhouette colors (ARGB). */
enum class VehicleColor(val argb: Long) {
    RED(0xFFE53935),
    BLUE(0xFF1E88E5),
    GREEN(0xFF43A047),
    YELLOW(0xFFFDD835),
    BLACK(0xFF263238),
    WHITE(0xFFECEFF1),
    ORANGE(0xFFFB8C00),
    PURPLE(0xFF8E24AA),
}

/** Shape of the top bar speedometer. */
enum class SpeedDialStyle {
    /** Circle with the number and the unit below. */
    CIRCLE,

    /** Large number and unit on a single line (pill). */
    INLINE,
}

/** Speedometer unit. */
enum class SpeedUnit {
    /** Based on region (mph in the US, UK and a few others). */
    AUTO,
    KMH,
    MPH,
}

/** Where the top scene's speed comes from. */
enum class SpeedSource {
    /** GPS: real speed (requires precise location). */
    GPS,

    /** Motion sensors: rough estimate (stopped / slow / fast). */
    SENSORS,
}

/** Where the motion glow reads turns from. */
enum class MotionSource {
    /** Gyroscope and accelerometer once they prove alive, GPS otherwise: fastest response. */
    SENSORS_AND_GPS,

    /** GPS only: for head units whose motion sensors misbehave. */
    GPS_ONLY,
}

/** Where the weather goes in the top bar. */
enum class WeatherPosition {
    LEFT,
    CENTER,
    RIGHT,
}

/** Weather temperature unit. */
enum class TemperatureUnit {
    /** Based on the system region (°F in the US and a few others; °C elsewhere). */
    AUTO,
    CELSIUS,
    FAHRENHEIT,
}

/** Dock clock format. */
enum class TimeFormat {
    /** The system one (Settings > Date & time). */
    SYSTEM,
    H12,
    H24,
}

/** Color theme chosen in settings. */
enum class ThemeMode {
    /** Follows the system light/dark mode. */
    SYSTEM,
    LIGHT,
    DARK,
}

/**
 * UI language: [SYSTEM] follows the device; the others force a BCP 47 language (+ script) tag.
 * No region on purpose: the device region keeps driving the automatic units.
 */
enum class AppLanguage(val tag: String) {
    SYSTEM(""),
    ES("es"),
    EN("en"),
    ZH("zh-Hans"),
    PT("pt"),
    FR("fr"),
}

/** Color of the motion glow: [ACCENT] follows the theme; braking is always brake-light red. */
enum class GlowColor(val argb: Long?) {
    ACCENT(null),
    CYAN(0xFF00E5FF),
    GREEN(0xFF00E676),
    PURPLE(0xFFD500F9),
    PINK(0xFFFF4081),
    ORANGE(0xFFFF9100),
    WHITE(0xFFFFFFFF),
}

/** Outline of the built-in module cards (navigation, media, notifications). */
enum class ModuleShape {
    /** Plain rounded corners. */
    ROUNDED,

    /** Edge that waves inward like a flower. */
    SCALLOPED,
}

/** Look of the voice assistant button in the bottom bar. */
enum class AssistantStyle {
    /** Pill with the animated Google-colored halo and audio bars. */
    GOOGLE_ANIMATED,

    /** Pill with the default assistant app's own logo. */
    APP_LOGO,

    /** Just three animated sparkles in Google colors, no text. */
    SPARKLES,
}
