package org.opencarproject.launcher.presentation.common

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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/** Maximum pill width (the bottom bar reserves half on each side of the center). */
val AssistantMaxWidth = 520.dp

/** Google brand colors. */
private val GoogleBlue = Color(0xFF4285F4)
private val GoogleRed = Color(0xFFEA4335)
private val GoogleYellow = Color(0xFFFBBC05)
private val GoogleGreen = Color(0xFF34A853)

/** Closed sweep (starts and ends on blue) so the halo rotates seamlessly. */
private val GoogleSweep = listOf(GoogleBlue, GoogleRed, GoogleYellow, GoogleGreen, GoogleBlue)

/**
 * AI-styled assistant button: a dark pill with a halo of Google colors that flow
 * and "breathe", animated voice bars, and rotating suggestions of what to ask.
 * Tapping it invokes the assistant.
 */
@Composable
fun GoogleAssistantButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "assistant")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing)),
        label = "spin",
    )
    val glow by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow",
    )
    val shape = RoundedCornerShape(percent = 50)
    // The colors flow along the pill (the shape doesn't rotate). The sweep is closed,
    // so the repeated tile has no seam.
    val flowWidthPx = 600f
    val shift = angle / 360f * flowWidthPx
    val flow = Brush.linearGradient(
        colors = GoogleSweep,
        start = Offset(shift, 0f),
        end = Offset(shift + flowWidthPx, flowWidthPx * 0.2f),
        tileMode = TileMode.Repeated,
    )

    Row(
        modifier = modifier
            .height(64.dp)
            // Glow: increasingly wide and faint layers (cheap blur, works on API 26+).
            .drawBehind {
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
            .border(2.dp, flow, shape)
            .clickable(role = Role.Button, onClickLabel = "Abrir asistente", onClick = onClick)
            .widthIn(max = AssistantMaxWidth)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        VoiceBars(Modifier.size(width = 30.dp, height = 28.dp))
        Column {
            Text(
                text = "Habla con Google",
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

/**
 * Four bars in Google colors that rise and fall like a speaking voice. Each bar
 * mixes two integer-frequency sines (seamless loop) with a different phase: irregular pattern.
 */
@Composable
private fun VoiceBars(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "voice")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
        label = "t",
    )
    val colors = GoogleSweep.dropLast(1)
    Canvas(modifier) {
        val barWidth = size.width / (colors.size + (colors.size - 1) * 0.7f)
        val gap = barWidth * 0.7f
        colors.forEachIndexed { i, color ->
            val wave = 0.6f * (0.5f + 0.5f * sin(2 * t + i * 1.3f)) +
                0.4f * (0.5f + 0.5f * sin(3 * t + i * 2.1f))
            // With rounded caps the line grows by barWidth/2 on each side.
            val length = (size.height - barWidth) * (0.2f + 0.8f * wave)
            val x = barWidth / 2 + i * (barWidth + gap)
            drawLine(
                color = color,
                start = Offset(x, center.y - length / 2),
                end = Offset(x, center.y + length / 2),
                strokeWidth = barWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** Ideas of what to ask the assistant, aimed at the car. */
private val ASSISTANT_SUGGESTIONS = listOf(
    "¿Cómo está el tráfico a casa?",
    "Llévame a la gasolinera más cercana",
    "Pon música para manejar",
    "Llama a mamá",
    "Lee mis mensajes",
    "¿A qué hora llego al trabajo?",
    "Dile a Ana que voy en camino",
    "¿Va a llover hoy?",
    "Busca un café cerca",
    "Sube el volumen",
    "Recuérdame comprar leche al llegar",
    "¿Dónde puedo estacionarme?",
    "Pon mi podcast favorito",
    "¿Qué restaurantes hay cerca?",
)

/** How long each suggestion stays visible. */
private const val SUGGESTION_MS = 4_500L

/** Current suggestion: changes every [SUGGESTION_MS] to a random one, never repeating the previous. */
@Composable
private fun rememberRotatingSuggestion(): String {
    val suggestion by produceState(ASSISTANT_SUGGESTIONS.random()) {
        while (true) {
            delay(SUGGESTION_MS)
            value = (ASSISTANT_SUGGESTIONS - value).random()
        }
    }
    return suggestion
}
