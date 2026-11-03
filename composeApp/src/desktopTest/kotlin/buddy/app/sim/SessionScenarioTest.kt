package buddy.app.sim

import buddy.app.Phase
import buddy.app.Sound
import buddy.core.Rules
import buddy.core.Stats
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** End-to-end session/break/leave flows through the real [buddy.app.AppController]. */
class SessionScenarioTest {

    private fun stats(h: Harness) =
        Stats(h.state(), TimeZone.currentSystemDefault(), h.controller.now())

    @Test
    fun countdownHappyPathRewardsThenOffersBreakThenEnds() {
        Harness().use { h ->
            h.controller.updateSettings { it.copy(screenOn = true, music = true, sfx = true) }
            h.controller.startSession(25, countUp = false, tag = Rules.DEFAULT_TAG, deepFocus = false)
            assertIs<Phase.Running>(h.phase())
            assertEquals(Sound.START, h.platform.sounds.firstOrNull())
            assertTrue(h.platform.ambientPlaying)
            assertTrue(h.platform.screenOn)

            // Walk the full 25 minutes; the ticker completes at the planned end.
            h.advance(25 * 60_000L)

            val rewarded = assertIs<Phase.Rewarded>(h.phase())
            assertEquals(30, rewarded.acorns)          // 25 + 20 %
            assertEquals(25, rewarded.minutes)
            assertEquals(30, h.state().acorns)
            assertEquals(30, h.state().earnedTotal)
            assertEquals(1, h.state().sessions.size)
            val recorded = h.state().sessions.single().session
            assertTrue(recorded.completed)
            assertEquals(25, recorded.plannedMin)
            assertEquals(25 * 60L, recorded.focusedSec)
            assertFalse(h.platform.ambientPlaying)
            assertFalse(h.platform.screenOn)
            assertTrue(h.platform.sounds.contains(Sound.COMPLETE))
            assertEquals(1, h.platform.callsNamed("onSessionEnded").size)

            h.controller.dismissResult()
            assertIs<Phase.BreakOffer>(h.phase())

            h.controller.startBreak(5)
            val onBreak = assertIs<Phase.OnBreak>(h.phase())
            assertEquals(h.clock.now + 5 * 60_000L, onBreak.endsAt)
            assertEquals(1, h.platform.callsNamed("onBreakStarted").size)

            h.advance(5 * 60_000L)
            assertIs<Phase.Idle>(h.phase())
            assertEquals(1, h.platform.callsNamed("onBreakEnded").size)
            assertTrue(h.platform.sounds.contains(Sound.BREAK_END))
        }
    }

    @Test
    fun statsCountsTheCompletedSession() {
        Harness().use { h ->
            h.controller.startSession(25, countUp = false, tag = Rules.DEFAULT_TAG, deepFocus = false)
            h.advance(25 * 60_000L)
            val s = stats(h)
            assertEquals(1, s.totalSessions)
            assertEquals(25, s.totalMinutes)
            assertEquals(1, s.currentStreak)
            assertEquals(1, s.bestStreak)
        }
    }

    @Test
    fun freeCancelWindowRecordsNothing() {
        Harness().use { h ->
            h.controller.startSession(25, countUp = false, tag = Rules.DEFAULT_TAG, deepFocus = false)
            h.advance(9_000)
            assertEquals(1, h.controller.freeCancelLeft((h.phase() as Phase.Running).session))
            h.controller.stopSession()

            assertIs<Phase.Idle>(h.phase())
            assertTrue(h.state().sessions.isEmpty())
            assertEquals(0, h.state().acorns)
            assertEquals(0, h.lineCount())
            assertEquals(1, h.platform.callsNamed("onSessionEnded").size)
            assertFalse(h.platform.sounds.contains(Sound.COMPLETE))
        }
    }

    @Test
    fun givingUpBeforeTenPercentLeavesNoTrace() {
        Harness().use { h ->
            h.controller.startSession(25, countUp = false, tag = Rules.DEFAULT_TAG, deepFocus = false)
            h.advance(11_000)
            assertEquals(0, h.controller.freeCancelLeft((h.phase() as Phase.Running).session))
            h.controller.stopSession()

            assertIs<Phase.Idle>(h.phase())
            assertEquals(0, h.state().acorns)
            assertTrue(h.state().sessions.isEmpty())
            assertEquals(0, stats(h).abandoned)
        }
    }

