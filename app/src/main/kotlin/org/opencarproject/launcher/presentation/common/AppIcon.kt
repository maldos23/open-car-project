package org.opencarproject.launcher.presentation.common

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import org.opencarproject.launcher.core.util.toImageBitmap
import org.opencarproject.launcher.core.util.toImageBitmapAspect

/**
 * Resolves an app's icon (by "package/activity" component) as an [ImageBitmap].
 * Shared between the grid and the panel (DRY).
 */
@Composable
fun rememberAppIcon(componentKey: String, sizePx: Int = 96, withoutBackground: Boolean = false): ImageBitmap? {
    val context = LocalContext.current
    return remember(componentKey, sizePx, withoutBackground) {
        runCatching {
            val cn = ComponentName.unflattenFromString(componentKey) ?: return@runCatching null
            val info = context.packageManager.getActivityInfo(cn, 0)
            val drawable = info.loadIcon(context.packageManager)
            if (withoutBackground) drawable.foregroundBitmap(sizePx) ?: drawable.toImageBitmap(sizePx) else drawable.toImageBitmap(sizePx)
        }.getOrNull()
    }
}

/**
 * Resolves an App Widget's preview (by "package/class" component).
 *
 * Uses `loadPreviewImage` (API 31+), which renders the provider's `previewLayout`/`previewImage`;
 * if missing, falls back to the provider's icon. That way the panel shows the widget's real look
 * instead of a generic icon.
 */
@Composable
fun rememberWidgetPreview(componentKey: String): ImageBitmap? {
    val context = LocalContext.current
    return remember(componentKey) {
        runCatching {
            val cn = ComponentName.unflattenFromString(componentKey) ?: return@runCatching null
            val manager = AppWidgetManager.getInstance(context)
            val info = manager.installedProviders?.firstOrNull { it.provider == cn }
                ?: return@runCatching null
            val density = context.resources.displayMetrics.densityDpi
            val drawable = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                    info.loadPreviewImage(context, density)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ->
                    info.loadIcon(context, density)
                else -> {
                    val res = info.icon
                    if (res != 0) context.packageManager.getDrawable(info.provider.packageName, res, null) else null
                }
            } ?: return@runCatching null
            drawable.toImageBitmapAspect(240, 120)
        }.getOrNull()
    }
}

/**
 * Just the logo of an adaptive icon (without the background shape). The foreground layer is 108 dp,
 * of which 72 are visible: it's drawn 1.5× larger and centered to crop the safe-zone margin.
 * `null` if the icon isn't adaptive (legacy icons already ship their own drawing).
 */
private fun Drawable.foregroundBitmap(sizePx: Int): ImageBitmap? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || this !is AdaptiveIconDrawable) return null
    val front = foreground ?: return null
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val inset = sizePx / 4
    front.setBounds(-inset, -inset, sizePx + inset, sizePx + inset)
    front.draw(Canvas(bitmap))
    // Some foreground layers carry a nearly transparent veil the size of the icon (e.g. the
    // Google app): on a dark background it shows up as a square. Nearly invisible pixels are
    // dropped; the logo and its anti-aliased edge are kept.
    val pixels = IntArray(sizePx * sizePx)
    bitmap.getPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
    for (i in pixels.indices) {
        if ((pixels[i] ushr 24) < VEIL_ALPHA) pixels[i] = 0
    }
    bitmap.setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
    return bitmap.asImageBitmap()
}

/** Alpha (0-255) below which a foreground-layer pixel counts as veil, not logo. */
private const val VEIL_ALPHA = 60
