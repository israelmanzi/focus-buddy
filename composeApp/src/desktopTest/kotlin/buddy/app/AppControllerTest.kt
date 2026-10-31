package buddy.app

import buddy.app.data.DeviceMeta
import buddy.app.data.RunningSession
import buddy.app.data.Store
import buddy.core.BuddyJson
import buddy.core.Event
import buddy.core.Payload
import buddy.core.Rules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppControllerTest {
    private lateinit var dir: File
    private lateinit var scope: CoroutineScope
    private var now = 1_000_000L

    private class FakePlatform(override val dataDir: File) : Platform {
        override val name = "test"
        override val leavingAppInterrupts = true
        var sessionsStarted = 0
        var sessionsEnded = 0
        var breaksStarted = 0
        var breaksEnded = 0

        override fun onSessionStarted(startedAtMs: Long, endsAtMs: Long?, deepFocus: Boolean, rules: BlockRules) {
            sessionsStarted++
        }
        override fun onSessionEnded() { sessionsEnded++ }
        override fun onBreakStarted(endsAtMs: Long) { breaksStarted++ }
        override fun onBreakEnded() { breaksEnded++ }
    }

    private fun controller(platform: Platform = FakePlatform(dir)) = AppController(platform, scope) { now }
    private fun session(min: Int) = Payload.Session(0, min * 60_000L, min, min * 60L, Rules.DEFAULT_TAG, completed = true)

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("buddy-controller-test").toFile()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        now = 1_000_000L
    }

    @AfterTest
    fun tearDown() {
        scope.cancel()
        dir.deleteRecursively()
    }

    @Test
    fun completesSessionThatEndedWhileTheProcessWasDead() {
        val started = 1_000_000L
        Store(dir).saveMeta(DeviceMeta("dev", running = RunningSession(started, 25, false, Rules.DEFAULT_TAG, false)))
        now = started + 30 * 60_000L
        val c = controller()
        val rec = c.state.value.sessions.single()
        assertTrue(rec.session.completed)
        assertEquals(25 * 60L, rec.session.focusedSec)
        assertEquals(started + 25 * 60_000L, rec.session.endedAt)
        assertEquals(30, c.state.value.acorns)
        assertTrue(c.phase.value is Phase.Rewarded)
    }

    @Test
    fun abandonsSessionWhoseGraceRanOutWhileDead() {
        val started = 1_000_000L
        val away = started + 100_000L
        Store(dir).saveMeta(
            DeviceMeta("dev", running = RunningSession(started, 25, false, Rules.DEFAULT_TAG, false, awaySince = away)),
        )
        now = started + 40 * 60_000L
        val c = controller()
        val rec = c.state.value.sessions.single()
        assertEquals(false, rec.session.completed)
        assertEquals(away, rec.session.endedAt)
        assertEquals(100L, rec.session.focusedSec)
        assertEquals(0, c.state.value.acorns)
        assertTrue(c.phase.value is Phase.Interrupted)
    }

    @Test
    fun completesSessionThatEndedInsideItsGraceWindowWhileDead() {
        val started = 1_000_000L
        val away = started + 25 * 60_000L - 10_000L // grace ends ten seconds after the planned end
        Store(dir).saveMeta(
            DeviceMeta("dev", running = RunningSession(started, 25, false, Rules.DEFAULT_TAG, false, awaySince = away)),
        )
        now = started + 60 * 60_000L
        val c = controller()
        val rec = c.state.value.sessions.single()
        assertTrue(rec.session.completed)
        assertEquals(started + 25 * 60_000L, rec.session.endedAt)
        assertEquals(30, c.state.value.acorns)
    }

    @Test
    fun resumingABreakRearmsThePlatformAlarm() {
        val endsAt = now + 5 * 60_000L
        Store(dir).saveMeta(DeviceMeta("dev", breakEndsAt = endsAt))
        val platform = FakePlatform(dir)
        val c = controller(platform)
        assertEquals(1, platform.breaksStarted)
        assertEquals(Phase.OnBreak(endsAt), c.phase.value)
    }

    @Test
    fun startingASessionCancelsAnActiveBreak() {
        val platform = FakePlatform(dir)
        val c = controller(platform)
        c.startBreak(5)
        assertTrue(c.phase.value is Phase.OnBreak)

        c.startSession(25, false, Rules.DEFAULT_TAG, false)
        assertEquals(1, platform.breaksEnded)
        assertNull(Store(dir).loadMeta().breakEndsAt)
        assertTrue(c.phase.value is Phase.Running)
    }

    @Test
    fun freeCancelUsesTheInjectedClockAndClampsBackwardsJumps() {
        val c = controller()
        c.startSession(25, false, Rules.DEFAULT_TAG, false)
        val rs = (c.phase.value as Phase.Running).session
        assertEquals(10, c.freeCancelLeft(rs))
        now += 10_000
        assertEquals(0, c.freeCancelLeft(rs))
        now -= 3_600_000
        assertEquals(10, c.freeCancelLeft(rs))
    }

    @Test
    fun stoppingAtThePlannedEndStillCompletes() {
        val c = controller()
        val started = now
        c.startSession(25, false, Rules.DEFAULT_TAG, false)
        // Beat the 500 ms ticker to the finish line.
        now = started + 25 * 60_000L
        c.stopSession()
        val rec = c.state.value.sessions.single()
        assertTrue(rec.session.completed)
        assertEquals(25 * 60L, rec.session.focusedSec)
        assertEquals(30, c.state.value.acorns)
        assertTrue(c.phase.value is Phase.Rewarded)
    }

    @Test
    fun ghostPendingIdsArePrunedOnStartup() {
        Store(dir).saveMeta(DeviceMeta("dev", pending = listOf("ghost")))
        controller()
        assertEquals(emptyList(), Store(dir).loadMeta().pending)
    }

    @Test
    fun importBackupDedupesIds() {
        val c = controller()
        val a = Event("same", "x", 1, session(100))
        val b = Event("same", "x", 2, session(100))
        val json = BuddyJson.encodeToString(ListSerializer(Event.serializer()), listOf(a, b))

        assertEquals(1, c.importBackup(json))
        assertEquals(120, c.state.value.acorns)
        assertEquals(1, c.state.value.sessions.size)
        assertEquals(1, Store(dir).loadEvents().size)
        assertEquals(1, Store(dir).loadMeta().pending.size)
    }
}
