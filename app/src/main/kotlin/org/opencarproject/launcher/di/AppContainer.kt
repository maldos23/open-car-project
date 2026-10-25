package org.opencarproject.launcher.di

import android.content.Context
import org.opencarproject.launcher.data.repository.CatalogRepositoryImpl
import org.opencarproject.launcher.data.repository.LayoutRepositoryImpl
import org.opencarproject.launcher.data.repository.SettingsRepositoryImpl
import org.opencarproject.launcher.data.repository.WidgetHostGatewayImpl
import org.opencarproject.launcher.data.source.LayoutDataSource
import org.opencarproject.launcher.data.source.SettingsDataSource
import org.opencarproject.launcher.data.source.SystemCatalogSource
import org.opencarproject.launcher.data.widget.LauncherAppWidgetHost
import org.opencarproject.launcher.domain.repository.CatalogRepository
import org.opencarproject.launcher.domain.repository.LayoutRepository
import org.opencarproject.launcher.domain.repository.SettingsRepository
import org.opencarproject.launcher.domain.repository.WidgetHostGateway
import org.opencarproject.launcher.domain.usecase.AddBoundWidget
import org.opencarproject.launcher.domain.usecase.AddItem
import org.opencarproject.launcher.domain.usecase.ChangeGridSize
import org.opencarproject.launcher.domain.usecase.DeleteWidgetId
import org.opencarproject.launcher.domain.usecase.LaunchApp
import org.opencarproject.launcher.domain.usecase.LaunchAssistant
import org.opencarproject.launcher.domain.usecase.LoadLayout
import org.opencarproject.launcher.domain.usecase.MoveItem
import org.opencarproject.launcher.domain.usecase.ObserveCatalog
import org.opencarproject.launcher.domain.usecase.ObserveSettings
import org.opencarproject.launcher.domain.usecase.RemoveItem
import org.opencarproject.launcher.domain.usecase.ResizeItem
import org.opencarproject.launcher.domain.usecase.SaveLayout
import org.opencarproject.launcher.domain.usecase.UpdateSettings

/**
 * Composition root: manual dependency container (no DI frameworks).
 *
 * Builds the object graph once and exposes it. Injection is always via constructor
 * (SOLID's DIP), which also allows swapping any piece for a "fake" in tests without
 * touching production code.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    /** Native widget host: owned by the Activity to control its lifecycle. */
    val widgetHost: LauncherAppWidgetHost = LauncherAppWidgetHost(appContext, HOST_ID)

    // --- Data sources ---
    private val catalogSource = SystemCatalogSource(appContext)
    private val layoutDataSource = LayoutDataSource(appContext)
    private val settingsDataSource = SettingsDataSource(appContext)

    // --- Repositories (concrete implementations behind the domain interfaces) ---
    private val catalogRepository: CatalogRepository = CatalogRepositoryImpl(catalogSource)
    private val layoutRepository: LayoutRepository = LayoutRepositoryImpl(layoutDataSource)
    private val hostGateway: WidgetHostGateway = WidgetHostGatewayImpl(appContext, widgetHost)
    private val settingsRepository: SettingsRepository = SettingsRepositoryImpl(settingsDataSource)

    // --- Use cases ---
    val observeCatalog = ObserveCatalog(catalogRepository)
    val loadLayout = LoadLayout(layoutRepository)
    val saveLayout = SaveLayout(layoutRepository)
    val addItem = AddItem(hostGateway, layoutRepository)
    val addBoundWidget = AddBoundWidget(layoutRepository)
    val deleteWidgetId = DeleteWidgetId(hostGateway)
    val removeItem = RemoveItem(hostGateway, layoutRepository)
    val moveItem = MoveItem(layoutRepository)
    val resizeItem = ResizeItem(layoutRepository)
    val launchApp = LaunchApp(hostGateway)
    val launchAssistant = LaunchAssistant(hostGateway)
    val observeSettings = ObserveSettings(settingsRepository)
    val updateSettings = UpdateSettings(settingsRepository)
    val changeGridSize = ChangeGridSize(layoutRepository)

    companion object {
        private const val HOST_ID = 1024
    }
}
