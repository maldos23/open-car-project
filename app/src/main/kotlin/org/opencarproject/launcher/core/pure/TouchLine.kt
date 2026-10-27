package org.opencarproject.launcher.core.pure

/** One finger of a [TouchLine], in display pixels. */
data class TouchPointer(val id: Int, val x: Float, val y: Float)

/**
 * One touch event as a text line for the root touch helper: `action index id x y [id x y]...`.
 * [action] is the masked MotionEvent action; [index] the pointer that went down/up.
 */
data class TouchLine(val action: Int, val index: Int, val pointers: List<TouchPointer>) {

    fun encode(): String = buildString {
        append(action).append(' ').append(index)
        pointers.forEach { append(' ').append(it.id).append(' ').append(it.x).append(' ').append(it.y) }
    }

    companion object {
        /** Returns null for a malformed line (the helper skips it instead of dying). */
        fun parse(line: String): TouchLine? {
            val parts = line.trim().split(' ')
            if (parts.size < 5 || (parts.size - 2) % 3 != 0) return null
            val action = parts[0].toIntOrNull() ?: return null
            val index = parts[1].toIntOrNull() ?: return null
            val pointers = parts.drop(2).chunked(3).map { (id, x, y) ->
                TouchPointer(id.toIntOrNull() ?: return null, x.toFloatOrNull() ?: return null, y.toFloatOrNull() ?: return null)
            }
            return TouchLine(action, index, pointers)
        }
    }
}
