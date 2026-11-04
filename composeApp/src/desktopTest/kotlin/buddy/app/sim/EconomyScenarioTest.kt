package buddy.app.sim

import buddy.core.Catalog
import buddy.core.CompanionStyle
import buddy.core.Rules
import buddy.core.Stats
import buddy.core.StatsRange
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Economy, ownership, equipping and tag rules, exercised through the controller. */
class EconomyScenarioTest {

    private val rugCozy = "decor:rug.cozy.0"        // 60
    private val lampRetro = "decor:lamp.retro.0"    // 100
    private val lampBasic = "decor:lamp.basic.0"    // 30

    @Test
    fun insufficientFundsRejectsPurchaseWithoutRecordingIt() {
        Harness().use { h ->
            h.seedSession(25) // 30 acorns
            assertEquals(30, h.state().acorns)

            assertFalse(h.controller.buy(rugCozy))
            assertEquals(30, h.state().acorns)
            assertFalse(h.state().owns(rugCozy))
            assertEquals(1, h.lineCount()) // only the seeded session
        }
    }

    @Test
    fun doubleBuyIsIdempotent() {
        Harness().use { h ->
            h.seedSession(210) // 252 acorns
            assertTrue(h.controller.buy(rugCozy))
            assertEquals(192, h.state().acorns)
            assertTrue(h.state().owns(rugCozy))

            assertFalse(h.controller.buy(rugCozy))
            assertEquals(192, h.state().acorns)
            assertEquals(1, h.state().owned.count { it == rugCozy })
            assertEquals(2, h.lineCount()) // session + one buy
        }
    }

    @Test
    fun buyThenEquipInTheSameRoomWorks() {
        Harness().use { h ->
            h.seedSession(210)
            assertTrue(h.controller.buy(rugCozy))
            h.advance(1) // realistic: the second tap happens after the first event
            h.controller.equip("living.rug", rugCozy)
            assertEquals(rugCozy, h.state().equipped["living.rug"])
        }
    }

    @Test
    fun wrongSlotEquipIsIgnored() {
        Harness().use { h ->
            h.seedSession(210)
            assertTrue(h.controller.buy(lampRetro))
            h.advance(1)
            val before = h.state().equipped["living.rug"]

            h.controller.equip("living.rug", lampRetro) // lamp into rug slot
            assertEquals(before, h.state().equipped["living.rug"])

            h.controller.equip("living.lamp", lampRetro)
            assertEquals(lampRetro, h.state().equipped["living.lamp"])
        }
    }

    @Test
    fun equipmentCannotBePlacedInALockedRoom() {
        Harness().use { h ->
            h.seedSession(300) // 360 acorns
            assertTrue(h.controller.buy(lampRetro))
            h.advance(1)

            // The Study is still locked: the valid lamp is ignored there.
            h.controller.equip("study.lamp", lampRetro)
            assertNull(h.state().equipped["study.lamp"])
            assertFalse(h.state().roomUnlocked("study"))

            assertTrue(h.controller.buy("room:study"))
            h.advance(1)
            h.controller.equip("study.lamp", lampRetro)
            assertEquals(lampRetro, h.state().equipped["study.lamp"])
        }
    }

    @Test
    fun equippingAnUnownedItemIsIgnored() {
        Harness().use { h ->
            val before = h.state().equipped["living.lamp"]
            h.controller.equip("living.lamp", lampBasic)
            assertEquals(before, h.state().equipped["living.lamp"])
            assertNull(h.state().equipped["living.lamp"])
        }
    }

    @Test
    fun clearingASlotRemovesIt() {
        Harness().use { h ->
            h.seedSession(210)
            assertTrue(h.controller.buy(rugCozy))
            h.advance(1)
            h.controller.equip("living.rug", rugCozy)
            assertNotNull(h.state().equipped["living.rug"])

            h.advance(1)
            h.controller.equip("living.rug", null)
            assertNull(h.state().equipped["living.rug"])
        }
    }

    @Test
    fun unknownItemsCannotBeBought() {
        Harness().use { h ->
            h.seedSession(300)
            assertFalse(h.controller.buy("decor:nonexistent.rainbow.9"))
            assertFalse(h.controller.buy("outfit:hat.nope"))
            assertFalse(h.controller.buy(""))
        }
    }

