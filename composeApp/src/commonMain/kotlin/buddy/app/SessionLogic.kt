package buddy.app

import buddy.app.data.RunningSession
import buddy.core.Rules

/** What the time-based rules say should happen to a running session. */
sealed interface SessionTick {
    data class Complete(val endedAt: Long) : SessionTick
    data class Abandon(val endedAt: Long) : SessionTick
    data object Continue : SessionTick
}

/**
 * Pure timer arithmetic, kept out of [AppController] so every boundary (grace, planned end,
 * count-up cap, free cancel, backwards clock jumps) is testable without coroutines.
 */
object SessionLogic {

    fun plannedEnd(rs: RunningSession): Long? =
        if (rs.countUp) null else rs.startedAt + rs.plannedMin * 60_000L

    /**
     * Applies completion rules at [nowMs]. While the user is away the timer is paused, so a
     * session can never be lost just by opening another app; [AppController] shifts the start
     * time forward when they come back.
     */
    fun tick(rs: RunningSession, nowMs: Long): SessionTick {
        if (rs.awaySince != null) return SessionTick.Continue
        val plannedEnd = plannedEnd(rs)
        return when {
            plannedEnd != null && nowMs >= plannedEnd -> SessionTick.Complete(plannedEnd)
            // Count-up sessions are capped at the maximum duration.
            rs.countUp && nowMs - rs.startedAt >= Rules.MAX_MINUTES * 60_000L ->
                SessionTick.Complete(rs.startedAt + Rules.MAX_MINUTES * 60_000L)
            else -> SessionTick.Continue
        }
    }

    /**
     * Seconds left in the free-cancel window. A backwards clock jump makes the raw elapsed time
     * negative; clamping keeps the window (and the UI counter) at ten seconds rather than endless.
     */
    fun freeCancelLeft(rs: RunningSession, nowMs: Long): Int {
        val elapsedSec = ((nowMs - rs.startedAt) / 1000).coerceAtLeast(0)
        return (Rules.FREE_CANCEL_SEC - elapsedSec).coerceIn(0, Rules.FREE_CANCEL_SEC.toLong()).toInt()
    }
}
