package org.opencarproject.launcher.presentation.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.opencarproject.launcher.R
import org.opencarproject.launcher.core.model.LauncherSettings
import org.opencarproject.launcher.data.source.UpdateSource
import org.opencarproject.launcher.presentation.LauncherActivity

/**
 * Daily check for a newer GitHub release while "Notify me about updates" is on, shown as a
 * notification (so it also reaches the notification island); tapping it downloads and installs.
 */
object UpdateNotifier {

    const val ACTION_UPDATE = "org.opencarproject.launcher.ACTION_UPDATE"
    private const val CHANNEL = "updates"
    private const val NOTIFICATION_ID = 7001
    private const val CHECK_EVERY_MS = 24 * 60 * 60 * 1000L
    // ponytail: in-process timer, restarts with the process (boot); WorkManager if checks must survive doze.

    private var scope: CoroutineScope? = null
    /** Already announced this session, so a restart of the loop doesn't repeat it. */
    private var notified: String? = null

    /** Follows the settings: called on every change from process start. */
    fun apply(context: Context, next: LauncherSettings) {
        val app = context.applicationContext
        if (!next.updateNotify) {
            scope?.cancel(); scope = null
            return
        }
        if (scope != null) return
        scope = MainScope().apply {
            launch {
                while (isActive) {
                    UpdateSource.available(app)?.let { if (it.version != notified) notify(app, it.version) }
                    delay(CHECK_EVERY_MS)
                }
            }
        }
    }

    fun canNotify(context: Context) = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun notify(app: Context, version: String) {
        if (!canNotify(app)) return
        val manager = app.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, app.getString(R.string.update_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
        // Opens Home, which starts the update: the install screen may only come from the foreground.
        val open = PendingIntent.getActivity(
            app, 0,
            Intent(app, LauncherActivity::class.java).setAction(ACTION_UPDATE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(app.getString(R.string.update_notification_title))
            .setContentText(app.getString(R.string.update_notification_text, version))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
        notified = version
    }

    /** From the notification: download and install the latest release, with a toast meanwhile. */
    fun installLatest(context: Context) {
        val app = context.applicationContext
        app.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        MainScope().launch {
            val release = UpdateSource.available(app)
                ?: return@launch toast(app, app.getString(R.string.settings_updates_latest))
            toast(app, app.getString(R.string.settings_updates_downloading))
            if (!UpdateSource.install(app, release)) toast(app, app.getString(R.string.settings_updates_error))
        }
    }

    internal fun toast(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()
}

/** Outcome of an install session: shows Android's confirmation screen, or the error. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION ->
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                    ?.let { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
            PackageInstaller.STATUS_SUCCESS -> Unit // The process is replaced by the new version.
            else -> UpdateNotifier.toast(
                context,
                context.getString(R.string.settings_updates_install_error, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: status.toString()),
            )
        }
    }
}
