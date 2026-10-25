package org.opencarproject.launcher.presentation.widget

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Parcelable
import android.service.notification.StatusBarNotification
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.opencarproject.launcher.core.util.dpInt
import org.opencarproject.launcher.core.util.toImageBitmap
import org.opencarproject.launcher.data.source.LauncherNotificationListener

/** How long a newly arrived notification shows its preview before collapsing. */
private const val PREVIEW_MS = 5_000

/** Only those arriving while the launcher is visible auto-expand (not old ones at startup). */
private const val FRESH_MS = 10_000L

/** Text longer than this doesn't fit in the summary line: it counts as a preview. */
private const val LONG_TEXT_CHARS = 70

/** Active notifications module; tapping one opens its `contentIntent`. */
@Composable
fun NotificationsCard(modifier: Modifier = Modifier) {
    if (!rememberNotificationAccess()) {
        NotificationAccessPrompt("Permite el acceso a notificaciones para verlas aquí", modifier)
        return
    }
    val notifications by LauncherNotificationListener.notifications.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    // LazyColumn keeps the first visible card fixed when inserting above: without this, a
    // new notification would stay hidden above. Only scrolls up if already at the top.
    LaunchedEffect(notifications.firstOrNull()?.key) {
        if (listState.firstVisibleItemIndex <= 1) listState.animateScrollToItem(0)
    }

    BoxWithConstraints(modifier.fillMaxSize().padding(12.dp)) {
        // Short: one-line summary (count + most recent); normal: list of cards.
        if (maxHeight < 150.dp) {
            CompactSummary(notifications)
            return@BoxWithConstraints
        }
        Column(Modifier.fillMaxSize()) {
            Text(
                text = "Notificaciones",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (notifications.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Sin notificaciones", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(notifications, key = { it.key }) {
                        // Animated enter / exit / reorder.
                        NotificationItem(it, Modifier.animateItem())
                    }
                }
            }
        }
    }
}

