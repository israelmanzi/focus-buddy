package buddy.core

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Regression tests for the event-log hardening pass (see docs/AUDIT.md). */
class HardeningTest {
    private var n = 0
    private fun ev(ts: Long, p: Payload, device: String = "a") = Event("e${n++}", device, ts, p)
    private fun session(
        min: Int,
        focusedSec: Long = min * 60L,
        plannedMin: Int = min,
        completed: Boolean = true,
        startedAt: Long = 0,
    ) = Payload.Session(startedAt, startedAt + focusedSec * 1000, plannedMin, focusedSec, Rules.DEFAULT_TAG, completed)

    @Test
    fun rewardCannotOverflowOrGoNegative() {
        assertEquals(432, Rules.reward(session(0, focusedSec = Long.MAX_VALUE, plannedMin = 0)))
        assertEquals(0, Rules.reward(session(0, focusedSec = Long.MIN_VALUE, plannedMin = 0)))
        assertEquals(0, Rules.reward(session(25, focusedSec = -60)))
    }

    @Test
    fun impossibleSessionsAreIgnoredByTheFold() {
        val events = listOf(
            ev(1, session(25, focusedSec = 1_500_000)),          // more focus than the plan
            ev(2, session(0, focusedSec = 360 * 60L + 1, plannedMin = 0)), // beyond the cap
            ev(3, Payload.Session(10_000, 5_000, 0, 0, Rules.DEFAULT_TAG, true)), // ends before it starts
            ev(4, session(25, plannedMin = 1)),                  // planned below the minimum
            ev(5, session(25, plannedMin = 999)),                // planned above the maximum
        )
        val s = GameState.fold(events)
        assertEquals(0, s.acorns)
        assertTrue(s.sessions.isEmpty())
    }

    @Test
    fun countUpSessionsAtTheCapAreStillValid() {
        val cap = Rules.MAX_MINUTES * 60L
        val s = GameState.fold(listOf(ev(1, session(0, focusedSec = cap, plannedMin = 0))))
        assertEquals(432, s.acorns)
        assertEquals(1, s.sessions.size)
    }

    @Test
    fun foldCountsDuplicateIdsOnceAndIndependentlyOfOrder() {
        val a = Event("dup", "a", 1, session(100))
        val b = Event("dup", "b", 2, session(100))
        val first = GameState.fold(listOf(a, b))
        val second = GameState.fold(listOf(b, a))
        assertEquals(first, second)
        assertEquals(120, first.acorns)
        assertEquals(1, first.sessions.size)
    }

    @Test
    fun styleCannotWearAnOutfitFromAnotherSlot() {
        val s = GameState.fold(listOf(
            ev(1, session(100)),
            ev(2, Payload.Buy("outfit:neck.scarf")),
            ev(3, Payload.Style("Mochi", "classic", hat = "neck.scarf")),
            ev(4, Payload.Style("Mochi", "classic", neck = "neck.scarf")),
        ))
        assertNull(s.style.hat)
        assertEquals("Mochi", s.style.name)
        assertEquals("neck.scarf", s.style.neck)
    }

    @Test
    fun byTagIgnoresFutureDatedSessions() {
        val now = 1_700_000_000_000L
        val valid = Payload.Session(now - 1_500_000, now, 25, 1_500L, "focus", completed = true)
        val future = Payload.Session(now + 1_000, now + 86_400_000, 25, 1_500L, "future", completed = true)
        val state = GameState.fold(listOf(
            Event("a", "d", 1, valid),
            Event("b", "d", 2, future),
        ))
        val stats = Stats(state, TimeZone.UTC, now)
        assertEquals(listOf("focus" to 25), stats.byTag(StatsRange.DAY))
        // Totals still include everything; only range breakdowns are bounded by "today".
        assertEquals(50, stats.totalMinutes)
    }
}
