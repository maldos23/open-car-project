package org.opencarproject.launcher

import android.app.Application
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.opencarproject.launcher.data.source.SettingsDataSource
import org.opencarproject.launcher.presentation.reverse.ReverseOverlay

/**
 * Starts what must work without the home screen being open: the reverse overlay follows the
 * settings from process start (boot, reinstall, or the system rebinding the notification
 * listener), so reverse gear shows it even while another app is in front.
 */
class LauncherApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MainScope().launch { SettingsDataSource(this@LauncherApp).settingsFlow.collect { ReverseOverlay.apply(this@LauncherApp, it) } }
    }
}
