package org.opencarproject.launcher.core.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.opencarproject.launcher.core.pure.scallopedOutline

/** Rounded rectangle whose edge waves inward like a flower (see [scallopedOutline]). */
data class ScallopedShape(val corner: Dp, val depth: Dp = 7.dp, val waveLength: Dp = 48.dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline = with(density) {
        val p = scallopedOutline(size.width, size.height, corner.toPx(), depth.toPx(), waveLength.toPx(), 2.dp.toPx())
        Outline.Generic(
            Path().apply {
                if (p.isNotEmpty()) {
                    moveTo(p[0], p[1])
                    for (i in 2 until p.size step 2) lineTo(p[i], p[i + 1])
                    close()
                }
            },
        )
    }
}
