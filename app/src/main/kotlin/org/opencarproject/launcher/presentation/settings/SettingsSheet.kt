package org.opencarproject.launcher.presentation.settings

import androidx.compose.animation.AnimatedVisibility
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.opencarproject.launcher.core.model.AccentColor
import org.opencarproject.launcher.core.model.AppMenuStyle
import org.opencarproject.launcher.core.model.BackgroundStyle
import org.opencarproject.launcher.core.model.DockElement
import org.opencarproject.launcher.core.model.DockIconSize
import org.opencarproject.launcher.core.model.GridSpec
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

/** Grid limits: fewer doesn't use the screen well; more makes cards unreadable while driving. */
val GridColumnsRange = 3..8
val GridRowsRange = 2..6

/** Settings sheet: fullscreen, theme and grid size. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    settings: LauncherSettings,
    grid: GridSpec,
    gridError: String?,
    onFullscreenChange: (Boolean) -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onTemperatureUnitChange: (TemperatureUnit) -> Unit,
    onTimeFormatChange: (TimeFormat) -> Unit,
    onWeatherPositionChange: (WeatherPosition) -> Unit,
    onBackgroundChange: (BackgroundStyle) -> Unit,
    onGradientParticlesChange: (Boolean) -> Unit,
    onSpeedSceneChange: (Boolean) -> Unit,
    onSpeedSourceChange: (SpeedSource) -> Unit,
    onRocketKmhChange: (Int) -> Unit,
    onShowSpeedDialChange: (Boolean) -> Unit,
    onSpeedUnitChange: (SpeedUnit) -> Unit,
    onSpeedDialStyleChange: (SpeedDialStyle) -> Unit,
    onVehicleShapeChange: (VehicleShape) -> Unit,
    onVehicleColorChange: (VehicleColor) -> Unit,
    onAppMenuStyleChange: (AppMenuStyle) -> Unit,
    onDockIconSizeChange: (DockIconSize) -> Unit,
    onShowAppLabelsChange: (Boolean) -> Unit,
    onAccentColorChange: (AccentColor) -> Unit,
    onElementVisibilityChange: (DockElement, Boolean) -> Unit,
    onGridChange: (GridSpec) -> Unit,
    onDismiss: () -> Unit,
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Text("Configuración", style = MaterialTheme.typography.headlineSmall)

            Section("Pantalla completa", "Oculta las barras del sistema. Desliza desde el borde para verlas.") {
                Switch(checked = settings.fullscreen, onCheckedChange = onFullscreenChange)
            }

            Choice(
                title = "Tema",
                options = listOf(ThemeMode.SYSTEM to "Sistema", ThemeMode.LIGHT to "Claro", ThemeMode.DARK to "Oscuro"),
                selected = settings.themeMode,
                onSelect = onThemeChange,
            )

            AccentPicker(selected = settings.accentColor, onSelect = onAccentColorChange)

            Choice(
                title = "Fondo",
                options = listOf(
                    BackgroundStyle.SOLID to "Liso",
                    BackgroundStyle.WALLPAPER to "Fondo del sistema",
                    BackgroundStyle.GRADIENT to "Degradado",
                ),
                selected = settings.background,
                onSelect = onBackgroundChange,
            )
            // Only applies to the gradient.
            AnimatedVisibility(settings.background == BackgroundStyle.GRADIENT) {
                Section("Partículas", "Puntos de luz que flotan sobre el degradado.") {
                    Switch(checked = settings.gradientParticles, onCheckedChange = onGradientParticlesChange)
                }
            }

            Choice(
                title = "Velocidad",
                options = listOf(SpeedUnit.AUTO to "Automático", SpeedUnit.KMH to "km/h", SpeedUnit.MPH to "mph"),
                selected = settings.speedUnit,
                onSelect = onSpeedUnitChange,
            )

            Choice(
                title = "Temperatura",
                options = listOf(
                    TemperatureUnit.AUTO to "Automático",
                    TemperatureUnit.CELSIUS to "Celsius (°C)",
                    TemperatureUnit.FAHRENHEIT to "Fahrenheit (°F)",
                ),
                selected = settings.temperatureUnit,
                onSelect = onTemperatureUnitChange,
            )

            Choice(
                title = "Posición del clima",
                options = listOf(WeatherPosition.LEFT to "Izquierda", WeatherPosition.CENTER to "Centro", WeatherPosition.RIGHT to "Derecha"),
                selected = settings.weatherPosition,
                onSelect = onWeatherPositionChange,
                // The center is freed when the speed scene is disabled.
                enabled = { it != WeatherPosition.CENTER || !settings.speedScene },
                note = if (settings.speedScene) "Centro no disponible con la escena de velocidad activa." else null,
            )

            AnimatedVisibility(settings.speedScene || settings.showSpeedDial) {
                Choice(
                    title = "Fuente de velocidad",
                    options = listOf(SpeedSource.GPS to "GPS (preciso)", SpeedSource.SENSORS to "Sensores (aproximado)"),
                    selected = settings.speedSource,
                    onSelect = onSpeedSourceChange,
                )
            }

            Section(
                "Velocímetro",
                if (settings.speedScene) "En la barra superior, dentro de la escena de velocidad." else "En la barra superior, del lado contrario al clima.",
            ) {
                Switch(checked = settings.showSpeedDial, onCheckedChange = onShowSpeedDialChange)
            }
            AnimatedVisibility(settings.showSpeedDial) {
                Choice(
                    title = "Estilo del velocímetro",
                    options = listOf(SpeedDialStyle.CIRCLE to "Círculo", SpeedDialStyle.INLINE to "Números"),
                    selected = settings.speedDialStyle,
                    onSelect = onSpeedDialStyleChange,
                )
            }

            Section(
                "Escena de velocidad",
                "Coche en la ciudad que se vuelve cohete al ir rápido, del lado contrario al clima.",
            ) {
                Switch(checked = settings.speedScene, onCheckedChange = onSpeedSceneChange)
            }
            AnimatedVisibility(settings.speedScene) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // The threshold is stored in km/h and shown in the chosen unit.
                    val mph = SpeedUnits.usesMph(settings.speedUnit, java.util.Locale.getDefault().country)
                    Stepper(
                        "Cohete desde (${SpeedUnits.label(mph)})",
                        SpeedUnits.display(settings.rocketKmh.toFloat(), mph).roundToInt(),
                        if (mph) 15..125 else 20..200,
                        step = if (mph) 5 else 10,
                        onChange = { onRocketKmhChange(SpeedUnits.toKmh(it.toFloat(), mph).roundToInt()) },
                    )
                    Choice(
                        title = "Silueta",
                        options = listOf(
                            VehicleShape.CAR to "Carro",
                            VehicleShape.SUV to "Camioneta",
                            VehicleShape.PICKUP to "Pickup",
                            VehicleShape.CAT to "Gato",
                            VehicleShape.DOG to "Perro",
                        ),
                        selected = settings.vehicleShape,
                        onSelect = onVehicleShapeChange,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Color", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            VehicleColor.entries.forEach { c ->
                                val isSelected = c == settings.vehicleColor
                                Box(
                                    Modifier
                                        .size(44.dp)
                                        .border(if (isSelected) 3.dp else 1.dp, if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline, CircleShape)
                                        .padding(5.dp)
                                        .clip(CircleShape)
                                        .background(Color(c.argb))
                                        .clickable { onVehicleColorChange(c) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (isSelected) Icon(Icons.Rounded.Check, contentDescription = null, tint = if (c == VehicleColor.WHITE || c == VehicleColor.YELLOW) Color.Black else Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }

            Choice(
                title = "Formato de hora",
                options = listOf(TimeFormat.SYSTEM to "Automático", TimeFormat.H12 to "12 h", TimeFormat.H24 to "24 h"),
                selected = settings.timeFormat,
                onSelect = onTimeFormatChange,
            )

            Choice(
                title = "Menú de aplicaciones",
                options = listOf(
                    AppMenuStyle.BOTTOM_SHEET to "Desde abajo",
                    AppMenuStyle.SWIPE_PAGE to "Página a la derecha (desliza ←)",
                ),
                selected = settings.appMenuStyle,
                onSelect = onAppMenuStyleChange,
            )

            Section("Nombres de apps", "Muestra el nombre debajo del icono de las apps del escritorio.") {
                Switch(checked = settings.showAppLabels, onCheckedChange = onShowAppLabelsChange)
            }

            Choice(
                title = "Tamaño de iconos de la barra",
                options = listOf(DockIconSize.SMALL to "Chico", DockIconSize.MEDIUM to "Mediano", DockIconSize.LARGE to "Grande"),
                selected = settings.dockIconSize,
                onSelect = onDockIconSizeChange,
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Mostrar", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Elige qué se ve en las barras. Configuración siempre queda visible.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Two columns of switches so the sheet doesn't get too long.
                DOCK_ELEMENT_LABELS.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        pair.forEach { (element, label) ->
                            Row(Modifier.weight(1f).height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                Switch(
                                    checked = element !in settings.hiddenElements,
                                    onCheckedChange = { onElementVisibilityChange(element, it) },
                                )
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Cuadrícula", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Stepper("Columnas", grid.columns, GridColumnsRange) { onGridChange(grid.copy(columns = it)) }
                        Stepper("Filas", grid.rows, GridRowsRange) { onGridChange(grid.copy(rows = it)) }
                    }
                    Spacer(Modifier.width(24.dp))
                    GridPreview(grid, Modifier.width(180.dp))
                }
                AnimatedVisibility(gridError != null) {
                    Text(
                        text = gridError.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            // Attribution required by the CC BY 4.0 license of the weather data.
            Text(
                text = "Datos del clima: Open-Meteo.com (CC BY 4.0)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Labels of the items that can be hidden (in the order they appear). */
