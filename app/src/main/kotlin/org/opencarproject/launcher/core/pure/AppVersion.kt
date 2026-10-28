package org.opencarproject.launcher.core.pure

/** Compares release versions ("v0.16.1", "0.16.0"): only plain x.y.z, as the release tags are. */
object AppVersion {

    /** True when [latest] is higher than [current]; false when either doesn't parse. */
    fun isNewer(latest: String, current: String): Boolean {
        val a = parse(latest) ?: return false
        val b = parse(current) ?: return false
        val (x, y) = a.zip(b).firstOrNull { (x, y) -> x != y } ?: return false
        return x > y
    }

    private fun parse(version: String): List<Int>? =
        version.trim().removePrefix("v").substringBefore('-').split('.')
            .map { it.toIntOrNull() ?: return null }
            .takeIf { it.size == 3 }
}
