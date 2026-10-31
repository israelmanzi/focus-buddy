package buddy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Deterministic folding: same events in any arrival order -> same state. */
class EventOrderingTest {

    private var seq = 0
    private fun event(ts: Long, payload: Payload, device: String = "a", id: String = "e${++seq}"): Event =
        Event(id, device, ts, payload)

    private fun session(ts: Long, minutes: Int = 100, device: String = "a"): Event =
        event(ts, Payload.Session(ts - minutes * 60_000L, ts, minutes, minutes * 60L, "focus", completed = true), device)

    @Test
    fun everyArrivalOrderProducesTheSameState() {
        val events = listOf(
            session(10, 300), // 360 acorns (the 6 h maximum)
            event(20, Payload.Buy("decor:rug.cozy.0")),
            event(30, Payload.Equip("living.rug", "decor:rug.cozy.0")),
            event(40, Payload.Tag("study", "Study", 0xFF112233)),
            event(50, Payload.Style("Mochi", "classic", null, null, null)),
            event(60, Payload.Buy("room:study")),
        )
        val expected = GameState.fold(events)
        // A handful of deterministic permutations, plus reversed arrival.
        listOf(
            events.reversed(),
            events.sortedBy { it.id },
            listOf(events[3], events[0], events[4], events[2], events[5], events[1]),
        ).forEach { arrival ->
            assertEquals(expected, GameState.fold(arrival))
        }
        assertTrue(expected.owns("decor:rug.cozy.0"))
        assertEquals("decor:rug.cozy.0", expected.equipped["living.rug"])
        assertTrue(expected.roomUnlocked("study"))
    }

    @Test
    fun equalTimestampsAreOrderedByEventId() {
        val session = session(0, 100)
        val first = event(1_000, Payload.Buy("decor:rug.cozy.0"), id = "a-buy")
        val second = event(1_000, Payload.Buy("decor:lamp.cozy.0"), id = "b-buy")
        val expected = GameState.fold(listOf(session, first, second))
        assertEquals(expected, GameState.fold(listOf(second, session, first)))
        assertTrue(expected.owns("decor:rug.cozy.0"))
        assertTrue(expected.owns("decor:lamp.cozy.0"))
    }

    @Test
    fun conflictingSameTimestampPurchasesResolveTheSameEverywhere() {
        val session = session(0, 125) // 150 acorns
        val mint = event(1_000, Payload.Buy("skin:mint"), device = "a", id = "aaa")
        val peach = event(1_000, Payload.Buy("skin:peach"), device = "b", id = "zzz")
        val oneWay = GameState.fold(listOf(session, mint, peach))
        val otherWay = GameState.fold(listOf(session, peach, mint))
        assertEquals(oneWay, otherWay)
        assertEquals(0, oneWay.acorns)
        assertEquals(1, listOf("skin:mint", "skin:peach").count { oneWay.owns(it) })
    }

    @Test
    fun purchaseCannotSpendAcornsFromAnEventWithALaterTimestamp() {
        // A device whose clock is behind stamps the buy before the earning session; the fold
        // (by design, sorted by timestamp) then drops the purchase. This is deterministic on
        // every device, but it is a UX bug - see the sync clock-skew test.
        val earning = session(10_000, 125, device = "a")
        val buy = event(5_000, Payload.Buy("skin:mint"), device = "b")
        val state = GameState.fold(listOf(earning, buy))
        assertFalse(state.owns("skin:mint"))
        assertEquals(150, state.acorns)
    }

    @Test
    fun abandonedSessionsHaveZeroRewardRegardlessOfLength() {
        val e = event(1, Payload.Session(0, 300 * 60_000L, 300, 300 * 60L, "focus", completed = false))
        val state = GameState.fold(listOf(e))
        assertEquals(0, state.acorns)
        assertEquals(1, state.sessions.size)
        assertEquals(0, state.sessions.single().reward)
    }

    @Test
    fun countUpSessionsUseFocusedSecondsForReward() {
        val five = event(1, Payload.Session(0, 300_000, 0, 300, "focus", completed = true))
        val four = event(2, Payload.Session(0, 299_000, 0, 299, "focus", completed = true))
        val state = GameState.fold(listOf(five, four))
        assertEquals(6, state.sessions.first { it.session.focusedSec == 300L }.reward)
        assertEquals(0, state.sessions.first { it.session.focusedSec == 299L }.reward)
        assertEquals(6, state.acorns)
    }

    @Test
    fun foldDoesNotMutateTheInputState() {
        val state = GameState()
        val after = state.apply(event(1, Payload.Buy("decor:rug.cozy.0")))
        assertEquals(GameState(), state)
        assertEquals(GameState(), after) // ignored: no funds
    }
}
