package buddy.app

import buddy.app.data.DeviceMeta
import buddy.app.data.LocalSettings
import buddy.app.data.RunningSession
import buddy.app.data.Store
import buddy.app.sync.SyncClient
import buddy.core.BuddyJson
import buddy.core.Catalog
import buddy.core.Event
import buddy.core.canonical
import buddy.core.GameState
import buddy.core.Payload
import buddy.core.Rules
import buddy.core.SyncRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import java.util.UUID

private const val SYNC_BATCH_EVENTS = 1_000

sealed interface Phase {
    data object Idle : Phase
    data class Running(val session: RunningSession) : Phase
    data class Interrupted(val focusedMin: Int) : Phase
    data class Rewarded(val acorns: Int, val minutes: Int) : Phase
    data object BreakOffer : Phase
    data class OnBreak(val endsAt: Long) : Phase
}

sealed interface SyncStatus {
    data object Off : SyncStatus
    data object Syncing : SyncStatus
    data class Ok(val at: Long) : SyncStatus
    data class Failed(val message: String) : SyncStatus
}

/**
 * Owns all app state. Every method must be called on the main thread; [scope] must use the main dispatcher.
 */
class AppController(
    val platform: Platform,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val store = Store(platform.dataDir)
    private val syncClient by lazy { SyncClient() }

    private val events = store.loadEvents().associateBy { it.id }.toMutableMap()
    private var meta: DeviceMeta = store.loadMeta()
    /** Last timestamp this device stamped, so two actions in one millisecond keep their order. */
    private var lastLocalTs: Long = events.values.filter { it.device == meta.deviceId }.maxOfOrNull { it.ts } ?: 0L

    private val _state = MutableStateFlow(GameState.fold(events.values))
    val state: StateFlow<GameState> = _state.asStateFlow()

    private val _settings = MutableStateFlow(store.loadSettings())
    val settings: StateFlow<LocalSettings> = _settings.asStateFlow()

    private val _phase = MutableStateFlow<Phase>(Phase.Idle)
    val phase: StateFlow<Phase> = _phase.asStateFlow()

    private val _sync = MutableStateFlow<SyncStatus>(if (_settings.value.syncUrl.isBlank()) SyncStatus.Off else SyncStatus.Ok(0))
    val sync: StateFlow<SyncStatus> = _sync.asStateFlow()

    private var ticker: Job? = null
    private var syncJob: Job? = null

    init {
        // Drop pending ids whose event never made it to disk (e.g. the process died between the
        // two writes); they can never be sent.
        if (meta.pending.any { it !in events }) saveMeta(meta.copy(pending = meta.pending.filter { it in events }))
        meta.running?.let { resume(it) }
        meta.breakEndsAt?.let {
            if (it > clock()) {
                // Re-arm the system alarm too: alarms do not survive a reboot.
                platform.onBreakStarted(it)
                startBreakTicker(it)
            } else {
                saveMeta(meta.copy(breakEndsAt = null))
            }
        }
        scope.launch {
            while (isActive) {
                if (_settings.value.autoSync) syncNow()
                delay(5 * 60_000L)
            }
        }
    }

    fun now() = clock()

    // ---- focus sessions ----

    fun startSession(minutes: Int, countUp: Boolean, tag: String, deepFocus: Boolean) {
        if (_phase.value is Phase.Running) return
        cancelBreak()
        val planned = if (countUp) 0 else minutes.coerceIn(Rules.MIN_MINUTES, Rules.MAX_MINUTES)
        val rs = RunningSession(clock(), planned, countUp, tag, deepFocus)
        updateSettings { it.copy(defaultMinutes = if (countUp) it.defaultMinutes else planned, countUp = countUp, lastTag = tag, deepFocus = deepFocus) }
        saveMeta(meta.copy(running = rs, breakEndsAt = null))
        _phase.value = Phase.Running(rs)
        val s = _settings.value
        platform.onSessionStarted(rs.startedAt, SessionLogic.plannedEnd(rs), deepFocus, BlockRules(s.allowMode, s.packages))
        platform.keepScreenOn(s.screenOn)
        if (s.sfx) platform.playSound(Sound.START)
        if (s.music) platform.setAmbient(true)
        startSessionTicker()
    }

    /** Seconds left in the no-penalty cancel window, 0 when it has passed. */
    fun freeCancelLeft(rs: RunningSession): Int = SessionLogic.freeCancelLeft(rs, clock())

    /** Ends the running session. Inside the free window nothing is recorded; count-up sessions finish normally. */
    fun stopSession() {
        val rs = (_phase.value as? Phase.Running)?.session ?: return
        // The ticker polls every 500 ms; a tap at the planned end must not record an earned
        // countdown as abandoned just because it beat the next tick.
        if (checkSession(rs)) return
        when {
            freeCancelLeft(rs) > 0 -> endSession(rs, null)
            rs.countUp -> finish(rs, clock(), completed = true)
            else -> finish(rs, clock(), completed = false)
        }
    }

    fun onAppBackgrounded() {
        val rs = (_phase.value as? Phase.Running)?.session ?: return
        if (!platform.leavingAppInterrupts || rs.deepFocus || rs.awaySince != null) return
        val away = rs.copy(awaySince = clock())
        saveMeta(meta.copy(running = away))
        _phase.value = Phase.Running(away)
        platform.onLeftDuringSession(away.awaySince!! + _settings.value.graceSec * 1000L)
    }

    fun onAppForegrounded() {
        val rs = (_phase.value as? Phase.Running)?.session ?: return
        if (rs.awaySince == null) return
        if (checkSession(rs)) return
        val back = rs.copy(awaySince = null)
        saveMeta(meta.copy(running = back))
        _phase.value = Phase.Running(back)
        platform.onReturned()
    }

    fun dismissResult() {
        _phase.value = when (_phase.value) {
            is Phase.Rewarded -> Phase.BreakOffer
            else -> Phase.Idle
        }
    }

    private fun resume(rs: RunningSession) {
        _phase.value = Phase.Running(rs)
        if (!checkSession(rs)) {
            val s = _settings.value
            platform.onSessionStarted(rs.startedAt, SessionLogic.plannedEnd(rs), rs.deepFocus, BlockRules(s.allowMode, s.packages))
            // Process death loses the host-side flags; restore the ones tied to the session.
            platform.keepScreenOn(s.screenOn)
            if (s.music) platform.setAmbient(true)
            startSessionTicker()
        }
    }

    private fun startSessionTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                val rs = (_phase.value as? Phase.Running)?.session ?: break
                if (checkSession(rs)) break
                delay(500)
            }
        }
    }

    /** Applies the time-based rules. Returns true if the session ended. */
    private fun checkSession(rs: RunningSession): Boolean {
        return when (val t = SessionLogic.tick(rs, clock(), _settings.value.graceSec)) {
            is SessionTick.Complete -> { finish(rs, t.endedAt, completed = true); true }
            is SessionTick.Abandon -> { finish(rs, t.endedAt, completed = false); true }
            SessionTick.Continue -> false
        }
    }

    private fun finish(rs: RunningSession, endedAt: Long, completed: Boolean) {
        val sec = ((endedAt - rs.startedAt) / 1000).coerceAtLeast(0)
        val session = Payload.Session(rs.startedAt, endedAt, rs.plannedMin, sec, rs.tag, completed, rs.deepFocus)
        val reward = Rules.reward(session)
        endSession(rs, session)
        _phase.value = when {
            reward > 0 -> Phase.Rewarded(reward, (sec / 60).toInt()).also {
                platform.showNotification("Session complete", "+$reward acorns · ${sec / 60} min focused")
            }
            else -> Phase.Interrupted((sec / 60).toInt()).also {
                platform.showNotification("Session ended early", "No acorns this time. Try again?")
            }
        }
    }

    private fun endSession(rs: RunningSession, record: Payload.Session?) {
        ticker?.cancel()
        saveMeta(meta.copy(running = null))
        if (record != null) emit(record)
        platform.onSessionEnded()
        platform.keepScreenOn(false)
        platform.setAmbient(false)
        if (record != null && record.completed && _settings.value.sfx) platform.playSound(Sound.COMPLETE)
        _phase.value = Phase.Idle
    }

    // ---- breaks ----

    fun startBreak(minutes: Int) {
        if (_phase.value is Phase.Running) return
        val endsAt = clock() + minutes * 60_000L
        updateSettings { it.copy(breakMinutes = minutes) }
        saveMeta(meta.copy(breakEndsAt = endsAt))
        platform.onBreakStarted(endsAt)
        startBreakTicker(endsAt)
    }

    fun endBreak() {
        ticker?.cancel()
        val hadBreak = meta.breakEndsAt != null
        cancelBreak()
        if (hadBreak && _settings.value.sfx) platform.playSound(Sound.BREAK_END)
        _phase.value = Phase.Idle
    }

    /** Cancels a break (and its system alarm) without the end-of-break announcement. */
    private fun cancelBreak() {
        if (meta.breakEndsAt == null) return
        saveMeta(meta.copy(breakEndsAt = null))
        platform.onBreakEnded()
    }

    private fun startBreakTicker(endsAt: Long) {
        _phase.value = Phase.OnBreak(endsAt)
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive && clock() < endsAt) delay(500)
            if (_phase.value is Phase.OnBreak) endBreak()
        }
    }

    // ---- economy & customisation ----

    fun buy(item: String): Boolean {
        val s = _state.value
        if (s.owns(item) || !s.canAfford(item)) return false
        emit(Payload.Buy(item))
        return true
    }

    fun equip(slot: String, item: String?) = emit(Payload.Equip(slot, item))

    fun setStyle(name: String, skin: String, hat: String?, neck: String?, face: String?) =
        emit(Payload.Style(name, skin, hat, neck, face))

    fun upsertTag(id: String?, name: String, color: Long, archived: Boolean = false) =
        emit(Payload.Tag(id ?: UUID.randomUUID().toString().take(8), name, color, archived))

    private fun emit(p: Payload) {
        val ts = maxOf(clock(), lastLocalTs + 1)
        lastLocalTs = ts
        val e = Event(UUID.randomUUID().toString(), meta.deviceId, ts, p)
        events[e.id] = e
        // Persist the pending id before the event itself. If the process dies in between, the id
        // is pruned (no event on disk); the opposite order would leave a recorded event that is
        // never queued for sync.
        saveMeta(meta.copy(pending = (meta.pending + e.id).distinct()))
        store.append(listOf(e))
        _state.value = GameState.fold(events.values)
        if (_settings.value.autoSync) scheduleSync()
    }

    // ---- settings ----

    fun updateSettings(f: (LocalSettings) -> LocalSettings) {
        val next = f(_settings.value)
        if (next == _settings.value) return
        _settings.value = next
        store.saveSettings(next)
        if (next.syncUrl.isBlank()) _sync.value = SyncStatus.Off
    }

    // ---- sync ----

    private fun scheduleSync() {
        syncJob?.cancel()
        syncJob = scope.launch { delay(2_000); syncNow() }
    }

    suspend fun syncNow() {
        val s = _settings.value
        if (s.syncUrl.isBlank() || _sync.value == SyncStatus.Syncing) return
        _sync.value = SyncStatus.Syncing
        try {
            var cursor = meta.syncCursor
            while (true) {
                // Batch uploads so a large backlog (notably after a backup import) stays under
                // the server's body limit, and skip ids whose event never reached disk.
                val sending = meta.pending.filter { it in events }.take(SYNC_BATCH_EVENTS).map { events.getValue(it) }
                val res = syncClient.sync(s.syncUrl, s.syncToken, SyncRequest(cursor, sending))
                val fresh = absorb(res.events)
                // Publish each page before persisting it: a later failure must not leave the UI
                // (and purchases) on a stale balance after some pages were merged.
                if (fresh.isNotEmpty()) _state.value = GameState.fold(events.values)
                store.append(fresh)
                val sentIds = sending.mapTo(HashSet()) { it.id }
                // A successful response acknowledges everything we sent, and heals pending ids
                // whose event is gone.
                saveMeta(
                    meta.copy(
                        syncCursor = res.cursor,
                        pending = meta.pending.filter { it !in sentIds && it in events },
                    ),
                )
                // The cursor can move *backwards* when the server was reset; any movement with
                // events means there may be more, and an unchanged cursor means no progress.
                val moreToPull = res.events.isNotEmpty() && res.cursor != cursor
                cursor = res.cursor
                if (!moreToPull && meta.pending.isEmpty()) break
            }
            _sync.value = SyncStatus.Ok(clock())
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) { _sync.value = SyncStatus.Failed("cancelled"); throw t }
            _sync.value = SyncStatus.Failed(t.message ?: t::class.simpleName ?: "error")
        }
    }

    // ---- backup ----

    fun exportBackup(): String =
        BuddyJson.encodeToString(ListSerializer(Event.serializer()), events.values.sortedWith(GameState.eventOrder))

    /** Merges a backup into the log (never deletes). Returns how many events were new. */
    /**
     * Adds new events and resolves id collisions deterministically (smaller canonical JSON wins),
     * so two devices that saw different payloads for one id still converge.
     * Returns the events that should be appended to the log.
     */
    private fun absorb(incoming: List<Event>): List<Event> {
        val added = mutableListOf<Event>()
        for (e in incoming) {
            val cur = events[e.id]
            when {
                cur == null -> { events[e.id] = e; added += e }
                cur != e && e.canonical() < cur.canonical() -> { events[e.id] = e; added += e }
            }
        }
        return added
    }

    fun importBackup(json: String): Int {
        // A hand-edited or duplicated backup must not append the same id twice: the in-memory
        // map would keep the last copy while the file keeps the first (and sync both).
        val incoming = BuddyJson.decodeFromString(ListSerializer(Event.serializer()), json)
        val fresh = absorb(incoming)
        saveMeta(meta.copy(pending = (meta.pending + fresh.map { it.id }).distinct()))
        store.append(fresh)
        _state.value = GameState.fold(events.values)
        return fresh.size
    }

    val deviceId get() = meta.deviceId

    fun tagName(id: String) = _state.value.tags[id]?.name ?: id

    fun roomPrice(roomId: String) = Catalog.room(roomId)?.price ?: 0

    private fun saveMeta(m: DeviceMeta) { meta = m; store.saveMeta(m) }
}
