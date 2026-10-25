package org.opencarproject.launcher.presentation.launcher

import org.opencarproject.launcher.R
import androidx.compose.ui.res.stringResource
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.navigationBarsPadding
import org.opencarproject.launcher.presentation.common.MotionGlowOverlay
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.opencarproject.launcher.core.model.AppMenuStyle
import org.opencarproject.launcher.core.model.BackgroundStyle
import org.opencarproject.launcher.core.model.CatalogItem
import org.opencarproject.launcher.core.model.CatalogItemKind
import org.opencarproject.launcher.core.model.DockElement
import org.opencarproject.launcher.core.model.DockSide
import org.opencarproject.launcher.core.model.LauncherSettings
import org.opencarproject.launcher.core.model.LayoutDefinition
import org.opencarproject.launcher.core.model.LayoutMode
import org.opencarproject.launcher.core.model.TemperatureUnit
import org.opencarproject.launcher.core.model.WeatherPosition
import org.opencarproject.launcher.core.pure.SpeedUnits
import org.opencarproject.launcher.core.model.PanelState
import org.opencarproject.launcher.core.ui.theme.Dimens
import org.opencarproject.launcher.data.widget.LauncherAppWidgetHost
import org.opencarproject.launcher.presentation.common.ClockText
import org.opencarproject.launcher.presentation.common.GoogleAssistantButton
import org.opencarproject.launcher.presentation.common.DockFavorites
import org.opencarproject.launcher.presentation.common.slotWidth
import org.opencarproject.launcher.presentation.common.HotspotIndicator
import org.opencarproject.launcher.presentation.common.MatchSystemBarsInDialog
import org.opencarproject.launcher.presentation.common.SignalIndicator
import org.opencarproject.launcher.presentation.common.SpeedScene
import org.opencarproject.launcher.presentation.common.StandaloneSpeedometer
import org.opencarproject.launcher.presentation.common.VolumeButton
import org.opencarproject.launcher.presentation.common.WeatherIndicator
import org.opencarproject.launcher.data.source.LauncherNotificationListener
import org.opencarproject.launcher.data.source.MediaSessionSource
import org.opencarproject.launcher.presentation.drawer.AppDrawerSheet
import org.opencarproject.launcher.presentation.drawer.AppsPage
import org.opencarproject.launcher.presentation.grid.LocalShowAppLabels
import org.opencarproject.launcher.presentation.grid.WidgetGrid
import org.opencarproject.launcher.presentation.picker.WidgetPickerPanel
import org.opencarproject.launcher.presentation.settings.SettingsSheet
import org.opencarproject.launcher.presentation.widget.NotificationIsland
import org.opencarproject.launcher.presentation.widget.NotificationsCard
import kotlinx.coroutines.launch

/**
 * Root screen of the launcher with native Material 3 components (Scaffold + top bar
 * with `FilledTonalIconButton`), grid and picker panel.
 */
