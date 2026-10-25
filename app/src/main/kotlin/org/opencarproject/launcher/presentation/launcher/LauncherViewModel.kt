package org.opencarproject.launcher.presentation.launcher

import org.opencarproject.launcher.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.opencarproject.launcher.core.model.AccentColor
import org.opencarproject.launcher.core.model.AppMenuStyle
import org.opencarproject.launcher.core.model.BackgroundStyle
import org.opencarproject.launcher.core.model.CatalogItem
import org.opencarproject.launcher.core.model.CatalogItemKind
import org.opencarproject.launcher.core.model.Cell
import org.opencarproject.launcher.core.model.DockElement
import org.opencarproject.launcher.core.model.DragPhase
import org.opencarproject.launcher.core.model.DockIconSize
import org.opencarproject.launcher.core.model.DockSide
import org.opencarproject.launcher.core.model.AppLanguage
import org.opencarproject.launcher.core.model.GridSpec
import org.opencarproject.launcher.core.model.LayoutDefinition
import org.opencarproject.launcher.core.model.LayoutMode
import org.opencarproject.launcher.core.model.LauncherSettings
import org.opencarproject.launcher.core.model.LauncherUiState
import org.opencarproject.launcher.core.model.PanelState
import org.opencarproject.launcher.core.model.PlacedItem
import org.opencarproject.launcher.core.model.SpeedDialStyle
import org.opencarproject.launcher.core.model.SpeedSource
import org.opencarproject.launcher.core.model.SpeedUnit
import org.opencarproject.launcher.core.model.TemperatureUnit
import org.opencarproject.launcher.core.model.ThemeMode
import org.opencarproject.launcher.core.model.TimeFormat
import org.opencarproject.launcher.core.model.VehicleColor
import org.opencarproject.launcher.core.model.VehicleShape
import org.opencarproject.launcher.core.model.WeatherPosition
import org.opencarproject.launcher.domain.usecase.AddBoundWidget
import org.opencarproject.launcher.domain.usecase.AddItem
import org.opencarproject.launcher.domain.usecase.AddItemResult
import org.opencarproject.launcher.domain.usecase.ChangeGridSize
import org.opencarproject.launcher.domain.usecase.DeleteWidgetId
import org.opencarproject.launcher.domain.usecase.LaunchApp
import org.opencarproject.launcher.domain.usecase.LaunchAssistant
import org.opencarproject.launcher.domain.usecase.LoadLayout
import org.opencarproject.launcher.domain.usecase.MoveItem
import org.opencarproject.launcher.domain.usecase.ObserveCatalog
import org.opencarproject.launcher.domain.usecase.ObserveSettings
import org.opencarproject.launcher.domain.usecase.RemoveItem
import org.opencarproject.launcher.domain.usecase.ResizeItem
import org.opencarproject.launcher.domain.usecase.UpdateSettings
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Transient interaction state, kept separate from the persisted layout. */
private data class InteractionState(
    val layoutMode: LayoutMode = LayoutMode.NORMAL,
    val panelState: PanelState = PanelState.CLOSED,
    val dragPhase: DragPhase = DragPhase.IDLE,
    val activeItemId: String? = null,
    val previewCell: Cell? = null,
    val previewSpan: Cell? = null,
)

/**
 * Launcher ViewModel: orchestrates state between the persisted layout, the catalog and
 * user interaction. Knows nothing about Android or Compose: only flows and use cases.
 */
