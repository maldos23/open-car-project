package org.opencarproject.launcher.presentation.launcher

import org.opencarproject.launcher.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.opencarproject.launcher.core.model.CatalogItem
import org.opencarproject.launcher.core.model.CatalogItemKind
import org.opencarproject.launcher.core.model.Cell
import org.opencarproject.launcher.core.model.DockElement
import org.opencarproject.launcher.core.model.DragPhase
import org.opencarproject.launcher.core.model.DockSide
import org.opencarproject.launcher.core.pure.MenuIconScaleRange
import org.opencarproject.launcher.core.pure.MotionGlowSensitivityRange
import org.opencarproject.launcher.core.pure.RocketKmhRange
import org.opencarproject.launcher.core.pure.UiScaleRange
import org.opencarproject.launcher.core.pure.snap
import org.opencarproject.launcher.core.model.GridSpec
import org.opencarproject.launcher.core.model.LayoutDefinition
import org.opencarproject.launcher.core.model.LayoutMode
import org.opencarproject.launcher.core.model.LauncherSettings
import org.opencarproject.launcher.core.model.LauncherUiState
import org.opencarproject.launcher.core.model.PanelState
import org.opencarproject.launcher.core.model.PlacedItem
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Transient interaction state, kept separate from the persisted layout. */
private data class InteractionState(
    val layoutMode: LayoutMode = LayoutMode.NORMAL,
    val panelState: PanelState = PanelState.CLOSED,
    val dragPhase: DragPhase = DragPhase.IDLE,
    val activeItemId: String? = null,
    val previewCell: Cell? = null,
    val previewSpan: Cell? = null,
) {
    /** No gesture in progress. */
    fun idle() = copy(dragPhase = DragPhase.IDLE, activeItemId = null, previewCell = null, previewSpan = null)
}

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

    /** Serializes layout changes: each one reads the result of the previous (no lost updates). */
    private val layoutMutex = Mutex()

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
            layoutMutex.withLock { layout.value = loadLayout().first() }
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
        interaction.update { it.idle().copy(layoutMode = LayoutMode.NORMAL, panelState = PanelState.CLOSED) }
        _homePressed.tryEmit(Unit)
    }

    fun toggleEditMode() {
        interaction.update { inter ->
            val newMode = if (inter.layoutMode == LayoutMode.EDIT) LayoutMode.NORMAL else LayoutMode.EDIT
            // The add panel only makes sense while editing (Back would leave it floating).
            inter.idle().copy(layoutMode = newMode, panelState = PanelState.CLOSED)
        }
    }

    fun openPanel() = interaction.update { it.copy(panelState = PanelState.OPEN) }

    fun closePanel() = interaction.update { it.copy(panelState = PanelState.CLOSED) }

    // ---- Add / remove ----

    fun onAddItem(candidate: CatalogItem) {
        mutateLayout { current ->
            val result = addItem(candidate, current)
            closePanel()
            when (result) {
                is AddItemResult.Success -> {
                    if (candidate.kind == CatalogItemKind.WIDGET) {
                        result.layout.items.lastOrNull()?.let { _widgetConfigureRequest.emit(it.widgetId) }
                    }
                    return@mutateLayout result.layout
                }
                // Third-party launcher: the system asks the user for permission for this widget.
                is AddItemResult.NeedsWidgetPick -> _widgetBindRequest.emit(result.appWidgetId to result.provider)
                AddItemResult.NoSpace -> _messages.emit(R.string.grid_no_space)
                AddItemResult.Unavailable -> _messages.emit(R.string.widget_unavailable)
            }
            null
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
        mutateLayout { current ->
            val updated = addBoundWidget(
                current,
                appWidgetId,
                provider,
                candidate.title,
                candidate.packageName,
                candidate.suggestedSpan,
            )
            if (updated == null) {
                deleteWidgetId(appWidgetId)
                _messages.emit(R.string.grid_no_space)
            } else {
                _widgetConfigureRequest.emit(appWidgetId)
            }
            updated
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
        mutateLayout { removeItem(it, itemId) }
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
        mutateLayout(onApplied = ::clearDrag) { moveItem(it, itemId, target) }
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
        mutateLayout(onApplied = ::clearDrag) { resizeItem(it, itemId, span) }
    }

    fun onResizeCancel() = clearDrag()

    // ---- Settings ----

    /** Plain setting changes (one field, no rule): the settings sheet passes the copy to apply. */
    fun onSettingsChange(transform: (LauncherSettings) -> LauncherSettings) {
        viewModelScope.launch { updateSettings(transform) }
    }

    /** Centered weather and the speed scene are mutually exclusive: choosing "Centro" turns off the scene. */
    fun onWeatherPositionChange(position: WeatherPosition) = onSettingsChange {
        it.copy(weatherPosition = position, speedScene = it.speedScene && position != WeatherPosition.CENTER)
    }

    /** Enabling the scene while the weather is centered moves it to the left. */
    fun onSpeedSceneChange(enabled: Boolean) = onSettingsChange {
        val position = if (enabled && it.weatherPosition == WeatherPosition.CENTER) WeatherPosition.LEFT else it.weatherPosition
        it.copy(speedScene = enabled, weatherPosition = position)
    }

    fun onRocketKmhChange(kmh: Int) = onSettingsChange { it.copy(rocketKmh = kmh.coerceIn(RocketKmhRange)) }

    fun onUiScaleChange(scale: Float) = onSettingsChange { it.copy(uiScale = snap(scale, 20, UiScaleRange)) }

    fun onMenuIconScaleChange(scale: Float) = onSettingsChange { it.copy(menuIconScale = snap(scale, 20, MenuIconScaleRange)) }

    fun onMotionGlowSensitivityChange(sensitivity: Float) = onSettingsChange {
        it.copy(motionGlowSensitivity = snap(sensitivity, 10, MotionGlowSensitivityRange))
    }

    /** Adds an app to one side of the bottom bar (without duplicating it on that side). */
    fun onDockAppAdd(side: DockSide, componentKey: String) = onSettingsChange {
        val apps = it.dockApps(side)
        if (componentKey in apps) it else it.withDockApps(side, apps + componentKey)
    }

    fun onDockAppRemove(side: DockSide, componentKey: String) = onSettingsChange {
        it.withDockApps(side, it.dockApps(side) - componentKey)
    }

    fun onElementVisibilityChange(element: DockElement, visible: Boolean) = onSettingsChange {
        it.copy(hiddenElements = if (visible) it.hiddenElements - element else it.hiddenElements + element)
    }

    fun onGridChange(grid: GridSpec) {
        mutateLayout { current ->
            changeGridSize(current, grid).also { _gridError.value = if (it == null) grid else null }
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

    private fun LauncherSettings.dockApps(side: DockSide) = when (side) {
        DockSide.LEFT -> dockLeftApps
        DockSide.RIGHT -> dockRightApps
    }

    private fun LauncherSettings.withDockApps(side: DockSide, apps: List<String>) = when (side) {
        DockSide.LEFT -> copy(dockLeftApps = apps)
        DockSide.RIGHT -> copy(dockRightApps = apps)
    }

    private fun clearDrag() = interaction.update { it.idle() }

    /**
     * Applies a layout change on top of the latest layout; `null` from [block] keeps it as is.
     * Two quick actions (e.g. two deletes) would otherwise both start from the same old layout.
     * [onApplied] runs right after the new layout is set (e.g. to end the gesture at that instant).
     */
    private fun mutateLayout(onApplied: () -> Unit = {}, block: suspend (LayoutDefinition) -> LayoutDefinition?) {
        viewModelScope.launch {
            layoutMutex.withLock {
                block(layout.value)?.let { layout.value = it }
                onApplied()
            }
        }
    }
}
