package org.opencarproject.launcher.data.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.Context

/** Host that manages the launcher's embedded widgets. */
class LauncherAppWidgetHost(context: Context, hostId: Int) : AppWidgetHost(context, hostId) {

    /** Creates the native view of an already bound widget, or `null` if it no longer exists. */
    fun createWidgetView(context: Context, appWidgetId: Int): AppWidgetHostView? {
        val info = AppWidgetManager.getInstance(context).getAppWidgetInfo(appWidgetId)
            ?: return null
        return createView(context, appWidgetId, info)
    }
}
