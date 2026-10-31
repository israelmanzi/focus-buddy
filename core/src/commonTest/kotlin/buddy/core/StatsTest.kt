package buddy.core

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Stats are pure functions over the folded state plus a timezone and a "now". */
class StatsTest {

    private val tz = TimeZone.of("America/New_York")
    private var seq = 0

    private fun at(y: Int, mo: Int, d: Int, h: Int = 12, mi: Int = 0): Long =
        LocalDateTime(y, mo, d, h, mi).toInstant(tz).toEpochMilliseconds()

    private fun session(
        endedAt: Long,
        minutes: Int,
        completed: Boolean = true,
        deep: Boolean = false,
        tag: String = Rules.DEFAULT_TAG,
    ): Event = Event(
        id = "s${++seq}",
        device = "test",
        ts = endedAt,
        payload = Payload.Session(
            startedAt = endedAt - minutes * 60_000L,
            endedAt = endedAt,
            plannedMin = minutes,
            focusedSec = minutes * 60L,
            tag = tag,
            completed = completed,
            deepFocus = deep,
        ),
    )

    private fun stats(now: Long, vararg events: Event): Stats =
        Stats(GameState.fold(events.toList()), tz, now)

    private fun Stats.mark(date: kotlinx.datetime.LocalDate): DayMark =
        tracker().single { it.first == date }.second

    private fun date(ms: Long) = Instant.fromEpochMilliseconds(ms).toLocalDateTime(tz).date

    @Test
    fun countsOnlyCompletedSessionsOfAtLeastFiveMinutes() {
        val now = at(2026, 10, 31)
        val s = stats(
            now,
            session(at(2026, 10, 31, 9), 25),
            session(at(2026, 10, 30, 9), 4),               // too short
            session(at(2026, 10, 29, 9), 25, completed = false), // abandoned
        )
        assertEquals(1, s.totalSessions)
        assertEquals(25, s.totalMinutes)
        assertEquals(1, s.abandoned)
    }

    @Test
    fun aFiveMinuteSessionCounts() {
        val now = at(2026, 10, 31)
        val s = stats(now, session(at(2026, 10, 31, 9), 5))
        assertEquals(1, s.totalSessions)
        assertEquals(5, s.totalMinutes)
    }

    @Test
    fun trackerHas28DaysOldestFirstAndEndsToday() {
        val now = at(2026, 10, 31, 12)
        val s = stats(
            now,
            session(at(2026, 10, 30, 9), 25),
            session(at(2026, 10, 29, 9), 50, deep = true),
            session(at(2026, 10, 20, 9), 25),
        )
        val tracker = s.tracker()
        assertEquals(28, tracker.size)
        assertEquals(date(now), tracker.last().first)
        assertEquals(date(now) - kotlinx.datetime.DatePeriod(days = 27), tracker.first().first)
        assertEquals(DayMark.NONE, s.mark(date(now)))
        assertEquals(DayMark.FOCUS, s.mark(date(at(2026, 10, 30))))
        assertEquals(DayMark.DEEP, s.mark(date(at(2026, 10, 29))))
        assertEquals(DayMark.FOCUS, s.mark(date(at(2026, 10, 20))))
    }

    @Test
    fun deepFocusTakesPrecedenceOverPlainFocusOnTheSameDay() {
        val now = at(2026, 10, 31)
        val s = stats(
            now,
            session(at(2026, 10, 30, 9), 25),
            session(at(2026, 10, 30, 18), 25, deep = true),
        )
        assertEquals(DayMark.DEEP, s.mark(date(at(2026, 10, 30))))
    }

    @Test
    fun currentStreakSpansMidnight() {
        val now = at(2026, 10, 31, 12)
        val s = stats(
            now,
            session(at(2026, 10, 29, 23, 59), 25),
            session(at(2026, 10, 30, 0, 1), 25),
            session(at(2026, 10, 31, 8), 25),
        )
        assertEquals(3, s.currentStreak)
        assertEquals(3, s.bestStreak)
    }

    @Test
    fun currentStreakFallsBackToYesterdayWhenTodayIsEmpty() {
        val now = at(2026, 10, 31, 12)
        val s = stats(
            now,
            session(at(2026, 10, 29, 9), 25),
            session(at(2026, 10, 30, 9), 25),
        )
        assertEquals(2, s.currentStreak)
        assertEquals(2, s.bestStreak)
    }

    @Test
    fun currentStreakIsZeroWhenYesterdayIsAlsoEmpty() {
        val now = at(2026, 10, 31, 12)
        val s = stats(now, session(at(2026, 10, 29, 9), 25))
        assertEquals(0, s.currentStreak)
        assertEquals(1, s.bestStreak)
    }

    @Test
    fun bestStreakSurvivesGapsBetterThanCurrent() {
        val now = at(2026, 10, 31, 12)
        val s = stats(
            now,
            session(at(2026, 10, 1, 9), 25),
            session(at(2026, 10, 2, 9), 25),
            session(at(2026, 10, 3, 9), 25),
            session(at(2026, 10, 10, 9), 25),
            session(at(2026, 10, 30, 9), 25),
        )
        assertEquals(1, s.currentStreak)
        assertEquals(3, s.bestStreak)
    }

