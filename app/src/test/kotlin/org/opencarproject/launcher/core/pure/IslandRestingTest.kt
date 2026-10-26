package org.opencarproject.launcher.core.pure

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.PI

class IslandRestingTest {

    @Test fun callBeatsMusicBeatsNotification() {
        assertEquals(IslandIndicator.CALL, IslandResting.pick("call", "song", "msg", null))
        assertEquals(IslandIndicator.MEDIA, IslandResting.pick(null, "song", "msg", null))
        assertEquals(IslandIndicator.NOTIFICATION, IslandResting.pick(null, null, "msg", null))
        assertNull(IslandResting.pick(null, null, null, null))
    }

    @Test fun swipedAwayGivesWayToTheNext() {
        assertEquals(IslandIndicator.NOTIFICATION, IslandResting.pick(null, "song", "msg", "song"))
        assertNull(IslandResting.pick(null, null, "msg", "msg"))
    }

    @Test fun audioBarsStayInRangeAndLoop() {
        for (i in 0..3) {
            for (step in 0..100) {
                val t = (2 * PI * step / 100).toFloat()
                val level = AudioBarsMath.level(t, i)
                assert(level in 0f..1f) { "bar $i at $t = $level" }
            }
            assertEquals(AudioBarsMath.level(0f, i), AudioBarsMath.level((2 * PI).toFloat(), i), 1e-4f)
        }
    }
}
