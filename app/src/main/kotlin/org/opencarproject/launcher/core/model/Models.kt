package org.opencarproject.launcher.core.model

import kotlinx.serialization.Serializable

/**
 * DTOs / data models shared by all layers.
 *
 * They are "shape carriers" (DTOs): they contain no business logic or references
 * to Android, so domain and JVM tests can use them without coupling.
 */

/** A grid cell (row, column). Also reused as `span` (height, width). */
@Serializable
data class Cell(val row: Int, val column: Int)

/** Grid dimensions in number of cells. */
@Serializable
data class GridSpec(val columns: Int, val rows: Int)

/** Item offered in the picker panel: a system widget or an app. */
@Serializable
data class CatalogItem(
    val kind: CatalogItemKind,
    /** Stable identifier: "package/class" (widget) or "package/activity" (app). */
    val key: String,
    val title: String,
    val packageName: String,
    val description: String = "",
    /** Initial size suggested by the widget (rows × columns); `null` = the default. */
    val suggestedSpan: Cell? = null,
)

/** Item already placed on the desktop. */
@Serializable
data class PlacedItem(
    /** Stable, unique identity of the placed instance (see [Companion.identity]). */
    val id: String,
    val kind: CatalogItemKind,
    /** Source component: "package/class" (widget) or "package/activity" (app). */
    val componentKey: String,
    /** Native appWidgetId for [CatalogItemKind.WIDGET]; `-1` for apps. */
    val widgetId: Int = -1,
    val cell: Cell,
    val span: Cell,
    val title: String,
    val packageName: String,
) {
    companion object {
        /**
         * Builds the stable identity of a placed instance. Widgets are
         * distinguished by their `appWidgetId` (two instances of the same provider can coexist);
         * apps, by their component (an app is placed only once).
         */
        fun identity(kind: CatalogItemKind, componentKey: String, widgetId: Int): String =
            when (kind) {
                CatalogItemKind.WIDGET -> "widget-$widgetId"
                CatalogItemKind.APP -> "app-$componentKey"
                // Built-in modules: a single instance per type.
                else -> "${kind.name.lowercase()}-$componentKey"
            }
    }
}

/** Launcher settings (persisted separately from the layout). */
data class LauncherSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Hides the system status and navigation bars (revealed by swiping from the edge). */
    val fullscreen: Boolean = false,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.AUTO,
    val weatherPosition: WeatherPosition = WeatherPosition.LEFT,
    val background: BackgroundStyle = BackgroundStyle.SOLID,
    /** Light particles over the gradient background. */
    val gradientParticles: Boolean = true,
    /** Speed scene (car/rocket) on the side opposite the weather; excludes centered weather. */
    val speedScene: Boolean = true,
    val speedSource: SpeedSource = SpeedSource.GPS,
    /** Above this speed the car turns into a rocket. */
    val rocketKmh: Int = 90,
    /** Circle showing the speed inside the top scene. */
    val showSpeedDial: Boolean = true,
    val speedDialStyle: SpeedDialStyle = SpeedDialStyle.CIRCLE,
    val speedUnit: SpeedUnit = SpeedUnit.AUTO,
    val vehicleShape: VehicleShape = VehicleShape.CAR,
    val vehicleColor: VehicleColor = VehicleColor.RED,
    val sceneTheme: SceneTheme = SceneTheme.CITY,
    val timeFormat: TimeFormat = TimeFormat.SYSTEM,
    val appMenuStyle: AppMenuStyle = AppMenuStyle.BOTTOM_SHEET,
    val accentColor: AccentColor = AccentColor.BLUE,
    val dockIconSize: DockIconSize = DockIconSize.MEDIUM,
    /** Label under the icon of desktop apps (icon only by default). */
    val showAppLabels: Boolean = false,
    /** Shortcuts ("package/activity") on each side of the bottom bar, in order. */
    val dockLeftApps: List<String> = emptyList(),
    val dockRightApps: List<String> = emptyList(),
    /** Items hidden by the user (everything visible by default). */
    val hiddenElements: Set<DockElement> = emptySet(),
    val appLanguage: AppLanguage = AppLanguage.SYSTEM,
    /** Zoom of the whole UI (1 = device density). */
    val uiScale: Float = 1f,
    /** Size of the app menu icons relative to the default. */
    val menuIconScale: Float = 1f,
    /** Neon glow on the screen edges following turns, acceleration and braking. */
    val motionGlow: Boolean = false,
    /** Multiplies the measured motion before thresholds (calibration for each car/sensor). */
    val motionGlowSensitivity: Float = 1f,
    /** How softly the glow glides between directions (0 = snappy, 1 = very soft). */
    val motionGlowSmoothness: Float = 0.5f,
    val motionGlowColor: GlowColor = GlowColor.ACCENT,
    /** Where the glow reads turns from. */
    val motionSource: MotionSource = MotionSource.SENSORS_AND_GPS,
    val moduleShape: ModuleShape = ModuleShape.ROUNDED,
    /** Corner radius of the module cards, in dp. */
    val moduleCornerDp: Int = 12,
    val assistantStyle: AssistantStyle = AssistantStyle.GOOGLE_ANIMATED,
    /** Colored halo and border around the assistant pill. */
    val assistantGlow: Boolean = true,
)

