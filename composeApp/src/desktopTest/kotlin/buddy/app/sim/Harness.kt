package buddy.app.sim

import buddy.app.AppController
import buddy.app.AppInfo
import buddy.app.BlockRules
import buddy.app.Platform
import buddy.app.Sound
import buddy.core.BuddyJson
import buddy.core.Event
import buddy.core.Payload
import buddy.core.Rules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.nio.file.Files

/** A recorded call into the host platform. */
data class PlatformCall(val name: String, val arg: Any? = null)

/**
 * In-memory stand-in for Android/desktop. Records every host callback, never touches audio or
 * notifications, and keeps the data dir under a temp directory.
 */
class FakePlatform(
    override val dataDir: File,
    override val leavingAppInterrupts: Boolean = true,
    override val supportsExactAlarms: Boolean = false,
    override val supportsDeepFocus: Boolean = true,
) : Platform {
    override val name: String = "fake"
    val calls = mutableListOf<PlatformCall>()
    val sounds = mutableListOf<Sound>()
    var ambientPlaying = false
    var screenOn = false
    var exactAlarms = false
    var deepFocusPermissions = true
    var launchedApps = emptyList<AppInfo>()

    private fun record(name: String, arg: Any? = null) { calls += PlatformCall(name, arg) }

    fun callsNamed(name: String): List<PlatformCall> = calls.filter { it.name == name }

    override fun onSessionStarted(startedAtMs: Long, endsAtMs: Long?, deepFocus: Boolean, rules: BlockRules) =
        record("onSessionStarted", Triple(startedAtMs, endsAtMs, deepFocus))

    override fun onSessionEnded() = record("onSessionEnded")
    override fun onSessionPaused() = record("onSessionPaused")
    override fun onSessionResumed() = record("onSessionResumed")
    override fun onBreakStarted(endsAtMs: Long) = record("onBreakStarted", endsAtMs)
    override fun onBreakEnded() = record("onBreakEnded")
    override fun keepScreenOn(on: Boolean) { screenOn = on; record("keepScreenOn", on) }
    override fun playSound(sound: Sound) { sounds += sound; record("playSound", sound) }
    override fun setAmbient(on: Boolean) { ambientPlaying = on; record("setAmbient", on) }

    override fun exactAlarmsGranted(): Boolean = exactAlarms
    override fun requestExactAlarms() { exactAlarms = true; record("requestExactAlarms") }
    override fun deepFocusReady(): Boolean = deepFocusPermissions && supportsDeepFocus
    override fun requestDeepFocusPermissions() { deepFocusPermissions = true; record("requestDeepFocusPermissions") }
    override suspend fun launchableApps(): List<AppInfo> = launchedApps
}

/** Controllable wall clock handed to [AppController]. */
class FakeClock(var now: Long) {
    fun advance(ms: Long) { now += ms }
}

/**
 * Test harness around [AppController].
 *
 * The controller's coroutines run on a [StandardTestDispatcher], so tickers only run when
 * [advance] moves both the injected wall clock and virtual time together. This keeps every
 * scenario deterministic and fast (no real sleeping).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Harness(
    val dir: File = Files.createTempDirectory("focus-buddy-test").toFile(),
    startMs: Long = DEFAULT_START,
    leavingAppInterrupts: Boolean = true,
) : AutoCloseable {
    companion object {
        /** 2023-11-14T22:13:20Z, far from DST boundaries. */
        const val DEFAULT_START = 1_700_000_000_000L
        fun encode(events: List<Event>): String =
            BuddyJson.encodeToString(ListSerializer(Event.serializer()), events)
    }

    val clock = FakeClock(startMs)
    val platform = FakePlatform(dir, leavingAppInterrupts = leavingAppInterrupts)
    private val dispatcher = StandardTestDispatcher()
    val scope = CoroutineScope(dispatcher + Job())
    val controller = AppController(platform, scope, clock::now)

    private var seq = 0

    fun state() = controller.state.value
    fun phase() = controller.phase.value
    fun settings() = controller.settings.value

    fun event(ts: Long, payload: Payload, device: String = controller.deviceId, id: String = "${controller.deviceId.take(8)}-${++seq}"): Event =
        Event(id, device, ts, payload)

    fun event(payload: Payload): Event = event(clock.now, payload)

    fun import(events: List<Event>): Int = controller.importBackup(encode(events))
    fun import(vararg events: Event): Int = import(events.toList())

    fun session(
        minutes: Int,
        endedAt: Long = clock.now - 60_000,
        completed: Boolean = true,
        tag: String = Rules.DEFAULT_TAG,
        deep: Boolean = false,
    ): Event = event(
        endedAt,
        Payload.Session(
            startedAt = endedAt - minutes * 60_000L,
            endedAt = endedAt,
            plannedMin = if (minutes > 0) minutes else 0,
            focusedSec = minutes * 60L,
            tag = tag,
            completed = completed,
            deepFocus = deep,
        ),
    )

    /** Gives the device acorns by importing a completed session dated in the past. */
    fun seedSession(minutes: Int, tag: String = Rules.DEFAULT_TAG, deep: Boolean = false): Event =
        session(minutes, endedAt = clock.now - 60_000, tag = tag, deep = deep).also { import(it) }

    /** Advances virtual time and the injected wall clock together, then runs due coroutines. */
    fun advance(ms: Long) {
        require(ms >= 0) { "cannot move backwards" }
        clock.advance(ms)
        dispatcher.scheduler.advanceTimeBy(ms)
        dispatcher.scheduler.runCurrent()
    }

    /** Runs coroutines that are already due without changing the clock. */
    fun drain() = dispatcher.scheduler.runCurrent()

    fun lineCount(): Int =
        File(dir, "events.jsonl").takeIf { it.exists() }?.readLines()?.count { it.isNotBlank() } ?: 0

    fun loadEventsFromDisk(): List<Event> =
        File(dir, "events.jsonl").takeIf { it.exists() }?.readLines()
            ?.filter { it.isNotBlank() }
            ?.map { BuddyJson.decodeFromString(Event.serializer(), it) }
            ?: emptyList()

    /** Simulates a process restart: same data dir, wall clock continues, new controller. */
    fun restart(afterMs: Long = 0): Harness {
        val next = Harness(dir, startMs = clock.now + afterMs)
        return next
    }

    override fun close() { close(deleteDir = true) }

    fun close(deleteDir: Boolean) {
        scope.cancel()
        if (deleteDir) dir.deleteRecursively()
    }
}
