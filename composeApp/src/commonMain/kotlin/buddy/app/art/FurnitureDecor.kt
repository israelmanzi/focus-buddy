package buddy.app.art

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import buddy.core.Catalog.Theme

// Larger isometric furniture: BED and DESK.

private val Linen = Color(0xFFFFFBF2)
private val Laptop = Color(0xFFCDD2D8)
private val Flame = Color(0xFFF8C85A)

// ---------------------------------------------------------------- BED

internal fun Pen.bed(t: Theme, p: Pal) {
    val iso = Iso()
    val c = iso.p(36, 23)
    shadow(c.x, c.y + 3, 58, 26)

    // headboard on the plane x = 0, drawn in (u = y, v = -z) coords with a thickness copy behind it
    fun headboard(path: Path, color: Color, deco: (Pen.() -> Unit)? = null) {
        shape(iso.side(path, -4), color.dark(0.25f))
        val front = iso.side(path, 0)
        fill(front, color)
        if (deco != null) clip(front, deco)
        line(front, Ink, 1f)
    }
    when (t) {
        Theme.BASIC -> headboard(rect(-1, -44, 47, -2, 5), p.wood) {
            for (u in listOf(12, 23, 34)) line(iso.side(lines(u, -40, u, -6), 0), Ink, 0.4f, 0.6f)
        }
        Theme.COZY -> headboard(Path().apply {
            moveTo(-1f, -2f); lineTo(-1f, -34f)
            for (i in 0 until 3) quadraticTo(-1f + i * 16f + 8f, -50f, -1f + (i + 1) * 16f, -36f)
            lineTo(47f, -2f); close()
        }, p.main.dark(0.1f)) {
            for (u in listOf(7, 23, 39)) for (v in listOf(-34, -24)) {
                val o = iso.p(0, u, -v); dot(o.x, o.y, 1.2f, p.main.dark(0.4f))
            }
        }
        Theme.GARDEN -> {
            for (i in 0 until 6) {
                val u = 2 + i * 8.6f
                shape(iso.side(leaf(u, -38, u + (if (i % 2 == 0) -4 else 4), -54, 4), 0), if (i % 2 == 0) p.main else p.main.light(0.25f), 0.6f)
            }
            headboard(rect(-1, -42, 47, -2, 8), p.wood) {
                fill(iso.side(flower(23, -28, 9, 6), 0), p.pop)
                dot(iso.p(0, 23, 28).x, iso.p(0, 23, 28).y, 2f, p.accent2)
            }
        }
        Theme.RETRO -> headboard(arch(-1, -52, 47, -2), p.accent) {
            val cs = listOf(p.main, p.pop, p.cream)
            listOf(20, 14, 8).forEachIndexed { i, r -> shape(iso.side(oval(23, -26, r).minus(rect(-10, -26, 60, 10)), 0), cs[i], 0.4f) }
        }
        Theme.NIGHT -> headboard(arch(-1, -52, 47, -2), p.main.dark(0.15f)) {
            fill(iso.side(crescent(23, -36, 7), 0), p.accent2)
            listOf(Triple(8, -24, 2.6f), Triple(38, -28, 2.2f), Triple(14, -42, 1.8f), Triple(33, -44, 2f)).forEach { (u, v, r) ->
                fill(iso.side(star(u, v, r), 0), p.accent)
            }
        }
    }

    val frame = if (t == Theme.RETRO) p.accent else if (t == Theme.NIGHT) p.wood else p.wood.dark(0.05f)
    for ((x, y) in listOf(68 to 0, 0 to 42, 68 to 42)) box(iso, x, y, 0, 4, 4, 5, frame.dark(0.1f), r = 0.6f)
    box(iso, 0, 0, 4, 72, 46, 11, frame, r = 2)
    box(iso, 1, 1, 15, 70, 44, 7, Linen, r = 2.5f)
    box(iso, 3, 4, 22, 15, 18, 5, Linen, r = 4)
    box(iso, 3, 24, 22, 15, 18, 5, if (t == Theme.COZY) p.accent else Linen, r = 4)

    val zt = 25.5f
    val blanket = box(iso, 24, -1, 12, 49, 48, zt - 12, p.main, r = 3)
    // pattern on the top (floor coords at zt), front (y = 47) and side (x = 73) faces
    fun stripeX(x0: Float, x1: Float, color: Color, alpha: Float = 1f) {
        fill(iso.floor(rect(x0, -1, x1, 47), zt), color, alpha)
        fill(iso.front(rect(x0, -zt, x1, -12), 47), color.dark(0.05f), alpha)
    }
    fun stripeY(y0: Float, y1: Float, color: Color, alpha: Float = 1f) {
        fill(iso.floor(rect(24, y0, 73, y1), zt), color, alpha)
        fill(iso.side(rect(y0, -zt, y1, -12), 73), color.dark(0.15f), alpha)
    }
    clip(blanket) {
        when (t) {
            Theme.BASIC -> { stripeX(60f, 63f, p.pop); stripeX(65f, 66.5f, p.pop) }
            Theme.COZY -> {
                for (i in 0 until 6) stripeX(33f + i * 7f, 35.5f + i * 7f, p.pop, 0.45f)
                for (i in 0 until 7) stripeY(2f + i * 7f, 4.5f + i * 7f, p.pop, 0.45f)
                for (i in 0 until 6) stripeX(37f + i * 7f, 37.8f + i * 7f, p.cream, 0.6f)
            }
            Theme.GARDEN -> {
                for (i in 0 until 4) for (j in 0 until 4) if ((i + j) % 2 == 0) {
                    fill(iso.floor(flower(38 + i * 10, 5 + j * 11, 5), zt), p.pop)
                    fill(iso.floor(oval(38 + i * 10, 5 + j * 11, 1.4f), zt), p.accent2)
                } else fill(iso.floor(leaf(36 + i * 10, 7 + j * 11, 40 + i * 10, 3 + j * 11, 1.8f), zt), p.main.dark(0.2f))
                for (i in 0 until 4) fill(iso.front(flower(36 + i * 11, -18, 3.5f), 47), p.pop)
            }
            Theme.RETRO -> { stripeX(40f, 46f, p.pop); stripeX(50f, 55f, p.cream); stripeX(59f, 63f, p.accent) }
            Theme.NIGHT -> {
                fill(iso.floor(crescent(52, 20, 7), zt), p.accent2)
                for ((x, y) in listOf(38 to 8, 64 to 36, 44 to 38, 66 to 8, 56 to 2)) fill(iso.floor(star(x, y, 2.6f), zt), p.accent)
                for (x in listOf(34, 48, 62)) fill(iso.front(star(x, -18, 2.4f), 47), p.accent)
            }
        }
    }
    line(blanket, Ink, 1f)
    box(iso, 24, -1.5f, 12, 7, 49, zt - 11.5f, if (t == Theme.COZY) p.cream else Linen, r = 3)
    if (t == Theme.NIGHT) {
        val o = iso.p(12, 32, 27)
        shape(crescent(o.x, o.y - 3, 7, 0.45f), p.accent2, 0.7f)
    }
}

