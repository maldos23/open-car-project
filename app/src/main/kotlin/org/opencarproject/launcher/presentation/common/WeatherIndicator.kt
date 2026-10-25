package org.opencarproject.launcher.presentation.common

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import org.opencarproject.launcher.core.model.TemperatureUnit
import org.opencarproject.launcher.core.model.Weather
import org.opencarproject.launcher.core.model.WeatherCondition
import org.opencarproject.launcher.core.pure.Temperature
import org.opencarproject.launcher.core.pure.WeatherCodes
import org.opencarproject.launcher.data.source.WeatherSource
import org.opencarproject.launcher.presentation.widget.rememberOnResume
import kotlinx.coroutines.delay
import java.util.Locale

/** How often to refetch while the launcher is visible. */
private const val REFRESH_MS = 30 * 60 * 1000L

/** When returning to the launcher, don't refetch if the data is younger than this. */
private const val FRESH_MS = 10 * 60 * 1000L

/** Last fetched weather (survives recompositions and theme changes). */
private var cached: Weather? = null
private var cachedAt = 0L

/** Last known weather, for other views (the speed scene) to use. */
val CurrentWeather = kotlinx.coroutines.flow.MutableStateFlow<Weather?>(null)

/**
 * Top-bar weather: animated condition icon, temperature and city. Refreshes every 30 min and on
 * returning to the launcher; tapping it forces a refresh. Without location permission, prompts for it.
 */
@Composable
fun WeatherIndicator(unit: TemperatureUnit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val source = remember { WeatherSource(context.applicationContext) }
    val hasPermission = rememberOnResume { source.hasPermission() }
    var weather by remember { mutableStateOf(cached) }
    var refreshKey by remember { mutableIntStateOf(0) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(hasPermission, refreshKey) {
        if (!hasPermission) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val force = refreshKey > 0 && System.currentTimeMillis() - cachedAt > 5_000
                if (cached == null || force || System.currentTimeMillis() - cachedAt > FRESH_MS) {
                    source.current()?.let {
                        cached = it
                        cachedAt = System.currentTimeMillis()
                        CurrentWeather.value = it
                        weather = it
                    }
                }
                delay(REFRESH_MS)
            }
        }
    }

    val current = weather
    val (from, to) = current?.gradient() ?: (Color.Transparent to Color.Transparent)
    val c1 by animateColorAsState(from, tween(1200), label = "c1")
    val c2 by animateColorAsState(to, tween(1200), label = "c2")
    // Slowly sliding gradient; during storms, a brief flash now and then.
    val t = rememberInfiniteTransition(label = "weatherBg")
    val drift by t.animateFloat(0f, 1f, infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Reverse), label = "drift")
    val flashPhase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(4200, easing = LinearEasing)), label = "flash")
    val flash = if (current?.condition == WeatherCondition.THUNDERSTORM && flashPhase in 0.02f..0.06f) 0.25f else 0f
    val onColor = if (current != null) Color.White else MaterialTheme.colorScheme.onSurface
    val onColorDim = if (current != null) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
    val shape = RoundedCornerShape(50)

    Row(
        modifier
            .clip(shape)
            .drawBehind {
                if (current == null) return@drawBehind
                drawRect(
                    Brush.linearGradient(
                        listOf(c1, c2, c1),
                        start = Offset(-size.width * drift, 0f),
                        end = Offset(size.width * (2f - drift), size.height),
                    ),
                )
                if (flash > 0f) drawRect(Color.White.copy(alpha = flash))
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                if (hasPermission) refreshKey++ else permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            .height(SceneHeight)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        // All on one line: icon · temperature · city.
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Animated condition icon; crossfades to the new one when the weather changes.
        Crossfade(targetState = current?.let { it.condition to it.isDay }, label = "weatherIcon") { state ->
            if (state == null) {
                Icon(
                    imageVector = Icons.Rounded.Thermostat,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(36.dp),
                )
            } else {
                AnimatedWeatherIcon(condition = state.first, isDay = state.second, modifier = Modifier.size(46.dp), cloudColor = Color.White)
            }
        }
        // Crossfades when updated.
        AnimatedContent(
            targetState = current?.let {
                val fahrenheit = Temperature.usesFahrenheit(unit, Locale.getDefault().country)
                "${Temperature.display(it.celsius, fahrenheit)}°${if (fahrenheit) "F" else "C"}"
            } ?: "--°",
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "temperature",
        ) {
            Text(
                text = it,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = onColor,
            )
        }
        Text(
            text = when {
                !hasPermission -> "Activar clima"
                current == null -> "Obteniendo…"
                else -> current.city ?: WeatherCodes.label(current.condition)
            },
            style = MaterialTheme.typography.titleMedium,
            color = onColorDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 320.dp),
        )
    }
}

/** Two-tone gradient based on weather and daytime; dark so white text stays readable. */
private fun Weather.gradient(): Pair<Color, Color> = when (condition) {
    WeatherCondition.CLEAR -> if (isDay) Color(0xFF1976D2) to Color(0xFFBF360C) else Color(0xFF1A237E) to Color(0xFF4A148C)
    WeatherCondition.PARTLY_CLOUDY -> if (isDay) Color(0xFF1976D2) to Color(0xFF607D8B) else Color(0xFF283593) to Color(0xFF37474F)
    WeatherCondition.CLOUDY, WeatherCondition.UNKNOWN -> Color(0xFF455A64) to Color(0xFF78909C)
    WeatherCondition.FOG -> Color(0xFF546E7A) to Color(0xFF8D9DA6)
    WeatherCondition.DRIZZLE -> Color(0xFF0277BD) to Color(0xFF4F7A99)
    WeatherCondition.RAIN -> Color(0xFF0D47A1) to Color(0xFF01579B)
    WeatherCondition.SNOW -> Color(0xFF3F7CAC) to Color(0xFF6A9FC9)
    WeatherCondition.THUNDERSTORM -> Color(0xFF311B92) to Color(0xFF37474F)
}
