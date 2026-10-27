package org.opencarproject.launcher.core.pure

/**
 * Stages of the vehicle → rocket launch, from the overall progress 0..1 (and back when
 * slowing down): the vehicle shakes and sparks, turns into a rocket, then lifts off into space.
 */
object LaunchMath {

    const val CHARGE_END = 0.25f
    const val MORPH_END = 0.55f
    const val LIFT_END = 0.8f

    /** Shaking and sparks on the road. */
    fun charge(progress: Float): Float = stage(progress, 0f, CHARGE_END)

    /** The rocket grows out of the vehicle while the vehicle fades. */
    fun morph(progress: Float): Float = stage(progress, CHARGE_END, MORPH_END)

    /** Lift-off: the backdrop fades into space and the rocket flies to its cruise spot. */
    fun lift(progress: Float): Float = stage(progress, MORPH_END, LIFT_END)

    private fun stage(progress: Float, from: Float, to: Float): Float = ((progress - from) / (to - from)).coerceIn(0f, 1f)
}
