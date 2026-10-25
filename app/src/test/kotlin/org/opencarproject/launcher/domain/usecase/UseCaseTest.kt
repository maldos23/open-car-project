package org.opencarproject.launcher.domain.usecase

import org.opencarproject.launcher.core.model.CatalogItem
import org.opencarproject.launcher.core.model.CatalogItemKind
import org.opencarproject.launcher.core.model.Cell
import org.opencarproject.launcher.core.model.LayoutDefinition
import org.opencarproject.launcher.core.model.PlacedItem
import org.opencarproject.launcher.core.pure.GridMath
import org.opencarproject.launcher.domain.repository.LayoutRepository
import org.opencarproject.launcher.domain.repository.WidgetHostGateway
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** In-memory layout repository for use case tests. */
private class FakeLayoutRepository(initial: LayoutDefinition = LayoutDefinition()) : LayoutRepository {
    private val state = MutableStateFlow(initial)
    override fun observeLayout(): Flow<LayoutDefinition> = state
    override suspend fun saveLayout(layout: LayoutDefinition) {
        state.value = layout
    }
}

/** Fake gateway: always binds, without touching the system. */
private class FakeWidgetHost : WidgetHostGateway {
    var nextId = 100
    override fun allocateAppWidgetId(): Int = nextId++
    override fun bindWidgetIfAllowed(appWidgetId: Int, provider: String): Boolean = true
    override fun deleteWidget(appWidgetId: Int) = Unit
    override fun launchApp(component: String) = Unit
    override fun launchAssistant() = Unit
}

/** Tests for the business use cases. */
class UseCaseTest {

    @Test
    fun addApp_twice_keepsSingleCopy() = runTest {
        val addItem = AddItem(FakeWidgetHost(), FakeLayoutRepository())
        val app = CatalogItem(CatalogItemKind.APP, "pkg/A", "A", "pkg")
        val first = (addItem(app, LayoutDefinition().toFineColumns()) as AddItemResult.Success).layout
        val second = (addItem(app, first) as AddItemResult.Success).layout
        assertEquals(1, second.items.size)
    }

    @Test
    fun addWidget_whenBindThrows_releasesIdAndReportsUnavailable() = runTest {
        val deleted = mutableListOf<Int>()
        val host = object : WidgetHostGateway by FakeWidgetHost() {
            override fun bindWidgetIfAllowed(appWidgetId: Int, provider: String): Boolean =
                throw IllegalArgumentException("provider gone")
            override fun deleteWidget(appWidgetId: Int) { deleted += appWidgetId }
        }
        val widget = CatalogItem(CatalogItemKind.WIDGET, "gone/W", "W", "gone")
        assertEquals(AddItemResult.Unavailable, AddItem(host, FakeLayoutRepository())(widget, LayoutDefinition()))
        assertEquals(listOf(100), deleted)
    }

    @Test
    fun addWidget_allocatesBindsAndPersists() = runTest {
        val repo = FakeLayoutRepository()
        val host = FakeWidgetHost()
        val addItem = AddItem(host, repo)

        val candidate = CatalogItem(
            kind = CatalogItemKind.WIDGET,
            key = "pkg/Clock",
            title = "Reloj",
            packageName = "pkg",
        )

        val result = addItem(candidate, LayoutDefinition())
        assertTrue(result is AddItemResult.Success)
        val layout = (result as AddItemResult.Success).layout
        assertEquals(1, layout.items.size)
        assertEquals(CatalogItemKind.WIDGET, layout.items.first().kind)
        assertTrue(layout.items.first().widgetId >= 0)
    }

    @Test
    fun addMedia_usesBuiltinSpanInFreeCell() = runTest {
        val addItem = AddItem(FakeWidgetHost(), FakeLayoutRepository())
        val current = LayoutDefinition(items = listOf(item("a", Cell(0, 0)))).toFineColumns()
        val candidate = CatalogItem(CatalogItemKind.MEDIA, "builtin/media", "Reproductor", "pkg")

        val layout = (addItem(candidate, current) as AddItemResult.Success).layout
        val media = layout.items.first { it.kind == CatalogItemKind.MEDIA }
        assertEquals(Cell(2, 6), media.span) // 3 cells = 6 half cells
        assertEquals(Cell(0, 2), media.cell) // "a" takes a whole cell = columns 0-1
    }

