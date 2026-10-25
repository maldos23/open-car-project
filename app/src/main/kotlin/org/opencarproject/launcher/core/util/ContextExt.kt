package org.opencarproject.launcher.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.TypedValue
import androidx.core.app.NotificationManagerCompat
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap

/**
 * Utilities and extensions reused across the app.
 * Kept free of business rules: only conversions and platform adaptations.
 */

/** Converts dp to pixels (Float). */
fun Context.dp(value: Float): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

/** Converts dp to pixels (Int). */
fun Context.dpInt(value: Float): Int = dp(value).toInt()

/** Renders a [Drawable] (e.g. an app icon) into a square [ImageBitmap]. */
fun Drawable.toImageBitmap(sizePx: Int): ImageBitmap {
    val bitmap = createBitmap(sizePx, sizePx)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, sizePx, sizePx)
    draw(canvas)
    return bitmap.asImageBitmap()
}

/**
 * Renders a [Drawable] into an [ImageBitmap] of the given size, preserving its aspect
 * ratio (centered). Useful for widget previews, which are not square.
 */
fun Drawable.toImageBitmapAspect(widthPx: Int, heightPx: Int): ImageBitmap {
    val intrinsicW = intrinsicWidth.coerceAtLeast(1)
    val intrinsicH = intrinsicHeight.coerceAtLeast(1)
    val scale = minOf(widthPx.toFloat() / intrinsicW, heightPx.toFloat() / intrinsicH)
    val w = (intrinsicW * scale).toInt().coerceAtLeast(1)
    val h = (intrinsicH * scale).toInt().coerceAtLeast(1)
    val bitmap = createBitmap(widthPx, heightPx)
    val canvas = Canvas(bitmap)
    val left = (widthPx - w) / 2
    val top = (heightPx - h) / 2
    setBounds(left, top, left + w, top + h)
    draw(canvas)
    return bitmap.asImageBitmap()
}

/**
 * Renders a [Drawable] no larger than [maxPx] on its longest side, preserving its aspect ratio.
 * Notification icons can be full-size photos: decoding them as is wastes megabytes per card.
 */
fun Drawable.toBoundedImageBitmap(maxPx: Int): ImageBitmap {
    val w = intrinsicWidth.takeIf { it > 0 } ?: maxPx
    val h = intrinsicHeight.takeIf { it > 0 } ?: maxPx
    val scale = minOf(1f, maxPx.toFloat() / maxOf(w, h))
    return toBitmap((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1)).asImageBitmap()
}

/** The app's name, or its package name if it can't be read (e.g. just uninstalled). */
fun Context.appLabel(packageName: String): String = runCatching {
    packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
}.getOrDefault(packageName)

/** The app's icon at [sizeDp], or `null` if it can't be read. */
fun Context.appIcon(packageName: String, sizeDp: Float): ImageBitmap? = runCatching {
    packageManager.getApplicationIcon(packageName).toImageBitmap(dpInt(sizeDp))
}.getOrNull()

/** `true` if the user granted notification access (also required to read MediaSessions). */
fun Context.hasNotificationAccess(): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
