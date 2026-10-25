package org.opencarproject.launcher.domain.usecase

import org.opencarproject.launcher.core.model.CatalogItem
import org.opencarproject.launcher.domain.repository.CatalogRepository
import kotlinx.coroutines.flow.Flow

/** Observes the catalog of addable items (single responsibility: reading the catalog). */
class ObserveCatalog(private val repository: CatalogRepository) {
    operator fun invoke(): Flow<List<CatalogItem>> = repository.observeCatalog()
}
