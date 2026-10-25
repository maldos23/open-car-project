package org.opencarproject.launcher.core.pure

import org.opencarproject.launcher.core.model.Cell
import org.opencarproject.launcher.core.model.GridSpec
import org.opencarproject.launcher.core.model.PlacedItem

/**
 * Pure grid math.
 *
 * Depends on neither Android nor the UI: takes pixels/cells and returns cells. That makes it
 * 100% testable on the JVM and the single place that defines how an item "snaps" into the
 * grid (DRY and a single responsibility: grid geometry).
 */
object GridMath {

    /** Cell under a position (px), clamped to the grid edges. */
    fun cellAt(x: Float, y: Float, cellWidth: Float, cellHeight: Float, grid: GridSpec): Cell {
        val column = if (cellWidth <= 0f) 0 else (x / cellWidth).toInt().coerceIn(0, grid.columns - 1)
        val row = if (cellHeight <= 0f) 0 else (y / cellHeight).toInt().coerceIn(0, grid.rows - 1)
        return Cell(row, column)
    }

    /** Adjusts a cell so an item with `span` fits entirely inside the grid. */
    fun clampCell(cell: Cell, span: Cell, grid: GridSpec): Cell = Cell(
        row = cell.row.coerceIn(0, (grid.rows - span.row).coerceAtLeast(0)),
        column = cell.column.coerceIn(0, (grid.columns - span.column).coerceAtLeast(0)),
    )

    /** Normalizes a span to the range [1..grid], respecting the origin cell. */
    fun clampSpan(cell: Cell, span: Cell, grid: GridSpec): Cell = Cell(
        row = span.row.coerceIn(1, (grid.rows - cell.row).coerceAtLeast(1)),
        column = span.column.coerceIn(1, (grid.columns - cell.column).coerceAtLeast(1)),
    )

    /** `true` if the rectangle defined by `bCell`/`bSpan` collides with `a`. */
    fun overlaps(a: PlacedItem, bCell: Cell, bSpan: Cell): Boolean {
        val aColEnd = a.cell.column + a.span.column
        val aRowEnd = a.cell.row + a.span.row
        val bColEnd = bCell.column + bSpan.column
        val bRowEnd = bCell.row + bSpan.row
        return a.cell.column < bColEnd && bCell.column < aColEnd &&
            a.cell.row < bRowEnd && bCell.row < aRowEnd
    }

    /** `true` if the rectangle lies entirely inside the grid. */
    fun isWithin(cell: Cell, span: Cell, grid: GridSpec): Boolean =
        cell.row >= 0 && cell.column >= 0 &&
            cell.row + span.row <= grid.rows &&
            cell.column + span.column <= grid.columns

    /**
     * Target cell after dragging `deltaX`/`deltaY` pixels from `start`: the cell alignment
     * closest to where the card is, clamped inside the grid.
     *
     * The offset is rounded instead of truncating the center: with even spans the center lands
     * right on a grid line and a couple of pixels would change the chosen row.
     */
    fun targetCell(
        start: Cell,
        span: Cell,
        deltaX: Float,
        deltaY: Float,
        cellWidth: Float,
        cellHeight: Float,
        grid: GridSpec,
    ): Cell {
        val column = start.column + if (cellWidth <= 0f) 0 else Math.round(deltaX / cellWidth)
        val row = start.row + if (cellHeight <= 0f) 0 else Math.round(deltaY / cellHeight)
        return clampCell(Cell(row, column), span, grid)
    }

    /** Target span after dragging `deltaX`/`deltaY` pixels from the initial span. */
    fun targetSpan(
        start: Cell,
        startSpan: Cell,
        deltaX: Float,
        deltaY: Float,
        cellWidth: Float,
        cellHeight: Float,
        grid: GridSpec,
    ): Cell {
        val extraColumns = if (cellWidth <= 0f) 0 else Math.round(deltaX / cellWidth).toInt()
        val extraRows = if (cellHeight <= 0f) 0 else Math.round(deltaY / cellHeight).toInt()
        return clampSpan(start, Cell(startSpan.row + extraRows, startSpan.column + extraColumns), grid)
    }