/** Current weather for the dock. */
data class Weather(
    /** Always in °C: the unit is applied on display, so changing it needs no new request. */
    val celsius: Double,
    val condition: WeatherCondition,
    /** Day/night as reported at fetch time; prefer `WeatherSchedule.isDay` with the sun times. */
    val isDay: Boolean,
    /** City resolved by the system geocoder; `null` if unavailable. */
    val city: String?,
    /** Today's sunrise and sunset there (Unix seconds), to tell day from night at any later time. */
    val sunriseSeconds: Long? = null,
    val sunsetSeconds: Long? = null,
    /** Where it was fetched, to refetch once the car has moved far enough. */
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
)

/** Device connected to the hotspot (only visible if the launcher is a system app). */
data class HotspotClient(val name: String?, val ip: String?, val mac: String?)

/**
 * Full definition of the layout persisted in DataStore.
 *
 * Columns are stored in "half cells" ([columnScale] = 2): an app takes 1 column (half a
 * cell, 2 fit per cell) and everything else takes twice that. Layouts saved earlier lack the
 * field (it defaults to 1) and [toFineColumns] converts them without moving anything.
 */
@Serializable
data class LayoutDefinition(
    val grid: GridSpec = GridSpec(columns = 6, rows = 4),
    val items: List<PlacedItem> = emptyList(),
    val columnScale: Int = 1,
) {
    /** Converts a whole-cell layout to half cells (idempotent). */
    fun toFineColumns(): LayoutDefinition {
        if (columnScale == COLUMN_SCALE) return this
        val factor = COLUMN_SCALE / columnScale.coerceAtLeast(1)
        return copy(
            grid = grid.copy(columns = grid.columns * factor),
            items = items.map {
                it.copy(
                    cell = it.cell.copy(column = it.cell.column * factor),
                    span = it.span.copy(column = it.span.column * factor),
                )
            },
            columnScale = COLUMN_SCALE,
        )
    }

    companion object {
        /** Fine columns per visible cell. */
        const val COLUMN_SCALE = 2

        private const val PKG = "org.opencarproject.launcher"

        private fun builtin(kind: CatalogItemKind, key: String, title: String, cell: Cell, span: Cell) =
            PlacedItem(
                id = PlacedItem.identity(kind, key, -1),
                kind = kind,
                componentKey = key,
                cell = cell,
                span = span,
                title = title,
                packageName = PKG,
            )

        /** Head-unit main menu on first launch: navigation + player + notifications. */
        val DEFAULT = LayoutDefinition(
            items = listOf(
                builtin(CatalogItemKind.MAP, BuiltinKeys.MAP, "Navegación", Cell(0, 0), Cell(4, 3)),
                builtin(CatalogItemKind.MEDIA, BuiltinKeys.MEDIA, "Reproductor", Cell(0, 3), Cell(2, 3)),
                builtin(CatalogItemKind.NOTIFICATIONS, BuiltinKeys.NOTIFICATIONS, "Notificaciones", Cell(2, 3), Cell(2, 3)),
            ),
        ).toFineColumns()
    }
}

/** Component keys of the built-in modules (shared by the catalog and the default layout). */
object BuiltinKeys {
    const val MAP = "builtin/map"
    const val MEDIA = "builtin/media"
    const val NOTIFICATIONS = "builtin/notifications"
    const val CLIMATE = "builtin/climate"
}

/**
 * Immutable UI state of the launcher (single source of truth for the screen).
 * Not serialized: it is the live state derived from layout + catalog + interaction.
 */
data class LauncherUiState(
    val grid: GridSpec = GridSpec(columns = 6, rows = 4),
    val items: List<PlacedItem> = emptyList(),
    val catalog: List<CatalogItem> = emptyList(),
    val layoutMode: LayoutMode = LayoutMode.NORMAL,
    val panelState: PanelState = PanelState.CLOSED,
    val dragPhase: DragPhase = DragPhase.IDLE,
    val activeItemId: String? = null,
    val previewCell: Cell? = null,
    val previewSpan: Cell? = null,
)