    @Test
    fun starterOwnershipMatchesCatalog() {
        Harness().use { h ->
            val s = h.state()
            assertEquals(Catalog.starterOwned, s.owned)
            assertEquals(Catalog.starterDecor, s.equipped)
            assertTrue(s.owns("skin:classic"))
            assertTrue(s.roomUnlocked("living"))
            assertFalse(s.roomUnlocked("study"))
        }
    }

    @Test
    fun unownedSkinCannotBeWorn() {
        Harness().use { h ->
            h.controller.setStyle("Mochi", "mint", null, null, null)
            assertEquals(CompanionStyle(), h.state().style)
        }
    }

    @Test
    fun buyingASkinThenWearingItWorks() {
        Harness().use { h ->
            h.seedSession(210) // 252
            assertTrue(h.controller.buy("skin:mint"))
            h.advance(1)
            h.controller.setStyle("Mochi", "mint", null, null, null)
            assertEquals("mint", h.state().style.skin)
            assertEquals("Mochi", h.state().style.name)
        }
    }

    @Test
    fun outfitMustBeOwnedAndAllFieldsMustBeValid() {
        Harness().use { h ->
            h.seedSession(300) // 360
            assertTrue(h.controller.buy("outfit:hat.beret"))
            h.advance(1)
            h.controller.setStyle("Pip", "classic", "hat.beret", null, null)
            assertEquals("hat.beret", h.state().style.hat)

            // An unowned neck item makes the whole style event invalid.
            h.controller.setStyle("Other", "classic", "hat.beret", "neck.scarf", null)
            assertEquals("Pip", h.state().style.name)
            assertEquals("hat.beret", h.state().style.hat)
            assertNull(h.state().style.neck)
        }
    }

    @Test
    fun buyingTheSameOutfitTwiceIsIdempotent() {
        Harness().use { h ->
            h.seedSession(210)
            assertTrue(h.controller.buy("outfit:hat.beret"))
            assertFalse(h.controller.buy("outfit:hat.beret"))
            assertEquals(1, h.state().owned.count { it == "outfit:hat.beret" })
        }
    }

    @Test
    fun styleNameIsCappedAt24CharsAndBlankFallsBackToPip() {
        Harness().use { h ->
            val long = "x".repeat(40)
            h.controller.setStyle(long, "classic", null, null, null)
            assertEquals("x".repeat(24), h.state().style.name)

            h.controller.setStyle("   ", "classic", null, null, null)
            assertEquals("Pip", h.state().style.name) // whitespace-only is blank
            h.controller.setStyle("", "classic", null, null, null)
            assertEquals("Pip", h.state().style.name)
        }
    }

    @Test
    fun tagsCanBeCreatedArchivedAndUsedInASession() {
        Harness().use { h ->
            val color = 0xFF6C9BD2
            h.controller.upsertTag(null, "Study", color)
            val tag = h.state().tags.values.single { it.name == "Study" }
            assertEquals(8, tag.id.length)
            assertFalse(tag.archived)

            h.controller.startSession(25, countUp = false, tag = tag.id)
            h.advance(25 * 60_000L)
            assertEquals(30, h.state().acorns)
            assertEquals(tag.id, h.state().sessions.single().session.tag)

            val stats = Stats(h.state(), TimeZone.currentSystemDefault(), h.controller.now())
            assertEquals(listOf(tag.id to 25), stats.byTag(StatsRange.WEEK))

            h.controller.upsertTag(tag.id, tag.name, tag.color, archived = true)
            assertTrue(h.state().tags.getValue(tag.id).archived)
        }
    }

    @Test
    fun tagNamesAreCappedAt24Chars() {
        Harness().use { h ->
            h.controller.upsertTag(null, "y".repeat(40), 0xFF000000)
            assertEquals("y".repeat(24), h.state().tags.values.single { it.id != Rules.DEFAULT_TAG }.name)
        }
    }

    @Test
    fun archivedTagStillResolvesForOldSessions() {
        Harness().use { h ->
            h.controller.upsertTag("old", "Old work", 0xFF112233, archived = true)
            h.controller.startSession(25, countUp = false, tag = "old")
            h.advance(25 * 60_000L)
            val stats = Stats(h.state(), TimeZone.currentSystemDefault(), h.controller.now())
            assertEquals(listOf("old" to 25), stats.byTag(StatsRange.DAY))
            assertEquals("Old work", h.controller.tagName("old"))
        }
    }
}
