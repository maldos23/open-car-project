package org.opencarproject.launcher.data.repository

import org.opencarproject.launcher.core.model.CatalogItem
import org.opencarproject.launcher.data.source.SystemCatalogSource
import org.opencarproject.launcher.domain.repository.CatalogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Catalog implementation that reads from the system. */
class CatalogRepositoryImpl(private val source: SystemCatalogSource) : CatalogRepository {
    override fun observeCatalog(): Flow<List<CatalogItem>> = flow { emit(source.listItems()) }
}