    @Test
    fun streakAcrossDstSpringForward() {
        // US DST starts 2026-03-08; local dates must still be consecutive.
        val now = at(2026, 3, 10, 12)
        val s = stats(
            now,
            session(at(2026, 3, 7, 12), 25),
            session(at(2026, 3, 8, 12), 25),
            session(at(2026, 3, 9, 12), 25),
            session(at(2026, 3, 10, 12), 25),
        )
        assertEquals(4, s.currentStreak)
        assertEquals(4, s.bestStreak)
        assertEquals(4, s.tracker().count { it.second == DayMark.FOCUS })
    }

    @Test
    fun streakAcrossDstFallBack() {
        // US DST ends 2026-11-01.
        val now = at(2026, 11, 3, 12)
        val s = stats(
            now,
            session(at(2026, 10, 31, 12), 25),
            session(at(2026, 11, 1, 12), 25),
            session(at(2026, 11, 2, 12), 25),
        )
        assertEquals(3, s.currentStreak)
        assertEquals(3, s.bestStreak)
        assertEquals(3, s.tracker().count { it.second == DayMark.FOCUS })
    }

    @Test
    fun rangeTotalsCoverTheExpectedWindows() {
        val now = at(2026, 10, 15, 12)
        val s = stats(
            now,
            session(at(2026, 10, 15, 10), 30),
            session(at(2026, 10, 14, 10), 25),
            session(at(2026, 10, 1, 10), 50),
            session(at(2026, 9, 30, 10), 40),
            session(at(2026, 1, 5, 10), 60),
            session(at(2025, 12, 31, 10), 20),
        )
        assertEquals(30, s.rangeTotal(StatsRange.DAY))
        assertEquals(55, s.rangeTotal(StatsRange.WEEK))
        assertEquals(105, s.rangeTotal(StatsRange.MONTH))
        assertEquals(205, s.rangeTotal(StatsRange.YEAR))
    }

    @Test
    fun dayBarsBucketLocalHoursAndCoverTheWholeDay() {
        val now = at(2026, 10, 15, 12)
        val s = stats(
            now,
            session(at(2026, 10, 15, 1), 10),
            session(at(2026, 10, 15, 7, 30), 20),
            session(at(2026, 10, 15, 22), 30),
        )
        val bars = s.bars(StatsRange.DAY)
        assertEquals(8, bars.size)
        assertEquals(10, bars[0].minutes) // 00-03
        assertEquals(20, bars[2].minutes) // 06-09
        assertEquals(30, bars[7].minutes) // 21-24
        assertEquals(60, s.rangeTotal(StatsRange.DAY))
    }

    @Test
    fun weekBarsAreTheLastSevenDays() {
        val now = at(2026, 10, 15, 12)
        val s = stats(
            now,
            session(at(2026, 10, 15, 10), 30),
            session(at(2026, 10, 12, 10), 20),
            session(at(2026, 10, 8, 10), 10), // eight days ago: outside the week
        )
        assertEquals(7, s.bars(StatsRange.WEEK).size)
        assertEquals(50, s.rangeTotal(StatsRange.WEEK))
        assertEquals(60, s.rangeTotal(StatsRange.MONTH))
    }

    @Test
    fun monthAndYearBarsHaveTheExpectedShape() {
        val now = at(2026, 10, 15)
        val s = stats(now)
        assertEquals(31, s.bars(StatsRange.MONTH).size)
        assertEquals(12, s.bars(StatsRange.YEAR).size)
    }

    @Test
    fun perTagTotalsRespectTheRangeAndSortByMinutes() {
        val now = at(2026, 10, 15, 12)
        val s = stats(
            now,
            session(at(2026, 10, 15, 10), 30, tag = "a"),
            session(at(2026, 10, 14, 10), 25, tag = "b"),
            session(at(2026, 10, 1, 10), 50, tag = "a"),
            session(at(2026, 9, 30, 10), 40, tag = "c"),
        )
        assertEquals(listOf("a" to 30), s.byTag(StatsRange.DAY))
        assertEquals(listOf("a" to 30, "b" to 25), s.byTag(StatsRange.WEEK))
        assertEquals(listOf("a" to 80, "b" to 25), s.byTag(StatsRange.MONTH))
        assertEquals(listOf("a" to 80, "c" to 40, "b" to 25), s.byTag(StatsRange.YEAR))
    }

    @Test
    fun abandonedAndShortSessionsNeverAppearInTheTracker() {
        val now = at(2026, 10, 31, 12)
        val s = stats(
            now,
            session(at(2026, 10, 30, 9), 25, completed = false),
            session(at(2026, 10, 30, 15), 4),
        )
        assertEquals(0, s.totalSessions)
        assertEquals(0, s.tracker().count { it.second != DayMark.NONE })
    }

    @Test
    fun emptyStateHasNoStatsButTrackerIsStill28Days() {
        val s = stats(at(2026, 10, 31))
        assertEquals(0, s.totalMinutes)
        assertEquals(0, s.totalSessions)
        assertEquals(0, s.currentStreak)
        assertEquals(0, s.bestStreak)
        assertEquals(28, s.tracker().size)
        assertTrue(s.byTag(StatsRange.YEAR).isEmpty())
        assertEquals(0, s.rangeTotal(StatsRange.YEAR))
    }
}
