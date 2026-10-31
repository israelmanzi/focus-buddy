package buddy.app

import buddy.app.data.RunningSession
import buddy.core.Rules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SessionLogicTest {
    private fun rs(startedAt: Long, plannedMin: Int, countUp: Boolean = false, awaySince: Long? = null) =
        RunningSession(startedAt, plannedMin, countUp, Rules.DEFAULT_TAG, deepFocus = false, awaySince = awaySince)

    @Test
    fun countdownCompletesExactlyAtPlannedEnd() {
        val s = rs(1_000, 25)
        assertEquals(SessionTick.Continue, SessionLogic.tick(s, 1_000 + 1_499_999, 20))
        assertEquals(SessionTick.Complete(1_000 + 1_500_000), SessionLogic.tick(s, 1_000 + 1_500_000, 20))
    }

    @Test
    fun awayPastGraceAbandonsAtTheMomentOfLeaving() {
        val s = rs(0, 25, awaySince = 100_000)
        assertEquals(SessionTick.Continue, SessionLogic.tick(s, 119_999, 20))
        assertEquals(SessionTick.Abandon(100_000), SessionLogic.tick(s, 120_000, 20))
        // Once the grace deadline has passed, still away: abandoned even if the plan had more time.
        assertEquals(SessionTick.Abandon(100_000), SessionLogic.tick(s, 1_400_000, 20))
    }

    @Test
    fun plannedEndInsideTheGraceWindowStillCompletes() {
        val s = rs(0, 25, awaySince = 1_490_000) // grace until 1_510_000, plan ends 1_500_000
        assertEquals(SessionTick.Complete(1_500_000), SessionLogic.tick(s, 1_500_000, 20))
        // The completion branch is checked first, so a dead process past the deadline still
        // gets the completed session it earned inside the grace window.
        assertEquals(SessionTick.Complete(1_500_000), SessionLogic.tick(s, 1_700_000, 20))
    }

    @Test
    fun countUpStopsAtTheCapEvenAfterAHugeClockJump() {
        val s = rs(0, 0, countUp = true)
        assertEquals(SessionTick.Continue, SessionLogic.tick(s, Rules.MAX_MINUTES * 60_000L - 1, 20))
        assertEquals(
            SessionTick.Complete(Rules.MAX_MINUTES * 60_000L),
            SessionLogic.tick(s, 10L * 365 * 86_400_000, 20),
        )
    }

    @Test
    fun freeCancelWindowIsClampedWhenTheClockJumpsBackwards() {
        val s = rs(1_000_000, 25)
        assertEquals(10, SessionLogic.freeCancelLeft(s, 1_000_000))
        assertEquals(1, SessionLogic.freeCancelLeft(s, 1_009_999))
        assertEquals(0, SessionLogic.freeCancelLeft(s, 1_010_000))
        assertEquals(0, SessionLogic.freeCancelLeft(s, 2_000_000))
        // Clock moved back an hour: still ten seconds, not an endless free cancel.
        assertEquals(10, SessionLogic.freeCancelLeft(s, 1_000_000 - 3_600_000))
    }

    @Test
    fun freeCancelCountsWholeSeconds() {
        val s = rs(0, 25)
        assertTrue(SessionLogic.freeCancelLeft(s, 9_999) == 1)
        assertTrue(SessionLogic.freeCancelLeft(s, 10_000) == 0)
    }
}
