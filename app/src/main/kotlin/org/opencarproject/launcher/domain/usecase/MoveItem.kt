package org.opencarproject.launcher.domain.usecase

import org.opencarproject.launcher.core.model.Cell
import org.opencarproject.launcher.core.model.LayoutDefinition
import org.opencarproject.launcher.core.pure.GridMath
import org.opencarproject.launcher.domain.repository.LayoutRepository

/**
 * Moves an item to a target cell (clamped within the grid bounds).
 * Items it overlaps are pushed to the nearest free cell; if they don't fit, nothing moves.
 */
class MoveItem(private val layoutRepository: LayoutRepository) {
    suspend operator fun invoke(current: LayoutDefinition, itemId: String, targetCell: Cell): LayoutDefinition {
        val item = current.items.find { it.id == itemId } ?: return current
        val clamped = GridMath.clampCell(targetCell, item.span, current.grid)
        val items = GridMath.placeWithPush(current.items, item.copy(cell = clamped), current.grid) ?: return current
        val updated = current.copy(items = items)
        layoutRepository.saveLayout(updated)
        return updated
    }
}
