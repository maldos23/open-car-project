package org.opencarproject.launcher.domain.usecase

import org.opencarproject.launcher.core.model.LauncherSettings
import org.opencarproject.launcher.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow

/** Observes the launcher settings. */
class ObserveSettings(private val repository: SettingsRepository) {
    operator fun invoke(): Flow<LauncherSettings> = repository.observeSettings()
}
