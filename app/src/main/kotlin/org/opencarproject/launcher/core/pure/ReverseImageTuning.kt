package org.opencarproject.launcher.core.pure

/**
 * Shader parameters for the reverse-camera cleanup, derived from the two user sliders
 * (0..1 each). Higher [denoise] keeps more frame history, tolerates stronger analog noise
 * before treating a change as motion, and smooths luma and chroma more.
 */
data class ReverseImageTuning(
    /** Weight of the new frame when the picture is still (1 = no temporal averaging). */
    val stillWeight: Float,
    /** Luma change (0..1) below which a difference is treated as noise, not motion. */
    val motionThreshold: Float,
    /** Bilateral falloff: higher keeps more edges (less smoothing). */
    val edgeFalloff: Float,
    /** How much chroma is replaced by its 3×3 average (0..1). */
    val chromaSmoothing: Float,
    /** Unsharp-mask amount. */
    val sharpen: Float,
    /** Added to every channel (-0.25..0.25; 0 = unchanged). */
    val brightness: Float = 0f,
    /** Gain around mid-grey (0.5..1.5; 1 = unchanged). */
    val contrast: Float = 1f,
) {
    companion object {
        /** All sliders are 0..1; brightness and contrast are neutral at 0.5. */
        fun of(denoise: Float, sharpness: Float, brightness: Float = 0.5f, contrast: Float = 0.5f): ReverseImageTuning {
            val d = denoise.coerceIn(0f, 1f)
            val s = sharpness.coerceIn(0f, 1f)
            return ReverseImageTuning(
                stillWeight = lerp(1f, 0.12f, d),
                motionThreshold = lerp(0.03f, 0.22f, d),
                edgeFalloff = lerp(80f, 10f, d),
                chromaSmoothing = d,
                sharpen = s * 1.2f,
                brightness = (brightness.coerceIn(0f, 1f) - 0.5f) * 0.5f,
                contrast = 0.5f + contrast.coerceIn(0f, 1f),
            )
        }

        private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
    }
}
