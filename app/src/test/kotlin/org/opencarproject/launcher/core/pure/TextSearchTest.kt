package org.opencarproject.launcher.core.pure

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSearchTest {

    @Test
    fun matches_ignoresCaseAndAccents() {
        assertTrue(TextSearch.matches("Cámara", "camara"))
        assertTrue(TextSearch.matches("Google Maps", "MAPS"))
        assertTrue(TextSearch.matches("Teléfono", "  telef "))
    }

    @Test
    fun matches_emptyQueryMatchesAll() {
        assertTrue(TextSearch.matches("Spotify", ""))
    }

    @Test
    fun matches_rejectsNonMatching() {
        assertFalse(TextSearch.matches("Spotify", "waze"))
    }
}
