package org.opencarproject.launcher.domain.usecase

import org.opencarproject.launcher.core.model.CatalogItem
import org.opencarproject.launcher.core.model.CatalogItemKind
import org.opencarproject.launcher.core.model.Cell
import org.opencarproject.launcher.core.model.LayoutDefinition
import org.opencarproject.launcher.core.model.PlacedItem
import org.opencarproject.launcher.core.pure.GridMath
import org.opencarproject.launcher.domain.repository.LayoutRepository
import org.opencarproject.launcher.domain.repository.WidgetHostGateway

/** Result of adding a catalog item. */
sealed interface AddItemResult {
    /** Success: returns the already-persisted layout with the new item. */
    data class Success(val layout: LayoutDefinition) : AddItemResult

    /**
     * The widget needs the system binding permission: the host must launch
     * `ACTION_APPWIDGET_BIND` with this already-allocated `appWidgetId` and the [provider].
     */
    data class NeedsWidgetPick(val appWidgetId: Int, val provider: String) : AddItemResult

    /** The grid is full: nothing is added. */
    data object NoSpace : AddItemResult
}

/**
 * Adds a catalog item to the home screen.
 *
 * For apps: creates the launchable card directly. For widgets: allocates and binds a native
 * `appWidgetId`. If the system rejects the binding (third-party launcher without permission),
 * returns [AddItemResult.NeedsWidgetPick] to ask the user for permission.
 */
class AddItem(
    private val host: WidgetHostGateway,
    private val layoutRepository: LayoutRepository,
) {
    suspend operator fun invoke(candidate: CatalogItem, current: LayoutDefinition): AddItemResult {
        // Spans in visible cells (rows × columns); the layout uses half cells horizontally.
        // An app takes half a cell: 2 fit per cell.
        val scale = current.columnScale
        val preferredSpan = when (candidate.kind) {
            CatalogItemKind.APP -> Cell(1, 1)
            CatalogItemKind.MAP, CatalogItemKind.MEDIA -> Cell(2, 3 * scale)
            CatalogItemKind.NOTIFICATIONS -> Cell(2, 2 * scale)
            CatalogItemKind.WIDGET -> (candidate.suggestedSpan ?: Cell(1, 1)).let { it.copy(column = it.column * scale) }
        }.let { GridMath.clampSpan(Cell(0, 0), it, current.grid) }
        // Wherever the grid allows: if it doesn't fit at full size, it is placed smaller.
        val (freeCell, span) = GridMath.placeNew(current.grid, current.items, preferredSpan) ?: return AddItemResult.NoSpace

        return when (candidate.kind) {
            CatalogItemKind.APP, CatalogItemKind.MAP, CatalogItemKind.MEDIA, CatalogItemKind.NOTIFICATIONS -> {
                val item = PlacedItem(
                    id = PlacedItem.identity(candidate.kind, candidate.key, -1),
                    kind = candidate.kind,
                    componentKey = candidate.key,
                    cell = freeCell,
                    span = span,
                    title = candidate.title,
                    packageName = candidate.packageName,
                )
                val newLayout = current.copy(items = current.items + item)
                layoutRepository.saveLayout(newLayout)
                AddItemResult.Success(newLayout)
            }

            CatalogItemKind.WIDGET -> {
                val appWidgetId = host.allocateAppWidgetId()
                if (host.bindWidgetIfAllowed(appWidgetId, candidate.key)) {
                    val item = PlacedItem(
                        id = PlacedItem.identity(candidate.kind, candidate.key, appWidgetId),
                        kind = candidate.kind,
                        componentKey = candidate.key,
                        widgetId = appWidgetId,
                        cell = freeCell,
                        span = span,
                        title = candidate.title,
                        packageName = candidate.packageName,
                    )
                    val newLayout = current.copy(items = current.items + item)
                    layoutRepository.saveLayout(newLayout)
                    AddItemResult.Success(newLayout)
                } else {
                    // The id is not released: it will be reused in the ACTION_APPWIDGET_BIND flow.
                    AddItemResult.NeedsWidgetPick(appWidgetId, candidate.key)
                }
            }
        }
    }
}
