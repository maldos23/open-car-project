package org.opencarproject.launcher.core.pure

/** An activity stack as listed by `am stack list`. */
data class StackRef(val id: Int, val displayId: Int)

object StackList {

    private val header = Regex("""^Stack id=(\d+) .*displayId=(\d+)""")

    /** Stacks holding a task of [packageName] in `am stack list` output. */
    fun findAll(output: String, packageName: String): List<StackRef> {
        var current: StackRef? = null
        val found = mutableListOf<StackRef>()
        output.lineSequence().forEach { line ->
            header.find(line)?.let { current = StackRef(it.groupValues[1].toInt(), it.groupValues[2].toInt()) }
            if (line.trimStart().startsWith("taskId=") && line.contains(": $packageName/")) current?.let { found += it }
        }
        return found.distinct()
    }
}
