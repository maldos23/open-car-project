package org.opencarproject.launcher.presentation

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import org.opencarproject.launcher.core.ui.theme.LauncherTheme
import org.opencarproject.launcher.core.ui.theme.isDark
import org.opencarproject.launcher.presentation.common.LocalFullscreen
import org.opencarproject.launcher.presentation.common.setSystemBarsHidden
import org.opencarproject.launcher.di.AppContainer
import org.opencarproject.launcher.presentation.launcher.LauncherScreen
import org.opencarproject.launcher.presentation.launcher.LauncherViewModel
import org.opencarproject.launcher.presentation.launcher.LauncherViewModelFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.opencarproject.launcher.core.model.AppLanguage
import java.util.Locale

/**
 * Home screen (HOME) of the launcher.
 *
 * It is the UI composition root: builds the [AppContainer], wires up the ViewModel and
 * owns the lifecycle of [AppContainer.widgetHost]. It also orchestrates the native widget
 * permission flow (`ACTION_APPWIDGET_BIND`), required for a third-party launcher to
 * obtain the binding permission (`ACTION_APPWIDGET_BIND`) and the widget configuration.
 */
class LauncherActivity : ComponentActivity() {

    private val container by lazy { AppContainer(this) }

    private val viewModel: LauncherViewModel by viewModels {
        LauncherViewModelFactory(container)
    }

    /** Language the resources were loaded with; a different saved one recreates the Activity. */
    private var appliedLanguage = AppLanguage.SYSTEM

    /**
     * Applies the in-app language before any resource is read. Done at the Activity level (not
     * in Compose) so dialogs and bottom sheets, which have their own windows, get it too.
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        // ponytail: one blocking DataStore read at startup (a few ms); a mirror in SharedPreferences
        // would avoid it at the cost of a second source of truth.
        appliedLanguage = runBlocking { container.observeSettings().first().appLanguage }
        val system = Resources.getSystem().configuration.locales[0]
        val locale = if (appliedLanguage == AppLanguage.SYSTEM) {
            system
        } else {
            // Keep the device region: °F/mph and other regional defaults must not follow the language.
            Locale.Builder().setLanguageTag(appliedLanguage.tag).setRegion(system.country).build().also {
                applyOverrideConfiguration(Configuration().apply { setLocale(it) })
            }
        }
        // Date and relative-time formatting reads the default locale, not the resources.
        Locale.setDefault(locale)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val ready by viewModel.ready.collectAsStateWithLifecycle()
            if (!ready) return@setContent
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val dark = settings.themeMode.isDark()

            // System bar icons legible on the chosen theme (not just the system one).
            LaunchedEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                )
            }
            LaunchedEffect(settings.fullscreen) { window.setSystemBarsHidden(settings.fullscreen) }
            LaunchedEffect(settings.appLanguage) { if (settings.appLanguage != appliedLanguage) recreate() }

            LauncherTheme(themeMode = settings.themeMode, accent = settings.accentColor) {
                CompositionLocalProvider(LocalFullscreen provides settings.fullscreen) {
                    LauncherScreen(
                        viewModel = viewModel,
                        widgetHost = container.widgetHost,
                    )
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    // The widget needs permission: system dialog "Allow creating widgets?".
                    viewModel.widgetBindRequest.collect { (appWidgetId, provider) -> requestBind(appWidgetId, provider) }
                }
                launch {
                    viewModel.widgetConfigureRequest.collect { appWidgetId -> configureIfNeeded(appWidgetId) }
                }
                launch {
                    viewModel.messages.collect { android.widget.Toast.makeText(this@LauncherActivity, getString(it), android.widget.Toast.LENGTH_LONG).show() }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        container.widgetHost.startListening()
    }

    override fun onStop() {
        super.onStop()
        container.widgetHost.stopListening()
    }

    private var pendingBind: Pair<Int, String>? = null

    private val bindLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val (appWidgetId, provider) = pendingBind ?: return@registerForActivityResult
        pendingBind = null
        if (result.resultCode == RESULT_OK) {
            viewModel.onWidgetPicked(appWidgetId, provider)
        } else {
            viewModel.onWidgetPickCancelled(appWidgetId)
        }
    }

    /** Asks the user for permission to bind that specific widget (not the generic picker). */
    private fun requestBind(appWidgetId: Int, provider: String) {
        val component = ComponentName.unflattenFromString(provider) ?: return viewModel.onWidgetPickCancelled(appWidgetId)
        pendingBind = appWidgetId to provider
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, component)
        runCatching { bindLauncher.launch(intent) }.onFailure {
            pendingBind = null
            viewModel.onWidgetPickCancelled(appWidgetId)
        }
    }

    /**
     * Opens the widget's configuration if it declares one (e.g. choosing a calendar). Uses the host,
     * which launches it on behalf of the launcher even if the activity is not exported.
     */
    private fun configureIfNeeded(appWidgetId: Int) {
        val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId) ?: return
        if (info.configure == null) return
        runCatching {
            container.widgetHost.startAppWidgetConfigureActivityForResult(this, appWidgetId, 0, REQUEST_CONFIGURE_APPWIDGET, null)
        }
    }

    @Deprecated("startAppWidgetConfigureActivityForResult only delivers its result here")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_CONFIGURE_APPWIDGET) return
        val appWidgetId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
        if (resultCode != RESULT_OK && appWidgetId != -1) viewModel.onWidgetConfigureCancelled(appWidgetId)
    }

    companion object {
        private const val REQUEST_CONFIGURE_APPWIDGET = 1002
    }
}
