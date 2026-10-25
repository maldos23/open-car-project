package org.opencarproject.launcher.presentation.widget

import org.opencarproject.launcher.R
import androidx.compose.ui.res.stringResource
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.service.notification.StatusBarNotification
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.opencarproject.launcher.core.util.dpInt
import org.opencarproject.launcher.core.util.toBoundedImageBitmap
import org.opencarproject.launcher.core.util.toImageBitmap
import org.opencarproject.launcher.data.source.LauncherNotificationListener

/**
 * Navigation module. No map of its own: Google Maps / Waze don't share their route, so
 * while they're guiding their next turn is shown (read from their notification) and, when not navigating,
 * an animated button that opens the default navigation app.
 */
@Composable
fun NavigationCard(modifier: Modifier = Modifier) {
    val navigation by LauncherNotificationListener.navigation.collectAsStateWithLifecycle()
    AnimatedContent(
        targetState = navigation,
        contentKey = { it != null },
        transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.92f)) togetherWith fadeOut() },
        label = "navigation",
        modifier = modifier.fillMaxSize(),
    ) { sbn ->
        if (sbn != null) GuidanceContent(sbn) else NavigateButton()
    }
}

// ---- Navigating: next turn ----

@Composable
private fun GuidanceContent(sbn: StatusBarNotification) {
    val context = LocalContext.current
    val extras = sbn.notification.extras
    val distance = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
    val instruction = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
    val arrival = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
    val arrow = remember(sbn.key, sbn.postTime) {
        runCatching { sbn.notification.getLargeIcon()?.loadDrawable(context)?.toBoundedImageBitmap(context.dpInt(96f)) }.getOrNull()
    }
    val app = remember(sbn.packageName) { context.appLabelAndIcon(sbn.packageName) }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .clickable { runCatching { sbn.notification.contentIntent?.send() } },
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        BoxWithConstraints(Modifier.padding(16.dp)) {
            val w = maxWidth
            val h = maxHeight
            // Three layouts depending on card size (without clipping content):
            // compact (turn and distance only), wide-short (arrow on the left) and tall (column).
            val compact = h < 180.dp || w < 200.dp
            val wide = !compact && w > h * 1.5f
            val distanceStyle = when {
                compact -> MaterialTheme.typography.headlineSmall
                w < 320.dp -> MaterialTheme.typography.headlineLarge
                else -> MaterialTheme.typography.displayMedium
            }
            val distanceText: @Composable () -> Unit = {
                // The distance rolls up when it updates, like a counter.
                AnimatedContent(
                    targetState = distance,
                    transitionSpec = {
                        (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut())
                    },
                    label = "distance",
                ) {
                    Text(
                        text = it,
                        style = distanceStyle,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                    )
                }
            }
            val instructionText: @Composable (Int, TextAlign) -> Unit = { lines, align ->
                if (instruction.isNotEmpty()) {
                    Text(
                        text = instruction,
                        style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = align,
                        maxLines = lines,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            val arrivalText: @Composable () -> Unit = {
                if (arrival.isNotEmpty()) {
                    Text(
                        text = arrival,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            when {
                compact -> Row(
                    Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (arrow != null) {
                        Image(arrow, contentDescription = null, modifier = Modifier.size((minOf(w, h) * 0.5f).coerceIn(32.dp, 72.dp)))
                    }
                    Column(Modifier.weight(1f)) {
                        distanceText()
                        instructionText(2, TextAlign.Start)
                    }
                }
                wide -> Row(
                    Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    if (arrow != null) {
                        Image(arrow, contentDescription = null, modifier = Modifier.size((h * 0.7f).coerceIn(56.dp, 180.dp)))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (h > 220.dp) AppHeader(app, stringResource(R.string.nav_navigating))
                        distanceText()
                        instructionText(2, TextAlign.Start)
                        arrivalText()
                    }
                }
                else -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (h > 300.dp) AppHeader(app, stringResource(R.string.nav_navigating))
                    Column(
                        Modifier.weight(1f).fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                    ) {
                        if (arrow != null) {
                            Image(arrow, contentDescription = null, modifier = Modifier.size((minOf(w, h) * 0.38f).coerceIn(56.dp, 180.dp)))
                        }
                        distanceText()
                        instructionText(3, TextAlign.Center)
                        arrivalText()
                    }
                }
            }
        }
    }
}

// ---- Not navigating: animated button ----

/**
 * "Navegar" button: an arrow swaying over rings pulsing outward and a dotted route
 * that advances. It sinks when pressed and opens the default navigation app.
 */
@Composable
private fun NavigateButton() {
    val context = LocalContext.current
    val app = remember { context.defaultNavigationApp() }
    val pressSource = remember { MutableInteractionSource() }
    val pressed by pressSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "press",
    )

    val transition = rememberInfiniteTransition(label = "navigate")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "pulse",
    )
    val sway by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sway",
    )
    val dash by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "dash",
    )
    val primary = MaterialTheme.colorScheme.primary

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = pressSource, indication = null) { context.openNavigationApp(app?.packageName) }
            .padding(12.dp),
    ) {
            val w = maxWidth
            val h = maxHeight
        // Compact: button only; wide-short: button on the left and text on the right;
        // tall: column. That way the animation is never clipped on small cards.
        // Wide-short (1 row, several columns) uses the row; it's only compact if small on both axes.
        val wide = w > h * 1.6f && h >= 80.dp
        val compact = !wide && (h < 150.dp || w < 150.dp)
        val stage: @Composable (Dp) -> Unit = { size ->
            NavigateStage(size, primary, pulse, dash, sway, pressScale)
        }
        val label: @Composable () -> Unit = {
            Text(
                text = stringResource(R.string.nav_navigate),
                style = if (h < 260.dp) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
        when {
            compact -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                stage(minOf(w, h).coerceAtLeast(48.dp))
            }
            wide -> Row(
                Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            ) {
                stage((h * 0.95f).coerceAtMost(220.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    label()
                    if (app != null) AppHeader(app, status = null)
                }
            }
            else -> Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            ) {
                stage(minOf(w, h * 0.65f).coerceIn(96.dp, 260.dp))
                label()
                if (app != null && h > 240.dp) AppHeader(app, status = null)
            }
        }
    }
}

