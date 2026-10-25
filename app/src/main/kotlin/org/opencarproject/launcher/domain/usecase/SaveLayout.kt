package org.opencarproject.launcher.domain.usecase

import org.opencarproject.launcher.core.model.LayoutDefinition
import org.opencarproject.launcher.domain.repository.LayoutRepository

/** Saves the full layout to persistence. */
class SaveLayout(private val repository: LayoutRepository) {
    suspend operator fun invoke(layout: LayoutDefinition) = repository.saveLayout(layout)
}
