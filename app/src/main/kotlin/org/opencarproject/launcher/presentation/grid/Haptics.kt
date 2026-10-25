package org.opencarproject.launcher.presentation.grid

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/** Desktop edit-mode haptics (with fallbacks for API < 30). */
enum class EditHaptic { PICK_UP, CELL_TICK, DROP, REJECT, DELETE }

fun View.haptic(kind: EditHaptic) {
    val modern = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    val constant = when (kind) {
        EditHaptic.PICK_UP -> if (modern) HapticFeedbackConstants.GESTURE_START else HapticFeedbackConstants.LONG_PRESS
        EditHaptic.CELL_TICK -> HapticFeedbackConstants.CLOCK_TICK
        EditHaptic.DROP -> if (modern) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
        EditHaptic.REJECT -> if (modern) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
        EditHaptic.DELETE -> HapticFeedbackConstants.VIRTUAL_KEY
    }
    performHapticFeedback(constant)
}