@Composable
fun LauncherScreen(
    viewModel: LauncherViewModel,
    widgetHost: LauncherAppWidgetHost?,
    initialSettingsOpen: Boolean = false,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(initialSettingsOpen) }
    // "Test" in settings: the sheet steps aside while the glow plays its demo, then comes back.
    var glowDemo by remember { mutableStateOf(false) }
    val settingsScroll = rememberScrollState()
    var notificationsOpen by rememberSaveable { mutableStateOf(false) }
    // Side of the bar a shortcut is being added to (null = picker closed).
    var dockPickerSide by remember { mutableStateOf<DockSide?>(null) }
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val gridError by viewModel.gridError.collectAsStateWithLifecycle()
    val notificationCount by LauncherNotificationListener.notifications.collectAsStateWithLifecycle()
    // The media session is always observed: the island announces song changes even if the
    // player card is not on the home screen.
    val appContext = LocalContext.current
    DisposableEffect(appContext) {
        MediaSessionSource.start(appContext)
        onDispose { MediaSessionSource.stop() }
    }

    // Permissions at startup in a single request (Android doesn't allow two dialogs at once):
    // phone state (4G/5G in the signal) and approximate location (dock weather).
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { /* cada módulo re-evalúa su permiso al volver (rememberOnResume) */ }
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        val missing = STARTUP_PERMISSIONS.filter {
            context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
    }

    // On the home screen "back" does nothing: without this Android sends the launcher to the background and
    // reveals the previous app. It is registered first so the following ones (editing, apps page)
    // take priority when they apply.
    BackHandler(enabled = true) { }
    // "Back" ends editing.
    BackHandler(enabled = state.layoutMode == LayoutMode.EDIT) { viewModel.toggleEditMode() }

    LauncherBackground(settings.background, particles = settings.gradientParticles)
    Scaffold(
        containerColor = if (settings.background == BackgroundStyle.SOLID) MaterialTheme.colorScheme.surface else Color.Transparent,
        // With a transparent background Material doesn't know which content color to use: it is set to the theme's.
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) { padding ->
        val swipeMenu = settings.appMenuStyle == AppMenuStyle.SWIPE_PAGE
        val pagerState = rememberPagerState(initialPage = HOME_PAGE) { 2 }
        val scope = rememberCoroutineScope()
        val apps = state.catalog.filter { it.kind == CatalogItemKind.APP }
        val launchFromMenu = { app: CatalogItem ->
            viewModel.onLaunchApp(app.key)
            drawerOpen = false
            // When returning to the launcher, the home screen is shown, not the list.
            scope.launch { pagerState.scrollToPage(HOME_PAGE) }
            Unit
        }
        val openApps: () -> Unit = {
            if (swipeMenu) scope.launch { pagerState.animateScrollToPage(APPS_PAGE) } else drawerOpen = true
        }
        LaunchedEffect(swipeMenu) { pagerState.scrollToPage(HOME_PAGE) }
        // Home button: closes sheets and returns to the home-screen page.
        LaunchedEffect(Unit) {
            viewModel.homePressed.collect {
                drawerOpen = false
                settingsOpen = false
                notificationsOpen = false
                if (pagerState.currentPage != HOME_PAGE) pagerState.animateScrollToPage(HOME_PAGE)
            }
        }
        BackHandler(enabled = swipeMenu && pagerState.currentPage == APPS_PAGE) {
            scope.launch { pagerState.animateScrollToPage(HOME_PAGE) }
        }

        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize().padding(Dimens.ScreenPadding)) {
                val shown = { element: DockElement -> element !in settings.hiddenElements }
                // With no weather and not editing, the top bar would be empty: it collapses.
                AnimatedVisibility(
                    visible = shown(DockElement.WEATHER) || settings.speedScene || settings.showSpeedDial || state.layoutMode == LayoutMode.EDIT,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    TopBar(
                        layoutMode = state.layoutMode,
                        showWeather = shown(DockElement.WEATHER),
                        weatherPosition = settings.weatherPosition,
                        settings = settings,
                        temperatureUnit = settings.temperatureUnit,
                        onToggleEdit = viewModel::toggleEditMode,
                        onAdd = viewModel::openPanel,
                    )
                }
                // Only the middle content slides: with the "page to the right" menu, swiping
                // left shifts the cards and brings in the app list; the
                // top and bottom bars stay fixed. Without that mode (or while editing) it doesn't slide.
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = swipeMenu && state.layoutMode == LayoutMode.NORMAL,
                    // Swiping a fifth (not half) is enough to change pages: short
                    // gestures while driving.
                    flingBehavior = PagerDefaults.flingBehavior(state = pagerState, snapPositionalThreshold = 0.2f),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                ) { page ->
                    if (page == APPS_PAGE) {
                        if (swipeMenu) {
                            AppsPage(
                                apps = apps,
                                onLaunch = launchFromMenu,
                                iconScale = settings.menuIconScale,
                                onBack = { scope.launch { pagerState.animateScrollToPage(HOME_PAGE) } },
                            )
                        }
                    } else {
                        CompositionLocalProvider(LocalShowAppLabels provides settings.showAppLabels) {
                            WidgetGrid(state = state, widgetHost = widgetHost, viewModel = viewModel)
                        }
                    }
                }
                // Bottom dock: time, apps | assistant (center, within the driver's reach) |
                // hotspot, signal, volume, notifications, settings.
                // Space for shortcuts: what remains in each half after the fixed elements
                // and half the max width of the assistant pill.
                val density = LocalDensity.current
                var dockWidth by remember { mutableIntStateOf(0) }
                var leftFixed by remember { mutableIntStateOf(0) }
                var rightFixed by remember { mutableIntStateOf(0) }
                // Measured pill width (widest seen, so rotating hints don't make slots jitter).
                var assistantWidth by remember { mutableIntStateOf(0) }
                fun capacity(fixedPx: Int): Int = with(density) {
                    val centerReserve = (if (shown(DockElement.ASSISTANT)) assistantWidth / 2f else 0f) + 16.dp.toPx()
                    val free = dockWidth / 2f - centerReserve - fixedPx - 16.dp.toPx()
                    (free / settings.dockIconSize.slotWidth().toPx()).toInt().coerceAtLeast(0)
                }
                val editing = state.layoutMode == LayoutMode.EDIT
                Box(Modifier.fillMaxWidth().padding(top = 12.dp).onSizeChanged { dockWidth = it.width }) {
                    Row(
                        Modifier.align(Alignment.CenterStart).padding(start = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(Modifier.onSizeChanged { leftFixed = it.width }, verticalAlignment = Alignment.CenterVertically) {
                        DockItem(shown(DockElement.CLOCK)) {
                            ClockText(timeFormat = settings.timeFormat)
                            Spacer(Modifier.width(20.dp))
                        }
                        DockItem(shown(DockElement.APPS)) {
                            IconButton(onClick = openApps, modifier = Modifier.size(settings.dockIconSize.slotDp.dp)) {
                                Icon(Icons.Rounded.Apps, contentDescription = stringResource(R.string.apps_title), modifier = Modifier.size((settings.dockIconSize.iconDp * 0.8f).dp))
                            }
                        }
                        }
                        Spacer(Modifier.width(8.dp))
                        DockFavorites(
                            apps = settings.dockLeftApps,
                            capacity = capacity(leftFixed),
                            size = settings.dockIconSize,
                            editing = editing,
                            plusFirst = false,
                            onLaunch = viewModel::onLaunchApp,
                            onRemove = { viewModel.onDockAppRemove(DockSide.LEFT, it) },
                            onAdd = { dockPickerSide = DockSide.LEFT },
                        )
                    }
                    // Fully qualified name: inside the Column the ColumnScope variant would be resolved.
                    androidx.compose.animation.AnimatedVisibility(
                        visible = shown(DockElement.ASSISTANT),
                        enter = fadeIn() + scaleIn(initialScale = 0.9f),
                        exit = fadeOut() + scaleOut(targetScale = 0.9f),
                        modifier = Modifier.align(Alignment.Center).onSizeChanged { assistantWidth = maxOf(assistantWidth, it.width) },
                    ) {
                        GoogleAssistantButton(onClick = viewModel::onLaunchAssistant)
                    }
                    Row(
                        Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DockFavorites(
                            apps = settings.dockRightApps,
                            capacity = capacity(rightFixed),
                            size = settings.dockIconSize,
                            editing = editing,
                            plusFirst = true,
                            onLaunch = viewModel::onLaunchApp,
                            onRemove = { viewModel.onDockAppRemove(DockSide.RIGHT, it) },
                            onAdd = { dockPickerSide = DockSide.RIGHT },
                        )
                        Spacer(Modifier.width(8.dp))
                        Row(Modifier.onSizeChanged { rightFixed = it.width }, verticalAlignment = Alignment.CenterVertically) {
                        DockItem(shown(DockElement.HOTSPOT)) {
                            HotspotIndicator()
                            Spacer(Modifier.width(12.dp))
                        }
                        DockItem(shown(DockElement.SIGNAL)) {
                            SignalIndicator()
                            Spacer(Modifier.width(16.dp))
                        }
                        DockItem(shown(DockElement.VOLUME)) {
                            VolumeButton()
                            Spacer(Modifier.width(8.dp))
                        }
                        DockItem(shown(DockElement.NOTIFICATIONS)) {
                            NotificationsButton(
                                count = notificationCount.size,
                                onClick = { notificationsOpen = true },
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        IconButton(onClick = { settingsOpen = true }, modifier = Modifier.size(56.dp)) {
                            Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.settings_title), modifier = Modifier.size(30.dp))
                        }
                        }
                    }
                }
            }

            // Notification island at top center (above everything except the sheets).
            NotificationIsland(Modifier.align(Alignment.TopCenter).padding(top = 8.dp))

            if (settingsOpen) {
                SettingsSheet(
                    settings = settings,
                    // Settings are in visible cells; the layout uses half cells.
                    grid = state.grid.copy(columns = state.grid.columns / LayoutDefinition.COLUMN_SCALE),
                    gridError = gridError,
                    onFullscreenChange = viewModel::onFullscreenChange,
                    onThemeChange = viewModel::onThemeChange,
                    onTemperatureUnitChange = viewModel::onTemperatureUnitChange,
                    onTimeFormatChange = viewModel::onTimeFormatChange,
                    onWeatherPositionChange = viewModel::onWeatherPositionChange,
                    onBackgroundChange = viewModel::onBackgroundChange,
                    onGradientParticlesChange = viewModel::onGradientParticlesChange,
                    onSpeedSceneChange = viewModel::onSpeedSceneChange,
                    onSpeedSourceChange = viewModel::onSpeedSourceChange,
                    onRocketKmhChange = viewModel::onRocketKmhChange,
                    onShowSpeedDialChange = viewModel::onShowSpeedDialChange,
                    onSpeedUnitChange = viewModel::onSpeedUnitChange,
                    onSpeedDialStyleChange = viewModel::onSpeedDialStyleChange,
                    onVehicleShapeChange = viewModel::onVehicleShapeChange,
                    onVehicleColorChange = viewModel::onVehicleColorChange,
                    onAppMenuStyleChange = viewModel::onAppMenuStyleChange,
                    onDockIconSizeChange = viewModel::onDockIconSizeChange,
                    onAppLanguageChange = viewModel::onAppLanguageChange,
                    onUiScaleChange = viewModel::onUiScaleChange,
                    onMenuIconScaleChange = viewModel::onMenuIconScaleChange,
                    onMotionGlowChange = viewModel::onMotionGlowChange,
                    onMotionGlowSensitivityChange = viewModel::onMotionGlowSensitivityChange,
                    onMotionGlowColorChange = viewModel::onMotionGlowColorChange,
                    onMotionGlowTest = {
                        settingsOpen = false
                        glowDemo = true
                    },
                    onShowAppLabelsChange = viewModel::onShowAppLabelsChange,
                    onAccentColorChange = viewModel::onAccentColorChange,
                    onElementVisibilityChange = viewModel::onElementVisibilityChange,
                    onGridChange = { viewModel.onGridChange(it.copy(columns = it.columns * LayoutDefinition.COLUMN_SCALE)) },
                    onDismiss = { settingsOpen = false },
                    scrollState = settingsScroll,
                )
            }

            if (notificationsOpen) {
                NotificationsSheet(onDismiss = { notificationsOpen = false })
            }

            dockPickerSide?.let { side ->
                AppDrawerSheet(
                    apps = apps,
                    iconScale = settings.menuIconScale,
                    title = if (side == DockSide.LEFT) stringResource(R.string.dock_add_left) else stringResource(R.string.dock_add_right),
                    onLaunch = {
                        viewModel.onDockAppAdd(side, it.key)
                        dockPickerSide = null
                    },
                    onDismiss = { dockPickerSide = null },
                )
            }

            if (!swipeMenu && drawerOpen) {
                AppDrawerSheet(apps = apps, onLaunch = launchFromMenu, onDismiss = { drawerOpen = false }, iconScale = settings.menuIconScale)
            }

            AnimatedVisibility(
                visible = state.panelState == PanelState.OPEN,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                WidgetPickerPanel(
                    catalog = state.catalog,
                    onSelect = viewModel::onAddItem,
                    onClose = viewModel::closePanel,
                )
            }
        }
    }
    // Over everything (true screen edges, not the Scaffold's inset box) but above an opaque
    // navigation bar/taskbar, which would hide the braking glow; draw-only.
    if (settings.motionGlow || glowDemo) {
        MotionGlowOverlay(
            sensitivity = settings.motionGlowSensitivity,
            color = settings.motionGlowColor.argb?.let(::Color) ?: MaterialTheme.colorScheme.primary,
            demo = glowDemo,
            onDemoEnd = {
                glowDemo = false
                settingsOpen = true
            },
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
        )
    }
}

