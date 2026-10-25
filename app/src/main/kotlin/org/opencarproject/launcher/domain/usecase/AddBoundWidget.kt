package org.opencarproject.launcher.domain.usecase

import org.opencarproject.launcher.core.model.CatalogItemKind
import org.opencarproject.launcher.core.model.Cell
import org.opencarproject.launcher.core.model.LayoutDefinition
import org.opencarproject.launcher.core.model.PlacedItem
import org.opencarproject.launcher.core.pure.GridMath
import org.opencarproject.launcher.domain.repository.LayoutRepository

/**
 * Adds to the home screen a widget whose `appWidgetId` has already been allocated and bound
 * (e.g. after the system `ACTION_APPWIDGET_PICK` flow). Returns `null` if the grid is full.
 */
class AddBoundWidget(private val layoutRepository: LayoutRepository) {

    suspend operator fun invoke(
        current: LayoutDefinition,
        appWidgetId: Int,
        provider: String,
        title: String,
        packageName: String,
        suggestedSpan: Cell? = null,
    ): LayoutDefinition? {
        // Same sizing as AddItem: the widget's suggested size (half-cell columns), shrunk to fit.
        val preferred = (suggestedSpan ?: Cell(1, 1)).let { it.copy(column = it.column * current.columnScale) }
        val (freeCell, span) = GridMath.placeNew(current.grid, current.items, GridMath.clampSpan(Cell(0, 0), preferred, current.grid))
            ?: return null
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