    @Test
    fun givingUpAfterTenPercentRecordsAnAbandonedSession() {
        Harness().use { h ->
            // 10 % of 25 minutes is 150 s; six minutes is well past it and over the 5 minute minimum.
            h.controller.startSession(25, countUp = false, tag = Rules.DEFAULT_TAG, deepFocus = false)
            h.advance(6 * 60_000L)
            h.controller.stopSession()

            val interrupted = assertIs<Phase.Interrupted>(h.phase())
            assertEquals(6, interrupted.focusedMin)
            assertEquals(6, h.state().acorns)  // focused minutes, no completion bonus
            assertEquals(1, h.state().sessions.size)
            assertFalse(h.state().sessions.single().session.completed)
            assertEquals(1, stats(h).abandoned)
        }
    }

    @Test
    fun exactlyTenSecondsIsNoLongerFree() {
        Harness().use { h ->
            h.controller.startSession(25, countUp = false, tag = Rules.DEFAULT_TAG, deepFocus = false)
            h.advance(10_000)
            assertEquals(0, h.controller.freeCancelLeft((h.phase() as Phase.Running).session))
            h.controller.stopSession()
            // Ten seconds of a 25 minute plan is under 10 %, so nothing is recorded.
            assertIs<Phase.Idle>(h.phase())
            assertTrue(h.state().sessions.isEmpty())
        }
    }

    @Test
    fun countUpOverFiveMinutesEarnsPerMinute() {
        Harness().use { h ->
            h.controller.startSession(25, countUp = true, tag = Rules.DEFAULT_TAG, deepFocus = false)
            h.advance(5 * 60_000L)
            assertIs<Phase.Running>(h.phase())
            h.controller.stopSession()

            val rewarded = assertIs<Phase.Rewarded>(h.phase())
            assertEquals(6, rewarded.acorns)   // 5 focused + ceil(20 %)
            assertEquals(5, rewarded.minutes)
            val recorded = h.state().sessions.single().session
            assertTrue(recorded.completed)
            assertEquals(0, recorded.plannedMin)
            assertEquals(1, stats(h).totalSessions)
            assertEquals(5, stats(h).totalMinutes)
        }
    }

    @Test
    fun countUpUnderFiveMinutesEarnsNothing() {
        Harness().use { h ->
            h.controller.startSession(25, countUp = true, tag = Rules.DEFAULT_TAG, deepFocus = false)
            h.advance(4 * 60_000L + 59_000L)
            h.controller.stopSession()

            val interrupted = assertIs<Phase.Interrupted>(h.phase())
            assertEquals(4, interrupted.focusedMin)
            assertEquals(0, h.state().acorns)
            assertEquals(0, h.state().earnedTotal)
            assertEquals(1, h.state().sessions.size)
            assertEquals(0, stats(h).totalSessions)
            assertEquals(0, stats(h).totalMinutes)
        }
    }

    @Test
    fun leavingKeepsTheTimerRunning() {
        Harness().use { h ->
            h.controller.startSession(25, countUp = false, tag = Rules.DEFAULT_TAG, deepFocus = false)
            h.advance(60_000)
            h.controller.onAppBackgrounded()
            // Researching in Chrome or taking notes does not touch the session.
            val away = assertIs<Phase.Running>(h.phase())
            assertEquals(null, away.session.awaySince)

            h.advance(10 * 60_000L)
            assertIs<Phase.Running>(h.phase())
            h.controller.onAppForegrounded()
            h.advance(14 * 60_000L)
            val rewarded = assertIs<Phase.Rewarded>(h.phase())
            assertEquals(30, rewarded.acorns)
            assertEquals(25 * 60L, h.state().sessions.single().session.focusedSec)
        }
    }

    @Test
    fun switchingAppsBrieflyKeepsCounting() {
        Harness().use { h ->
            h.controller.startSession(25, countUp = false, tag = Rules.DEFAULT_TAG, deepFocus = false)
            h.advance(60_000)
            h.controller.onAppBackgrounded()
            h.advance(10_000)
            h.controller.onAppForegrounded()
            assertIs<Phase.Running>(h.phase())

            h.advance(24 * 60_000L)
            assertIs<Phase.Rewarded>(h.phase())
            assertEquals(25 * 60L, h.state().sessions.single().session.focusedSec)
        }
    }

