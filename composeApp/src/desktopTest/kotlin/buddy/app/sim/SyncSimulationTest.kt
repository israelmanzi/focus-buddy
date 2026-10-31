package buddy.app.sim

import buddy.app.SyncStatus
import buddy.core.BuddyJson
import buddy.core.Event
import buddy.core.Payload
import buddy.core.Rules
import buddy.server.EventStore
import buddy.server.buddyModule
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * True end-to-end sync: the real Ktor/SQLite server listens on an ephemeral port and two
 * [buddy.app.AppController] instances talk to it through the production OkHttp client.
 */
class SyncSimulationTest {

    private val token = "sync-simulation-token-0123456789"
    private lateinit var tmp: File
    private lateinit var store: EventStore
    private lateinit var server: ApplicationEngine
    private lateinit var url: String

    @BeforeTest
    fun startServer() {
        tmp = Files.createTempDirectory("buddy-sync-sim").toFile()
        store = EventStore(File(tmp, "events.db").path)
        val embedded = embeddedServer(CIO, port = 0, host = "127.0.0.1") { buddyModule(token, store) }
        server = embedded.start(wait = false).engine
        val port = runBlocking { server.resolvedConnectors().first().port }
        url = "http://127.0.0.1:$port"
    }

    @AfterTest
    fun stopServer() {
        server.stop(100, 1_000)
        store.close()
        tmp.deleteRecursively()
    }

    private fun device(startMs: Long = Harness.DEFAULT_START): Harness {
        val h = Harness(startMs = startMs)
        h.controller.updateSettings {
            it.copy(syncUrl = url, syncToken = token, autoSync = false)
        }
        return h
    }

    private fun sync(vararg devices: Harness) = runBlocking {
        for (d in devices) d.controller.syncNow()
    }

    private fun assertSynced(h: Harness) {
        val status = h.controller.sync.value
        assertIs<SyncStatus.Ok>(status, "sync failed: $status")
    }

    @Test
    fun twoDevicesEndInAnIdenticalState() {
        val a = device()
        val b = device()
        try {
            // Device A focuses, then spends the reward.
            a.controller.startSession(25, countUp = false, tag = Rules.DEFAULT_TAG, deepFocus = false)
            a.advance(25 * 60_000L)
            a.controller.dismissResult()
            a.controller.dismissResult()
            a.advance(1)
            assertTrue(a.controller.buy("decor:lamp.basic.0")) // 30 acorns
            a.advance(1)
            a.controller.equip("living.lamp", "decor:lamp.basic.0")
            sync(a)
            assertSynced(a)

            sync(b)
            assertSynced(b)
            assertEquals(a.state(), b.state())
            assertEquals(0, b.state().acorns)
            assertEquals("decor:lamp.basic.0", b.state().equipped["living.lamp"])

            // Device B adds a count-up session; both converge again.
            b.controller.startSession(25, countUp = true, tag = Rules.DEFAULT_TAG, deepFocus = false)
            b.advance(10 * 60_000L)
            b.controller.stopSession()
            assertIs<buddy.app.Phase.Rewarded>(b.phase())
            sync(b, a)
            assertSynced(b)
            assertSynced(a)
            assertEquals(12, a.state().acorns)
            assertEquals(a.state(), b.state())
            assertEquals(2, b.state().sessions.size)
            assertEquals(4, a.loadEventsFromDisk().distinctBy { it.id }.size)
            assertEquals(a.loadEventsFromDisk().map { it.id }.toSet(), b.loadEventsFromDisk().map { it.id }.toSet())
        } finally {
            a.close(); b.close()
        }
    }

    @Test
    fun concurrentConflictingPurchasesConvergeDeterministically() {
        val a = device()
        val b = device()
        try {
            a.seedSession(125) // 150 acorns
            sync(a)
            sync(b)
            assertEquals(150, b.state().acorns)

            // Both devices spend everything at the same instant on different skins.
            assertTrue(a.controller.buy("skin:mint"))
            assertTrue(b.controller.buy("skin:peach"))

            sync(a, b, a, b)
            assertEquals(a.state(), b.state())
            assertEquals(0, a.state().acorns)
            assertEquals(1, listOf("skin:mint", "skin:peach").count { a.state().owns(it) })
            assertEquals(1, a.state().sessions.size)
        } finally {
            a.close(); b.close()
        }
    }

