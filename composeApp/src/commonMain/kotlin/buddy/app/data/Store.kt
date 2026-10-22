package buddy.app.data

import buddy.core.BuddyJson
import buddy.core.Event
import kotlinx.serialization.Serializable
import java.io.File
import java.util.UUID

@Serializable
data class LocalSettings(
    val defaultMinutes: Int = 25,
    val countUp: Boolean = false,
    val lastTag: String = "focus",
    val deepFocus: Boolean = false,
    val allowMode: Boolean = false,
    val packages: Set<String> = emptySet(),
    val graceSec: Int = 20,
    val music: Boolean = true,
    val sfx: Boolean = true,
    val screenOn: Boolean = false,
    val breakMinutes: Int = 5,
    val syncUrl: String = "",
    val syncToken: String = "",
    val autoSync: Boolean = true,
)

/** A session in progress, persisted so it survives the process being killed. */
@Serializable
data class RunningSession(
    val startedAt: Long,
    val plannedMin: Int,
    val countUp: Boolean,
    val tag: String,
    val deepFocus: Boolean,
    val awaySince: Long? = null,
)

@Serializable
data class DeviceMeta(
    val deviceId: String,
    val syncCursor: Long = 0,
    /** Ids of local events the server has not acknowledged yet. */
    val pending: List<String> = emptyList(),
    val running: RunningSession? = null,
    val breakEndsAt: Long? = null,
)

/**
 * Events live in an append-only JSON-lines file; settings and device meta in small JSON files.
 * Single-process, single-writer: all calls come from the controller.
 */
class Store(private val dir: File) {
    private val eventsFile = File(dir, "events.jsonl")
    private val settingsFile = File(dir, "settings.json")
    private val metaFile = File(dir, "device.json")

    init { dir.mkdirs() }

    fun loadEvents(): List<Event> {
        if (!eventsFile.exists()) return emptyList()
        return eventsFile.readLines().mapNotNull { line ->
            line.takeIf { it.isNotBlank() }?.let { runCatching { BuddyJson.decodeFromString(Event.serializer(), it) }.getOrNull() }
        }.distinctBy { it.id }
    }

    fun append(events: List<Event>) {
        if (events.isEmpty()) return
        eventsFile.appendText(events.joinToString("") { BuddyJson.encodeToString(Event.serializer(), it) + "\n" })
    }

    /** Replace the whole log (backup import). */
    fun replaceEvents(events: List<Event>) {
        writeAtomic(eventsFile, events.joinToString("") { BuddyJson.encodeToString(Event.serializer(), it) + "\n" })
    }

    fun loadSettings(): LocalSettings = read(settingsFile, LocalSettings.serializer()) ?: LocalSettings()
    fun saveSettings(s: LocalSettings) = writeAtomic(settingsFile, BuddyJson.encodeToString(LocalSettings.serializer(), s))

    fun loadMeta(): DeviceMeta = read(metaFile, DeviceMeta.serializer())
        ?: DeviceMeta(UUID.randomUUID().toString()).also(::saveMeta)
    fun saveMeta(m: DeviceMeta) = writeAtomic(metaFile, BuddyJson.encodeToString(DeviceMeta.serializer(), m))

    private fun <T> read(f: File, ser: kotlinx.serialization.KSerializer<T>): T? =
        if (!f.exists()) null else runCatching { BuddyJson.decodeFromString(ser, f.readText()) }.getOrNull()

    private fun writeAtomic(f: File, text: String) {
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }
}
