package org.opencarproject.launcher.presentation.sample

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import org.opencarproject.launcher.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Sample clock widget, bundled with the launcher.
 *
 * Demonstrates the use of native APIs (RemoteViews + AlarmManager) and ensures the
 * test environment always has a widget to add. Updates every minute.
 */
class SampleGlassClockWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        updateClocks(context, manager, appWidgetIds)
        scheduleNextTick(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TICK) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, SampleGlassClockWidget::class.java))
            updateClocks(context, manager, ids)
            scheduleNextTick(context)
        }
    }

    private fun updateClocks(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val date = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date())
        ids.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_clock)
            views.setTextViewText(R.id.widget_clock_time, time)
            views.setTextViewText(R.id.widget_clock_date, date)
            manager.updateAppWidget(id, views)
        }
    }

    /** Schedules the next "tick" at the start of the next minute (inexact, no permissions). */
    private fun scheduleNextTick(context: Context) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, SampleGlassClockWidget::class.java).setAction(ACTION_TICK)
        val pending = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val nextMinute = System.currentTimeMillis() + 60_000 - (System.currentTimeMillis() % 60_000) + 1_000
        alarm.setAndAllowWhileIdle(AlarmManager.RTC, nextMinute, pending)
    }

    companion object {
        private const val ACTION_TICK = "org.opencarproject.launcher.action.CLOCK_TICK"
    }
}
