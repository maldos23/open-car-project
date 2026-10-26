package org.opencarproject.launcher.core.pure

import kotlin.math.roundToInt

/** Grid limits: fewer doesn't use the screen well; more makes cards unreadable while driving. */
val GridColumnsRange = 3..8
val GridRowsRange = 2..6

/** Zoom limits: below 80 % touch targets get too small for driving; above 130 % little fits. */
val UiScaleRange = 0.8f..1.3f
val MenuIconScaleRange = 0.75f..1.5f
val MotionGlowSensitivityRange = 0.5f..2f
val MotionGlowSmoothnessRange = 0f..1f

/** Rocket threshold limits, in km/h. */
val RocketKmhRange = 20..200

/** Rounds [value] to the nearest 1/[stepsPerUnit] (e.g. 20 → 5 % steps) and keeps it within [range]. */
fun snap(value: Float, stepsPerUnit: Int, range: ClosedFloatingPointRange<Float>): Float =
    ((value * stepsPerUnit).roundToInt() / stepsPerUnit.toFloat()).coerceIn(range)
