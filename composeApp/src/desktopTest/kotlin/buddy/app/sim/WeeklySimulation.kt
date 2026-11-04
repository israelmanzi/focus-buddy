@file:OptIn(kotlin.time.ExperimentalTime::class)

package buddy.app.sim

import buddy.app.Phase
import buddy.core.Catalog
import buddy.core.Stats
import buddy.core.StatsRange
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import java.io.File
import java.nio.file.Files
import kotlin.time.Instant

/**
 * A week of realistic usage against a throwaway data dir, printed as a report.
 *
 * Run with `simulation/run.sh` (compiles the desktop test sources, then runs this main with the
 * test runtime classpath). No Gradle module or product code is involved.
 */
private val tags = listOf("focus", "study", "writing", "reading")

fun main() {
    val dir = Files.createTempDirectory("focus-buddy-week").toFile()
    val now = System.currentTimeMillis()
    val tz = TimeZone.currentSystemDefault()
    // Start at local midnight six days ago so the report's seven days end with today.
    val today = Instant.fromEpochMilliseconds(now).toLocalDateTime(tz).date
    val firstDay = today.minus(DatePeriod(days = 6))
    val startMs = LocalDateTime(firstDay.year, firstDay.month, firstDay.dayOfMonth, 0, 0)
        .toInstant(tz).toEpochMilliseconds()
    val h = Harness(startMs = startMs, dir = dir)
    try {
        h.controller.upsertTag("study", "Study", 0xFF6C9BD2)
        h.controller.upsertTag("writing", "Writing", 0xFFE07A5F)
        h.controller.upsertTag("reading", "Reading", 0xFF7FB069)

        val days = 7
        for (day in 0 until days) {
            // Morning: one solid countdown, sometimes deep focus.
            focus(h, minutes = if (day % 3 == 0) 50 else 25, tag = tags[day % tags.size], deep = day % 4 == 0)
            shop(h)
            h.advance(3 * 3_600_000L)

            // Afternoon: a shorter session.
            focus(h, minutes = 25, tag = tags[(day + 1) % tags.size], deep = false)
            shop(h)

            // Some days the user gives up mid-session, or uses the stopwatch.
            when (day % 3) {
                0 -> {
                    h.controller.startSession(50, countUp = false, tag = "study")
                    h.advance(17 * 60_000L)
                    h.controller.stopSession() // gave up after the free window
                    h.controller.dismissResult()
                }
                1 -> {
                    h.controller.startSession(25, countUp = true, tag = "writing")
                    h.advance(11 * 60_000L)
                    h.controller.stopSession()
                    h.controller.dismissResult()
                }
                else -> Unit
            }
            shop(h)

            // Evening: wind down, then sleep (except after the final day, so the report ends today).
            h.advance(5 * 3_600_000L)
            if (day < days - 1) h.advance(16 * 3_600_000L - (day % 2) * 20 * 60_000L)
        }

        printReport(h, dir, now)
    } finally {
        h.close()
    }
}

private val wishlist = listOf(
    "decor:lamp.basic.0",
    "decor:rug.cozy.0",
    "outfit:hat.beret",
    "skin:mint",
    "decor:shelf.retro.1",
    "room:study",
    "decor:plant.garden.2",
    "outfit:neck.scarf",
)

private fun focus(h: Harness, minutes: Int, tag: String, deep: Boolean) {
    h.controller.startSession(minutes, countUp = false, tag = tag)
    h.advance(minutes * 60_000L)
    check(h.phase() is Phase.Rewarded) { "expected a reward for a $minutes min session" }
    h.controller.dismissResult() // reward -> break offer
    if (minutes >= 50) {
        h.controller.startBreak(5)
        h.advance(5 * 60_000L)
    } else {
        h.controller.dismissResult() // skip the break
    }
}

private fun shop(h: Harness) {
    for (item in wishlist) {
        val state = h.state()
        if (state.owns(item) || !state.canAfford(item)) continue
        if (!h.controller.buy(item)) continue
        val decor = Catalog.decor(item)
        if (decor != null) {
            h.advance(1)
            val slot = Catalog.rooms.filter { h.state().roomUnlocked(it.id) }
                .flatMap { it.slots }
                .firstOrNull { it.kind == decor.kind }
            if (slot != null) h.controller.equip(slot.id, item)
        }
    }
}

private fun printReport(h: Harness, dir: File, now: Long) {
    val state = h.state()
    val tz = TimeZone.currentSystemDefault()
    val stats = Stats(state, tz, h.controller.now())
    val completed = state.sessions.count { it.session.completed && it.session.focusedSec >= 300 }
    val abandoned = state.sessions.count { !it.session.completed }
    val spent = state.earnedTotal - state.acorns

    println("Focus Buddy - one-week simulation")
    println("data dir          : $dir (temporary)")
    println("sessions          : $completed completed, $abandoned abandoned, ${state.sessions.size} recorded")
    println("focused           : ${stats.totalMinutes} min over 7 simulated days")
    println("acorns            : earned ${state.earnedTotal}, spent $spent, balance ${state.acorns}")
    println("owned             : ${state.owned.size - Catalog.starterOwned.size} items beyond the starter set")
    println("equipped slots    : ${state.equipped.size}")
    println("streak            : current ${stats.currentStreak}, best ${stats.bestStreak}")
    println()
    println("per-day minutes")
    val tracker = stats.tracker(7)
    for ((date, mark) in tracker) {
        println("  $date  ${stats.minutesOn(date).toString().padStart(4)} min  ${mark.name.lowercase()}")
    }
    println()
    println("per-tag minutes (year)")
    stats.byTag(StatsRange.YEAR).forEach { (tag, min) ->
        println("  ${state.tags[tag]?.name ?: tag}: $min min")
    }
    println()
    println("28-day tracker (oldest -> today): " + stats.tracker().joinToString("") {
        when (it.second) {
            buddy.core.DayMark.NONE -> "."
            buddy.core.DayMark.FOCUS -> "o"
            buddy.core.DayMark.DEEP -> "O"
        }
    })
    println()
    // Backup round trip, as the Settings screen does it.
    val backup = h.controller.exportBackup()
    Harness(startMs = h.clock.now).use { restored ->
        val imported = restored.controller.importBackup(backup)
        println("backup round trip : ${backup.length} bytes, $imported events, state identical=${restored.state() == state}")
    }
    println("done (temporary data dir removed on exit)")
    check(state.sessions.isNotEmpty())
    check(stats.totalMinutes > 0)
    check(now > 0)
}
