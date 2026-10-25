package org.opencarproject.launcher.presentation.sample

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import org.opencarproject.launcher.R

/**
 * Sample media widget. Its buttons demonstrate RemoteViews interaction
 * via PendingIntent (the provider's own receiver).
 */
class SampleGlassMediaWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id ->
            manager.updateAppWidget(id, buildViews(context, context.getString(R.string.app_name)))
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_PLAY, ACTION_NEXT -> {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, SampleGlassMediaWidget::class.java))
                val subtitle = context.getString(if (intent.action == ACTION_PLAY) R.string.widget_media_playing else R.string.widget_media_next_track)
                ids.forEach { id -> manager.updateAppWidget(id, buildViews(context, subtitle)) }
            }
        }
    }

    private fun buildViews(context: Context, subtitle: String): RemoteViews {
        return RemoteViews(context.packageName, R.layout.widget_media).apply {
            setTextViewText(R.id.widget_media_title, context.getString(R.string.widget_media_now_playing))
            setTextViewText(R.id.widget_media_subtitle, subtitle)
            setOnClickPendingIntent(R.id.widget_media_play, broadcast(context, ACTION_PLAY))
            setOnClickPendingIntent(R.id.widget_media_next, broadcast(context, ACTION_NEXT))
        }
    }

    private fun broadcast(context: Context, action: String): PendingIntent {
        val intent = Intent(context, SampleGlassMediaWidget::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val ACTION_PLAY = "org.opencarproject.launcher.action.MEDIA_PLAY"
        private const val ACTION_NEXT = "org.opencarproject.launcher.action.MEDIA_NEXT"
    }
}
