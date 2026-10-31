package buddy.app.art

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import buddy.core.Catalog
import buddy.core.Catalog.DecorKind
import kotlin.math.min

/** Outline width relative to the target rect's smaller side. */
private const val OUTLINE = 0.035f

/** Built stickers; drawing happens on the UI thread, so a plain map is enough. */
private val cache = HashMap<Pair<Catalog.DecorItem, Boolean>, Sticker>()

internal fun decorSticker(item: Catalog.DecorItem, night: Boolean = false): Sticker = cache.getOrPut(item to night) {
    val p = palette(item.theme, item.variant)
    val t = item.theme
    Pen().apply {
        when (item.kind) {
            DecorKind.WALL_ART -> wallArt(t, p)
            DecorKind.WINDOW -> window(t, p, night)
            DecorKind.CLOCK -> clock(t, p)
            DecorKind.SHELF -> shelf(t, p)
            DecorKind.RUG -> rug(t, p)
            DecorKind.PLANT -> plant(t, p)
            DecorKind.LAMP -> lamp(t, p)
            DecorKind.SEAT -> seat(t, p)
            DecorKind.TABLE -> table(t, p)
            DecorKind.BED -> bed(t, p)
            DecorKind.DESK -> desk(t, p)
        }
    }.build()
}

/** Draws [item] fitted inside the rect (topLeft, size), bottom-centered (floor items stand on the rect's bottom edge). */
fun DrawScope.drawDecor(item: Catalog.DecorItem, topLeft: Offset, size: Size, night: Boolean = false) {
    if (size.width <= 0f || size.height <= 0f) return
    val s = decorSticker(item, night)
    val stroke = size.minDimension * OUTLINE
    val b = s.bounds
    val k = min((size.width - stroke) / b.width, (size.height - stroke) / b.height)
    if (k <= 0f) return
    val dx = topLeft.x + size.width / 2f - b.center.x * k
    val dy = topLeft.y + size.height - stroke / 2f - b.bottom * k
    translate(dx, dy) {
        scale(k, k, pivot = Offset.Zero) { render(s.ops, stroke / k) }
    }
}