    @Test
    fun addWidget_usesSuggestedSpanClampedToGrid() = runTest {
        val addItem = AddItem(FakeWidgetHost(), FakeLayoutRepository())
        val calendar = CatalogItem(CatalogItemKind.WIDGET, "cal/Schedule", "Agenda", "cal", suggestedSpan = Cell(3, 9))
        val layout = (addItem(calendar, LayoutDefinition().toFineColumns()) as AddItemResult.Success).layout
        assertEquals(Cell(3, 12), layout.items.single().span) // 9 cells (18 halves) don't fit in 12

    }

    @Test
    fun addApp_takesHalfCell_soTwoFitInOne() = runTest {
        val repo = FakeLayoutRepository()
        val addItem = AddItem(FakeWidgetHost(), repo)
        val app = { key: String -> CatalogItem(CatalogItemKind.APP, key, key, "pkg") }
        val first = (addItem(app("pkg/A"), LayoutDefinition().toFineColumns()) as AddItemResult.Success).layout
        val second = (addItem(app("pkg/B"), first) as AddItemResult.Success).layout
        assertEquals(listOf(Cell(0, 0), Cell(0, 1)), second.items.map { it.cell }) // same visible cell
        assertEquals(listOf(Cell(1, 1), Cell(1, 1)), second.items.map { it.span })
    }

    @Test
    fun toFineColumns_migratesLegacyLayoutOnce() {
        val legacy = LayoutDefinition(items = listOf(item("a", Cell(1, 2), Cell(2, 3))))
        val fine = legacy.toFineColumns()
        assertEquals(12, fine.grid.columns)
        assertEquals(Cell(1, 4), fine.items.single().cell)
        assertEquals(Cell(2, 6), fine.items.single().span)
        assertEquals(fine, fine.toFineColumns()) // idempotent
    }

    @Test
    fun defaultLayout_fitsGridWithoutOverlaps() {
        val layout = LayoutDefinition.DEFAULT
        layout.items.forEach { assertTrue(GridMath.isWithin(it.cell, it.span, layout.grid)) }
        layout.items.forEach { a ->
            assertTrue(layout.items.none { b -> b.id != a.id && GridMath.overlaps(b, a.cell, a.span) })
        }
    }

    @Test
    fun moveItem_relocatesAndPersists() = runTest {
        val repo = FakeLayoutRepository()
        val current = LayoutDefinition(items = listOf(item("a", Cell(0, 0))))
        val moveItem = MoveItem(repo)

        val result = moveItem(current, "a", Cell(2, 3))
        assertEquals(Cell(2, 3), result.items.first { it.id == "a" }.cell)
    }

    @Test
    fun resizeItem_changesSpan() = runTest {
        val repo = FakeLayoutRepository()
        val current = LayoutDefinition(items = listOf(item("a", Cell(0, 0), Cell(1, 1))))
        val resizeItem = ResizeItem(repo)

        val result = resizeItem(current, "a", Cell(2, 3))
        assertEquals(Cell(2, 3), result.items.first { it.id == "a" }.span)
    }

    @Test
    fun removeItem_deletesEntry() = runTest {
        val repo = FakeLayoutRepository()
        val host = FakeWidgetHost()
        val current = LayoutDefinition(
            items = listOf(
                PlacedItem(
                    id = "widget-7",
                    kind = CatalogItemKind.WIDGET,
                    componentKey = "pkg/C",
                    widgetId = 7,
                    cell = Cell(0, 0),
                    span = Cell(1, 1),
                    title = "t",
                    packageName = "pkg",
                ),
            ),
        )
        val removeItem = RemoveItem(host, repo)

        val result = removeItem(current, "widget-7")
        assertTrue(result.items.isEmpty())
    }

    private fun item(id: String, cell: Cell, span: Cell = Cell(1, 1)) = PlacedItem(
        id = id,
        kind = CatalogItemKind.APP,
        componentKey = "pkg/$id",
        cell = cell,
        span = span,
        title = "t",
        packageName = "pkg",
    )
}
