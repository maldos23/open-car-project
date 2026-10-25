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
 * Native Material 3 color scheme (dark theme, typical of a vehicle environment).
 * Components use the native Material 3 `Card`, `FilledTonalIconButton`, `IconButton`, etc.,
 * without custom styles.
 */
private val DarkColors = darkColorScheme(
    primary = Color(0xFF8CD0FF),
    onPrimary = Color(0xFF00344D),
    primaryContainer = Color(0xFF004C6E),
    onPrimaryContainer = Color(0xFFC9E7FF),
    secondary = Color(0xFFB6C9D9),
    onSecondary = Color(0xFF21323E),
    secondaryContainer = Color(0xFF374955),
    onSecondaryContainer = Color(0xFFD1E5F6),
    tertiary = Color(0xFFB8C8EA),
    onTertiary = Color(0xFF23304C),
    background = Color(0xFF0F1419),
    onBackground = Color(0xFFDEE4EA),
    surface = Color(0xFF0F1419),
    onSurface = Color(0xFFDEE4EA),
    surfaceVariant = Color(0xFF41474D),
    onSurfaceVariant = Color(0xFFC1C7CE),
    surfaceContainerHigh = Color(0xFF1A2026),
    outline = Color(0xFF8B9198),
)

/** Light variant with the same (blue) palette for daytime use. */
private val LightColors = lightColorScheme(
    primary = Color(0xFF00658F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC9E7FF),
    onPrimaryContainer = Color(0xFF001E2E),
    secondary = Color(0xFF4F616E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD2E5F5),
    onSecondaryContainer = Color(0xFF0B1D29),
    tertiary = Color(0xFF515E7D),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF6FAFE),
    onBackground = Color(0xFF171C20),
    surface = Color(0xFFF6FAFE),
    onSurface = Color(0xFF171C20),
    surfaceVariant = Color(0xFFDDE3EA),
    onSurfaceVariant = Color(0xFF41474D),
    outline = Color(0xFF71787E),
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
    accent: AccentColor = AccentColor.BLUE,
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
