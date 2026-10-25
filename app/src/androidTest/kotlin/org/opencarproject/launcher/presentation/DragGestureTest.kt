package org.opencarproject.launcher.presentation

import org.opencarproject.launcher.domain.usecase.UpdateSettings
import org.opencarproject.launcher.domain.usecase.ObserveSettings
import org.opencarproject.launcher.domain.usecase.ChangeGridSize
import org.opencarproject.launcher.domain.repository.SettingsRepository
import org.opencarproject.launcher.core.model.LauncherSettings
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.opencarproject.launcher.core.model.CatalogItem
import org.opencarproject.launcher.core.model.CatalogItemKind
import org.opencarproject.launcher.core.model.GridSpec
import org.opencarproject.launcher.core.model.LayoutDefinition
import org.opencarproject.launcher.core.model.LayoutMode
import org.opencarproject.launcher.core.model.PlacedItem
import org.opencarproject.launcher.core.ui.theme.LauncherTheme
import org.opencarproject.launcher.domain.repository.CatalogRepository
import org.opencarproject.launcher.domain.repository.LayoutRepository
import org.opencarproject.launcher.domain.repository.WidgetHostGateway
import org.opencarproject.launcher.domain.usecase.AddBoundWidget
import org.opencarproject.launcher.domain.usecase.AddItem
import org.opencarproject.launcher.domain.usecase.DeleteWidgetId
import org.opencarproject.launcher.domain.usecase.LaunchApp
import org.opencarproject.launcher.domain.usecase.LaunchAssistant
import org.opencarproject.launcher.domain.usecase.LoadLayout
import org.opencarproject.launcher.domain.usecase.MoveItem
import org.opencarproject.launcher.domain.usecase.ObserveCatalog
import org.opencarproject.launcher.domain.usecase.RemoveItem
import org.opencarproject.launcher.domain.usecase.ResizeItem
import org.opencarproject.launcher.presentation.launcher.LauncherScreen
import org.opencarproject.launcher.presentation.launcher.LauncherViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// ---- Fakes (replace the real sources without touching the system) ----

private class FakeLayoutRepository(initial: LayoutDefinition) : LayoutRepository {
    private val state = MutableStateFlow(initial)
    override fun observeLayout(): Flow<LayoutDefinition> = state
    override suspend fun saveLayout(layout: LayoutDefinition) {
        state.value = layout
    }
}

private class FakeCatalogRepository(private val items: List<CatalogItem>) : CatalogRepository {
    override fun observeCatalog(): Flow<List<CatalogItem>> = flowOf(items)
}

private class FakeWidgetHost : WidgetHostGateway {
    private var nextId = 100
    override fun allocateAppWidgetId(): Int = nextId++
    override fun bindWidgetIfAllowed(appWidgetId: Int, provider: String): Boolean = true
    override fun deleteWidget(appWidgetId: Int) = Unit
    override fun launchApp(component: String) = Unit
    override fun launchAssistant() = Unit
}

/** In-memory settings. */
private class FakeSettingsRepository : SettingsRepository {
    private val state = MutableStateFlow(LauncherSettings())
    override fun observeSettings(): Flow<LauncherSettings> = state
    override suspend fun updateSettings(transform: (LauncherSettings) -> LauncherSettings) {
        state.value = transform(state.value)
    }
}

/**
 * UI test (deliverable 3): select an item in the panel and set its position by
 * dragging it with gestures. Validates the full panel → add → drag flow.
 */
@RunWith(AndroidJUnit4::class)
class DragGestureTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun selectFromPanel_thenDrag_repositionsItem() {
        val app = CatalogItem(
            kind = CatalogItemKind.APP,
            key = "pkg/Main",
            title = "Mapas",
            packageName = "pkg",
        )
        val viewModel = buildViewModel(catalog = listOf(app), items = emptyList())

        rule.setContent {
            LauncherTheme { LauncherScreen(viewModel = viewModel, widgetHost = null) }
        }

        // 1) Long press on the desktop: enters edit mode and the top bar appears.
        rule.onNodeWithTag("widget-grid").performTouchInput { longClick() }
        rule.waitUntil(timeoutMillis = 5_000) {
            viewModel.uiState.value.layoutMode == LayoutMode.EDIT
        }

        // 2) Open the panel and select an item.
        rule.onNodeWithContentDescription("Agregar").performClick()
        rule.onNodeWithText("Mapas").performClick()
        rule.waitUntil(timeoutMillis = 5_000) { viewModel.uiState.value.items.size == 1 }

        val itemId = viewModel.uiState.value.items.first().id
        val before = viewModel.uiState.value.items.first().cell

        // 3) Drag the item to move it.
        rule.onNodeWithTag("placed-$itemId").performTouchInput {
            swipe(start = center, end = center + Offset(300f, 200f), durationMillis = 400)
        }
        rule.waitUntil(timeoutMillis = 5_000) {
            viewModel.uiState.value.items.firstOrNull()?.cell != before
        }

        val after = viewModel.uiState.value.items.first().cell
        assertNotEquals("El elemento debió cambiar de celda tras el arrastre", before, after)
        assertTrue(viewModel.uiState.value.items.size == 1)
    }

    private fun buildViewModel(catalog: List<CatalogItem>, items: List<PlacedItem>): LauncherViewModel {
        val layoutRepo = FakeLayoutRepository(LayoutDefinition(grid = GridSpec(6, 4), items = items))
        val catalogRepo = FakeCatalogRepository(catalog)
        val host = FakeWidgetHost()
        val settingsRepo = FakeSettingsRepository()
        return LauncherViewModel(
            observeCatalog = ObserveCatalog(catalogRepo),
            loadLayout = LoadLayout(layoutRepo),
            addItem = AddItem(host, layoutRepo),
            addBoundWidget = AddBoundWidget(layoutRepo),
            deleteWidgetId = DeleteWidgetId(host),
            removeItem = RemoveItem(host, layoutRepo),
            moveItem = MoveItem(layoutRepo),
            resizeItem = ResizeItem(layoutRepo),
            launchApp = LaunchApp(host),
            launchAssistant = LaunchAssistant(host),
            observeSettings = ObserveSettings(settingsRepo),
            updateSettings = UpdateSettings(settingsRepo),
            changeGridSize = ChangeGridSize(layoutRepo),
        )
    }
}
