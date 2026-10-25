package org.opencarproject.launcher.data.repository

import org.opencarproject.launcher.core.model.LayoutDefinition
import org.opencarproject.launcher.data.source.LayoutDataSource
import org.opencarproject.launcher.domain.repository.LayoutRepository
import kotlinx.coroutines.flow.Flow

/** Layout repository implementation backed by [LayoutDataSource]. */
class LayoutRepositoryImpl(private val dataSource: LayoutDataSource) : LayoutRepository {
    override fun observeLayout(): Flow<LayoutDefinition> = dataSource.layoutFlow
    override suspend fun saveLayout(layout: LayoutDefinition) = dataSource.save(layout)
}
