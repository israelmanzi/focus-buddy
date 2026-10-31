package buddy.app.art

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import buddy.core.Catalog
import buddy.core.Catalog.DecorKind

private val OUTLINE = Color(0xFF4A342E)

data class RoomPalette(
    val leftWall: Color, val rightWall: Color, val floor: Color, val floorLine: Color,
    val trim: Color, val outdoor: Boolean = false,
)

fun roomPalette(roomId: String) = when (roomId) {
    "study" -> RoomPalette(Color(0xFFD6E3C8), Color(0xFFE4EDD8), Color(0xFFB98B5E), Color(0xFF9C7048), Color(0xFF8DAA7A))
    "bedroom" -> RoomPalette(Color(0xFFEBD5E0), Color(0xFFF4E3EB), Color(0xFFE9D9C0), Color(0xFFD4C0A0), Color(0xFFC79AB2))
    "porch" -> RoomPalette(Color(0xFFE8D2A8), Color(0xFFEFDDB8), Color(0xFFC79A66), Color(0xFFA77B4C), Color(0xFF9DBF85), outdoor = true)
    else -> RoomPalette(Color(0xFFF1E3BF), Color(0xFFF8EDD5), Color(0xFFD9A86C), Color(0xFFBF8C52), Color(0xFF9BC9C2))
}

/** How a slot sits in the room: on a wall (flat, skewed onto it) or standing on the floor. */
private enum class Plane { LEFT_WALL, RIGHT_WALL, FLOOR, FLAT_FLOOR }

private data class SlotSpot(val plane: Plane, val a: Float, val b: Float, val w: Float, val h: Float)

/**
 * Wall spots: a = position along the wall (0 = back corner, 1 = outer corner), b = height (0 = floor, 1 = top).
 * Floor spots: a,b = iso coords (0,0 back corner, 1 along right edge / left edge).
 * w,h are fractions of the scene width.
 */
private fun spot(kind: DecorKind, roomId: String): SlotSpot = when (kind) {
    DecorKind.WINDOW -> SlotSpot(Plane.RIGHT_WALL, 0.42f, 0.58f, 0.24f, 0.26f)
    DecorKind.SHELF -> if (roomId == "porch") SlotSpot(Plane.RIGHT_WALL, 0.45f, 0.6f, 0.22f, 0.16f)
        else SlotSpot(Plane.RIGHT_WALL, 0.82f, 0.62f, 0.2f, 0.15f)
    DecorKind.WALL_ART -> SlotSpot(Plane.LEFT_WALL, 0.6f, 0.6f, 0.17f, 0.2f)
    DecorKind.CLOCK -> SlotSpot(Plane.LEFT_WALL, 0.2f, 0.75f, 0.11f, 0.11f)
    DecorKind.RUG -> SlotSpot(Plane.FLAT_FLOOR, 0.55f, 0.55f, 0.5f, 0.25f)
    DecorKind.PLANT -> SlotSpot(Plane.FLOOR, 0.1f, 0.12f, 0.14f, 0.22f)
    DecorKind.LAMP -> SlotSpot(Plane.FLOOR, 0.82f, 0.1f, 0.13f, 0.3f)
    DecorKind.SEAT -> SlotSpot(Plane.FLOOR, 0.18f, 0.72f, 0.24f, 0.22f)
    DecorKind.DESK -> SlotSpot(Plane.FLOOR, 0.5f, 0.1f, 0.28f, 0.24f)
    DecorKind.BED -> SlotSpot(Plane.FLOOR, 0.2f, 0.45f, 0.36f, 0.26f)
    DecorKind.TABLE -> if (roomId == "bedroom") SlotSpot(Plane.FLOOR, 0.12f, 0.88f, 0.14f, 0.15f)
        else SlotSpot(Plane.FLOOR, 0.62f, 0.2f, 0.16f, 0.16f)
}

class RoomGeometry(val size: Size) {
    val w = size.width
    val halfW = w * 0.47f
    val halfH = halfW / 2f
    val wallH = minOf(size.height * 0.42f, w * 0.62f)
    val center = Offset(w / 2f, size.height - halfH - size.height * 0.04f)
    val back = Offset(center.x, center.y - halfH)
    val left = Offset(center.x - halfW, center.y)
    val right = Offset(center.x + halfW, center.y)
    val front = Offset(center.x, center.y + halfH)

    private val shells = HashMap<String, RoomShellArt>()

    /** The static shell paths/segments for [roomId], built once per geometry. */
    internal fun shell(roomId: String): RoomShellArt = shells.getOrPut(roomId) { RoomShellArt(this, roomId) }