    @Test
    fun clientLoopsThroughMoreThanFiveThousandEvents() {
        val total = 5_005
        val seeded = (1..total).map { i ->
            Event(
                id = "seed-$i",
                device = "seed-device",
                ts = i.toLong(),
                payload = Payload.Session(
                    startedAt = i.toLong(),
                    endedAt = i.toLong(),
                    plannedMin = 0,
                    focusedSec = 0,
                    tag = Rules.DEFAULT_TAG,
                    completed = false,
                ),
            )
        }
        val stored = seeded.map { it.id to BuddyJson.encodeToString(Event.serializer(), it) }
        store.sync(0, stored, 100_000) // seed the server DB directly

        val c = device()
        try {
            sync(c)
            assertSynced(c)
            assertEquals(total, c.state().sessions.size)
            assertEquals(total, c.lineCount())
            assertEquals(total, c.loadEventsFromDisk().distinctBy { it.id }.size)
            assertEquals(0, c.state().acorns)

            // A second sync must not duplicate anything.
            sync(c)
            assertSynced(c)
            assertEquals(total, c.lineCount())
            assertEquals(total, c.state().sessions.size)
        } finally {
            c.close()
        }
    }

    @Test
    fun clientStartsFromItsPersistedCursorAfterRestart() {
        val a = device()
        val b = device()
        try {
            a.seedSession(25)
            sync(a)
            sync(b)
            assertEquals(a.state(), b.state())
            assertEquals(30, b.state().acorns)
            val linesBeforeRestart = b.lineCount()

            // Restart device B against the same data dir; the persisted cursor/settings survive.
            val b2 = b.restart()
            b.close(deleteDir = false)
            try {
                assertEquals(url, b2.controller.settings.value.syncUrl)
                sync(b2)
                assertSynced(b2)
                assertEquals(a.state(), b2.state())
                assertEquals(linesBeforeRestart, b2.lineCount())
            } finally {
                b2.close()
            }
        } finally {
            a.close()
        }
    }

    /**
     * BUG: the server deliberately stores unknown payload types opaquely so it need not be
     * redeployed for new event types, but the client's sealed [buddy.core.Payload] cannot decode
     * them. One such event makes the whole sync response fail; the cursor never advances, so the
     * device retries the same page and stays offline forever.
     */
    @Test
    fun unknownPayloadTypesDoNotBlockSync() {
        val known = Event(
            id = "known-tag",
            device = "seed-device",
            ts = 1,
            payload = Payload.Tag("study", "Study", 0xFF6C9BD2),
        )
        val unknown = """{"id":"future-event","device":"seed-device","ts":2,"payload":{"type":"future","x":1}}"""
        store.sync(
            0,
            listOf(
                known.id to BuddyJson.encodeToString(Event.serializer(), known),
                "future-event" to unknown,
            ),
            100_000,
        )

        val c = device()
        try {
            sync(c)
            val status = c.controller.sync.value
            // A retry must not be stuck on the same page either.
            sync(c)
            assertIs<SyncStatus.Ok>(
                status,
                "BUG: one unknown payload type failed sync ($status); the cursor cannot advance and every retry fails",
            )
            assertTrue(c.state().tags.containsKey("study"))
            assertIs<SyncStatus.Ok>(c.controller.sync.value)
        } finally {
            c.close()
        }
    }

    /**
     * BUG: the server dedupes by id (first write wins) but the client filters incoming events by
     * id without comparing payloads. If two devices ever hold different events under the same id,
     * each keeps its own copy and they never converge, contradicting the event-log contract.
     */
    @Test
    fun conflictingDuplicateIdsConverge() {
        val serverVersion = Event("dup-id", "seed-device", 1, Payload.Tag("from-server", "Server", 0xFF112233))
        store.sync(0, listOf("dup-id" to BuddyJson.encodeToString(Event.serializer(), serverVersion)), 100_000)

        val a = device()
        val b = device()
        try {
            val localVersion = Event("dup-id", a.controller.deviceId, 2, Payload.Tag("from-a", "Local", 0xFF445566))
            a.import(localVersion)

            sync(a, b)
            sync(a)
            assertEquals(
                b.state(), a.state(),
                "BUG: same id with different payloads never converges (A keeps its local event, B keeps the server's)",
            )
        } finally {
            a.close(); b.close()
        }
    }

    /**
     * BUG: a device with a slow clock buys with acorns earned by an event stamped in the future
     * (relative to that device). The purchase is accepted locally by [AppController.buy] but the
     * fold orders it before the earning event and silently drops it; after syncing, the item is
     * gone and the acorns are unspent.
     */
    @Test
    fun purchaseSurvivesClockSkewBetweenDevices() {
        val fast = device(startMs = Harness.DEFAULT_START + 60 * 60_000L)
        val slow = device(startMs = Harness.DEFAULT_START)
        try {
            fast.seedSession(125) // stamped at `fast` time, 1 h ahead of `slow`
            sync(fast)
            sync(slow)
            assertEquals(150, slow.state().acorns)

            assertTrue(slow.controller.buy("skin:mint"), "the UI offered the purchase, so buy() must accept it")
            sync(slow, fast)
            assertEquals(fast.state(), slow.state()) // both devices agree
            assertEquals(0, slow.state().acorns)      // the acorns were spent
            assertTrue(slow.state().owns("skin:mint"), "clock skew must not swallow an accepted purchase")
        } finally {
            fast.close(); slow.close()
        }
    }
}
