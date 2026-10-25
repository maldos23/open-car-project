package org.opencarproject.launcher.core.pure

import java.text.Normalizer

/** Lenient matching for the app search: ignores case and accents ("camara" ≈ "Cámara"). */
object TextSearch {

    private val accents = Regex("\\p{Mn}+")

    fun normalize(text: String): String =
        accents.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "").lowercase().trim()

    /** `true` if `title` contains `query` (empty query = everything matches). */
    fun matches(title: String, query: String): Boolean = normalize(title).contains(normalize(query))
}
