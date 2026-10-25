package org.opencarproject.launcher.data.repository

import org.opencarproject.launcher.core.model.LauncherSettings
import org.opencarproject.launcher.data.source.SettingsDataSource
import org.opencarproject.launcher.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow

/** Settings repository implementation on top of [SettingsDataSource]. */
class SettingsRepositoryImpl(private val dataSource: SettingsDataSource) : SettingsRepository {
    override fun observeSettings(): Flow<LauncherSettings> = dataSource.settingsFlow
    override suspend fun updateSettings(transform: (LauncherSettings) -> LauncherSettings) =
        dataSource.update(transform)
}