class LauncherViewModel(
    private val observeCatalog: ObserveCatalog,
    private val loadLayout: LoadLayout,
    private val addItem: AddItem,
    private val addBoundWidget: AddBoundWidget,
    private val deleteWidgetId: DeleteWidgetId,
    private val removeItem: RemoveItem,
    private val moveItem: MoveItem,
    private val resizeItem: ResizeItem,
    private val launchApp: LaunchApp,
    private val launchAssistant: LaunchAssistant,
    observeSettings: ObserveSettings,
    private val updateSettings: UpdateSettings,
    private val changeGridSize: ChangeGridSize,
) : ViewModel() {

    private val layout = MutableStateFlow(LayoutDefinition())
    private val layoutLoaded = MutableStateFlow(false)
    private val interaction = MutableStateFlow(InteractionState())

    /** Asks the Activity for the system permission to bind that widget (`ACTION_APPWIDGET_BIND`). */
    private val _widgetBindRequest = MutableSharedFlow<Pair<Int, String>>(extraBufferCapacity = 1)
    val widgetBindRequest: SharedFlow<Pair<Int, String>> = _widgetBindRequest.asSharedFlow()

    /** Newly placed widget: the Activity opens its configuration if the provider declares one. */
    private val _widgetConfigureRequest = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val widgetConfigureRequest: SharedFlow<Int> = _widgetConfigureRequest.asSharedFlow()

    /** Single UI state, derived from the three sources. */
    val uiState: StateFlow<LauncherUiState> = combine(
        layout,
        observeCatalog(),
        interaction,
    ) { lay, catalog, inter ->
        LauncherUiState(
            grid = lay.grid,
            items = lay.items,
            catalog = catalog,
            layoutMode = inter.layoutMode,
            panelState = inter.panelState,
            dragPhase = inter.dragPhase,
            activeItemId = inter.activeItemId,
            previewCell = inter.previewCell,
            previewSpan = inter.previewSpan,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LauncherUiState())

    /** Settings (theme, fullscreen); the Activity applies them to the window. */
    val settings: StateFlow<LauncherSettings> = observeSettings()
        .stateIn(viewModelScope, SharingStarted.Eagerly, LauncherSettings())

    /**
     * `true` once the saved settings have been read: until then nothing is drawn (the wallpaper
     * shows through) to avoid briefly flashing the default theme and grid.
     */
    val ready: StateFlow<Boolean> = combine(observeSettings(), layoutLoaded) { _, loaded -> loaded }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Short messages for the user as string resources (the Activity shows them as Toasts). */
    private val _messages = MutableSharedFlow<Int>(extraBufferCapacity = 2)
    val messages: SharedFlow<Int> = _messages.asSharedFlow()

    /** Emitted when Home is pressed with the launcher already open: the screen returns to the desktop. */
    private val _homePressed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val homePressed: SharedFlow<Unit> = _homePressed.asSharedFlow()

    /** Reason for the last rejected grid change (cleared on the next valid one). */
    private val _gridError = MutableStateFlow<GridSpec?>(null)
    /** The last grid size rejected because the items don't fit (the UI words the error). */
    val gridError: StateFlow<GridSpec?> = _gridError.asStateFlow()

    init {
        viewModelScope.launch {
            layout.value = loadLayout().first()
            layoutLoaded.value = true
        }
    }

    // ---- Mode / panel ----

    /**
     * Home button: as in any launcher, leaves the desktop "clean": exits edit mode,
     * closes the add panel and tells the screen to close sheets and return to the main
     * page.
     */
    fun onHomePressed() {
        interaction.update {
            it.copy(
                layoutMode = LayoutMode.NORMAL,
                panelState = PanelState.CLOSED,
                dragPhase = DragPhase.IDLE,
                activeItemId = null,
                previewCell = null,
                previewSpan = null,
            )
        }
        _homePressed.tryEmit(Unit)
    }

    fun toggleEditMode() {
        interaction.update { inter ->
            val newMode = if (inter.layoutMode == LayoutMode.EDIT) LayoutMode.NORMAL else LayoutMode.EDIT
            inter.copy(
                layoutMode = newMode,
                dragPhase = DragPhase.IDLE,
                activeItemId = null,
                previewCell = null,
                previewSpan = null,
            )
        }
    }

    fun openPanel() = interaction.update { it.copy(panelState = PanelState.OPEN) }

    fun closePanel() = interaction.update { it.copy(panelState = PanelState.CLOSED) }

    // ---- Add / remove ----

    fun onAddItem(candidate: CatalogItem) {
        viewModelScope.launch {
            when (val result = addItem(candidate, layout.value)) {
                is AddItemResult.Success -> {
                    layout.value = result.layout
                    if (candidate.kind == CatalogItemKind.WIDGET) {
                        result.layout.items.lastOrNull()?.let { _widgetConfigureRequest.emit(it.widgetId) }
                    }
                }
                // Third-party launcher: the system asks the user for permission for this widget.
                is AddItemResult.NeedsWidgetPick -> _widgetBindRequest.emit(result.appWidgetId to result.provider)
                AddItemResult.NoSpace -> _messages.emit(R.string.grid_no_space)
            }
            closePanel()
        }
    }

    /** The user picked a widget in the system picker (permission already granted). */
    fun onWidgetPicked(appWidgetId: Int, provider: String) {
        val candidate = uiState.value.catalog
            .firstOrNull { it.kind == CatalogItemKind.WIDGET && it.key == provider }
        if (candidate == null) {
            onWidgetPickCancelled(appWidgetId)
            return
        }
        viewModelScope.launch {
            val updated = addBoundWidget(
                layout.value,
                appWidgetId,
                provider,
                candidate.title,
                candidate.packageName,
                candidate.suggestedSpan,
            )
            if (updated == null) {
                deleteWidgetId(appWidgetId)
                _messages.emit(R.string.grid_no_space)
                return@launch
            }
            layout.value = updated
            _widgetConfigureRequest.emit(appWidgetId)
        }
    }

    /** The user cancelled the widget configuration: remove it from the desktop. */
    fun onWidgetConfigureCancelled(appWidgetId: Int) {
        val item = layout.value.items.firstOrNull { it.kind == CatalogItemKind.WIDGET && it.widgetId == appWidgetId } ?: return
        onDeleteItem(item.id)
    }

    /** The user cancelled the permission: release the reserved id. */
    fun onWidgetPickCancelled(appWidgetId: Int) {
        deleteWidgetId(appWidgetId)
    }

    fun onDeleteItem(itemId: String) {
        viewModelScope.launch {
            layout.value = removeItem(layout.value, itemId)
        }
    }

    // ---- Drag gestures (move) ----

    fun onMoveStart(itemId: String) {
        val item = layout.value.items.find { it.id == itemId } ?: return
        interaction.update {
            it.copy(
                dragPhase = DragPhase.MOVING,
                activeItemId = itemId,
                previewCell = item.cell,
                previewSpan = null,
            )
        }
    }

    fun onMovePreview(cell: Cell) {
        interaction.update { it.copy(previewCell = cell) }
    }

    fun onMoveEnd() {
        val inter = interaction.value
        val itemId = inter.activeItemId
        val target = inter.previewCell
        if (itemId == null || target == null) return clearDrag()
        // The gesture is cleared at the same instant the new layout arrives, so the card
        // goes from "stuck to the finger" to settling in its new cell without first snapping back to its origin.
        viewModelScope.launch {
            val result = moveItem(layout.value, itemId, target)
            layout.value = result
            clearDrag()
        }
    }

    fun onMoveCancel() = clearDrag()

    // ---- Drag gestures (resize) ----

    fun onResizeStart(itemId: String) {
        val item = layout.value.items.find { it.id == itemId } ?: return
        interaction.update {
            it.copy(
                dragPhase = DragPhase.RESIZING,
                activeItemId = itemId,
                previewSpan = item.span,
                previewCell = null,
            )
        }
    }

    fun onResizePreview(span: Cell) {
        interaction.update { it.copy(previewSpan = span) }
    }

    fun onResizeEnd() {
        val inter = interaction.value
        val itemId = inter.activeItemId
        val span = inter.previewSpan
        if (itemId == null || span == null) return clearDrag()
        viewModelScope.launch {
            val result = resizeItem(layout.value, itemId, span)
            layout.value = result
            clearDrag()
        }
    }

    fun onResizeCancel() = clearDrag()

    // ---- Settings ----

    fun onFullscreenChange(enabled: Boolean) {
        viewModelScope.launch { updateSettings { it.copy(fullscreen = enabled) } }
    }

    fun onThemeChange(mode: ThemeMode) {
        viewModelScope.launch { updateSettings { it.copy(themeMode = mode) } }
    }

    fun onTemperatureUnitChange(unit: TemperatureUnit) {
        viewModelScope.launch { updateSettings { it.copy(temperatureUnit = unit) } }
    }

    fun onGradientParticlesChange(enabled: Boolean) {
        viewModelScope.launch { updateSettings { it.copy(gradientParticles = enabled) } }
    }

    fun onBackgroundChange(style: BackgroundStyle) {
        viewModelScope.launch { updateSettings { it.copy(background = style) } }
    }

    /** Centered weather and the speed scene are mutually exclusive: choosing "Centro" turns off the scene. */
    fun onWeatherPositionChange(position: WeatherPosition) {
        viewModelScope.launch {
            updateSettings {
                it.copy(weatherPosition = position, speedScene = it.speedScene && position != WeatherPosition.CENTER)
            }
        }
    }

    /** Enabling the scene while the weather is centered moves it to the left. */
    fun onSpeedSceneChange(enabled: Boolean) {
        viewModelScope.launch {
            updateSettings {
                val position = if (enabled && it.weatherPosition == WeatherPosition.CENTER) WeatherPosition.LEFT else it.weatherPosition
                it.copy(speedScene = enabled, weatherPosition = position)
            }
        }
    }

    fun onSpeedSourceChange(source: SpeedSource) {
        viewModelScope.launch { updateSettings { it.copy(speedSource = source) } }
    }

    fun onVehicleShapeChange(shape: VehicleShape) {
        viewModelScope.launch { updateSettings { it.copy(vehicleShape = shape) } }
    }

    fun onVehicleColorChange(color: VehicleColor) {
        viewModelScope.launch { updateSettings { it.copy(vehicleColor = color) } }
    }

    fun onShowSpeedDialChange(show: Boolean) {
        viewModelScope.launch { updateSettings { it.copy(showSpeedDial = show) } }
    }

    fun onSpeedDialStyleChange(style: SpeedDialStyle) {
        viewModelScope.launch { updateSettings { it.copy(speedDialStyle = style) } }
    }

    fun onSpeedUnitChange(unit: SpeedUnit) {
        viewModelScope.launch { updateSettings { it.copy(speedUnit = unit) } }
    }

    fun onRocketKmhChange(kmh: Int) {
        viewModelScope.launch { updateSettings { it.copy(rocketKmh = kmh.coerceIn(20, 200)) } }
    }

    fun onTimeFormatChange(format: TimeFormat) {
        viewModelScope.launch { updateSettings { it.copy(timeFormat = format) } }
    }

    fun onAccentColorChange(accent: AccentColor) {
        viewModelScope.launch { updateSettings { it.copy(accentColor = accent) } }
    }

    fun onShowAppLabelsChange(show: Boolean) {
        viewModelScope.launch { updateSettings { it.copy(showAppLabels = show) } }
    }

    fun onAppLanguageChange(language: AppLanguage) {
        viewModelScope.launch { updateSettings { it.copy(appLanguage = language) } }
    }

    fun onDockIconSizeChange(size: DockIconSize) {
        viewModelScope.launch { updateSettings { it.copy(dockIconSize = size) } }
    }

    /** Adds an app to one side of the bottom bar (without duplicating it on that side). */
    fun onDockAppAdd(side: DockSide, componentKey: String) {
        viewModelScope.launch {
            updateSettings {
                when (side) {
                    DockSide.LEFT -> if (componentKey in it.dockLeftApps) it else it.copy(dockLeftApps = it.dockLeftApps + componentKey)
                    DockSide.RIGHT -> if (componentKey in it.dockRightApps) it else it.copy(dockRightApps = it.dockRightApps + componentKey)
                }
            }
        }
    }

    fun onDockAppRemove(side: DockSide, componentKey: String) {
        viewModelScope.launch {
            updateSettings {
                when (side) {
                    DockSide.LEFT -> it.copy(dockLeftApps = it.dockLeftApps - componentKey)
                    DockSide.RIGHT -> it.copy(dockRightApps = it.dockRightApps - componentKey)
                }
            }
        }
    }

    fun onAppMenuStyleChange(style: AppMenuStyle) {
        viewModelScope.launch { updateSettings { it.copy(appMenuStyle = style) } }
    }

    fun onElementVisibilityChange(element: DockElement, visible: Boolean) {
        viewModelScope.launch {
            updateSettings {
                it.copy(hiddenElements = if (visible) it.hiddenElements - element else it.hiddenElements + element)
            }
        }
    }

    fun onGridChange(grid: GridSpec) {
        viewModelScope.launch {
            val result = changeGridSize(layout.value, grid)
            if (result == null) {
                _gridError.value = grid
            } else {
                layout.value = result
                _gridError.value = null
            }
        }
    }

    // ---- Direct interaction ----

    /** Long press anywhere on the desktop: enters edit mode. */
    fun enterEditMode() {
        if (interaction.value.layoutMode == LayoutMode.NORMAL) {
            interaction.update { it.copy(layoutMode = LayoutMode.EDIT) }
        }
    }

    /** Short tap in normal mode: launches the app if the item is an app. */
    fun onTapItem(item: PlacedItem) {
        if (interaction.value.layoutMode != LayoutMode.NORMAL) return
        if (item.kind == CatalogItemKind.APP) {
            launchApp(item.componentKey)
        }
    }

    /** Opens an app from the app drawer. */
    fun onLaunchApp(componentKey: String) = launchApp(componentKey)

    /** Invokes the voice assistant (Google Assistant). */
    fun onLaunchAssistant() = launchAssistant()

    private fun clearDrag() {
        interaction.update {
            it.copy(
                dragPhase = DragPhase.IDLE,
                activeItemId = null,
                previewCell = null,
                previewSpan = null,
            )
        }
    }
}
