package org.opencarproject.launcher.domain.usecase

import org.opencarproject.launcher.core.model.GridSpec
import org.opencarproject.launcher.core.model.LayoutDefinition
import org.opencarproject.launcher.core.pure.GridMath
import org.opencarproject.launcher.domain.repository.LayoutRepository

/**
 * Changes the grid size, rearranging items so they fit.
 * Returns `null` (and saves nothing) if they don't all fit even when shrunk to 1×1.
 */
class ChangeGridSize(private val layoutRepository: LayoutRepository) {
    suspend operator fun invoke(current: LayoutDefinition, grid: GridSpec): LayoutDefinition? {
        val items = GridMath.refit(current.items, grid) ?: return null
        val updated = current.copy(grid = grid, items = items)
        layoutRepository.saveLayout(updated)
        return updated
    }
}
