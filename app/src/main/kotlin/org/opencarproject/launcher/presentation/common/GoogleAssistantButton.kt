package org.opencarproject.launcher.presentation.common

import org.opencarproject.launcher.R
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Maximum pill width (the bottom bar reserves half on each side of the center). */
val AssistantMaxWidth = 520.dp

/** Google brand colors. */
private val GoogleBlue = Color(0xFF4285F4)
private val GoogleRed = Color(0xFFEA4335)
private val GoogleYellow = Color(0xFFFBBC05)
private val GoogleGreen = Color(0xFF34A853)

/** Closed sweep (starts and ends on blue) so the halo rotates seamlessly. */
private val GoogleSweep = listOf(GoogleBlue, GoogleRed, GoogleYellow, GoogleGreen, GoogleBlue)

/** Google colors flowing along the pill for the spin [angle] (the sweep is closed: no seam). */
private fun googleFlow(angle: Float): Brush {
    val flowWidthPx = 600f
    val shift = angle / 360f * flowWidthPx
    return Brush.linearGradient(
        colors = GoogleSweep,
        start = Offset(shift, 0f),
        end = Offset(shift + flowWidthPx, flowWidthPx * 0.2f),
        tileMode = TileMode.Repeated,
    )
}

/**
 * AI-styled assistant button: a dark pill with a halo of Google colors that flow
 * and "breathe", animated voice bars, and rotating suggestions of what to ask.
 * Tapping it invokes the assistant.
 */
@Composable
fun GoogleAssistantButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "assistant")
    // Animated values are read only while drawing: the pill redraws, it never recomposes.
    val angle = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing)),
        label = "spin",
    )
    val glow = transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow",
    )
    val shape = RoundedCornerShape(percent = 50)
    // The colors flow along the pill (the shape doesn't rotate). The sweep is closed,
    // so the repeated tile has no seam.

    Row(
        modifier = modifier
            .height(64.dp)
            // Glow: increasingly wide and faint layers (cheap blur, works on API 26+).
            .drawBehind {
                val flow = googleFlow(angle.value)
                val glow = glow.value
                for (layer in 1..4) {
                    val spread = layer * 3.dp.toPx()
                    drawRoundRect(
                        brush = flow,
                        topLeft = Offset(-spread, -spread),
                        size = Size(size.width + spread * 2, size.height + spread * 2),
                        cornerRadius = CornerRadius(size.height / 2 + spread),
                        alpha = glow * 0.22f / layer,
                    )
                }
            }
            .clip(shape)
            .background(Color(0xFF111318))
            .drawWithContent {
                drawContent()
                val stroke = 2.dp.toPx()
                drawRoundRect(
                    brush = googleFlow(angle.value),
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius((size.height - stroke) / 2),
                    style = Stroke(stroke),
                )
            }
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.assistant_open), onClick = onClick)
            .widthIn(max = AssistantMaxWidth)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Four bars in Google colors that rise and fall like a speaking voice.
        AudioBars(GoogleSweep.dropLast(1), Modifier.size(width = 30.dp, height = 28.dp))
        Column {
            Text(
                text = stringResource(R.string.assistant_talk),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.65f),
            )
            // The phrase exits upward and the next enters from below; the width adjusts itself.
            AnimatedContent(
                targetState = rememberRotatingSuggestion(),
                transitionSpec = {
                    (slideInVertically(tween(450)) { it } + fadeIn(tween(450))) togetherWith
                        (slideOutVertically(tween(350)) { -it } + fadeOut(tween(250))) using
                        SizeTransform(clip = false)
                },
                label = "suggestion",
            ) { suggestion ->
                Text(
                    text = "“$suggestion”",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** How long each suggestion stays visible. */
private const val SUGGESTION_MS = 4_500L

/** Current suggestion: changes every [SUGGESTION_MS] to a random one, never repeating the previous. */
@Composable
private fun rememberRotatingSuggestion(): String {
    // Ideas of what to ask the assistant, aimed at the car.
    val suggestions = stringArrayResource(R.array.assistant_suggestions).toList()
    val suggestion by produceState(suggestions.random(), suggestions) {
        while (true) {
            delay(SUGGESTION_MS)
            value = (suggestions - value).random()
        }
    }
    return suggestion
}
