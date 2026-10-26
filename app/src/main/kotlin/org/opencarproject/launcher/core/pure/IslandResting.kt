package org.opencarproject.launcher.core.pure

/** What the island's resting bubble shows: an ongoing call, the music playing or the last notification. */
enum class IslandIndicator { CALL, MEDIA, NOTIFICATION }

/**
 * Picks the island's resting bubble: an ongoing call first, then the music playing, then the
 * newest notification. Ids are those of the candidates; the one the user swiped away is skipped
 * so the next in line shows instead.
 */
object IslandResting {

    fun pick(callId: String?, playingMediaId: String?, newestNotificationId: String?, swipedAwayId: String?): IslandIndicator? =
        listOf(
            IslandIndicator.CALL to callId,
            IslandIndicator.MEDIA to playingMediaId,
            IslandIndicator.NOTIFICATION to newestNotificationId,
        ).firstOrNull { (_, id) -> id != null && id != swipedAwayId }?.first
}
