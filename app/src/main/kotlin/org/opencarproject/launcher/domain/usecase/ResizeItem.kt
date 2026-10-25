package org.opencarproject.launcher.domain.usecase

import org.opencarproject.launcher.core.model.Cell
import org.opencarproject.launcher.core.model.LayoutDefinition
import org.opencarproject.launcher.core.pure.GridMath
import org.opencarproject.launcher.domain.repository.LayoutRepository

/**
 * Resizes an item by adjusting its span (height x width in cells). Like [MoveItem],
 * it pushes the neighbors it overlaps; if they don't fit, the size doesn't change.
 */
class ResizeItem(private val layoutRepository: LayoutRepository) {
    suspend operator fun invoke(current: LayoutDefinition, itemId: String, newSpan: Cell): LayoutDefinition {
        val item = current.items.find { it.id == itemId } ?: return current
        val span = GridMath.clampSpan(item.cell, newSpan, current.grid)
        val items = GridMath.placeWithPush(current.items, item.copy(span = span), current.grid) ?: return current
        val updated = current.copy(items = items)
        layoutRepository.saveLayout(updated)
        return updated
    }
}
