package org.opencarproject.launcher.core.pure

import org.opencarproject.launcher.core.model.CatalogItemKind
import org.opencarproject.launcher.core.model.Cell
import org.opencarproject.launcher.core.model.GridSpec
import org.opencarproject.launcher.core.model.PlacedItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the pure grid math (no Android). */
class GridMathTest {

    private val grid = GridSpec(columns = 6, rows = 4)

    @Test
    fun cellAt_mapsPixelsToCell() {
        assertEquals(Cell(0, 0), GridMath.cellAt(10f, 10f, 100f, 100f, grid))
        assertEquals(Cell(1, 2), GridMath.cellAt(250f, 150f, 100f, 100f, grid))
    }

    @Test
    fun cellAt_clampsToBounds() {
        assertEquals(Cell(3, 5), GridMath.cellAt(9999f, 9999f, 100f, 100f, grid))
    }

    @Test
    fun clampCell_keepsWithinGrid() {
        assertEquals(Cell(3, 4), GridMath.clampCell(Cell(10, 10), Cell(1, 2), grid))
    }

    @Test
    fun clampSpan_neverBelowOne() {
        assertEquals(Cell(1, 1), GridMath.clampSpan(Cell(0, 0), Cell(0, 0), grid))
    }

    @Test
    fun overlaps_detectsCollision() {
        val a = item("a", Cell(0, 0), Cell(2, 2))
        assertTrue(GridMath.overlaps(a, Cell(1, 1), Cell(1, 1)))
        assertFalse(GridMath.overlaps(a, Cell(2, 0), Cell(1, 1)))
    }

    @Test
    fun findFreeCell_skipsOccupied() {
        val occupied = item("a", Cell(0, 0), Cell(1, 1))
        assertEquals(Cell(0, 1), GridMath.findFreeCell(grid, listOf(occupied), Cell(1, 1)))
    }

    @Test
    fun targetCell_followsDragDelta() {
        // Delta (250,150) in 100-px cells -> +3 columns (2.5 rounds up), +2 rows (1.5).
        val target = GridMath.targetCell(Cell(0, 0), Cell(1, 1), 250f, 150f, 100f, 100f, grid)
        assertEquals(Cell(2, 3), target)
    }

    @Test
    fun targetCell_evenSpanSnapsToVisibleRow() {
        // A 2-row card in row 2 dragged almost 2 rows up: it appears in
        // row 0 and must land there (previously the center sat on the line and picked row 1).
        val target = GridMath.targetCell(Cell(2, 3), Cell(2, 3), 0f, -568f, 300f, 285f, grid)
        assertEquals(Cell(0, 3), target)
    }

    @Test
    fun targetSpan_growsWithDragDelta() {
        val span = GridMath.targetSpan(Cell(0, 0), Cell(1, 1), 200f, 100f, 100f, 100f, grid)
        assertEquals(Cell(2, 3), span)
    }

    @Test
    fun placeWithPush_movesCollidingNeighbourToNearestFreeCell() {
        val a = item("a", Cell(0, 0), Cell(2, 2))
        val b = item("b", Cell(0, 2), Cell(2, 2))
        val result = GridMath.placeWithPush(listOf(a, b), a.copy(cell = Cell(0, 2)), grid)!!

        assertEquals(Cell(0, 2), result.first { it.id == "a" }.cell)
        val pushed = result.first { it.id == "b" }
        assertFalse(GridMath.overlaps(pushed, Cell(0, 2), Cell(2, 2)))
        assertEquals(Cell(0, 0), pushed.cell) // the gap left by "a": swap
    }

    @Test
    fun placeWithPush_leavesNonCollidingItemsAlone() {
        val a = item("a", Cell(0, 0), Cell(1, 1))
        val c = item("c", Cell(3, 5), Cell(1, 1))
        val result = GridMath.placeWithPush(listOf(a, c), a.copy(cell = Cell(1, 1)), grid)!!
        assertEquals(Cell(3, 5), result.first { it.id == "c" }.cell)
    }

    @Test
    fun placeWithPush_rejectsWhenNoRoom() {
        val a = item("a", Cell(0, 0), Cell(4, 3))
        val b = item("b", Cell(0, 3), Cell(4, 3))
        // "a" grows to 4 columns: "b" (3 columns) no longer fits anywhere.
        assertEquals(null, GridMath.placeWithPush(listOf(a, b), a.copy(span = Cell(4, 4)), grid))
    }

    @Test
    fun refit_keepsPositionsWhenTheyFit() {
        val items = listOf(item("a", Cell(0, 0), Cell(2, 2)), item("b", Cell(0, 2), Cell(1, 1)))
        val result = GridMath.refit(items, GridSpec(columns = 8, rows = 5))!!
        assertEquals(items, result)
    }

    @Test
    fun refit_shrinksAndMovesToFitSmallerGrid() {
        // Default menu (6x4) to 4x3: nothing overflows or overlaps.
        val items = listOf(
            item("map", Cell(0, 0), Cell(4, 3)),
            item("media", Cell(0, 3), Cell(2, 3)),
            item("notif", Cell(2, 3), Cell(2, 3)),
        )
        val small = GridSpec(columns = 4, rows = 3)
        val result = GridMath.refit(items, small)!!
        result.forEach { assertTrue(GridMath.isWithin(it.cell, it.span, small)) }
        result.forEach { a -> assertTrue(result.none { b -> b.id != a.id && GridMath.overlaps(b, a.cell, a.span) }) }
        assertEquals(listOf("map", "media", "notif"), result.map { it.id }) // original order
    }

    @Test
    fun refit_rejectsWhenMoreItemsThanCells() {
        val items = (0 until 5).map { item("i$it", Cell(0, it), Cell(1, 1)) }
        assertEquals(null, GridMath.refit(items, GridSpec(columns = 2, rows = 2)))
    }

    @Test
    fun placeNew_shrinksToFitAndRejectsWhenFull() {
        // Only row 3 is free: something 2 rows tall gets placed with 1 row.
        val busy = listOf(item("a", Cell(0, 0), Cell(3, 6)))
        assertEquals(Cell(3, 0) to Cell(1, 2), GridMath.placeNew(grid, busy, Cell(2, 2)))
        val full = listOf(item("a", Cell(0, 0), Cell(4, 6)))
        assertEquals(null, GridMath.placeNew(grid, full, Cell(1, 1)))
    }

    private fun item(id: String, cell: Cell, span: Cell) = PlacedItem(
        id = id,
        kind = CatalogItemKind.APP,
        componentKey = "pkg/$id",
        cell = cell,
        span = span,
        title = "t",
        packageName = "pkg",
    )
}
