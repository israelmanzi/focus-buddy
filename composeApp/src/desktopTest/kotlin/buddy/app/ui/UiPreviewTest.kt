package buddy.app.ui

import buddy.app.Phase
import buddy.app.previewController
import buddy.app.renderPng
import buddy.core.Catalog
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders the main screens at the phone default, a wide/short desktop window and a very short
 * window, so layout regressions (clipping, overflow) are visible in `build/ui-preview/`.
 */
class UiPreviewTest {

    private val screens = listOf(
        "home" to Sheet.NONE,
        "focus" to Sheet.FOCUS,
        "shop" to Sheet.SHOP,
        "stats" to Sheet.STATS,
        "settings" to Sheet.SETTINGS,
        "rooms" to Sheet.ROOMS,
    )

    @Test
    fun renderResponsiveScreens() {
        val out = File("build/ui-preview")
        val sizes = listOf(400 to 820, 1000 to 700, 400 to 560, 360 to 900)
        for ((width, height) in sizes) {
            for ((name, sheet) in screens) {
                val c = previewController(File(out, "profiles/${width}x$height-$name"))
                renderPng(File(out, "${width}x$height-$name.png"), width, height, 3_000_000_000L) {
                    App(c, sheet)
                }
            }
        }
        assertTrue(File(out, "400x820-home.png").length() > 0)
        assertTrue(File(out, "1000x700-settings.png").length() > 0)
        assertTrue(File(out, "400x560-focus.png").length() > 0)
    }

    /** Dialogs and the decorate picker are not part of the BUDDY_PREVIEW screen set. */
    @Test
    fun renderDialogs() {
        val out = File("build/ui-preview")
        for ((width, height) in listOf(400 to 820, 400 to 560, 1000 to 700)) {
            val c = previewController(File(out, "profiles/${width}x$height-dialogs"))
            renderPng(File(out, "${width}x$height-reward.png"), width, height, 3_000_000_000L) {
                RewardCard(c, Phase.Rewarded(acorns = 12, minutes = 25))
            }
            renderPng(File(out, "${width}x$height-interrupted.png"), width, height, 3_000_000_000L) {
                InterruptedCard(c, Phase.Interrupted(focusedMin = 12))
            }
            renderPng(File(out, "${width}x$height-break-offer.png"), width, height, 3_000_000_000L) {
                BreakOffer(c)
            }
            renderPng(File(out, "${width}x$height-slot-picker.png"), width, height, 3_000_000_000L) {
                SlotPicker(c, "living", Catalog.room("living")!!.slots.first(), onPreview = {}, onClose = {})
            }
        }
        assertTrue(File(out, "400x560-reward.png").length() > 0)
    }
}
