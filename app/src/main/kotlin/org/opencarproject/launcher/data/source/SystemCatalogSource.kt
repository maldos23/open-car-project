package org.opencarproject.launcher.data.source

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ResolveInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import org.opencarproject.launcher.core.model.BuiltinKeys
import org.opencarproject.launcher.core.model.CatalogItem
import org.opencarproject.launcher.core.model.CatalogItemKind
import org.opencarproject.launcher.core.model.Cell
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlin.math.ceil

/**
 * Reads the addable items from the system: installed App Widgets and launchable apps.
 * It is the only class that queries `AppWidgetManager` / `PackageManager` for the catalog.
 */
class SystemCatalogSource(
    private val context: Context,
    // Injectable so tests can run it on a test dispatcher.
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    /**
     * The catalog, listed again whenever an app is installed, removed or changed. Listing queries
     * PackageManager for every app, so it runs off the main thread.
     */
    fun observeItems(): Flow<List<CatalogItem>> = callbackFlow {
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        val callback = object : LauncherApps.Callback() {
            override fun onPackageAdded(packageName: String, user: UserHandle) { trySend(Unit) }
            override fun onPackageRemoved(packageName: String, user: UserHandle) { trySend(Unit) }
            override fun onPackageChanged(packageName: String, user: UserHandle) { trySend(Unit) }
            override fun onPackagesAvailable(names: Array<out String>, user: UserHandle, replacing: Boolean) { trySend(Unit) }
            override fun onPackagesUnavailable(names: Array<out String>, user: UserHandle, replacing: Boolean) { trySend(Unit) }
        }
        trySend(Unit)
        launcherApps?.registerCallback(callback, Handler(Looper.getMainLooper()))
        awaitClose { launcherApps?.unregisterCallback(callback) }
    }.conflate().map { listItems() }.flowOn(ioDispatcher)

    /** Returns built-in modules first, then widgets and apps, deduplicated by component. */
    fun listItems(): List<CatalogItem> =
        (builtinItems() + listWidgets() + listApps()).distinctBy { it.key }

    /** Modules drawn by the launcher itself; climate only on head units with a compatible CAN app. */
    private fun builtinItems() = listOfNotNull(
        builtin(CatalogItemKind.MAP, BuiltinKeys.MAP, "Navegación", "Próximo giro de Google Maps o Waze"),
        builtin(CatalogItemKind.MEDIA, BuiltinKeys.MEDIA, "Reproductor", "Controla la música en curso"),
        builtin(CatalogItemKind.NOTIFICATIONS, BuiltinKeys.NOTIFICATIONS, "Notificaciones", "Notificaciones activas"),
        builtin(CatalogItemKind.CLIMATE, BuiltinKeys.CLIMATE, "Clima", "Temperatura y modos del aire acondicionado")
            .takeIf { ClimateSource.isAvailable(context) },
        builtin(CatalogItemKind.EMBEDDED_MAP, BuiltinKeys.EMBEDDED_MAP, "Mapa integrado", "Google Maps dentro de la tarjeta (requiere adb root)"),
    )

    private fun builtin(kind: CatalogItemKind, key: String, title: String, description: String) = CatalogItem(
        kind = kind,
        key = key,
        title = title,
        packageName = context.packageName,
        description = description,
    )

    private fun listWidgets(): List<CatalogItem> {
        val manager = AppWidgetManager.getInstance(context)
        val providers = manager.installedProviders ?: return emptyList()
        return providers
            .mapNotNull { info ->
                val provider = info.provider ?: return@mapNotNull null
                val label = info.loadLabel(context.packageManager) ?: provider.shortClassName
                CatalogItem(
                    kind = CatalogItemKind.WIDGET,
                    key = "${provider.packageName}/${provider.className}",
                    title = label,
                    packageName = provider.packageName,
                    description = provider.packageName,
                    suggestedSpan = suggestedSpan(info),
                )
            }
            .distinctBy { it.key }
            .sortedBy { it.title.lowercase() }
    }

    private fun listApps(): List<CatalogItem> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved: List<ResolveInfo> = pm.queryIntentActivities(intent, 0)
        return resolved
            .mapNotNull { info ->
                val activity = info.activityInfo ?: return@mapNotNull null
                val label = info.loadLabel(pm)?.toString() ?: activity.name
                CatalogItem(
                    kind = CatalogItemKind.APP,
                    key = "${activity.packageName}/${activity.name}",
                    title = label,
                    packageName = activity.packageName,
                    description = activity.packageName,
                )
            }
            .sortedBy { it.title.lowercase() }
    }

    /**
     * Initial widget size: the one it declares (API 31+, `targetCell*`) or, otherwise, its minimum in
     * dp converted to this launcher's cells (~200 × 140 dp in the head unit's 6 × 4 grid).
     */
    private fun suggestedSpan(info: AppWidgetProviderInfo): Cell {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && info.targetCellWidth > 0 && info.targetCellHeight > 0) {
            return Cell(info.targetCellHeight, info.targetCellWidth)
        }
        val density = context.resources.displayMetrics.density
        val columns = ceil(info.minWidth / density / CELL_WIDTH_DP).toInt().coerceAtLeast(1)
        val rows = ceil(info.minHeight / density / CELL_HEIGHT_DP).toInt().coerceAtLeast(1)
        return Cell(rows, columns)
    }

    private companion object {
        const val CELL_WIDTH_DP = 200f
        const val CELL_HEIGHT_DP = 140f
    }
}
