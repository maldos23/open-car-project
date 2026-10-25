package org.opencarproject.launcher.domain.repository

import org.opencarproject.launcher.core.model.LauncherSettings
import kotlinx.coroutines.flow.Flow

/** Persistence of the launcher settings (theme, full screen). */
interface SettingsRepository {
    fun observeSettings(): Flow<LauncherSettings>
    suspend fun updateSettings(transform: (LauncherSettings) -> LauncherSettings)
}
