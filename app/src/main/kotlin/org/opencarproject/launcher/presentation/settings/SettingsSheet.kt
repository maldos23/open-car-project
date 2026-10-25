package org.opencarproject.launcher.presentation.settings

import org.opencarproject.launcher.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedVisibility
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import org.opencarproject.launcher.core.pure.GridColumnsRange
import org.opencarproject.launcher.core.pure.GridRowsRange
import org.opencarproject.launcher.core.pure.MenuIconScaleRange
import org.opencarproject.launcher.core.pure.MotionGlowSensitivityRange
import org.opencarproject.launcher.core.pure.UiScaleRange
import org.opencarproject.launcher.core.model.AccentColor
import org.opencarproject.launcher.core.model.AppLanguage
import org.opencarproject.launcher.core.model.AppMenuStyle
import org.opencarproject.launcher.core.model.BackgroundStyle
import org.opencarproject.launcher.core.model.DockElement
import org.opencarproject.launcher.core.model.DockIconSize
import org.opencarproject.launcher.core.model.GlowColor
import org.opencarproject.launcher.core.model.GridSpec
import org.opencarproject.launcher.core.model.LayoutDefinition
import org.opencarproject.launcher.core.model.LauncherSettings
import org.opencarproject.launcher.core.model.SpeedDialStyle
import org.opencarproject.launcher.core.model.SpeedSource
import org.opencarproject.launcher.core.model.SpeedUnit
import org.opencarproject.launcher.core.pure.SpeedUnits
import kotlin.math.roundToInt
import org.opencarproject.launcher.core.model.TemperatureUnit
import org.opencarproject.launcher.core.model.ThemeMode
import org.opencarproject.launcher.core.model.TimeFormat
import org.opencarproject.launcher.core.model.VehicleColor
import org.opencarproject.launcher.core.model.VehicleShape
import org.opencarproject.launcher.core.model.WeatherPosition
import org.opencarproject.launcher.core.ui.theme.swatch
import org.opencarproject.launcher.presentation.common.MatchSystemBarsInDialog