    /**
     * First free cell (row-by-row scan) where `span` fits without colliding.
     * `exceptId` lets the item being relocated be ignored.
     */
    fun findFreeCell(
        grid: GridSpec,
        items: List<PlacedItem>,
        span: Cell,
        exceptId: String? = null,
    ): Cell {
        for (row in 0 until grid.rows) {
            for (column in 0 until grid.columns) {
                val candidate = Cell(row, column)
                if (!isWithin(candidate, span, grid)) continue
                val collides = items.any { it.id != exceptId && overlaps(it, candidate, span) }
                if (!collides) return candidate
            }
        }
        // No room: return the origin; the UI layer will report it as unplaced.
        return Cell(0, 0)
    }

    /**
     * Places `moved` (already with its new cell/span) and pushes the items it overlaps to the
     * free cell closest to their original position. Items that don't collide are left alone.
     * Returns `null` if any displaced item doesn't fit: the gesture is rejected and nothing moves.
     */
    fun placeWithPush(items: List<PlacedItem>, moved: PlacedItem, grid: GridSpec): List<PlacedItem>? {
        val others = items.filter { it.id != moved.id }
        val (displaced, untouched) = others.partition { overlaps(it, moved.cell, moved.span) }
        val placed = (untouched + moved).toMutableList()
        // Largest first: they are the hardest to relocate.
        for (item in displaced.sortedByDescending { it.span.row * it.span.column }) {
            val cell = nearestFreeCell(grid, placed, item.span, item.cell) ?: return null
            placed += item.copy(cell = cell)
        }
        // Keep the original order (so draw order doesn't jump).
        val byId = placed.associateBy { it.id }
        return items.map { byId.getValue(it.id) }
    }

    /** Free cell fitting `span` closest (Manhattan distance) to `near`, or `null`. */
    private fun nearestFreeCell(grid: GridSpec, items: List<PlacedItem>, span: Cell, near: Cell): Cell? {
        var best: Cell? = null
        var bestDistance = Int.MAX_VALUE
        for (row in 0 until grid.rows) {
            for (column in 0 until grid.columns) {
                val candidate = Cell(row, column)
                if (!isWithin(candidate, span, grid)) continue
                if (items.any { overlaps(it, candidate, span) }) continue
                val distance = Math.abs(row - near.row) + Math.abs(column - near.column)
                if (distance < bestDistance) {
                    best = candidate
                    bestDistance = distance
                }
            }
        }
        return best
    }

    /**
     * Rearranges the items into a grid of a different size. Each keeps its place if it fits;
     * otherwise it goes to the nearest free slot, shrinking if needed. Items are processed
     * top-left to bottom-right so the first ones keep their position.
     * Returns `null` if any item doesn't fit even at 1×1 (more items than cells).
     */
    fun refit(items: List<PlacedItem>, grid: GridSpec): List<PlacedItem>? {
        val placed = mutableListOf<PlacedItem>()
        for (item in items.sortedWith(compareBy({ it.cell.row }, { it.cell.column }))) {
            val maxSpan = Cell(item.span.row.coerceIn(1, grid.rows), item.span.column.coerceIn(1, grid.columns))
            val home = clampCell(item.cell, maxSpan, grid)
            val fitted = if (placed.none { overlaps(it, home, maxSpan) }) {
                item.copy(cell = home, span = maxSpan)
            } else {
                // From largest to smallest area: shrink only as much as needed.
                shrinkingSpans(maxSpan).firstNotNullOfOrNull { span ->
                    nearestFreeCell(grid, placed, span, item.cell)?.let { item.copy(cell = it, span = span) }
                } ?: return null
            }
            placed += fitted
        }
        val byId = placed.associateBy { it.id }
        return items.map { byId.getValue(it.id) }
    }

    private fun shrinkingSpans(max: Cell): List<Cell> =
        (1..max.row).flatMap { r -> (1..max.column).map { c -> Cell(r, c) } }
            .sortedByDescending { it.row * it.column }

    /**
     * Where and at what size a new item fits: the requested size first; if there's no room,
     * the largest that fits (from largest to smallest area). `null` if not a single cell is free.
     */
    fun placeNew(grid: GridSpec, items: List<PlacedItem>, span: Cell): Pair<Cell, Cell>? {
        for (candidate in shrinkingSpans(span)) {
            // First slot scanning row by row (like filling a grid).
            for (row in 0 until grid.rows) for (column in 0 until grid.columns) {
                val cell = Cell(row, column)
                if (isWithin(cell, candidate, grid) && items.none { overlaps(it, cell, candidate) }) return cell to candidate
            }
        }
        return null
    }
}
