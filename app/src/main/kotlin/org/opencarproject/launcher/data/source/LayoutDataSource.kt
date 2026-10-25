package org.opencarproject.launcher.data.source

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import org.opencarproject.launcher.core.model.LayoutDefinition
import org.opencarproject.launcher.core.pure.JsonLayoutCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Per-process DataStore (singleton per Context). */
private val Context.launcherDataStore by preferencesDataStore(name = "launcher_layout")

/**
 * Local persistence source for the layout (Preferences DataStore + JSON).
 *
 * Serialization is delegated to [JsonLayoutCodec], the app's only codec (DRY). That way,
 * any format change is centralized in a single place.
 */
class LayoutDataSource(
    private val context: Context,
    private val codec: JsonLayoutCodec = JsonLayoutCodec(),
) {
    private val layoutKey = stringPreferencesKey("layout_json")

    /** Flow of the persisted layout; emits the default menu if none exists yet. */
    val layoutFlow: Flow<LayoutDefinition> = context.launcherDataStore.data.map { prefs ->
        prefs[layoutKey]?.let { codec.decode(it)?.toFineColumns() } ?: LayoutDefinition.DEFAULT
    }

    /** Persists the whole layout (atomic DataStore operation). */
    suspend fun save(layout: LayoutDefinition) {
        context.launcherDataStore.edit { prefs ->
            prefs[layoutKey] = codec.encode(layout)
        }
    }
}