private val DOCK_ELEMENT_LABELS = listOf(
    DockElement.WEATHER to "Clima",
    DockElement.CLOCK to "Hora",
    DockElement.APPS to "Apps",
    DockElement.ASSISTANT to "Asistente",
    DockElement.HOTSPOT to "Hotspot",
    DockElement.SIGNAL to "Señal",
    DockElement.VOLUME to "Volumen",
    DockElement.NOTIFICATIONS to "Notificaciones",
)

/** Accent names in the order they're shown. */
private val ACCENT_LABELS = listOf(
    AccentColor.BLUE to "Azul",
    AccentColor.TEAL to "Turquesa",
    AccentColor.GREEN to "Verde",
    AccentColor.PURPLE to "Morado",
    AccentColor.PINK to "Rosa",
    AccentColor.ORANGE to "Naranja",
    AccentColor.RED to "Rojo",
    AccentColor.DYNAMIC to "Dinámico",
)

/** 48 dp circular swatches; the selected one gets a ring and ✓. "Dinámico" only on Android 12+. */
@Composable
private fun AccentPicker(selected: AccentColor, onSelect: (AccentColor) -> Unit) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val options = ACCENT_LABELS.filter { (accent, _) ->
        accent != AccentColor.DYNAMIC || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Color de acento", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            options.forEach { (accent, label) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val isSelected = accent == selected
                    Box(
                        Modifier
                            .size(48.dp)
                            .border(
                                width = if (isSelected) 3.dp else 0.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                shape = CircleShape,
                            )
                            .padding(5.dp)
                            .clip(CircleShape)
                            .then(
                                if (accent == AccentColor.DYNAMIC) {
                                    Modifier.background(Brush.sweepGradient(listOf(Color(0xFF4285F4), Color(0xFFEA4335), Color(0xFFFBBC05), Color(0xFF34A853), Color(0xFF4285F4))))
                                } else {
                                    Modifier.background(accent.swatch(dark))
                                },
                            )
                            .clickable { onSelect(accent) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSelected) Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

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

@Composable
private fun Section(title: String, subtitle: String, trailing: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

/** − value + with 48 dp buttons (in-car use). */
@Composable
private fun Stepper(label: String, value: Int, range: IntRange, step: Int = 1, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        FilledTonalIconButton(onClick = { onChange(value - step) }, enabled = value > range.first, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Rounded.Remove, contentDescription = "Menos $label")
        }
        Text(
            text = "$value",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(48.dp),
            textAlign = TextAlign.Center,
        )
        FilledTonalIconButton(onClick = { onChange(value + step) }, enabled = value < range.last, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Rounded.Add, contentDescription = "Más $label")
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
