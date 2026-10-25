package org.opencarproject.launcher.presentation.widget

import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.opencarproject.launcher.data.widget.LauncherAppWidgetHost

/**
 * Wraps a native [android.appwidget.AppWidgetHostView] inside Compose.
 *
 * Each instance is bound to a single `appWidgetId`; since the placed item's identity
 * is stable, the AndroidView is not recreated unnecessarily.
 */
@Composable
fun GlassWidgetHostView(
    host: LauncherAppWidgetHost,
    appWidgetId: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            host.createWidgetView(ctx, appWidgetId)?.apply {
                // AppWidgetHostView adds default padding (~8 dp per side): without it, the
                // widget's edge lines up with the other cards in the grid.
                setPadding(0, 0, 0, 0)
            } ?: FrameLayout(ctx)
        },
    )
}