// ---------------------------------------------------------------- DESK

internal fun Pen.desk(t: Theme, p: Pal) {
    val iso = Iso()
    val c = iso.p(33, 15)
    shadow(c.x, c.y + 2, 46, 21)
    val top = when (t) { Theme.RETRO -> p.accent2; Theme.NIGHT -> p.wood; else -> p.wood }
    val legs = when (t) { Theme.GARDEN -> p.main.dark(0.15f); Theme.RETRO -> p.main; Theme.NIGHT -> p.wood.dark(0.15f); else -> p.wood.dark(0.1f) }
    val drawers = when (t) { Theme.BASIC -> p.main; Theme.COZY -> p.main; Theme.GARDEN -> p.wood; Theme.RETRO -> p.main; Theme.NIGHT -> p.main }
    val round = if (t == Theme.RETRO) 3f else 1.2f

    box(iso, 2, 2, 0, 4, 26, 34, legs, r = round * 0.6f)
    box(iso, 44, 2, 0, 20, 26, 34, drawers, r = round)
    for ((v0, v1) in listOf(-31f to -20f, -18f to -5f)) shape(iso.front(rect(46, v0, 62, v1, 1.5f), 28), drawers.light(0.2f), 0.5f)
    for (z in listOf(25.5f, 11.5f)) {
        val k = iso.p(54, 28, z)
        if (t == Theme.RETRO) shape(oval(k.x, k.y, 2), p.pop, 0.5f) else shape(oval(k.x, k.y, 1.4f, 1.1f), p.accent.takeIf { t == Theme.NIGHT } ?: Ink.light(0.2f), 0.4f)
    }
    box(iso, 0, 0, 34, 66, 30, 4, top, r = round)

    // computer
    if (t == Theme.RETRO) {
        box(iso, 8, 4, 38, 20, 16, 15, p.cream, r = 3)
        val scr = iso.front(rect(10, -51, 26, -41, 3), 20)
        shape(scr, Color(0xFF2E5B57), 0.6f)
        clip(scr) { for (i in 0 until 3) fill(iso.front(rect(12, -48 + i * 3f, 20 - i * 2f, -47 + i * 3f), 20), Color(0xFF8FF0C8)) }
        box(iso, 9, 22, 38, 18, 6, 1.6f, p.cream.dark(0.08f), r = 1)
    } else {
        val screenFill = when (t) { Theme.NIGHT -> p.pop.light(0.3f); Theme.COZY -> p.skyLow; else -> p.sky }
        val lid = if (t == Theme.COZY) p.cream.dark(0.08f) else Laptop
        if (t == Theme.NIGHT) { val g = iso.p(19, 6, 46); glow(g.x, g.y, 26, p.pop, 0.5f) }
        shape(iso.front(rect(8, -54, 30, -39, 1.6f), 5), lid.dark(0.2f), 0.8f)
        shape(iso.front(rect(8, -54, 30, -39, 1.6f), 6), lid, 0.9f)
        shape(iso.front(rect(10, -52, 28, -41, 1), 6), screenFill, 0.5f)
        val shineL = iso.front(lines(12, -44, 15, -50), 6); shine(shineL)
        box(iso, 8, 6, 38, 22, 15, 1.6f, lid, r = 1)
        fill(iso.floor(rect(10, 9, 28, 15, 1), 39.6f), lid.dark(0.18f))
    }

    // books
    box(iso, 38, 5, 38, 15, 11, 3, p.pop, r = 0.8f)
    box(iso, 39, 6, 41, 13, 9, 3, if (t == Theme.BASIC) p.cream else p.main.light(0.2f), r = 0.8f)
    box(iso, 38.5f, 5, 44, 14, 11, 2.5f, p.accent.takeIf { t == Theme.RETRO || t == Theme.GARDEN } ?: p.main.dark(0.15f), r = 0.8f)

    // front-right item
    val m = iso.p(59, 25, 38)
    when (t) {
        Theme.BASIC, Theme.COZY -> {
            if (t == Theme.COZY) {
                line(curve(m.x - 2, m.y - 12, m.x - 5, m.y - 17, m.x - 1, m.y - 22), Ink, 0.4f, 0.5f)
                line(curve(m.x + 2, m.y - 12, m.x - 1, m.y - 17, m.x + 3, m.y - 22), Ink, 0.4f, 0.5f)
            }
            line(oval(m.x + 5.5f, m.y - 5, 2.4f, 2.8f), Ink, 2f); line(oval(m.x + 5.5f, m.y - 5, 2.4f, 2.8f), p.cream, 0.9f)
            cylinder(m.x, m.y, 4.5f, 9, if (t == Theme.COZY) p.main else p.cream)
        }
        Theme.GARDEN -> {
            for (a in listOf(-150, -120, -90, -60, -30)) {
                val tip = polar(at(m.x, m.y - 10), 12, a)
                shape(leaf(at(m.x, m.y - 10), tip, 3), p.leaf, 0.5f)
            }
            pot(m.x, m.y + 2, 11, 8, 11, p.accent)
        }
        Theme.RETRO -> {
            // lava lamp
            shape(Path().apply { moveTo(m.x - 5, m.y); lineTo(m.x + 5, m.y); lineTo(m.x + 3, m.y - 6); lineTo(m.x - 3, m.y - 6); close() }, p.accent, 0.7f)
            shape(poly(m.x - 3, m.y - 6, m.x + 3, m.y - 6, m.x + 4.5f, m.y - 20, m.x - 4.5f, m.y - 20, r = 2), p.pop.light(0.35f), 0.7f)
            fill(oval(m.x - 0.5f, m.y - 11, 1.8f, 2.6f), p.main); fill(oval(m.x + 1, m.y - 16, 1.2f, 1.6f), p.main)
            shape(poly(m.x - 4.5f, m.y - 20, m.x + 4.5f, m.y - 20, m.x + 2, m.y - 25, m.x - 2, m.y - 25, r = 1), p.accent, 0.7f)
        }
        Theme.NIGHT -> {
            glow(m.x, m.y - 14, 18, p.glow, 0.6f)
            shape(rect(m.x - 3.5f, m.y - 10, m.x + 3.5f, m.y, 1.5f), p.accent2, 0.7f)
            shape(leaf(m.x, m.y - 11, m.x, m.y - 18, 1.8f), Flame, 0.45f)
            fill(sparkle(m.x + 12, m.y - 22, 3), p.accent)
        }
    }
}
