package buddy.app.sim

import buddy.app.Phase
import buddy.core.BuddyJson
import buddy.core.Event
import buddy.core.GameState
import buddy.core.Payload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Backup round trips, merges and event-ordering robustness.
 *
 * Tests whose name starts with `productBug_` are expected to fail until the product is fixed;
 * they encode the behaviour the app promises, not the behaviour it currently has.
 */
class BackupAndOrderingTest {

    private val rugCozy = "decor:rug.cozy.0"

    private fun richHarness(): Harness {
        val h = Harness()
        h.seedSession(210, tag = "focus")
        h.controller.upsertTag("study", "Study", 0xFF6C9BD2)
        h.controller.startSession(25, countUp = false, tag = "study", deepFocus = false)
        h.advance(25 * 60_000L)
        h.controller.dismissResult()
        h.controller.dismissResult()
        h.advance(1)
        h.controller.buy(rugCozy)
        h.advance(1)
        h.controller.equip("living.rug", rugCozy)
        h.advance(1)
        h.controller.setStyle("Mochi", "classic", null, null, null)
        return h
    }

    @Test
    fun exportImportRoundTripPreservesStateExactly() {
        richHarness().use { source ->
            val backup = source.controller.exportBackup()
            val jsonEvents = BuddyJson.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(Event.serializer()), backup,
            )
            assertEquals(source.state().sessions.size, jsonEvents.count { it.payload is Payload.Session })

            Harness().use { restored ->
                assertEquals(jsonEvents.size, restored.controller.importBackup(backup))
                assertEquals(source.state(), restored.state())
                // imported events are queued for sync
                assertTrue(restored.lineCount() == jsonEvents.size)
            }
        }
    }

    @Test
    fun wipeAndImportIntoFreshDataDirRestoresEverything() {
        val source = richHarness()
        val backup = source.controller.exportBackup()
        val expected = source.state()
        source.close() // deletes the original data dir, like an uninstall

        Harness().use { fresh ->
            fresh.controller.importBackup(backup)
            assertEquals(expected, fresh.state())
            assertEquals(expected.acorns, fresh.state().acorns)
            assertEquals(expected.equipped, fresh.state().equipped)
            assertEquals(expected.style, fresh.state().style)
        }
    }

    @Test
    fun importingTheSameBackupTwiceAddsNothingTheSecondTime() {
        richHarness().use { source ->
            val backup = source.controller.exportBackup()
            Harness().use { target ->
                val first = target.controller.importBackup(backup)
                val stateAfterFirst = target.state()
                val linesAfterFirst = target.lineCount()
                assertTrue(first > 0)

                assertEquals(0, target.controller.importBackup(backup))
                assertEquals(stateAfterFirst, target.state())
                assertEquals(linesAfterFirst, target.lineCount())
            }
        }
    }

    @Test
    fun mergingTwoDevicesConvergesWithoutDuplicateEvents() {
        val a = Harness()
        val b = Harness()
        try {
            a.seedSession(25)
            a.controller.upsertTag("a", "Alpha", 0xFF112233)
            b.seedSession(50)
            b.controller.upsertTag("b", "Beta", 0xFF445566)

            val backupA = a.controller.exportBackup()
            val backupB = b.controller.exportBackup()
            val expectedEvents = (a.loadEventsFromDisk() + b.loadEventsFromDisk()).associateBy { it.id }

            assertEquals(2, a.controller.importBackup(backupB)) // B's session + tag
            assertEquals(2, b.controller.importBackup(backupA))

            assertEquals(GameState.fold(expectedEvents.values), a.state())
            assertEquals(GameState.fold(expectedEvents.values), b.state())
            assertEquals(a.state(), b.state())

            // No duplicate ids on disk, even after both directions.
            assertEquals(expectedEvents.size, a.loadEventsFromDisk().distinctBy { it.id }.size)
            assertEquals(expectedEvents.size, a.lineCount())
            assertEquals(expectedEvents.size, b.lineCount())
        } finally {
            a.close(); b.close()
        }
    }

    @Test
    fun outOfOrderArrivalFoldsToTheSameState() {
        richHarness().use { source ->
            val events = source.loadEventsFromDisk()
            val shuffled = events.shuffled(kotlin.random.Random(7))

            Harness().use { target ->
                target.controller.importBackup(Harness.encode(shuffled))
                assertEquals(source.state(), target.state())
            }
        }
    }

    @Test
    fun productBug_duplicateIdsInsideOneBackupShouldBeCountedOnce() {
        Harness().use { h ->
            val e = h.session(25)
            val restored = h.controller.importBackup(Harness.encode(listOf(e, e)))

            // The fold and in-memory map already dedupe...
            assertEquals(1, h.state().sessions.size)
            // ...but the log and the return value should as well.
            assertEquals(1, h.lineCount())
            assertEquals(1, restored)
        }
    }

    /**
     * BUG: events are ordered by (timestamp, random UUID). Two causally ordered events emitted in
     * the same millisecond can therefore be folded in the wrong order. Reproduced through the
     * public API: buy an item, then immediately equip it (the slot picker does exactly this).
     */
    @Test
    fun productBug_sameMillisecondBuyThenEquipCanLoseTheEquip() {
        val missing = mutableListOf<Int>()
        repeat(24) { i ->
            val h = Harness()
            try {
                h.seedSession(210) // plenty of acorns, timestamped in the past
                assertTrue(h.controller.buy(rugCozy))
                h.controller.equip("living.rug", rugCozy) // same fake millisecond
                if (h.state().equipped["living.rug"] != rugCozy) missing += i
            } finally {
                h.close()
            }
        }
        assertEquals(
            emptyList(), missing,
            "buy+equip in one millisecond lost the equip on ${missing.size}/24 runs " +
                "because same-ts events are ordered by random UUID instead of emission order",
        )
    }

    @Test
    fun productBug_sameTimestampEquipBeforeBuyIsIgnoredByTheFold() {
        val device = "device-a"
        val session = Event("00-session", device, 1_000, Payload.Session(0, 100 * 60_000L, 100, 100 * 60L, "focus", true))
        val buy = Event("zz-buy", device, 2_000, Payload.Buy(rugCozy))
        val equip = Event("aa-equip", device, 2_000, Payload.Equip("living.rug", rugCozy))

        // Same wall-clock millisecond, but the equip's id sorts first, so it is dropped.
        val state = GameState.fold(listOf(session, buy, equip))
        assertEquals(rugCozy, state.equipped["living.rug"], "causally ordered same-ms events must not be reordered")
    }

    @Test
    fun importDoesNotOverwriteAnExistingEventWithTheSameId() {
        Harness().use { h ->
            val original = h.session(25)
            h.import(original)
            val expected = h.state()

            // A different payload under the same id must be ignored (first write wins).
            val conflicting = Event(original.id, "other-device", original.ts + 1, Payload.Buy("room:porch"))
            val restored = h.controller.importBackup(Harness.encode(listOf(conflicting)))
            assertEquals(0, restored)
            assertEquals(expected, h.state())
        }
    }
}
