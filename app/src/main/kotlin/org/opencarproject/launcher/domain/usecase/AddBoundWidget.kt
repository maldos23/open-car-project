package org.opencarproject.launcher.domain.usecase

import org.opencarproject.launcher.core.model.CatalogItemKind
import org.opencarproject.launcher.core.model.Cell
import org.opencarproject.launcher.core.model.LayoutDefinition
import org.opencarproject.launcher.core.model.PlacedItem
import org.opencarproject.launcher.core.pure.GridMath
import org.opencarproject.launcher.domain.repository.LayoutRepository

/**
 * Adds to the home screen a widget whose `appWidgetId` has already been allocated and bound
 * (e.g. after the system `ACTION_APPWIDGET_PICK` flow).
 */
class AddBoundWidget(private val layoutRepository: LayoutRepository) {

    suspend operator fun invoke(
        current: LayoutDefinition,
        appWidgetId: Int,
        provider: String,
        title: String,
        packageName: String,
    ): LayoutDefinition {
        val span = Cell(1, 1)
        val freeCell = GridMath.findFreeCell(current.grid, current.items, span)
        val item = PlacedItem(
            id = PlacedItem.identity(CatalogItemKind.WIDGET, provider, appWidgetId),
            kind = CatalogItemKind.WIDGET,
            componentKey = provider,
            widgetId = appWidgetId,
            cell = freeCell,
            span = span,
            title = title,
            packageName = packageName,
        )
        val newLayout = current.copy(items = current.items + item)
        layoutRepository.saveLayout(newLayout)
        return newLayout
    }
}
