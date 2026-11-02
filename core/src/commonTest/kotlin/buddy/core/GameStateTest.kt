package buddy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameStateTest {
    private var n = 0
    private fun ev(ts: Long, p: Payload, device: String = "a") = Event("e${n++}", device, ts, p)
    private fun session(min: Int, completed: Boolean = true) =
        Payload.Session(0, min * 60_000L, min, min * 60L, Rules.DEFAULT_TAG, completed)

    @Test fun rewardsCompletedSessionsOnly() {
        assertEquals(6, Rules.reward(session(5)))
        assertEquals(30, Rules.reward(session(25)))
        assertEquals(0, Rules.reward(session(25, completed = false)))
        assertEquals(0, Rules.reward(session(4)))
    }

    @Test fun purchasesNeedFundsAndAreIdempotent() {
        val s = GameState.fold(listOf(
            ev(1, Payload.Buy("decor:rug.cozy.0")),        // too poor: ignored
            ev(2, session(100)),                            // +120
            ev(3, Payload.Buy("decor:rug.cozy.0")),        // -60
            ev(4, Payload.Buy("decor:rug.cozy.0")),        // already owned: ignored
        ))
        assertEquals(60, s.acorns)
        assertTrue(s.owns("decor:rug.cozy.0"))
    }

    @Test fun equipRequiresOwnershipKindAndUnlockedRoom() {
        val s = GameState.fold(listOf(
            ev(1, session(200)),
            ev(2, Payload.Buy("decor:lamp.retro.1")),
            ev(3, Payload.Equip("living.lamp", "decor:lamp.retro.1")),
            ev(4, Payload.Equip("living.rug", "decor:lamp.retro.1")),  // wrong kind
            ev(5, Payload.Equip("study.lamp", "decor:lamp.retro.1")),  // room locked
        ))
        assertEquals("decor:lamp.retro.1", s.equipped["living.lamp"])
        assertEquals("decor:rug.basic.0", s.equipped["living.rug"])
        assertFalse("study.lamp" in s.equipped)
    }

    @Test fun foldIsOrderIndependent() {
        val evs = listOf(ev(3, Payload.Buy("skin:mint"), "b"), ev(1, session(150), "a"), ev(2, Payload.Buy("room:study"), "a"))
        assertEquals(GameState.fold(evs), GameState.fold(evs.reversed()))
    }

    @Test fun revokedSessionsAreIgnoredInEitherOrder() {
        val session = ev(1, session(100))
        val revoke = ev(2, Payload.Revoke(session.id))
        val forward = GameState.fold(listOf(session, revoke))
        val backward = GameState.fold(listOf(revoke, session))
        assertEquals(0, forward.acorns)
        assertTrue(forward.sessions.isEmpty())
        assertEquals(forward, backward)
    }

    @Test fun revokingAnUnknownIdIsHarmless() {
        val s = GameState.fold(listOf(ev(1, session(50)), ev(2, Payload.Revoke("nope"))))
        assertEquals(1, s.sessions.size)
    }

    @Test fun eventsRoundTripThroughJson() {
        val e = ev(1, session(25))
        assertEquals(e, BuddyJson.decodeFromString(Event.serializer(), BuddyJson.encodeToString(Event.serializer(), e)))
    }
}