    @Test
    fun theSessionCompletesWhileTheUserIsInAnotherApp() {
        Harness().use { h ->
            h.controller.startSession(25, countUp = false, tag = Rules.DEFAULT_TAG, deepFocus = false)
            h.advance(60_000)
            h.controller.onAppBackgrounded()
            h.advance(25 * 60_000L)
            // The planned end arrives while Chrome is open; the session counts it.
            val rewarded = assertIs<Phase.Rewarded>(h.phase())
            assertEquals(30, rewarded.acorns)
            assertEquals(25 * 60L, h.state().sessions.single().session.focusedSec)
        }
    }

    @Test
    fun deepFocusBackgroundingIsNotAnInterruption() {
        Harness().use { h ->
            h.controller.startSession(25, countUp = false, tag = Rules.DEFAULT_TAG, deepFocus = true)
            h.advance(60_000)
            h.controller.onAppBackgrounded()

            val running = assertIs<Phase.Running>(h.phase())
            assertEquals(null, running.session.awaySince)
            assertTrue(h.platform.callsNamed("onSessionPaused").isEmpty())
            assertEquals(0, h.lineCount())
        }
    }

    @Test
    fun desktopPlatformDoesNotInterruptWhenLeaving() {
        Harness(leavingAppInterrupts = false).use { h ->
            h.controller.startSession(25, countUp = false, tag = Rules.DEFAULT_TAG, deepFocus = false)
            h.advance(60_000)
            h.controller.onAppBackgrounded()
            assertIs<Phase.Running>(h.phase())
            assertTrue(h.platform.callsNamed("onSessionPaused").isEmpty())
        }
    }

    @Test
    fun stoppingWhileIdleIsANoOp() {
        Harness().use { h ->
            h.controller.stopSession()
            h.controller.onAppBackgrounded()
            h.controller.onAppForegrounded()
            assertIs<Phase.Idle>(h.phase())
            assertEquals(0, h.lineCount())
        }
    }

    @Test
    fun startingASecondSessionWhileRunningIsIgnored() {
        Harness().use { h ->
            h.controller.startSession(25, countUp = false, tag = Rules.DEFAULT_TAG, deepFocus = false)
            h.controller.startSession(50, countUp = false, tag = Rules.DEFAULT_TAG, deepFocus = false)
            val running = assertIs<Phase.Running>(h.phase())
            assertEquals(25, running.session.plannedMin)
        }
    }

    @Test
    fun sessionResumesAfterProcessRestart() {
        val first = Harness()
        first.controller.startSession(25, countUp = false, tag = Rules.DEFAULT_TAG, deepFocus = false)
        first.advance(5 * 60_000L)
        val second = first.restart()
        try {
            val running = assertIs<Phase.Running>(second.phase())
            assertEquals(first.clock.now - 5 * 60_000L, running.session.startedAt)
            assertTrue(second.platform.callsNamed("onSessionStarted").isNotEmpty())

            second.advance(20 * 60_000L)
            assertIs<Phase.Rewarded>(second.phase())
            assertEquals(30, second.state().acorns)
        } finally {
            first.close(deleteDir = false)
            second.close()
        }
    }

    @Test
    fun leavingJustBeforeTheEndStillCompletes() {
        Harness().use { h ->
            h.controller.startSession(25, countUp = false, tag = Rules.DEFAULT_TAG, deepFocus = false)
            h.advance(25 * 60_000L - 5_000)
            h.controller.onAppBackgrounded()
            h.advance(5_000)
            val rewarded = assertIs<Phase.Rewarded>(h.phase())
            assertEquals(30, rewarded.acorns)
            assertEquals(25 * 60L, h.state().sessions.single().session.focusedSec)
        }
    }

    @Test
    fun breakCanBeSkipped() {
        Harness().use { h ->
            h.controller.startSession(25, countUp = false, tag = Rules.DEFAULT_TAG, deepFocus = false)
            h.advance(25 * 60_000L)
            assertIs<Phase.Rewarded>(h.phase())
            h.controller.dismissResult()
            assertIs<Phase.BreakOffer>(h.phase())
            h.controller.dismissResult()
            assertIs<Phase.Idle>(h.phase())
            assertEquals(0, h.platform.callsNamed("onBreakStarted").size)
        }
    }
}
