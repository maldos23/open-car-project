package org.opencarproject.launcher.core.pure

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {

    @Test
    fun higherPart_isNewer() {
        assertTrue(AppVersion.isNewer("v0.16.1", "0.16.0"))
        assertTrue(AppVersion.isNewer("v0.17.0", "0.16.9"))
        assertTrue(AppVersion.isNewer("v1.0.0", "0.99.99"))
    }

    @Test
    fun numbersCompareAsNumbers() {
        assertTrue(AppVersion.isNewer("v0.10.0", "0.9.0"))
    }

    @Test
    fun sameOrOlder_isNotNewer() {
        assertFalse(AppVersion.isNewer("v0.16.0", "0.16.0"))
        assertFalse(AppVersion.isNewer("v0.15.9", "0.16.0"))
    }

    @Test
    fun unparsable_isNotNewer() {
        assertFalse(AppVersion.isNewer("nightly", "0.16.0"))
        assertFalse(AppVersion.isNewer("v0.16", "0.15.0"))
        assertFalse(AppVersion.isNewer("v0.17.0", ""))
    }
}
