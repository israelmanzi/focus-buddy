package buddy.core

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

enum class DayMark { NONE, FOCUS, DEEP }

enum class StatsRange { DAY, WEEK, MONTH, YEAR }

data class Bar(val label: String, val minutes: Int)

class Stats(state: GameState, private val tz: TimeZone, private val nowMs: Long) {

    private val done = state.sessions.filter { it.session.completed && it.session.focusedSec >= Rules.MIN_MINUTES * 60 }
    private val today = local(nowMs).date

    private fun local(ms: Long): LocalDateTime = Instant.fromEpochMilliseconds(ms).toLocalDateTime(tz)
    private fun dateOf(r: SessionRecord) = local(r.session.endedAt).date
    private fun minutes(r: SessionRecord) = (r.session.focusedSec / 60).toInt()

    val totalMinutes = done.sumOf { minutes(it) }
    val totalSessions = done.size
    val abandoned = state.sessions.count { !it.session.completed }

    private val activeDays: Set<LocalDate> = done.map { dateOf(it) }.toSet()

    val currentStreak: Int = run {
        var d = if (today in activeDays) today else today.minus(DatePeriod(days = 1))
        var n = 0
        while (d in activeDays) { n++; d = d.minus(DatePeriod(days = 1)) }
        n
    }

    val bestStreak: Int = run {
        var best = 0; var run = 0; var prev: LocalDate? = null
        for (d in activeDays.sorted()) {
            run = if (prev != null && prev.daysUntil(d) == 1) run + 1 else 1
            best = maxOf(best, run); prev = d
        }
        best
    }

    /** Oldest first, ending today. */
    fun tracker(days: Int = 28): List<Pair<LocalDate, DayMark>> = (days - 1 downTo 0).map { back ->
        val d = today.minus(DatePeriod(days = back))
        val onDay = done.filter { dateOf(it) == d }
        d to when {
            onDay.any { it.session.deepFocus } -> DayMark.DEEP
            onDay.isNotEmpty() -> DayMark.FOCUS
            else -> DayMark.NONE
        }
    }

    fun minutesOn(d: LocalDate) = done.filter { dateOf(it) == d }.sumOf { minutes(it) }

    fun bars(range: StatsRange): List<Bar> = when (range) {
        StatsRange.DAY -> (0 until 24 step 3).map { h ->
            Bar("$h", done.filter { dateOf(it) == today && local(it.session.endedAt).hour in h until h + 3 }.sumOf { minutes(it) })
        }
        StatsRange.WEEK -> (6 downTo 0).map { back ->
            val d = today.minus(DatePeriod(days = back))
            Bar(d.dayOfWeek.name.take(2).lowercase().replaceFirstChar { it.uppercase() }, minutesOn(d))
        }
        StatsRange.MONTH -> {
            val first = LocalDate(today.year, today.month, 1)
            val len = first.daysUntil(first.plus(1, DateTimeUnit.MONTH))
            (0 until len).map { i -> val d = first.plus(i, DateTimeUnit.DAY); Bar("${i + 1}", minutesOn(d)) }
        }
        StatsRange.YEAR -> (1..12).map { m ->
            Bar(
                listOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")[m - 1],
                done.filter { val d = dateOf(it); d.year == today.year && d.month.ordinal + 1 == m }.sumOf { minutes(it) },
            )
        }
    }

    fun rangeTotal(range: StatsRange) = bars(range).sumOf { it.minutes }

    /** Minutes per tag id within the range, largest first. */
    fun byTag(range: StatsRange): List<Pair<String, Int>> {
        val from = when (range) {
            StatsRange.DAY -> today
            StatsRange.WEEK -> today.minus(DatePeriod(days = 6))
            StatsRange.MONTH -> LocalDate(today.year, today.month, 1)
            StatsRange.YEAR -> LocalDate(today.year, 1, 1)
        }
        return done.filter { dateOf(it) >= from }
            .groupBy { it.session.tag }
            .map { (tag, rs) -> tag to rs.sumOf { minutes(it) } }
            .sortedByDescending { it.second }
    }
}
