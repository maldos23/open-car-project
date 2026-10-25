package org.opencarproject.launcher.domain.usecase

import org.opencarproject.launcher.domain.repository.WidgetHostGateway

/** Launches an app by its "package/activity" component. */
class LaunchApp(private val host: WidgetHostGateway) {
    operator fun invoke(component: String) = host.launchApp(component)
}