/** One line: bell with count and title of the most recent one (tapping opens it). */
@Composable
private fun CompactSummary(notifications: List<StatusBarNotification>) {
    val context = LocalContext.current
    val latest = notifications.firstOrNull()
    val content = remember(latest?.key, latest?.postTime) { latest?.let { context.readContent(it) } }
    Row(
        Modifier.fillMaxSize().clickable(enabled = latest != null) { runCatching { latest?.notification?.contentIntent?.send() } },
        verticalAlignment = Alignment.CenterVertically,
        // Extra space: the counter sticks out to the right of the bell.
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        BadgedBox(badge = { if (notifications.isNotEmpty()) Badge { Text("${notifications.size}") } }) {
            Icon(Icons.Rounded.Notifications, contentDescription = null, modifier = Modifier.size(32.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = content?.title ?: "Sin notificaciones",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (content != null && content.text.isNotEmpty()) {
                Text(
                    text = content.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Notification content already extracted from its extras (shared by card and island). */
internal class NotificationContent(
    val appName: String,
    val appIcon: ImageBitmap?,
    val avatar: ImageBitmap?,
    val title: String,
    val text: String,
    /** Long text, inbox lines or conversation messages (empty if none). */
    val detail: String,
    val picture: ImageBitmap?,
    /** Progress declared by the app (trip, download, delivery…); `null` if none. */
    val progress: NotificationProgress? = null,
    /** Notification buttons ("Responder", "Ver pedido"…), exactly as the app defines them. */
    val actions: List<NotificationAction> = emptyList(),
    /**
     * The notification uses something we don't map (custom view, ProgressStyle, CallStyle, sports
     * scoreboard…): it is shown with the native rendering Android generates for it.
     */
    val native: Boolean = false,
) {
    val hasPreview: Boolean get() = picture != null || detail.isNotBlank() || progress != null || native || actions.isNotEmpty()
}

internal class NotificationProgress(val value: Int, val max: Int, val indeterminate: Boolean)

internal class NotificationAction(val title: String, val intent: android.app.PendingIntent?)

/**
 * A notification card. If it has a preview (big picture, long text, inbox or
 * conversation) and just arrived, it shows expanded for [PREVIEW_MS] ms with a draining
 * bar, then collapses on its own. The arrow expands/collapses it again; tapping opens it.
 */
@Composable
private fun NotificationItem(sbn: StatusBarNotification, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val content = remember(sbn.key, sbn.postTime) { context.readContent(sbn) }
    val fresh = remember(sbn.key, sbn.postTime) { System.currentTimeMillis() - sbn.postTime < FRESH_MS }

    var expanded by rememberSaveable(sbn.key, sbn.postTime) { mutableStateOf(false) }
    val countdown = remember(sbn.key, sbn.postTime) { Animatable(0f) }
    LaunchedEffect(sbn.key, sbn.postTime) {
        if (fresh && content.hasPreview) {
            expanded = true
            countdown.snapTo(1f)
            countdown.animateTo(0f, tween(PREVIEW_MS, easing = LinearEasing))
            expanded = false
        }
    }
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    val accent = MaterialTheme.colorScheme.primary

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { runCatching { sbn.notification.contentIntent?.send() } },
        shape = RoundedCornerShape(16.dp),
        // Lighter than the module (which already uses surfaceContainerHighest) so it reads as a card.
        color = MaterialTheme.colorScheme.surfaceBright,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Column(
            Modifier
                .padding(14.dp)
                // Preview countdown bar (drains until it collapses).
                .drawBehind {
                    if (countdown.value > 0f) {
                        val y = size.height + 10.dp.toPx()
                        drawLine(
                            color = accent,
                            start = Offset(0f, y),
                            end = Offset(size.width * countdown.value, y),
                            strokeWidth = 3.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                },
        ) {
            // Header: app · time ago.
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (content.appIcon != null) {
                    Image(content.appIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Text(
                    text = "${content.appName} · ${DateUtils.getRelativeTimeSpanString(sbn.postTime)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp).weight(1f),
                )
                if (content.hasPreview) {
                    IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.ExpandMore,
                            contentDescription = if (expanded) "Contraer" else "Expandir",
                            modifier = Modifier.graphicsLayer { rotationZ = chevron },
                        )
                    }
                }
            }

            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = content.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // Collapsed: one summary line. Expanded: full detail below.
                    AnimatedVisibility(visible = !expanded && content.text.isNotEmpty()) {
                        Text(
                            text = content.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (content.avatar != null) {
                    Image(
                        bitmap = content.avatar,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.padding(start = 12.dp).size(44.dp).clip(CircleShape),
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                exit = shrinkVertically(spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)) + fadeOut(),
            ) {
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (content.native) {
                        // Whatever we don't map is drawn by Android just like in its notification shade.
                        NativeNotificationView(sbn)
                    } else {
                    Text(
                        text = content.detail.ifBlank { content.text },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (content.picture != null) {
                        Image(
                            bitmap = content.picture,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black),
                        )
                    }
                    content.progress?.let { ProgressBar(it) }
                    }
                    if (content.actions.isNotEmpty()) ActionsRow(content.actions)
                }
            }
        }
    }
}

/** Extracts from the extras what the card shows (each style stores the detail in a different place). */
@Suppress("DEPRECATION") // getParcelable without class: the typed variant is API 33+.
internal fun Context.readContent(sbn: StatusBarNotification): NotificationContent {
    val extras = sbn.notification.extras
    val pm = packageManager
    val appName = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString() }
        .getOrDefault(sbn.packageName)
    val appIcon = runCatching { pm.getApplicationIcon(sbn.packageName).toImageBitmap(dpInt(18f)) }.getOrNull()
    val avatar = runCatching { sbn.notification.getLargeIcon()?.loadDrawable(this)?.toBitmap()?.asImageBitmap() }.getOrNull()

    val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
    val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
    val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString("\n").orEmpty()
    // Message parsing is public from API 30; older versions fall back to EXTRA_TEXT.
    val messages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            ?.let { Notification.MessagingStyle.Message.getMessagesFromBundleArray(it) }
            ?.takeLast(4)
            ?.joinToString("\n") { m ->
                val who = m.senderPerson?.name ?: m.sender
                if (who != null) "$who: ${m.text}" else m.text.toString()
            }
            .orEmpty()
    } else {
        ""
    }

    val picture = (extras.getParcelable<Parcelable>(Notification.EXTRA_PICTURE) as? Bitmap)
        ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (extras.getParcelable<Parcelable>(Notification.EXTRA_PICTURE_ICON) as? Icon)
                ?.let { runCatching { it.loadDrawable(this)?.toBitmap() }.getOrNull() }
        } else {
            null
        }

    return NotificationContent(
        appName = appName,
        appIcon = appIcon,
        avatar = avatar,
        title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString().orEmpty(),
        text = text,
        detail = listOf(bigText, lines, messages).firstOrNull { it.isNotBlank() }
            ?: text.takeIf { it.length > LONG_TEXT_CHARS || '\n' in it }.orEmpty(),
        picture = picture?.asImageBitmap(),
        progress = extras.progress(),
        actions = sbn.notification.actions.orEmpty().take(3).mapNotNull { a ->
            a.title?.toString()?.takeIf { it.isNotBlank() }?.let { NotificationAction(it, a.actionIntent) }
        },
        native = sbn.notification.needsNativeView(),
    )
}

/** Styles the card draws itself; any other uses the native view. */
private val MAPPED_TEMPLATES = setOf(
    "android.app.Notification\$BigTextStyle",
    "android.app.Notification\$BigPictureStyle",
    "android.app.Notification\$InboxStyle",
    "android.app.Notification\$MessagingStyle",
)

@Suppress("DEPRECATION") // contentView/bigContentView: the way to tell whether it has a custom view.
private fun Notification.needsNativeView(): Boolean {
    val template = extras.getString(Notification.EXTRA_TEMPLATE)
    val custom = contentView != null || bigContentView != null
    return custom || (template != null && template !in MAPPED_TEMPLATES)
}

private fun android.os.Bundle.progress(): NotificationProgress? {
    val max = getInt(Notification.EXTRA_PROGRESS_MAX, 0)
    val indeterminate = getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
    if (max <= 0 && !indeterminate) return null
    return NotificationProgress(getInt(Notification.EXTRA_PROGRESS, 0), max, indeterminate)
}

/**
 * Native notification view: the same RemoteViews Android builds for the notification
 * shade (includes the app's custom views, scoreboards, trips, ProgressStyle…).
 */
@Composable
private fun NativeNotificationView(sbn: StatusBarNotification) {
    androidx.compose.ui.viewinterop.AndroidView(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
        factory = { ctx ->
            val frame = android.widget.FrameLayout(ctx)
            runCatching {
                val builder = Notification.Builder.recoverBuilder(ctx, sbn.notification)
                @Suppress("DEPRECATION")
                val views = builder.createBigContentView() ?: builder.createContentView()
                frame.addView(views.apply(ctx, frame))
            }
            frame
        },
    )
}

@Composable
private fun ProgressBar(p: NotificationProgress) {
    if (p.indeterminate) {
        androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
    } else {
        androidx.compose.material3.LinearProgressIndicator(
            progress = { (p.value.toFloat() / p.max).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The app's action buttons (sending their PendingIntent is equivalent to tapping them in the shade). */
@Composable
private fun ActionsRow(actions: List<NotificationAction>) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.forEach { action ->
            androidx.compose.material3.FilledTonalButton(onClick = { runCatching { action.intent?.send() } }) {
                Text(action.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