/**
 * Top bar: weather on the left, center or right (configurable); ✓ and + on the
 * right only while editing (entered via long press). The island appears above, centered.
 */
@Composable
private fun TopBar(
    layoutMode: LayoutMode,
    showWeather: Boolean,
    weatherPosition: WeatherPosition,
    settings: LauncherSettings,
    temperatureUnit: TemperatureUnit,
    onToggleEdit: () -> Unit,
    onAdd: () -> Unit,
) {
    Box(
        // Same bottom margin as top (ScreenPadding), so the weather is centered between edge and cards.
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(bottom = Dimens.ScreenPadding),
        contentAlignment = Alignment.Center,
    ) {
        // On the right, it shifts while editing so it doesn't sit under ✓ and +.
        val editEndPadding by animateDpAsState(
            if (weatherPosition == WeatherPosition.RIGHT && layoutMode == LayoutMode.EDIT) 120.dp else 0.dp,
            label = "weatherPad",
        )
        AnimatedVisibility(
            showWeather,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(
                    when (weatherPosition) {
                        WeatherPosition.LEFT -> Alignment.CenterStart
                        WeatherPosition.CENTER -> Alignment.Center
                        WeatherPosition.RIGHT -> Alignment.CenterEnd
                    },
                )
                .padding(horizontal = 8.dp)
                .padding(end = editEndPadding),
        ) {
            WeatherIndicator(unit = temperatureUnit)
        }
        // Speed scene on the side opposite the weather (never when the weather is centered).
        val sceneOnRight = weatherPosition == WeatherPosition.LEFT
        val sceneEndPadding by animateDpAsState(
            if (sceneOnRight && layoutMode == LayoutMode.EDIT) 120.dp else 0.dp,
            label = "scenePad",
        )
        // Without the scene, the speedometer goes alone on that same side (on the right if the weather is centered).
        val dialOnRight = weatherPosition != WeatherPosition.RIGHT
        val dialEndPadding by animateDpAsState(
            if (dialOnRight && layoutMode == LayoutMode.EDIT) 120.dp else 0.dp,
            label = "dialPad",
        )
        AnimatedVisibility(
            visible = !settings.speedScene && settings.showSpeedDial,
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f),
            modifier = Modifier
                .align(if (dialOnRight) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(horizontal = 8.dp)
                .padding(end = dialEndPadding),
        ) {
            StandaloneSpeedometer(
                style = settings.speedDialStyle,
                source = settings.speedSource,
                rocketKmh = settings.rocketKmh,
                mph = SpeedUnits.usesMph(settings.speedUnit, androidx.compose.ui.text.intl.Locale.current.region),
            )
        }
        AnimatedVisibility(
            visible = settings.speedScene && weatherPosition != WeatherPosition.CENTER,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(if (sceneOnRight) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(horizontal = 8.dp)
                .padding(end = sceneEndPadding),
        ) {
            SpeedScene(
                source = settings.speedSource,
                rocketKmh = settings.rocketKmh,
                vehicle = settings.vehicleShape,
                color = Color(settings.vehicleColor.argb),
                showDial = settings.showSpeedDial,
                dialStyle = settings.speedDialStyle,
                mph = SpeedUnits.usesMph(settings.speedUnit, androidx.compose.ui.text.intl.Locale.current.region),
            )
        }
        AnimatedVisibility(
            visible = layoutMode == LayoutMode.EDIT,
            enter = fadeIn() + slideInHorizontally { it / 2 },
            exit = fadeOut() + slideOutHorizontally { it / 2 },
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            Row {
                FilledTonalIconButton(onClick = onToggleEdit) {
                    Icon(imageVector = Icons.Rounded.Check, contentDescription = stringResource(R.string.edit_done))
                }
                Spacer(Modifier.width(8.dp))
                FilledTonalIconButton(onClick = onAdd) {
                    Icon(imageVector = Icons.Rounded.Add, contentDescription = stringResource(R.string.add))
                }
            }
        }
    }
}

private val STARTUP_PERMISSIONS = listOf(
    Manifest.permission.READ_PHONE_STATE,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

/** Dock item that enters/exits by collapsing horizontally: neighbors adjust on their own. */
@Composable
private fun DockItem(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandHorizontally(),
        exit = fadeOut() + shrinkHorizontally(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) { content() }
    }
}

/** Dock bell with the total notification count in a badge. */
@Composable
private fun NotificationsButton(count: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(56.dp)) {
        BadgedBox(
            badge = {
                if (count > 0) Badge { Text(if (count > 99) "99+" else "$count") }
            },
        ) {
            Icon(
                imageVector = Icons.Rounded.Notifications,
                contentDescription = if (count > 0) stringResource(R.string.notifications_count, count) else stringResource(R.string.notifications),
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

/** Sheet with all notifications (same module as the home-screen card). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationsSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        MatchSystemBarsInDialog()
        NotificationsCard(Modifier.fillMaxWidth().fillMaxHeight(0.8f).padding(horizontal = 12.dp))
    }
}

/** Horizontal pager pages: the apps to the right of the home screen. */
private const val HOME_PAGE = 0
private const val APPS_PAGE = 1

/**
 * Background layer behind everything: animated gradient (theme and accent tones) or, with the
 * system wallpaper, faint scrims top and bottom so bars and text are legible over the photo.
 */
@Composable
private fun LauncherBackground(style: BackgroundStyle, particles: Boolean) {
    when (style) {
        BackgroundStyle.SOLID -> Unit
        BackgroundStyle.GRADIENT -> {
            val scheme = MaterialTheme.colorScheme
            val t = rememberInfiniteTransition(label = "bg")
            val drift by t.animateFloat(0f, 1f, infiniteRepeatable(tween(20_000, easing = LinearEasing), RepeatMode.Reverse), label = "drift")
            val c1 = scheme.surface
            val c2 = androidx.compose.ui.graphics.lerp(scheme.surface, scheme.primaryContainer, 0.75f)
            val c3 = androidx.compose.ui.graphics.lerp(scheme.surface, scheme.tertiaryContainer, 0.6f)
            val rise by t.animateFloat(0f, 1f, infiniteRepeatable(tween(30_000, easing = LinearEasing)), label = "rise")
            val glow = scheme.onSurface
            Box(
                Modifier.fillMaxSize().drawBehind {
                    drawRect(
                        Brush.linearGradient(
                            listOf(c2, c1, c3),
                            start = Offset(size.width * drift, 0f),
                            end = Offset(size.width * (1f - drift), size.height),
                        ),
                    )
                    // Light particles that rise slowly and twinkle (stable positions per index).
                    if (particles) for (i in 0 until PARTICLES) {
                        val seedX = particleHash(i, 1)
                        val speed = 0.4f + particleHash(i, 2) * 0.6f
                        val y = (1f - ((particleHash(i, 3) + rise * speed) % 1f)) * size.height
                        val x = seedX * size.width + kotlin.math.sin((rise * 6f + i) * 2f) * 12f
                        val twinkle = 0.5f + 0.5f * kotlin.math.sin(rise * 120f * speed + i)
                        val r = (1.5f + particleHash(i, 4) * 3.5f) * density
                        // Soft halo (radial gradient) + core.
                        drawCircle(
                            Brush.radialGradient(listOf(glow.copy(alpha = 0.18f * twinkle), Color.Transparent), center = Offset(x, y), radius = r * 4f),
                            radius = r * 4f,
                            center = Offset(x, y),
                        )
                        drawCircle(glow.copy(alpha = 0.25f + 0.35f * twinkle), radius = r, center = Offset(x, y))
                    }
                },
            )
        }
        BackgroundStyle.WALLPAPER -> Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.45f),
                    0.18f to Color.Transparent,
                    0.8f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.55f),
                ),
            ),
        )
    }
}

private const val PARTICLES = 46

/** Stable pseudo-random 0..1 per index, so each particle keeps its trajectory. */
private fun particleHash(i: Int, seed: Int): Float {
    var x = i * 374761393 + seed * 668265263
    x = (x xor (x ushr 13)) * 1274126177
    return ((x xor (x ushr 16)) and 0x7FFFFFFF) / 2147483647f
}
