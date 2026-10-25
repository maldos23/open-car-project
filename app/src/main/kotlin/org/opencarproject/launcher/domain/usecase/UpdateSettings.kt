package org.opencarproject.launcher.domain.usecase

import org.opencarproject.launcher.core.model.LauncherSettings
import org.opencarproject.launcher.domain.repository.SettingsRepository

/** Changes one or more settings values and persists them. */
class UpdateSettings(private val repository: SettingsRepository) {
    suspend operator fun invoke(transform: (LauncherSettings) -> LauncherSettings) =
        repository.updateSettings(transform)
}
