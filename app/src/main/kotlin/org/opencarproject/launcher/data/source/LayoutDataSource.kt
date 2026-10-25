package org.opencarproject.launcher.data.source

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import org.opencarproject.launcher.core.model.LayoutDefinition
import org.opencarproject.launcher.core.pure.JsonLayoutCodec
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.io.IOException

/** Per-process DataStore (singleton per Context). */
private val Context.launcherDataStore by preferencesDataStore(
    name = "launcher_layout",
    // A corrupt file falls back to the default menu instead of crashing the home screen.
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * Local persistence source for the layout (Preferences DataStore + JSON).
 *
 * Serialization is delegated to [JsonLayoutCodec], the app's only codec (DRY). That way,
 * any format change is centralized in a single place.
 */
class LayoutDataSource(
    private val context: Context,
    private val codec: JsonLayoutCodec = JsonLayoutCodec(),
    private val decodeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val layoutKey = stringPreferencesKey("layout_json")

    /** Flow of the persisted layout; emits the default menu if none exists yet. */
    val layoutFlow: Flow<LayoutDefinition> = context.launcherDataStore.data.recoverIoErrors().map { prefs ->
        prefs[layoutKey]?.let { codec.decode(it)?.toFineColumns() } ?: LayoutDefinition.DEFAULT
    }.flowOn(decodeDispatcher) // JSON decoding stays off the main thread.

    /** Persists the whole layout (atomic DataStore operation). */
    suspend fun save(layout: LayoutDefinition) {
        context.launcherDataStore.edit { prefs ->
            prefs[layoutKey] = codec.encode(layout)
        }
    }
}

/** A failed read (e.g. I/O error) shows defaults instead of crashing the launcher. */
internal fun Flow<Preferences>.recoverIoErrors(): Flow<Preferences> =
    catch { if (it is IOException) emit(emptyPreferences()) else throw it }
