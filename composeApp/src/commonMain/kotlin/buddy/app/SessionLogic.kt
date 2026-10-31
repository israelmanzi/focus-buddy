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

    fun graceEnd(rs: RunningSession, graceSec: Int): Long? =
        rs.awaySince?.plus(graceSec * 1000L)

    /** Applies completion/abandonment rules at [nowMs]. */
    fun tick(rs: RunningSession, nowMs: Long, graceSec: Int): SessionTick {
        val graceEnd = graceEnd(rs, graceSec)
        val plannedEnd = plannedEnd(rs)
        return when {
            // A planned session that ends while the user is inside the grace window still counts.
            plannedEnd != null && nowMs >= plannedEnd && (graceEnd == null || plannedEnd <= graceEnd) ->
                SessionTick.Complete(plannedEnd)

            // Away past the grace deadline; the session is abandoned at the moment they left.
            graceEnd != null && nowMs >= graceEnd -> SessionTick.Abandon(rs.awaySince!!)

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
