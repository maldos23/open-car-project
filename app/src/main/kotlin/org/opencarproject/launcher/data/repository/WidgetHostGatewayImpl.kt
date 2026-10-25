package org.opencarproject.launcher.data.repository

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import org.opencarproject.launcher.data.widget.LauncherAppWidgetHost
import org.opencarproject.launcher.domain.repository.WidgetHostGateway

/**
 * Implementation of the gateway to the system. Isolates the native APIs
 * (AppWidgetHost / AppWidgetManager / PackageManager) behind [WidgetHostGateway].
 */
class WidgetHostGatewayImpl(
    private val context: Context,
    private val host: LauncherAppWidgetHost,
) : WidgetHostGateway {

    private val manager: AppWidgetManager
        get() = AppWidgetManager.getInstance(context)

    override fun allocateAppWidgetId(): Int = host.allocateAppWidgetId()

    override fun bindWidgetIfAllowed(appWidgetId: Int, provider: String): Boolean {
        val component = ComponentName.unflattenFromString(provider) ?: return false
        return manager.bindAppWidgetIdIfAllowed(appWidgetId, component)
    }

    override fun deleteWidget(appWidgetId: Int) {
        host.deleteAppWidgetId(appWidgetId)
    }

    override fun launchApp(component: String) {
        val cn = ComponentName.unflattenFromString(component) ?: return
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(cn)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    override fun launchAssistant() {
        // VOICE_COMMAND targeted at the Google app opens the Assistant session over the
        // launcher, listening (same as "Ok Google"). Plain ACTION_ASSIST opens the Google app
        // on its search screen, so it's only kept as a fallback.
        val voice = Intent(Intent.ACTION_VOICE_COMMAND)
            .setPackage(GOOGLE_APP_PACKAGE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val opened = runCatching { context.startActivity(voice) }.isSuccess
        if (!opened) {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_ASSIST).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    private companion object {
        const val GOOGLE_APP_PACKAGE = "com.google.android.googlequicksearchbox"
    }
}