    /** i: back→right edge, j: back→left edge, both 0..1. */
    fun floor(i: Float, j: Float) = Offset(back.x + i * (right.x - back.x) + j * (left.x - back.x), back.y + i * (right.y - back.y) + j * (left.y - back.y))

    fun wall(leftWall: Boolean, along: Float, height: Float): Offset {
        val end = if (leftWall) left else right
        return Offset(back.x + (end.x - back.x) * along, back.y + (end.y - back.y) * along - wallH * height)
    }

    fun slotRect(slot: Catalog.Slot, roomId: String): Rect {
        val s = spot(slot.kind, roomId)
        val sz = Size(s.w * w, s.h * w)
        return when (s.plane) {
            Plane.LEFT_WALL, Plane.RIGHT_WALL -> {
                val c = wall(s.plane == Plane.LEFT_WALL, s.a, s.b)
                Rect(Offset(c.x - sz.width / 2, c.y - sz.height / 2), sz)
            }
            Plane.FLAT_FLOOR -> { val c = floor(s.a, s.b); Rect(Offset(c.x - sz.width / 2, c.y - sz.height / 2), sz) }
            Plane.FLOOR -> { val c = floor(s.a, s.b); Rect(Offset(c.x - sz.width / 2, c.y - sz.height), sz) }
        }
    }

    fun isWall(slot: Catalog.Slot, roomId: String) = spot(slot.kind, roomId).plane.let { it == Plane.LEFT_WALL || it == Plane.RIGHT_WALL }
    fun isRug(slot: Catalog.Slot, roomId: String) = spot(slot.kind, roomId).plane == Plane.FLAT_FLOOR
    fun skew(slot: Catalog.Slot, roomId: String) = when (spot(slot.kind, roomId).plane) {
        Plane.LEFT_WALL -> -0.5f
        Plane.RIGHT_WALL -> 0.5f
        else -> 0f
    }
}

/**
 * Static parts of a room shell. Paths and floor-line endpoints are built once; [drawRoomShell]
 * then only issues draw calls, so the animated home screen allocates nothing per frame here.
 */
internal class RoomShellArt(g: RoomGeometry, roomId: String) {
    val palette = roomPalette(roomId)
    private val outdoor = palette.outdoor
    private val top = { o: Offset -> Offset(o.x, o.y - g.wallH) }

    val leftWall: Path = Path().apply {
        moveTo(g.left.x, g.left.y); lineTo(g.back.x, g.back.y); lineTo(top(g.back).x, top(g.back).y); lineTo(top(g.left).x, top(g.left).y); close()
    }
    val rightWall: Path = Path().apply {
        moveTo(g.back.x, g.back.y); lineTo(g.right.x, g.right.y); lineTo(top(g.right).x, top(g.right).y); lineTo(top(g.back).x, top(g.back).y); close()
    }
    val floor: Path = Path().apply {
        moveTo(g.back.x, g.back.y); lineTo(g.right.x, g.right.y); lineTo(g.front.x, g.front.y); lineTo(g.left.x, g.left.y); close()
    }
    val slab: Path = Path().apply {
        val s = g.w * 0.025f
        moveTo(g.left.x, g.left.y); lineTo(g.front.x, g.front.y); lineTo(g.right.x, g.right.y)
        lineTo(g.right.x, g.right.y + s); lineTo(g.front.x, g.front.y + s); lineTo(g.left.x, g.left.y + s); close()
    }

