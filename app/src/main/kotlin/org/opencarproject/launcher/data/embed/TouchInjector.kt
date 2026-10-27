package org.opencarproject.launcher.data.embed

import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.MotionEvent
import org.opencarproject.launcher.core.pure.TouchLine

/**
 * Root helper, not app code: [EmbeddedAppSession] starts it with `app_process` over the root
 * adb shell (same idea as scrcpy). It reads one [TouchLine] per stdin line and injects it into
 * the virtual display given as the first argument, through hidden InputManager APIs that an
 * app can't call but a root process can.
 */
object TouchInjector {

    @JvmStatic
    fun main(args: Array<String>) {
        val displayId = args[0].toInt()
        val inputManager = Class.forName("android.hardware.input.InputManager").getMethod("getInstance").invoke(null)
        val inject = inputManager.javaClass.getMethod("injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType)
        val setDisplayId = MotionEvent::class.java.getMethod("setDisplayId", Int::class.javaPrimitiveType)
        var downTime = 0L
        System.`in`.bufferedReader().forEachLine { raw ->
            val line = TouchLine.parse(raw) ?: return@forEachLine
            val now = SystemClock.uptimeMillis()
            if (line.action == MotionEvent.ACTION_DOWN) downTime = now
            val properties = line.pointers.map {
                MotionEvent.PointerProperties().apply { id = it.id; toolType = MotionEvent.TOOL_TYPE_FINGER }
            }.toTypedArray()
            val coords = line.pointers.map {
                MotionEvent.PointerCoords().apply { x = it.x; y = it.y; pressure = 1f; size = 1f }
            }.toTypedArray()
            val action = line.action or (line.index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
            val event = MotionEvent.obtain(
                downTime, now, action, properties.size, properties, coords,
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0,
            )
            setDisplayId.invoke(event, displayId)
            inject.invoke(inputManager, event, 0) // 0 = INJECT_INPUT_EVENT_MODE_ASYNC
            event.recycle()
        }
    }
}
