package org.opencarproject.launcher.domain.repository

import org.opencarproject.launcher.core.model.CatalogItem
import kotlinx.coroutines.flow.Flow

/** Catalog of addable items (system widgets and launchable apps). */
interface CatalogRepository {
    fun observeCatalog(): Flow<List<CatalogItem>>
}
