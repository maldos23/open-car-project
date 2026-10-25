package org.opencarproject.launcher.domain.usecase

import org.opencarproject.launcher.domain.repository.WidgetHostGateway

/** Releases an allocated `appWidgetId` (e.g. if the user cancels the picker). */
class DeleteWidgetId(private val host: WidgetHostGateway) {
    operator fun invoke(appWidgetId: Int) = host.deleteWidget(appWidgetId)
}
