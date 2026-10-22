package buddy.app

import buddy.app.data.DeviceMeta
import buddy.app.data.LocalSettings
import buddy.app.data.RunningSession
import buddy.app.data.Store
import buddy.app.sync.SyncClient
import buddy.core.BuddyJson
import buddy.core.Catalog
import buddy.core.Event
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
        meta.running?.let { resume(it) }
        meta.breakEndsAt?.let { if (it > clock()) startBreakTicker(it) else saveMeta(meta.copy(breakEndsAt = null)) }
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
        val planned = if (countUp) 0 else minutes.coerceIn(Rules.MIN_MINUTES, Rules.MAX_MINUTES)
        val rs = RunningSession(clock(), planned, countUp, tag, deepFocus)
        updateSettings { it.copy(defaultMinutes = if (countUp) it.defaultMinutes else planned, countUp = countUp, lastTag = tag, deepFocus = deepFocus) }
        saveMeta(meta.copy(running = rs, breakEndsAt = null))
        _phase.value = Phase.Running(rs)
        val s = _settings.value
        platform.onSessionStarted(if (countUp) null else rs.startedAt + planned * 60_000L, deepFocus, BlockRules(s.allowMode, s.packages))
        platform.keepScreenOn(s.screenOn)
        startSessionTicker()
    }

    /** Seconds left in the no-penalty cancel window, 0 when it has passed. */
    fun freeCancelLeft(rs: RunningSession): Int =
        (Rules.FREE_CANCEL_SEC - ((clock() - rs.startedAt) / 1000)).toInt().coerceAtLeast(0)

    /** Ends the running session. Inside the free window nothing is recorded; count-up sessions finish normally. */
    fun stopSession() {
        val rs = (_phase.value as? Phase.Running)?.session ?: return
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
            platform.onSessionStarted(if (rs.countUp) null else rs.startedAt + rs.plannedMin * 60_000L, rs.deepFocus, BlockRules(s.allowMode, s.packages))
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
        val now = clock()
        val graceEnd = rs.awaySince?.plus(_settings.value.graceSec * 1000L)
        val plannedEnd = if (rs.countUp) null else rs.startedAt + rs.plannedMin * 60_000L
        return when {
            plannedEnd != null && now >= plannedEnd && (graceEnd == null || plannedEnd <= graceEnd) -> {
                finish(rs, plannedEnd, completed = true); true
            }
            graceEnd != null && now >= graceEnd -> {
                finish(rs, rs.awaySince, completed = false); true
            }
            rs.countUp && now - rs.startedAt >= Rules.MAX_MINUTES * 60_000L -> {
                finish(rs, rs.startedAt + Rules.MAX_MINUTES * 60_000L, completed = true); true
            }
            else -> false
        }
    }

    private fun finish(rs: RunningSession, endedAt: Long, completed: Boolean) {
        val sec = ((endedAt - rs.startedAt) / 1000).coerceAtLeast(0)
        val session = Payload.Session(rs.startedAt, endedAt, rs.plannedMin, sec, rs.tag, completed, rs.deepFocus)
        val reward = Rules.reward(session)
        endSession(rs, session)
        _phase.value = when {
            reward > 0 -> Phase.Rewarded(reward, (sec / 60).toInt())
            else -> Phase.Interrupted((sec / 60).toInt())
        }
    }

    private fun endSession(rs: RunningSession, record: Payload.Session?) {
        ticker?.cancel()
        saveMeta(meta.copy(running = null))
        if (record != null) emit(record)
        platform.onSessionEnded()
        platform.keepScreenOn(false)
        _phase.value = Phase.Idle
    }

    // ---- breaks ----

    fun startBreak(minutes: Int) {
        val endsAt = clock() + minutes * 60_000L
        updateSettings { it.copy(breakMinutes = minutes) }
        saveMeta(meta.copy(breakEndsAt = endsAt))
        platform.onBreakStarted(endsAt)
        startBreakTicker(endsAt)
    }

    fun endBreak() {
        ticker?.cancel()
        saveMeta(meta.copy(breakEndsAt = null))
        platform.onBreakEnded()
        _phase.value = Phase.Idle
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
        val e = Event(UUID.randomUUID().toString(), meta.deviceId, clock(), p)
        events[e.id] = e
        store.append(listOf(e))
        saveMeta(meta.copy(pending = meta.pending + e.id))
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
        val sending = meta.pending.mapNotNull { events[it] }
        try {
            val res = syncClient.sync(s.syncUrl, s.syncToken, SyncRequest(meta.syncCursor, sending))
            val fresh = res.events.filter { it.id !in events }
            fresh.forEach { events[it.id] = it }
            store.append(fresh)
            val sentIds = sending.map { it.id }.toSet()
            saveMeta(meta.copy(syncCursor = res.cursor, pending = meta.pending.filter { it !in sentIds }))
            if (fresh.isNotEmpty()) _state.value = GameState.fold(events.values)
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
    fun importBackup(json: String): Int {
        val incoming = BuddyJson.decodeFromString(ListSerializer(Event.serializer()), json)
        val fresh = incoming.filter { it.id !in events }
        fresh.forEach { events[it.id] = it }
        store.append(fresh)
        saveMeta(meta.copy(pending = meta.pending + fresh.map { it.id }))
        _state.value = GameState.fold(events.values)
        return fresh.size
    }

    val deviceId get() = meta.deviceId

    fun tagName(id: String) = _state.value.tags[id]?.name ?: id

    fun roomPrice(roomId: String) = Catalog.room(roomId)?.price ?: 0

    private fun saveMeta(m: DeviceMeta) { meta = m; store.saveMeta(m) }
}
