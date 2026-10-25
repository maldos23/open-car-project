package org.opencarproject.launcher.presentation.launcher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.opencarproject.launcher.di.AppContainer

/**
 * ViewModel factory. Connects the composition root ([AppContainer]) to the UI.
 * It is a substitution point: tests can build the ViewModel directly
 * with "fake" use cases without going through this factory.
 */
class LauncherViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LauncherViewModel::class.java)) {
            return LauncherViewModel(
                observeCatalog = container.observeCatalog,
                loadLayout = container.loadLayout,
                addItem = container.addItem,
                addBoundWidget = container.addBoundWidget,
                deleteWidgetId = container.deleteWidgetId,
                removeItem = container.removeItem,
                moveItem = container.moveItem,
                resizeItem = container.resizeItem,
                launchApp = container.launchApp,
                launchAssistant = container.launchAssistant,
                observeSettings = container.observeSettings,
                updateSettings = container.updateSettings,
                changeGridSize = container.changeGridSize,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}
