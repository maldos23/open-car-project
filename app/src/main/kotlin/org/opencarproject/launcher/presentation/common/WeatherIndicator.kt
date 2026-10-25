package org.opencarproject.launcher.presentation.common

import org.opencarproject.launcher.R
import androidx.compose.ui.res.stringResource
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
                // No city yet (the geocoder timed out or failed): retry on the next pass.
                val stale = cached?.city == null || System.currentTimeMillis() - cachedAt > FRESH_MS
                if (force || stale) {
                    source.current()?.let { fresh ->
                        // A failed lookup keeps the last known city instead of falling back to the condition.
                        val it = if (fresh.city == null) fresh.copy(city = cached?.city) else fresh
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
    // Animated values are read only in drawBehind: they redraw the pill, never recompose it.
    val c1 = animateColorAsState(from, tween(1200), label = "c1")
    val c2 = animateColorAsState(to, tween(1200), label = "c2")
    // Slowly sliding gradient; during storms, a brief flash now and then.
    val t = rememberInfiniteTransition(label = "weatherBg")
    val drift = t.animateFloat(0f, 1f, infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Reverse), label = "drift")
    val flashPhase = t.animateFloat(0f, 1f, infiniteRepeatable(tween(4200, easing = LinearEasing)), label = "flash")
    val storm = current?.condition == WeatherCondition.THUNDERSTORM
    val onColor = if (current != null) Color.White else MaterialTheme.colorScheme.onSurface
    val onColorDim = if (current != null) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
    val shape = RoundedCornerShape(50)

    Row(
        modifier
            .clip(shape)
            .drawBehind {
                if (current == null) return@drawBehind
                val d = drift.value
                drawRect(
                    Brush.linearGradient(
                        listOf(c1.value, c2.value, c1.value),
                        start = Offset(-size.width * d, 0f),
                        end = Offset(size.width * (2f - d), size.height),
                    ),
                )
                if (storm && flashPhase.value in 0.02f..0.06f) drawRect(Color.White.copy(alpha = 0.25f))
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
                val degrees = Temperature.display(it.celsius, fahrenheit)
                stringResource(if (fahrenheit) R.string.temperature_value_f else R.string.temperature_value_c, degrees)
            } ?: stringResource(R.string.temperature_value_unknown),
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
                !hasPermission -> stringResource(R.string.weather_enable)
                current == null -> stringResource(R.string.weather_loading)
                else -> current.city ?: stringResource(current.condition.labelRes())
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

/** Short name to show when the city is unknown. */
private fun WeatherCondition.labelRes(): Int = when (this) {
    WeatherCondition.CLEAR -> R.string.weather_clear
    WeatherCondition.PARTLY_CLOUDY -> R.string.weather_partly_cloudy
    WeatherCondition.CLOUDY -> R.string.weather_cloudy
    WeatherCondition.FOG -> R.string.weather_fog
    WeatherCondition.DRIZZLE -> R.string.weather_drizzle
    WeatherCondition.RAIN -> R.string.weather_rain
    WeatherCondition.SNOW -> R.string.weather_snow
    WeatherCondition.THUNDERSTORM -> R.string.weather_thunderstorm
    WeatherCondition.UNKNOWN -> R.string.weather_unknown
}
