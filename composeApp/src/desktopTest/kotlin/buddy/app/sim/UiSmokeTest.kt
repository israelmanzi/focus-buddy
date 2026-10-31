package buddy.app.sim

import buddy.app.Phase
import buddy.app.renderPng
import buddy.app.ui.App
import buddy.app.ui.Sheet
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders every screen and the awkward edge states through the same ImageComposeScene harness
 * used for `BUDDY_PREVIEW`. PNGs go to `composeApp/build/ui-smoke/` (never docs/).
 */
class UiSmokeTest {

    private val outDir = File("build/ui-smoke")
    private val rendered = mutableListOf<String>()

    @AfterTest
    fun report() {
        println("UI smoke rendered ${rendered.size} screens to ${outDir.absolutePath}")
        assertTrue(rendered.isNotEmpty())
    }

    private fun shot(
        name: String,
        sheet: Sheet = Sheet.NONE,
        decorating: Boolean = false,
        width: Int = 400,
        height: Int = 860,
        setup: (Harness) -> Unit = {},
    ) {
        val h = Harness()
        try {
            setup(h)
            val file = File(outDir, "$name.png")
            renderPng(file, width, height, 3_000_000_000L) {
                App(h.controller, sheet, initialDecorating = decorating)
            }
            assertTrue(file.length() > 500, "$name produced an empty PNG")
            rendered += name
        } finally {
            h.close()
        }
    }

    @Test
    fun rendersEveryScreenAndEdgeState() {
        outDir.deleteRecursively()
        outDir.mkdirs()

        // Plain states: zero events, zero acorns.
        shot("home-zero-events")
        shot("focus-setup", sheet = Sheet.FOCUS)
        shot("shop-zero-acorns", sheet = Sheet.SHOP)
        shot("editor-default", sheet = Sheet.EDITOR)
        shot("stats-zero-events", sheet = Sheet.STATS)
        shot("settings-default", sheet = Sheet.SETTINGS)
        shot("rooms-all-locked", sheet = Sheet.ROOMS)
        shot("decorating", decorating = true)

        // Huge acorn count.
        shot("home-huge-acorns") { h -> h.import(h.session(5_000_000)) }
        shot("shop-huge-acorns", sheet = Sheet.SHOP) { h -> h.import(h.session(5_000_000)) }
        shot("stats-huge-session", sheet = Sheet.STATS) { h -> h.import(h.session(5_000_000)) }

        // Long names and long tag lists.
        val longName = "N".repeat(24)
        shot("editor-24-char-name", sheet = Sheet.EDITOR) { h ->
            h.controller.setStyle(longName, "classic", null, null, null)
        }
        shot("focus-long-tag-list", sheet = Sheet.FOCUS) { h -> seedTags(h) }
        shot("settings-long-tag-list", sheet = Sheet.SETTINGS) { h -> seedTags(h) }
        shot("focus-24-char-tag", sheet = Sheet.FOCUS) { h ->
            h.controller.upsertTag(null, "T".repeat(24), 0xFF6C9BD2)
        }

        // Running sessions, including the maximum duration and the grace-period overlay.
        shot("running-6-hours") { h ->
            h.controller.startSession(360, countUp = false, tag = "focus", deepFocus = false)
        }
        shot("running-25-min") { h ->
            h.controller.startSession(25, countUp = false, tag = "focus", deepFocus = false)
            h.advance(60_000)
        }
        shot("running-grace-active") { h ->
            h.controller.startSession(25, countUp = false, tag = "focus", deepFocus = false)
            h.advance(60_000)
            h.controller.onAppBackgrounded()
            assertTrue((h.phase() as Phase.Running).session.awaySince != null)
        }
        shot("running-count-up") { h ->
            h.controller.startSession(25, countUp = true, tag = "focus", deepFocus = false)
            h.advance(10 * 60_000L)
        }

        // End-of-session cards and break states.
        shot("rewarded") { h ->
            h.controller.startSession(25, countUp = false, tag = "focus", deepFocus = false)
            h.advance(25 * 60_000L)
            assertTrue(h.phase() is Phase.Rewarded)
        }
        shot("interrupted") { h ->
            h.controller.startSession(25, countUp = false, tag = "focus", deepFocus = false)
            h.advance(200_000) // past 10 % of 25 minutes, so the abandon is kept
            h.controller.stopSession()
            assertTrue(h.phase() is Phase.Interrupted)
        }
        shot("break-offer") { h ->
            h.controller.startSession(25, countUp = false, tag = "focus", deepFocus = false)
            h.advance(25 * 60_000L)
            h.controller.dismissResult()
            assertTrue(h.phase() is Phase.BreakOffer)
        }
        shot("break-screen") { h -> h.controller.startBreak(5) }

        // A room with its whole catalogue unlocked, and a 4-item equipped room.
        shot("decorate-unlocked") { h ->
            h.import(h.session(5000))
            for (room in listOf("study", "bedroom", "porch")) h.controller.buy("room:$room")
            h.advance(2)
        }
        shot("stats-with-history", sheet = Sheet.STATS) { h ->
            h.import(
                h.session(25, endedAt = h.clock.now - 2 * 86_400_000L, deep = true),
                h.session(50, endedAt = h.clock.now - 86_400_000L),
                h.session(25, endedAt = h.clock.now - 3_600_000L),
            )
            h.controller.upsertTag("study", "Study", 0xFF6C9BD2)
        }
    }

    private fun seedTags(h: Harness) {
        repeat(60) { i ->
            h.controller.upsertTag(
                id = "tag-$i",
                name = "Tag ${i.toString().padStart(2, '0')} long name".take(24),
                color = 0xFF000000 or (i * 1_000L),
            )
        }
    }
}
