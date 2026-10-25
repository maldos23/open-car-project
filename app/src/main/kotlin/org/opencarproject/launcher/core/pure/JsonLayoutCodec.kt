package org.opencarproject.launcher.core.pure

import org.opencarproject.launcher.core.model.LayoutDefinition
import kotlinx.serialization.encodeToString
import org.opencarproject.launcher.core.model.CatalogItemKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Layout JSON encoder/decoder.
 *
 * Centralizes serialization (DRY) and makes it lenient: layouts saved with an
 * older version still load even when new fields are added.
 */
class JsonLayoutCodec(
    private val json: Json = Json {
        ignoreUnknownKeys = true // forward-compat: new fields don't break old layouts
        encodeDefaults = true    // deterministic, complete output
        isLenient = true
    },
) {

    /** Serializes a layout to its JSON representation. */
    fun encode(layout: LayoutDefinition): String = json.encodeToString(layout)

    /**
     * Deserializes a layout; returns `null` if the JSON is corrupt. Cards of a type that
     * no longer exists (e.g. after rolling back to an older version) are dropped one by one
     * instead of losing the whole layout.
     */
    fun decode(raw: String): LayoutDefinition? = runCatching {
        val root = json.parseToJsonElement(raw).jsonObject
        val known = CatalogItemKind.entries.map { it.name }.toSet()
        val items = root["items"]?.jsonArray?.filter { it.jsonObject["kind"]?.jsonPrimitive?.content in known }
        val cleaned = if (items == null) root else JsonObject(root + ("items" to JsonArray(items)))
        json.decodeFromJsonElement<LayoutDefinition>(cleaned)
    }.getOrNull()
}
