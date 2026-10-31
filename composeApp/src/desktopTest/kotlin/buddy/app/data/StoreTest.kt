package buddy.app.data

import buddy.core.BuddyJson
import buddy.core.Event
import buddy.core.Payload
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StoreTest {
    private lateinit var dir: File

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("buddy-store-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun corruptLinesAreSkippedInsteadOfLosingTheWholeLog() {
        val good = Event("a", "d", 1, Payload.Buy("decor:rug.cozy.0"))
        File(dir, "events.jsonl").writeText(
            BuddyJson.encodeToString(Event.serializer(), good) + "\n" +
                "{\"id\":\"truncated\"" + "\n" +
                BuddyJson.encodeToString(Event.serializer(), good.copy(id = "b")) + "\n",
        )
        assertEquals(listOf("a", "b"), Store(dir).loadEvents().map { it.id })
    }

    @Test
    fun duplicateIdsInTheFileAreFoldedOnce() {
        val first = Event("dup", "d", 1, Payload.Buy("decor:rug.cozy.0"))
        val second = Event("dup", "d", 2, Payload.Buy("decor:lamp.cozy.0"))
        File(dir, "events.jsonl").writeText(
            BuddyJson.encodeToString(Event.serializer(), first) + "\n" +
                BuddyJson.encodeToString(Event.serializer(), second) + "\n",
        )
        assertEquals(listOf("dup"), Store(dir).loadEvents().map { it.id })
    }

    @Test
    fun settingsAndDeviceFilesAreOwnerOnly() {
        val store = Store(dir)
        store.saveSettings(LocalSettings(syncToken = "secret-token"))
        store.loadMeta()

        for (name in listOf("settings.json", "device.json")) {
            // Silently skip on filesystems without POSIX permissions (the fix is a no-op there).
            val perms = runCatching { Files.getPosixFilePermissions(File(dir, name).toPath()) }.getOrElse { return }
            assertEquals(PosixFilePermissions.fromString("rw-------"), perms, name)
        }
        // No temporary file is left behind by an atomic write.
        assertFalse(File(dir, "settings.json.tmp").exists())
    }

    @Test
    fun atomicWriteReplacesTheFileAndLeavesItReadable() {
        val store = Store(dir)
        store.saveSettings(LocalSettings(defaultMinutes = 50))
        store.saveSettings(LocalSettings(defaultMinutes = 20))
        assertEquals(20, store.loadSettings().defaultMinutes)
        assertTrue(File(dir, "settings.json").readText().contains("\"defaultMinutes\":20"))
    }
}