    /** Posts (base) and rails for the garden porch, in draw order. */
    val fencePosts: List<Offset> = if (!outdoor) emptyList() else buildList {
        for (wall in listOf(true, false)) {
            val end = if (wall) g.left else g.right
            for (k in 0..8) {
                val f = k / 8f
                add(Offset(g.back.x + (end.x - g.back.x) * f, g.back.y + (end.y - g.back.y) * f))
            }
        }
    }
    val fenceRails: List<Pair<Offset, Offset>> = if (!outdoor) emptyList() else buildList {
        for (wall in listOf(true, false)) {
            val end = if (wall) g.left else g.right
            for (h in listOf(0.12f, 0.28f)) {
                add(Offset(g.back.x, g.back.y - g.wallH * h) to Offset(end.x, end.y - g.wallH * h))
            }
        }
    }
    val wallDots: List<Offset> = if (outdoor) emptyList() else buildList {
        for (k in 0..5) for (m in 1..4) {
            for (lw in listOf(true, false)) add(g.wall(lw, (k + 0.5f) / 6f, m / 5f))
        }
    }
    val planks: List<Pair<Offset, Offset>> = buildList {
        for (k in 1..11) {
            val f = k / 12f
            add(g.floor(f, 0f) to g.floor(f, 1f))
        }
    }
    val ticks: List<Pair<Offset, Offset>> = buildList {
        for (k in 0..11) for (m in 0..3) {
            val f = (k + 0.5f) / 12f
            val j = (m + (k % 2) * 0.5f) / 4f + 0.12f
            add(g.floor(f - 0.04f, j) to g.floor(f + 0.04f, j))
        }
    }
    val baseboardLeft = g.left.copy(y = g.left.y - g.w * 0.016f) to g.back.copy(y = g.back.y - g.w * 0.016f)
    val baseboardRight = g.back.copy(y = g.back.y - g.w * 0.016f) to g.right.copy(y = g.right.y - g.w * 0.016f)
    val topTrimLeft = top(g.left) to top(g.back)
    val topTrimRight = top(g.back) to top(g.right)
    val strokeWidth = g.w * 0.008f
    val outlineStroke = Stroke(strokeWidth)
}

fun DrawScope.drawRoomShell(g: RoomGeometry, roomId: String) {
    val a = g.shell(roomId)
    val p = a.palette
    val sw = a.strokeWidth
    val outline = OUTLINE

    drawPath(a.slab, p.trim)
    drawPath(a.slab, outline, style = a.outlineStroke)

    if (p.outdoor) {
        var i = 0
        while (i < a.fencePosts.size) {
            val b = a.fencePosts[i]
            val t = Offset(b.x, b.y - g.wallH * 0.35f)
            drawLine(p.leftWall, b, t, sw * 3.5f)
            drawLine(outline, b, t, sw * 0.6f)
            i++
        }
        i = 0
        while (i < a.fenceRails.size) {
            val (from, to) = a.fenceRails[i]
            drawLine(p.rightWall, from, to, sw * 2.5f)
            i++
        }
    } else {
        drawPath(a.leftWall, p.leftWall)
        drawPath(a.rightWall, p.rightWall)
        val dotColor = outline.copy(alpha = 0.06f)
        var i = 0
        while (i < a.wallDots.size) {
            drawCircle(dotColor, g.w * 0.006f, a.wallDots[i])
            i++
        }
        drawLine(p.trim, a.baseboardLeft.first, a.baseboardLeft.second, sw * 3)
        drawLine(p.trim, a.baseboardRight.first, a.baseboardRight.second, sw * 3)
        drawPath(a.leftWall, outline, style = a.outlineStroke)
        drawPath(a.rightWall, outline, style = a.outlineStroke)
        drawLine(p.trim, a.topTrimLeft.first, a.topTrimLeft.second, sw * 2.5f)
        drawLine(p.trim, a.topTrimRight.first, a.topTrimRight.second, sw * 2.5f)
    }

    drawPath(a.floor, p.floor)
    clipPath(a.floor) {
        var i = 0
        while (i < a.planks.size) {
            val (from, to) = a.planks[i]
            drawLine(p.floorLine, from, to, sw * 0.7f)
            i++
        }
        i = 0
        while (i < a.ticks.size) {
            val (from, to) = a.ticks[i]
            drawLine(p.floorLine, from, to, sw * 0.6f)
            i++
        }
    }
    drawPath(a.floor, outline, style = a.outlineStroke)
}


/** Draws a decor item inside a slot rect, skewed onto the wall when needed. */
fun DrawScope.drawSlotItem(g: RoomGeometry, slot: Catalog.Slot, roomId: String, item: Catalog.DecorItem, alpha: Float = 1f, night: Boolean = false) {
    val r = g.slotRect(slot, roomId)
    val skew = g.skew(slot, roomId)
    withTransform({
        if (skew != 0f) {
            // y' = y + skew * (x - centerX): shears the item onto the wall plane around its centre
            val m = Matrix()
            m.values[Matrix.SkewY] = skew
            m.values[Matrix.TranslateY] = -skew * r.center.x
            transform(m)
        }
    }) {
        if (alpha < 1f) {
            drawContext.canvas.saveLayer(Rect(Offset.Zero, size), androidx.compose.ui.graphics.Paint().apply { this.alpha = alpha })
            drawDecor(item, r.topLeft, r.size, night)
            drawContext.canvas.restore()
        } else drawDecor(item, r.topLeft, r.size, night)
    }
}
