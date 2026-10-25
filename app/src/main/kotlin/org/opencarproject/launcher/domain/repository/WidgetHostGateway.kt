package org.opencarproject.launcher.domain.repository

/**
 * Gateway to the system (AppWidgetHost / AppWidgetManager / PackageManager).
 *
 * Lives in `domain` so use cases don't depend on Android (Dependency Inversion):
 * the concrete implementation is in `data` and is injected.
 */
interface WidgetHostGateway {
    /** Allocates a native `appWidgetId` for a new widget. */
    fun allocateAppWidgetId(): Int

    /** Tries to bind the widget to the provider; `false` if the system rejects it. */
    fun bindWidgetIfAllowed(appWidgetId: Int, provider: String): Boolean

    /** Releases an `appWidgetId` when a widget is removed. */
    fun deleteWidget(appWidgetId: Int)

    /** Launches an app by its "package/activity" component. */
    fun launchApp(component: String)

    /** Invokes the voice assistant (Google Assistant). */
    fun launchAssistant()
}
