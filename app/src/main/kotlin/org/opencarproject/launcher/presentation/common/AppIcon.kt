package org.opencarproject.launcher.presentation.common

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.createBitmap
import org.opencarproject.launcher.core.util.dpInt
import org.opencarproject.launcher.core.util.toImageBitmap
import org.opencarproject.launcher.core.util.toImageBitmapAspect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Resolves an app's icon (by "package/activity" component) as an [ImageBitmap].
 * Shared between the grid, the dock and the menus (DRY). Decoded off the main thread and kept
 * in a process-wide cache, so scrolling the menu or reopening it doesn't decode again.
 */
@Composable
fun rememberAppIcon(componentKey: String, sizePx: Int = 96, withoutBackground: Boolean = false): ImageBitmap? {
    val context = LocalContext.current
    val key = "$componentKey/$sizePx/$withoutBackground"
    return produceState(AppIconCache.get(key), key) {
        value = AppIconCache.get(key) ?: withContext(Dispatchers.IO) {
            loadAppIcon(context, componentKey, sizePx, withoutBackground)
        }?.also { AppIconCache.put(key, it) }
    }.value
}

// ponytail: never invalidated, so an app that changes its icon shows the new one after a restart.
private object AppIconCache : LruCache<String, ImageBitmap>(16 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
}

private fun loadAppIcon(context: Context, componentKey: String, sizePx: Int, withoutBackground: Boolean): ImageBitmap? =
    runCatching {
        val cn = ComponentName.unflattenFromString(componentKey) ?: return@runCatching null
        val drawable = context.packageManager.getActivityInfo(cn, 0).loadIcon(context.packageManager)
        val logo = if (withoutBackground) drawable.foregroundBitmap(sizePx) else null
        logo ?: drawable.toImageBitmap(sizePx)
    }.getOrNull()

/**
 * Resolves an App Widget's preview (by "package/class" component), in this order:
 * the system-generated preview (API 35+), the provider's `previewLayout` (API 31+, what most
 * modern widgets ship), its `previewImage`, and finally its icon. Layout previews are rendered
 * offscreen into a bitmap, so the panel shows the widget's real look instead of an icon.
 */
@Composable
fun rememberWidgetPreview(componentKey: String): ImageBitmap? {
    val context = LocalContext.current
    val densityDpi = LocalConfiguration.current.densityDpi
    return remember(componentKey, densityDpi) {
        runCatching {
            val cn = ComponentName.unflattenFromString(componentKey) ?: return@runCatching null
            val manager = AppWidgetManager.getInstance(context)
            val info = manager.installedProviders?.firstOrNull { it.provider == cn }
                ?: return@runCatching null
            val layoutPreview = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM -> runCatching {
                    manager.getWidgetPreview(cn, Process.myUserHandle(), AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN)
                }.getOrNull()
                else -> null
            } ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && info.previewLayout != 0) {
                RemoteViews(cn.packageName, info.previewLayout)
            } else {
                null
            }
            layoutPreview?.let { runCatching { it.render(context, info) }.getOrNull() }?.let { return@runCatching it }

            val density = densityDpi
            val drawable = info.loadPreviewImage(context, density)
                ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    info.loadIcon(context, density)
                } else {
                    val res = info.icon
                    if (res != 0) context.packageManager.getDrawable(info.provider.packageName, res, null) else null
                }
                ?: return@runCatching null
            drawable.toImageBitmapAspect(240, 120)
        }.getOrNull()
    }
}

/** Inflates the preview at the widget's minimum size and draws it into a bitmap. */
private fun RemoteViews.render(context: Context, info: AppWidgetProviderInfo): ImageBitmap {
    val minPx = context.dpInt(120f)
    val width = info.minWidth.coerceAtLeast(minPx)
    val height = info.minHeight.coerceAtLeast(minPx / 2)
    val view = apply(context, FrameLayout(context))
    view.measure(
        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
    )
    view.layout(0, 0, width, height)
    val bitmap = createBitmap(width, height)
    view.draw(Canvas(bitmap))
    return bitmap.asImageBitmap()
}

/**
 * Just the logo of an adaptive icon (without the background shape). The foreground layer is 108 dp,
 * of which 72 are visible: it's drawn 1.5× larger and centered to crop the safe-zone margin.
 * `null` if the icon isn't adaptive (legacy icons already ship their own drawing).
 */
private fun Drawable.foregroundBitmap(sizePx: Int): ImageBitmap? {
    if (this !is AdaptiveIconDrawable) return null
    val front = foreground ?: return null
    val bitmap = createBitmap(sizePx, sizePx)
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
