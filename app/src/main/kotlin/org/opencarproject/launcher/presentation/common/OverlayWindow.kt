package org.opencarproject.launcher.presentation.common

import android.content.Context
import android.content.res.Configuration
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import org.opencarproject.launcher.core.model.AppLanguage
import java.util.Locale

/** Minimal lifecycle so Compose can run in an overlay window that no Activity owns. */
internal class OverlayOwner : SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val controller = SavedStateRegistryController.create(this).apply { performRestore(null) }
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry
    fun resume() { registry.currentState = Lifecycle.State.RESUMED }
    fun destroy() { registry.currentState = Lifecycle.State.DESTROYED }
}

/** Overlays live outside the Activity, so they apply the in-app language themselves. */
internal fun Context.localized(language: AppLanguage): Context {
    if (language == AppLanguage.SYSTEM) return this
    val config = Configuration(resources.configuration).apply { setLocale(Locale.forLanguageTag(language.tag)) }
    return createConfigurationContext(config)
}
