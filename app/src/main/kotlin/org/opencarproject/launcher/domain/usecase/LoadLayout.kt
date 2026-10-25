package org.opencarproject.launcher.domain.usecase

import org.opencarproject.launcher.core.model.LayoutDefinition
import org.opencarproject.launcher.domain.repository.LayoutRepository
import kotlinx.coroutines.flow.Flow

/** Loads the persisted layout as a reactive flow. */
class LoadLayout(private val repository: LayoutRepository) {
    operator fun invoke(): Flow<LayoutDefinition> = repository.observeLayout()
}
