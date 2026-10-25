package org.opencarproject.launcher.domain.usecase

import org.opencarproject.launcher.core.model.CatalogItemKind
import org.opencarproject.launcher.core.model.LayoutDefinition
import org.opencarproject.launcher.domain.repository.LayoutRepository
import org.opencarproject.launcher.domain.repository.WidgetHostGateway

/** Removes an item from the home screen and releases its native widget if applicable. */
class RemoveItem(
    private val host: WidgetHostGateway,
    private val layoutRepository: LayoutRepository,
) {
    suspend operator fun invoke(current: LayoutDefinition, itemId: String): LayoutDefinition {
        val item = current.items.find { it.id == itemId } ?: return current
        if (item.kind == CatalogItemKind.WIDGET && item.widgetId >= 0) {
            host.deleteWidget(item.widgetId)
        }
        val updated = current.copy(items = current.items.filterNot { it.id == itemId })
        layoutRepository.saveLayout(updated)
        return updated
    }
}
