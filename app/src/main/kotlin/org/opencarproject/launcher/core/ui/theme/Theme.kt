package org.opencarproject.launcher.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.isSystemInDarkTheme
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import org.opencarproject.launcher.core.model.AccentColor
import org.opencarproject.launcher.core.model.ThemeMode
import org.opencarproject.launcher.core.pure.AccentPalette

/**
 * Native Material 3 color scheme (dark theme, typical of a vehicle environment), in the
 * Morado fork's purple palette.
 * Components use the native Material 3 `Card`, `FilledTonalIconButton`, `IconButton`, etc.,
 * without custom styles.
 */
private val DarkColors = darkColorScheme(
    primary = Color(0xFFA07CFF),
    onPrimary = Color(0xFF0F0C15),
    primaryContainer = Color(0xFF3B2A66),
    onPrimaryContainer = Color(0xFFE6DCFF),
    secondary = Color(0xFFC6B8E0),
    onSecondary = Color(0xFF2A2140),
    secondaryContainer = Color(0xFF241D31),
    onSecondaryContainer = Color(0xFFE4DAF5),
    tertiary = Color(0xFFE08BD8),
    onTertiary = Color(0xFF3A1036),
    background = Color(0xFF0F0C15),
    onBackground = Color(0xFFF4F0FA),
    surface = Color(0xFF0F0C15),
    onSurface = Color(0xFFF4F0FA),
    surfaceVariant = Color(0xFF2E2640),
    onSurfaceVariant = Color(0xFFA99CBF),
    surfaceContainerHigh = Color(0xFF1A1523),
    outline = Color(0xFF8A7FA0),
)

/** Light variant with the same (purple) palette for daytime use. */
private val LightColors = lightColorScheme(
    primary = Color(0xFF7B4FE0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEDE7F6),
    onPrimaryContainer = Color(0xFF24104F),
    secondary = Color(0xFF5E5470),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE6DDF4),
    onSecondaryContainer = Color(0xFF1E1530),
    tertiary = Color(0xFF9046C8),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF5F2FA),
    onBackground = Color(0xFF1B1524),
    surface = Color(0xFFF5F2FA),
    onSurface = Color(0xFF1B1524),
    surfaceVariant = Color(0xFFE2DAEE),
    onSurfaceVariant = Color(0xFF5E5470),
    outline = Color(0xFF7A6F8C),
)

/** `true` if the effective theme is dark (resolves [ThemeMode.SYSTEM] against the system). */
@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/**
 * Root app theme, based on native Material 3. The accent replaces the "brand" color roles
 * (primary, containers, tonal buttons); surfaces and text stay unchanged.
 */
@Composable
fun LauncherTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    accent: AccentColor = AccentColor.PURPLE,
    content: @Composable () -> Unit,
) {
    val dark = themeMode.isDark()
    val context = LocalContext.current
    val base = if (dark) DarkColors else LightColors
    val scheme = when {
        accent == AccentColor.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        accent.hue != null -> base.withAccent(accent.hue, dark)
        else -> base
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/** Accent swatch color (for the Settings picker). */
@Composable
fun AccentColor.swatch(dark: Boolean): Color = when {
    hue != null -> Color(AccentPalette.tones(hue, dark).primary)
    else -> (if (dark) DarkColors else LightColors).primary
}

private fun ColorScheme.withAccent(hue: Float, dark: Boolean): ColorScheme {
    val t = AccentPalette.tones(hue, dark)
    return copy(
        primary = Color(t.primary),
        onPrimary = Color(t.onPrimary),
        primaryContainer = Color(t.primaryContainer),
        onPrimaryContainer = Color(t.onPrimaryContainer),
        secondaryContainer = Color(t.secondaryContainer),
        onSecondaryContainer = Color(t.onSecondaryContainer),
        surfaceTint = Color(t.primary),
    )
}