/** Radar rings, dotted route and circle with the arrow, scaled to [size]. */
@Composable
private fun NavigateStage(
    stageSize: Dp,
    primary: Color,
    pulse: Float,
    dash: Float,
    sway: Float,
    pressScale: Float,
) {
    Box(Modifier.size(stageSize), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val maxRadius = size.minDimension / 2
                // Two out-of-phase rings that expand and fade out (radar).
                for (k in 0..1) {
                    val p = (pulse + k * 0.5f) % 1f
                    drawCircle(
                        color = primary.copy(alpha = (1f - p) * 0.45f),
                        radius = maxRadius * (0.42f + 0.58f * p),
                        style = Stroke(width = 3.dp.toPx()),
                    )
                }
                // Dotted route that "advances" under the arrow.
                val step = 14.dp.toPx()
                drawLine(
                    color = primary.copy(alpha = 0.6f),
                    start = center.copy(y = center.y + maxRadius * 0.95f),
                    end = center.copy(y = center.y + maxRadius * 0.45f),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(step * 0.4f, step * 0.6f), phase = dash * step),
                )
            }
            Surface(
                shape = CircleShape,
                color = primary,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .size(stageSize * 0.42f)
                    .graphicsLayer {
                        scaleX = pressScale
                        scaleY = pressScale
                    },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Navigation,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .size(stageSize * 0.22f)
                            .graphicsLayer {
                                rotationZ = sway * 8f
                                translationY = -sway * 3.dp.toPx()
                            },
                    )
                }
            }
        }
}

// ---- Shared ----

private class NavApp(val packageName: String, val label: String, val icon: ImageBitmap?)

@Composable
private fun AppHeader(app: NavApp?, status: String?) {
    if (app == null) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (app.icon != null) Image(app.icon, contentDescription = null, modifier = Modifier.size(22.dp))
        Text(
            text = listOfNotNull(if (status == null) stringResource(R.string.nav_with_app, app.label) else app.label, status).joinToString(" · "),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun Context.appLabelAndIcon(packageName: String): NavApp? = runCatching {
    val pm = packageManager
    val info = pm.getApplicationInfo(packageName, 0)
    NavApp(packageName, pm.getApplicationLabel(info).toString(), pm.getApplicationIcon(info).toImageBitmap(dpInt(22f)))
}.getOrNull()

/**
 * Default navigation app: the one the user chose for opening directions (`geo:`);
 * if they chose none (Android would resolve to the chooser), the first installed one from
 * [LauncherNotificationListener.NAVIGATION_PACKAGES].
 */
private fun Context.defaultNavigationApp(): NavApp? {
    val pm = packageManager
    val chosen = pm.resolveActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0")), PackageManager.MATCH_DEFAULT_ONLY)
        ?.activityInfo?.packageName
        ?.takeIf { it != "android" && pm.getLaunchIntentForPackage(it) != null }
    val pkg = chosen ?: LauncherNotificationListener.NAVIGATION_PACKAGES.firstOrNull { pm.getLaunchIntentForPackage(it) != null }
    return pkg?.let { appLabelAndIcon(it) }
}

/** Opens the given app; if none is installed, delegates to `geo:` (Android offers a choice). */
private fun Context.openNavigationApp(packageName: String?) {
    val intent = packageName?.let { packageManager.getLaunchIntentForPackage(it) }
        ?: Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0"))
    runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
