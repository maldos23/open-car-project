package org.opencarproject.launcher.domain.usecase

import org.opencarproject.launcher.domain.repository.WidgetHostGateway

/** Invokes the voice assistant (Google Assistant). */
class LaunchAssistant(private val host: WidgetHostGateway) {
    operator fun invoke() = host.launchAssistant()
}
