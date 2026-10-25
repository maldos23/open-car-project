package org.opencarproject.launcher.presentation.launcher

import org.opencarproject.launcher.core.model.CatalogItem
import org.opencarproject.launcher.core.model.CatalogItemKind
import org.opencarproject.launcher.core.model.Cell
import org.opencarproject.launcher.core.model.LauncherSettings
import org.opencarproject.launcher.core.model.LayoutDefinition
import org.opencarproject.launcher.core.model.PlacedItem
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
import org.opencarproject.launcher.domain.usecase.UpdateSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** Layout repository whose save takes a while, like DataStore: exposes lost updates. */
private class SlowLayoutRepository(initial: LayoutDefinition) : LayoutRepository {
    val state = MutableStateFlow(initial)
    override fun observeLayout(): Flow<LayoutDefinition> = state
    override suspend fun saveLayout(layout: LayoutDefinition) {
        delay(50)
        state.value = layout
    }
}

private object NoopHost : WidgetHostGateway {
    override fun allocateAppWidgetId() = 1
    override fun bindWidgetIfAllowed(appWidgetId: Int, provider: String) = true
    override fun deleteWidget(appWidgetId: Int) = Unit
    override fun launchApp(component: String) = Unit
    override fun launchAssistant() = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
class LauncherViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun twoQuickDeletes_bothStick() = runTest(dispatcher) {
        val repo = SlowLayoutRepository(LayoutDefinition(items = listOf(app("a", 0), app("b", 2), app("c", 4))))
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.onDeleteItem("app-pkg/a")
        vm.onDeleteItem("app-pkg/b")
        advanceUntilIdle()

        assertEquals(listOf("app-pkg/c"), repo.state.value.items.map { it.id })
    }

    private fun viewModel(repo: LayoutRepository): LauncherViewModel {
        val settings = object : SettingsRepository {
            override fun observeSettings() = flowOf(LauncherSettings())
            override suspend fun updateSettings(transform: (LauncherSettings) -> LauncherSettings) = Unit
        }
        val catalog = object : CatalogRepository {
            override fun observeCatalog() = flowOf(emptyList<CatalogItem>())
        }
        return LauncherViewModel(
            observeCatalog = ObserveCatalog(catalog),
            loadLayout = LoadLayout(repo),
            addItem = AddItem(NoopHost, repo),
            addBoundWidget = AddBoundWidget(repo),
            deleteWidgetId = DeleteWidgetId(NoopHost),
            removeItem = RemoveItem(NoopHost, repo),
            moveItem = MoveItem(repo),
            resizeItem = ResizeItem(repo),
            launchApp = LaunchApp(NoopHost),
            launchAssistant = LaunchAssistant(NoopHost),
            observeSettings = ObserveSettings(settings),
            updateSettings = UpdateSettings(settings),
            changeGridSize = ChangeGridSize(repo),
        )
    }

    private fun app(name: String, column: Int) = PlacedItem(
        id = PlacedItem.identity(CatalogItemKind.APP, "pkg/$name", -1),
        kind = CatalogItemKind.APP,
        componentKey = "pkg/$name",
        cell = Cell(0, column),
        span = Cell(1, 1),
        title = name,
        packageName = "pkg",
    )
}