/** Settings sheet: fullscreen, theme and grid size. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    settings: LauncherSettings,
    grid: GridSpec,
    gridError: GridSpec?,
    /** Plain one-field changes: receives the copy to apply (e.g. `{ it.copy(fullscreen = true) }`). */
    onChange: ((LauncherSettings) -> LauncherSettings) -> Unit,
    // Changes with a rule (coupled fields, clamping, rounding) go through their own callback.
    onWeatherPositionChange: (WeatherPosition) -> Unit,
    onSpeedSceneChange: (Boolean) -> Unit,
    onRocketKmhChange: (Int) -> Unit,
    onUiScaleChange: (Float) -> Unit,
    onMenuIconScaleChange: (Float) -> Unit,
    onMotionGlowSensitivityChange: (Float) -> Unit,
    onMotionGlowTest: () -> Unit,
    onElementVisibilityChange: (DockElement, Boolean) -> Unit,
    onGridChange: (GridSpec) -> Unit,
    onDismiss: () -> Unit,
    // Hoisted by the caller so the position survives closing and reopening (e.g. around "Test").
    scrollState: ScrollState = rememberScrollState(),
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        MatchSystemBarsInDialog()
        Column(
            Modifier
                .fillMaxWidth()
                // With more options the sheet may not fit vertically: it scrolls.
                .verticalScroll(scrollState)
                .padding(horizontal = 28.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall)

            Choice(
                title = stringResource(R.string.settings_language),
                options = listOf(
                    AppLanguage.SYSTEM to stringResource(R.string.option_system),
                    AppLanguage.ES to stringResource(R.string.language_es),
                    AppLanguage.EN to stringResource(R.string.language_en),
                    AppLanguage.ZH to stringResource(R.string.language_zh),
                    AppLanguage.PT to stringResource(R.string.language_pt),
                    AppLanguage.FR to stringResource(R.string.language_fr),
                ),
                selected = settings.appLanguage,
                onSelect = { v -> onChange { it.copy(appLanguage = v) } },
            )

            // Commits on release only: each change recreates the screen at the new density.
            SliderRow(
                title = stringResource(R.string.settings_ui_scale),
                value = settings.uiScale,
                range = UiScaleRange,
                steps = 9,
                onChange = onUiScaleChange,
            )

            SwitchRow(stringResource(R.string.settings_fullscreen), stringResource(R.string.settings_fullscreen_desc), settings.fullscreen) { v ->
                onChange { it.copy(fullscreen = v) }
            }

            Choice(
                title = stringResource(R.string.settings_theme),
                options = listOf(ThemeMode.SYSTEM to stringResource(R.string.option_system), ThemeMode.LIGHT to stringResource(R.string.theme_light), ThemeMode.DARK to stringResource(R.string.theme_dark)),
                selected = settings.themeMode,
                onSelect = { v -> onChange { it.copy(themeMode = v) } },
            )

            AccentPicker(selected = settings.accentColor, onSelect = { v -> onChange { it.copy(accentColor = v) } })

            Choice(
                title = stringResource(R.string.settings_background),
                options = listOf(
                    BackgroundStyle.SOLID to stringResource(R.string.background_solid),
                    BackgroundStyle.WALLPAPER to stringResource(R.string.background_wallpaper),
                    BackgroundStyle.GRADIENT to stringResource(R.string.background_gradient),
                ),
                selected = settings.background,
                onSelect = { v -> onChange { it.copy(background = v) } },
            )
            // Only applies to the gradient.
            AnimatedVisibility(settings.background == BackgroundStyle.GRADIENT) {
                SwitchRow(stringResource(R.string.settings_particles), stringResource(R.string.settings_particles_desc), settings.gradientParticles) { v ->
                    onChange { it.copy(gradientParticles = v) }
                }
            }

            Choice(
                title = stringResource(R.string.settings_speed_unit),
                options = listOf(SpeedUnit.AUTO to stringResource(R.string.option_auto), SpeedUnit.KMH to SpeedUnits.label(mph = false), SpeedUnit.MPH to SpeedUnits.label(mph = true)),
                selected = settings.speedUnit,
                onSelect = { v -> onChange { it.copy(speedUnit = v) } },
            )

            Choice(
                title = stringResource(R.string.settings_temperature),
                options = listOf(
                    TemperatureUnit.AUTO to stringResource(R.string.option_auto),
                    TemperatureUnit.CELSIUS to stringResource(R.string.temperature_celsius),
                    TemperatureUnit.FAHRENHEIT to stringResource(R.string.temperature_fahrenheit),
                ),
                selected = settings.temperatureUnit,
                onSelect = { v -> onChange { it.copy(temperatureUnit = v) } },
            )

            Choice(
                title = stringResource(R.string.settings_weather_position),
                options = listOf(WeatherPosition.LEFT to stringResource(R.string.position_left), WeatherPosition.CENTER to stringResource(R.string.position_center), WeatherPosition.RIGHT to stringResource(R.string.position_right)),
                selected = settings.weatherPosition,
                onSelect = onWeatherPositionChange,
                // The center is freed when the speed scene is disabled.
                enabled = { it != WeatherPosition.CENTER || !settings.speedScene },
                note = if (settings.speedScene) stringResource(R.string.settings_weather_center_unavailable) else null,
            )

            AnimatedVisibility(settings.speedScene || settings.showSpeedDial) {
                Choice(
                    title = stringResource(R.string.settings_speed_source),
                    options = listOf(SpeedSource.GPS to stringResource(R.string.speed_source_gps), SpeedSource.SENSORS to stringResource(R.string.speed_source_sensors)),
                    selected = settings.speedSource,
                    onSelect = { v -> onChange { it.copy(speedSource = v) } },
                )
            }

            SwitchRow(
                stringResource(R.string.settings_speed_dial),
                stringResource(if (settings.speedScene) R.string.settings_speed_dial_desc_scene else R.string.settings_speed_dial_desc),
                settings.showSpeedDial,
            ) { v -> onChange { it.copy(showSpeedDial = v) } }
            AnimatedVisibility(settings.showSpeedDial) {
                Choice(
                    title = stringResource(R.string.settings_speed_dial_style),
                    options = listOf(SpeedDialStyle.CIRCLE to stringResource(R.string.speed_dial_circle), SpeedDialStyle.INLINE to stringResource(R.string.speed_dial_inline)),
                    selected = settings.speedDialStyle,
                    onSelect = { v -> onChange { it.copy(speedDialStyle = v) } },
                )
            }

            SwitchRow(stringResource(R.string.settings_motion_glow), stringResource(R.string.settings_motion_glow_desc), settings.motionGlow) { v ->
                onChange { it.copy(motionGlow = v) }
            }
            AnimatedVisibility(settings.motionGlow) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SliderRow(
                        title = stringResource(R.string.settings_motion_glow_sensitivity),
                        value = settings.motionGlowSensitivity,
                        range = MotionGlowSensitivityRange,
                        steps = 14,
                        onChange = onMotionGlowSensitivityChange,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.settings_motion_glow_color), style = MaterialTheme.typography.titleMedium)
                        val accent = MaterialTheme.colorScheme.primary
                        ColorSwatches(
                            options = GlowColor.entries.map { it to (it.argb?.let(::Color) ?: accent) },
                            selected = settings.motionGlowColor,
                            onSelect = { v -> onChange { it.copy(motionGlowColor = v) } },
                        )
                    }
                    FilledTonalButton(onClick = onMotionGlowTest, modifier = Modifier.height(48.dp)) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_motion_glow_test))
                    }
                }
            }

            SwitchRow(stringResource(R.string.settings_speed_scene), stringResource(R.string.settings_speed_scene_desc), settings.speedScene, onCheckedChange = onSpeedSceneChange)
            AnimatedVisibility(settings.speedScene) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // The threshold is stored in km/h and shown in the chosen unit.
                    val mph = SpeedUnits.usesMph(settings.speedUnit, androidx.compose.ui.text.intl.Locale.current.region)
                    Stepper(
                        stringResource(R.string.settings_rocket_from, SpeedUnits.label(mph)),
                        SpeedUnits.display(settings.rocketKmh.toFloat(), mph).roundToInt(),
                        if (mph) 15..125 else 20..200,
                        step = if (mph) 5 else 10,
                        onChange = { onRocketKmhChange(SpeedUnits.toKmh(it.toFloat(), mph).roundToInt()) },
                    )
                    Choice(
                        title = stringResource(R.string.settings_vehicle_shape),
                        options = listOf(
                            VehicleShape.CAR to stringResource(R.string.vehicle_car),
                            VehicleShape.SUV to stringResource(R.string.vehicle_suv),
                            VehicleShape.PICKUP to stringResource(R.string.vehicle_pickup),
                            VehicleShape.CAT to stringResource(R.string.vehicle_cat),
                            VehicleShape.DOG to stringResource(R.string.vehicle_dog),
                        ),
                        selected = settings.vehicleShape,
                        onSelect = { v -> onChange { it.copy(vehicleShape = v) } },
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.settings_color), style = MaterialTheme.typography.titleMedium)
                        ColorSwatches(
                            options = VehicleColor.entries.map { it to Color(it.argb) },
                            selected = settings.vehicleColor,
                            onSelect = { v -> onChange { it.copy(vehicleColor = v) } },
                        )
                    }
                }
            }

            Choice(
                title = stringResource(R.string.settings_time_format),
                options = listOf(TimeFormat.SYSTEM to stringResource(R.string.option_auto), TimeFormat.H12 to stringResource(R.string.time_12h), TimeFormat.H24 to stringResource(R.string.time_24h)),
                selected = settings.timeFormat,
                onSelect = { v -> onChange { it.copy(timeFormat = v) } },
            )

            Choice(
                title = stringResource(R.string.settings_app_menu),
                options = listOf(
                    AppMenuStyle.BOTTOM_SHEET to stringResource(R.string.app_menu_bottom_sheet),
                    AppMenuStyle.SWIPE_PAGE to stringResource(R.string.app_menu_swipe_page),
                ),
                selected = settings.appMenuStyle,
                onSelect = { v -> onChange { it.copy(appMenuStyle = v) } },
            )

            SliderRow(
                title = stringResource(R.string.settings_menu_icon_size),
                value = settings.menuIconScale,
                range = MenuIconScaleRange,
                steps = 14,
                onChange = onMenuIconScaleChange,
            )

            SwitchRow(stringResource(R.string.settings_app_labels), stringResource(R.string.settings_app_labels_desc), settings.showAppLabels) { v ->
                onChange { it.copy(showAppLabels = v) }
            }

            Choice(
                title = stringResource(R.string.settings_dock_icon_size),
                options = listOf(DockIconSize.SMALL to stringResource(R.string.size_small), DockIconSize.MEDIUM to stringResource(R.string.size_medium), DockIconSize.LARGE to stringResource(R.string.size_large)),
                selected = settings.dockIconSize,
                onSelect = { v -> onChange { it.copy(dockIconSize = v) } },
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.settings_show), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.settings_show_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Two columns of switches so the sheet doesn't get too long.
                DOCK_ELEMENT_LABELS.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        pair.forEach { (element, label) ->
                            SwitchRow(
                                title = stringResource(label),
                                subtitle = null,
                                checked = element !in settings.hiddenElements,
                                onCheckedChange = { onElementVisibilityChange(element, it) },
                                modifier = Modifier.weight(1f).height(56.dp),
                            )
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.settings_grid), style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Stepper(stringResource(R.string.settings_columns), grid.columns, GridColumnsRange) { onGridChange(grid.copy(columns = it)) }
                        Stepper(stringResource(R.string.settings_rows), grid.rows, GridRowsRange) { onGridChange(grid.copy(rows = it)) }
                    }
                    Spacer(Modifier.width(24.dp))
                    GridPreview(grid, Modifier.width(180.dp))
                }
                // The last error stays shown while the message animates out.
                var lastError by remember { mutableStateOf(gridError) }
                SideEffect { if (gridError != null) lastError = gridError }
                AnimatedVisibility(gridError != null) {
                    Text(
                        text = (gridError ?: lastError)?.let { stringResource(R.string.settings_grid_error, it.columns / LayoutDefinition.COLUMN_SCALE, it.rows) }.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            // Attribution required by the CC BY 4.0 license of the weather data.
            Text(
                text = stringResource(R.string.settings_weather_attribution),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Labels of the items that can be hidden (in the order they appear). */
private val DOCK_ELEMENT_LABELS = listOf(
    DockElement.WEATHER to R.string.element_weather,
    DockElement.CLOCK to R.string.element_clock,
    DockElement.APPS to R.string.apps,
    DockElement.ASSISTANT to R.string.element_assistant,
    DockElement.HOTSPOT to R.string.element_hotspot,
    DockElement.SIGNAL to R.string.element_signal,
    DockElement.VOLUME to R.string.element_volume,
    DockElement.NOTIFICATIONS to R.string.notifications,
)

/** Accent names in the order they're shown. */
private val ACCENT_LABELS = listOf(
    AccentColor.BLUE to R.string.accent_blue,
    AccentColor.TEAL to R.string.accent_teal,
    AccentColor.GREEN to R.string.accent_green,
    AccentColor.PURPLE to R.string.accent_purple,
    AccentColor.PINK to R.string.accent_pink,
    AccentColor.ORANGE to R.string.accent_orange,
    AccentColor.RED to R.string.accent_red,
    AccentColor.DYNAMIC to R.string.accent_dynamic,
)

/** 48 dp circular swatches; the selected one gets a ring and ✓. "Dinámico" only on Android 12+. */
@Composable
private fun AccentPicker(selected: AccentColor, onSelect: (AccentColor) -> Unit) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val options = ACCENT_LABELS.filter { (accent, _) ->
        accent != AccentColor.DYNAMIC || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.settings_accent), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            options.forEach { (accent, label) ->
                // Swatch and name are one target: bigger to hit, and read together by TalkBack.
                Column(
                    Modifier.selectable(accent == selected, role = Role.RadioButton) { onSelect(accent) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val fill = if (accent == AccentColor.DYNAMIC) DynamicSwatch else SolidColor(accent.swatch(dark))
                    Swatch(fill, accent == selected, outlined = false, checkTint = Color.White)
                    Text(stringResource(label), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

/** Row of 48 dp color circles; the selected one gets a ring and ✓ (dark on light colors). */
@Composable
private fun <T> ColorSwatches(options: List<Pair<T, Color>>, selected: T, onSelect: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        options.forEach { (value, color) ->
            Swatch(
                fill = SolidColor(color),
                selected = value == selected,
                outlined = true,
                checkTint = if (color.luminance() > 0.5f) Color.Black else Color.White,
                // Selectable before the padding: the whole 48 dp circle is the touch target.
                modifier = Modifier.selectable(value == selected, role = Role.RadioButton) { onSelect(value) },
            )
        }
    }
}

/** 48 dp color circle; the selected one gets a ring and ✓. */
@Composable
private fun Swatch(fill: Brush, selected: Boolean, outlined: Boolean, checkTint: Color, modifier: Modifier = Modifier) {
    val ring = when {
        selected -> MaterialTheme.colorScheme.onSurface
        outlined -> MaterialTheme.colorScheme.outline
        else -> Color.Transparent
    }
    Box(
        modifier
            .size(48.dp)
            .border(if (selected) 3.dp else 1.dp, ring, CircleShape)
            .padding(5.dp)
            .clip(CircleShape)
            .background(fill),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = checkTint, modifier = Modifier.size(18.dp))
    }
}

/** "Dynamic" accent: the wallpaper decides, shown as a Google-colors wheel. */
private val DynamicSwatch = Brush.sweepGradient(
    listOf(Color(0xFF4285F4), Color(0xFFEA4335), Color(0xFFFBBC05), Color(0xFF34A853), Color(0xFF4285F4)),
)

/** Title + single-choice segmented buttons. */
@Composable
private fun <T> Choice(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    enabled: (T) -> Boolean = { true },
    note: String? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (value, label) ->
                val isEnabled = enabled(value)
                SegmentedButton(
                    selected = selected == value,
                    onClick = { onSelect(value) },
                    enabled = isEnabled,
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    icon = { if (!isEnabled) Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(16.dp)) else SegmentedButtonDefaults.Icon(selected == value) },
                ) { Text(label) }
            }
        }
        if (note != null) {
            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Title (and optional description) with a switch; the whole row toggles it, not only the switch. */
@Composable
private fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (subtitle == null) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
            } else {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** Title, percentage and a slider that reports the value when released (not while dragging). */
@Composable
private fun SliderRow(title: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onChange: (Float) -> Unit) {
    var dragged by remember(value) { mutableFloatStateOf(value) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.percent, (dragged * 100).roundToInt()), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        Slider(
            value = dragged,
            onValueChange = { dragged = it },
            onValueChangeFinished = { if (dragged != value) onChange(dragged) },
            valueRange = range,
            steps = steps,
        )
    }
}

/** − value + with 48 dp buttons (in-car use). */
@Composable
private fun Stepper(label: String, value: Int, range: IntRange, step: Int = 1, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        FilledTonalIconButton(onClick = { onChange(value - step) }, enabled = value > range.first, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Rounded.Remove, contentDescription = stringResource(R.string.stepper_decrease, label))
        }
        Text(
            text = "$value",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(48.dp),
            textAlign = TextAlign.Center,
        )
        FilledTonalIconButton(onClick = { onChange(value + step) }, enabled = value < range.last, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.stepper_increase, label))
        }
    }
}

/** Grid thumbnail with the screen's aspect ratio. */
@Composable
private fun GridPreview(grid: GridSpec, modifier: Modifier = Modifier) {
    Column(
        modifier
            .aspectRatio(16f / 10f)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(10.dp))
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        repeat(grid.rows) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(grid.columns) {
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f, matchHeightConstraintsFirst = true)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(3.dp)),
                    )
                }
            }
        }
    }
}
