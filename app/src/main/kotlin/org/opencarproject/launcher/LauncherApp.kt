package org.opencarproject.launcher

import android.app.Application
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.opencarproject.launcher.data.source.SettingsDataSource
import org.opencarproject.launcher.presentation.reverse.ReverseOverlay
import org.opencarproject.launcher.presentation.widget.ClimateOverlay
import org.opencarproject.launcher.presentation.widget.FloatingIsland

/**
 * Starts what must work without the home screen being open: the reverse overlay and the floating
 * music island and the climate bar follow the settings from process start (boot, reinstall, or the system rebinding the notification
 * listener), so reverse gear shows it even while another app is in front.
 */
class LauncherApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MainScope().launch { SettingsDataSource(this@LauncherApp).settingsFlow.collect {
            ReverseOverlay.apply(this@LauncherApp, it)
            FloatingIsland.apply(this@LauncherApp, it)
            ClimateOverlay.apply(this@LauncherApp, it)
        } }
    }
}
