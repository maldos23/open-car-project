package org.opencarproject.launcher.domain.repository

import org.opencarproject.launcher.core.model.LayoutDefinition
import kotlinx.coroutines.flow.Flow

/** Persistence of the home-screen layout. */
interface LayoutRepository {
    fun observeLayout(): Flow<LayoutDefinition>
    suspend fun saveLayout(layout: LayoutDefinition)
}
