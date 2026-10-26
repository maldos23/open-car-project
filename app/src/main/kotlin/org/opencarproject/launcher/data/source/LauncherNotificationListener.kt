package org.opencarproject.launcher.data.source

import android.app.Notification
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Listens to system notifications. Enabling it also grants access to other apps'
 * MediaSessions (`MediaSessionManager.getActiveSessions` requires an enabled listener).
 *
 * The system instantiates the service, not the AppContainer; that's why state lives in the companion.
 */
class LauncherNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        // Ones already present must not "pop" in the island on connect.
        alerted.clear()
        active.clear()
        runCatching { activeNotifications.orEmpty().forEach { active[it.key] = it } }
        alerted += active.keys
        publish()
        MediaSessionSource.onListenerConnected(this)
    }

    override fun onListenerDisconnected() {
        active.clear()
        publish()
        // The system may unbind the listener (low memory, doze, app update) and not bind it back
        // on its own: without this, no notification or media session reaches the launcher again.
        runCatching { requestRebind(ComponentName(this, LauncherNotificationListener::class.java)) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        // The posted notification is applied to the local copy: asking the system for the whole
        // list (a binder call) on every progress update would be wasteful.
        active[sbn.key] = sbn
        publish()
        if (!sbn.isUserFacing()) return
        // Only new ones alert, or updated ones that don't request "alert only once"
        // (so a download progress or an edited chat doesn't pop on every change).
        val isNew = alerted.add(sbn.key)
        if (isNew || sbn.notification.flags and Notification.FLAG_ONLY_ALERT_ONCE == 0) {
            _posted.tryEmit(sbn)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        alerted -= sbn.key
        active -= sbn.key
        publish()
    }

    /** Active notifications by key, kept in sync with the posted/removed callbacks. */
    private val active = LinkedHashMap<String, StatusBarNotification>()

    private fun publish() {
        // "Ongoing" ones (music, navigation) already have their own module. Group summaries
        // (e.g. the "ranker_group" Android creates when bundling several from one app) carry no content.
        _notifications.value = active.values.filter { it.isUserFacing() }.sortedByDescending { it.postTime }
        // Google Maps / Waze don't share their route; their guidance notification is the only public data.
        val ongoing = active.values.filter { it.isOngoing }
        _navigation.value = NAVIGATION_PACKAGES.firstNotNullOfOrNull { pkg -> ongoing.firstOrNull { it.packageName == pkg } }
        _call.value = ongoing.firstOrNull { it.notification.category == Notification.CATEGORY_CALL }
    }

    companion object {
        /** Notifications shown to the user (neither "ongoing" nor group summaries). */
        private fun StatusBarNotification.isUserFacing(): Boolean =
            !isOngoing && notification.flags and Notification.FLAG_GROUP_SUMMARY == 0

        /** Keys that already alerted, so the island isn't repeated on every update. */
        private val alerted = mutableSetOf<String>()

        private val _posted = MutableSharedFlow<StatusBarNotification>(extraBufferCapacity = 8)

        /**
         * Event for each incoming notification worth alerting (the top island).
         * No replay: if the launcher isn't visible, they don't pile up.
         */
        val posted: SharedFlow<StatusBarNotification> = _posted.asSharedFlow()

        /** Navigation apps in order of preference (must be in the manifest's `<queries>`). */
        val NAVIGATION_PACKAGES = listOf("com.waze", "com.google.android.apps.maps")

        private val _notifications = MutableStateFlow<List<StatusBarNotification>>(emptyList())
        val notifications: StateFlow<List<StatusBarNotification>> = _notifications.asStateFlow()

        private val _navigation = MutableStateFlow<StatusBarNotification?>(null)

        /** Active guidance notification (next turn, distance, arrival) or `null` if not navigating. */
        val navigation: StateFlow<StatusBarNotification?> = _navigation.asStateFlow()

        private val _call = MutableStateFlow<StatusBarNotification?>(null)

        /** Ongoing call notification (dialing or in a call), or `null` if there is none. */
        val call: StateFlow<StatusBarNotification?> = _call.asStateFlow()
    }
}
