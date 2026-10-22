package buddy.app.art

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import buddy.core.Catalog.Theme

// Wall items face the viewer flat.

private val Sun = Color(0xFFF8D46E)
private val White = Color(0xFFFFFDF8)

/** Picture-hanging string and nail above a frame whose top edge is at [top]. */
private fun Pen.hanger(cx: Number, top: Number, span: Number = 14) {
    val x = cx.f; val t = top.f; val s = span.f
    line(lines(x - s, t + 2, x, t - s * 0.7f, x + s, t + 2), Ink, 0.5f)
    dot(x, t - s * 0.7f, 2.2f)
}

private fun hill(x0: Number, x1: Number, base: Number, y0: Number, cx: Number, cy: Number, y1: Number) = Path().apply {
    moveTo(x0.f, base.f); lineTo(x0.f, y0.f); quadraticTo(cx.f, cy.f, x1.f, y1.f); lineTo(x1.f, base.f); close()
}

private fun Pen.sky(p: Path, top: Color, bottom: Color, y0: Number, y1: Number) =
    fill(p, Brush.verticalGradient(listOf(top, bottom), y0.f, y1.f), bounded = true)

// ---------------------------------------------------------------- WALL_ART

internal fun Pen.wallArt(t: Theme, p: Pal) {
    when (t) {
        Theme.BASIC -> {
            hanger(40, 0)
            shape(rect(0, 0, 80, 62, 4), p.wood)
            shape(rect(6, 6, 74, 56, 2), p.cream, 0.5f)
            val pic = rect(12, 12, 68, 50)
            sky(pic, p.sky, p.skyLow, 12, 50)
            clip(pic) {
                fill(oval(53, 24, 6), Sun)
                fill(cloud(27, 22, 7), White)
                shape(hill(12, 68, 52, 38, 40, 16, 44), p.pop.light(0.25f), 0.5f)
                shape(hill(12, 68, 52, 46, 30, 32, 40), p.pop, 0.5f)
                if (p.v % 2 == 1) { // tiny tree
                    shape(rect(57, 34, 59, 41), p.wood.dark(0.3f), 0.4f)
                    shape(oval(58, 31, 4.5f, 5.5f), p.pop.dark(0.15f), 0.4f)
                }
            }
            line(pic, Ink, 0.5f)
            shine(lines(3.5f, 10, 3.5f, 24))
        }
        Theme.COZY -> {
            line(oval(40, 2, 5, 6), p.pop, 0.7f)
            shape(oval(40, 44, 34), p.wood)
            shape(oval(40, 44, 28.5f), p.cream, 0.6f)
            clip(oval(40, 44, 28.5f)) { // gingham fabric
                for (i in -3..3) {
                    fill(rect(40 + i * 8 - 2, 10, 40 + i * 8 + 2, 80), p.main, 0.13f)
                    fill(rect(5, 44 + i * 8 - 2, 75, 44 + i * 8 + 2), p.main, 0.13f)
                }
            }
            for (i in 0 until 20) { // running stitch ring
                val a = polar(Offset(40f, 44f), 24, i * 18f)
                val b = polar(Offset(40f, 44f), 24, i * 18f + 9f)
                line(lines(a.x, a.y, b.x, b.y), p.pop, 0.45f)
            }
            shape(heart(40, 46, 13), p.main, 0.7f)
            shine(curve(33, 41, 33, 37, 36, 36))
            if (p.v % 2 == 1) {
                shape(leaf(30, 60, 21, 55, 3), p.leaf, 0.5f)
                shape(leaf(50, 60, 59, 55, 3), p.leaf, 0.5f)
            }
            shape(rect(34, 5, 46, 13, 2), p.wood.dark(0.15f), 0.8f)
            dot(40, 9, 1.6f, p.cream)
            shine(curve(15, 32, 20, 20, 32, 14))
        }
        Theme.GARDEN -> {
            hanger(30, 0)
            shape(rect(0, 0, 60, 80, 3), p.wood)
            for (y in listOf(20, 40, 60)) { line(lines(0, y, 5, y), Ink, 0.4f); line(lines(55, y, 60, y), Ink, 0.4f) }
            shape(rect(5, 5, 55, 75, 1.5f), p.cream, 0.5f)
            line(curve(30, 69, 25, 44, 31, 20), p.leaf.dark(0.3f), 0.6f)
            val lv = listOf(
                listOf(29, 60, 16, 52), listOf(29, 58, 43, 51), listOf(27, 47, 15, 39),
                listOf(27, 45, 41, 37), listOf(28, 34, 19, 27), listOf(28, 33, 39, 27),
            )
            lv.forEachIndexed { i, l ->
                val c = if (i % 2 == 0) p.main else p.main.dark(0.12f)
                shape(leaf(l[0], l[1], l[2], l[3], 4), c, 0.5f)
                line(lines(l[0], l[1], (l[0].f + l[2].f) / 2f, (l[1].f + l[3].f) / 2f), Ink, 0.3f, 0.6f)
            }
            shape(flower(31, 17, 10, 6), p.pop, 0.55f)
            shape(oval(31, 17, 3.4f), p.accent2, 0.45f)
            line(lines(20, 71, 40, 71), Ink, 0.35f, 0.5f)
        }
        Theme.RETRO -> {
            hanger(32, 0)
            shape(arch(0, 0, 64, 82), p.accent)
            val inner = arch(5, 5, 59, 77)
            shape(inner, p.cream, 0.5f)
            clip(inner) {
                val rs = listOf(26, 20, 14)
                val cs = listOf(p.main, p.pop, p.accent.light(0.15f))
                rs.forEachIndexed { i, r -> shape(oval(32, 77, r), cs[i], 0.45f) }
                shape(oval(32, 77, 8), p.cream, 0.45f)
            }
            shape(oval(32, 27, 7), Sun, 0.5f)
            for (i in 0 until 8) {
                val a = polar(Offset(32f, 27f), 10, i * 45f); val b = polar(Offset(32f, 27f), 13, i * 45f)
                line(lines(a.x, a.y, b.x, b.y), p.pop, 0.55f)
            }
            line(lines(5, 77, 59, 77), Ink, 0.5f)
            shine(curve(9, 40, 9, 20, 20, 11))
        }
        Theme.NIGHT -> {
            hanger(35, 0)
            shape(rect(0, 0, 70, 70, 4), p.wood)
            line(rect(4, 4, 66, 66, 2), p.accent, 0.45f)
            val pic = rect(8, 8, 62, 62, 1)
            sky(pic, p.sky, p.skyLow, 8, 62)
            clip(pic) {
                glow(44, 25, 20, p.glow, 0.45f)
                shape(crescent(44, 25, 9), p.accent2, 0.5f)
                listOf(Triple(18, 18, 3), Triple(28, 30, 2), Triple(56, 40, 2.4f), Triple(22, 42, 1.8f)).forEach { (x, y, r) ->
                    fill(sparkle(x, y, r.f * 1.2f), p.accent)
                }
                shape(hill(8, 62, 64, 52, 26, 36, 56), p.main, 0.5f)
                shape(hill(8, 62, 64, 58, 46, 40, 50), p.main.dark(0.2f), 0.5f)
                shape(poly(36, 54, 36, 49, 39.5f, 46, 43, 49, 43, 54), p.main.light(0.3f), 0.4f)
                fill(rect(38.5f, 49.5f, 40.5f, 52), p.glow)
            }
            line(pic, Ink, 0.5f)
        }
    }
}

// ---------------------------------------------------------------- WINDOW

/** Gathered curtain hanging from [top] at [edge], extending [w] inward ([dir] = +1 for the left curtain). */
private fun Pen.curtain(
    edge: Float, dir: Float, top: Float, bottom: Float, w: Float, c: Color, tie: Color,
    pattern: (Pen.() -> Unit)? = null,
) {
    fun x(u: Float) = edge + dir * u
    val h = bottom - top
    val tieY = top + h * 0.56f
    val path = Path().apply {
        moveTo(x(0f), top); lineTo(x(w), top)
        quadraticTo(x(w * 0.95f), tieY - h * 0.22f, x(w * 0.42f), tieY)
        quadraticTo(x(w * 0.5f), tieY + h * 0.25f, x(w * 0.88f), bottom)
        lineTo(x(0f), bottom); close()
    }
    fill(path, c)
    if (pattern != null) clip(path, pattern)
    clip(path) { fill(rect(minOf(x(0f), x(w * 0.25f)), top, maxOf(x(0f), x(w * 0.25f)), bottom), c.dark(0.12f)) }
    line(curve(x(w * 0.55f), top + 2, x(w * 0.5f), tieY - h * 0.25f, x(w * 0.3f), tieY - 2), Ink, 0.4f, 0.6f)
    line(curve(x(w * 0.3f), tieY + 3, x(w * 0.33f), tieY + h * 0.25f, x(w * 0.5f), bottom - 1), Ink, 0.4f, 0.6f)
    line(path, Ink, 1f)
    val (t0, t1) = listOf(x(0f), x(w * 0.48f)).sorted()
    shape(rect(t0 - 0.5f, tieY - 2.6f, t1 + 0.5f, tieY + 2.6f, 2.5f), tie, 0.6f)
}

private fun Pen.rod(l: Number, r: Number, y: Number, c: Color) {
    shape(rect(l, y.f - 2, r, y.f + 2, 2), c, 0.8f)
    shape(oval(l, y, 3.6f), c.dark(0.1f), 0.8f)
    shape(oval(r, y, 3.6f), c.dark(0.1f), 0.8f)
}

private fun Pen.bars(c: Color, vararg r: Path) = r.forEach { shape(it, c, 0.5f) }

internal fun Pen.window(t: Theme, p: Pal) {
    when (t) {
        Theme.BASIC -> {
            rod(-12, 82, -6, p.wood)
            shape(rect(0, 0, 70, 80, 4), White)
            val g = rect(6, 6, 64, 74, 2)
            sky(g, p.sky, p.skyLow, 6, 74)
            clip(g) {
                fill(cloud(22, 24, 8), White); fill(cloud(50, 52, 6), White)
                fill(oval(52, 20, 6), Sun)
            }
            line(g, Ink, 0.5f)
            bars(White, rect(33, 6, 37, 74), rect(6, 38, 64, 42))
            shine(lines(12, 64, 22, 52)); shine(lines(12, 58, 16, 54))
            shape(rect(-5, 76, 75, 84, 2.5f), p.wood)
            curtain(-10f, 1f, -6f, 78f, 26f, p.main, p.pop)
            curtain(80f, -1f, -6f, 78f, 26f, p.main, p.pop)
        }
        Theme.COZY -> {
            rod(-12, 84, -6, p.wood.dark(0.1f))
            shape(rect(0, 0, 72, 84, 4), p.wood)
            val g = rect(6, 6, 66, 78, 2)
            sky(g, p.sky, p.skyLow, 6, 78)
            clip(g) {
                fill(oval(36, 70, 14), Sun.light(0.2f))
                shape(hill(6, 66, 80, 66, 30, 54, 70), p.leaf.dark(0.1f), 0.5f)
                line(curve(18, 22, 21, 19, 24, 22), Ink, 0.4f); line(curve(24, 22, 27, 19, 30, 22), Ink, 0.4f)
            }
            line(g, Ink, 0.5f)
            bars(p.wood, rect(34, 6, 38, 78), rect(6, 29, 66, 32), rect(6, 52, 66, 55))
            shape(rect(-5, 80, 77, 88, 2.5f), p.wood.dark(0.08f))
            // candle in a jar on the sill
            glow(44, 70, 14, p.glow, 0.5f)
            shape(rect(39, 70, 49, 81, 2.5f), p.cream.a(0.9f), 0.7f)
            shape(rect(41, 72, 47, 81, 1), p.main.light(0.3f), 0.4f)
            shape(leaf(44, 72, 44, 64, 2), Sun, 0.5f)
            val plaid: Pen.() -> Unit = {
                for (i in -3..10) fill(rect(-20 + i * 9, -10, -17 + i * 9, 90), p.pop, 0.35f)
                for (i in 0..10) fill(rect(-20, -6 + i * 9, 95, -3 + i * 9), p.pop, 0.35f)
                for (i in -3..10) fill(rect(-14.5f + i * 9, -10, -14 + i * 9, 90), p.cream, 0.5f)
            }
            curtain(-10f, 1f, -6f, 82f, 27f, p.main, p.cream, plaid)
            curtain(82f, -1f, -6f, 82f, 27f, p.main, p.cream, plaid)
        }
        Theme.GARDEN -> {
            rod(-12, 82, -6, p.wood)
            shape(rect(0, 0, 70, 80, 4), p.main)
            val g = rect(6, 6, 64, 74, 2)
            sky(g, p.sky, p.skyLow, 6, 74)
            clip(g) {
                fill(oval(20, 20, 6), Sun)
                fill(cloud(46, 26, 7), White)
                shape(hill(6, 64, 76, 62, 30, 50, 58), p.leaf.light(0.2f), 0.5f)
                shape(rect(47, 50, 49, 60), p.wood.dark(0.3f), 0.4f)
                shape(oval(48, 47, 6, 7), p.leaf, 0.4f)
            }
            line(g, Ink, 0.5f)
            bars(p.main, rect(33, 6, 37, 74), rect(6, 38, 64, 42))
            shine(lines(12, 66, 22, 54))
            val dots: Pen.() -> Unit = {
                for (i in 0..12) for (j in 0..12) if ((i + j) % 2 == 0) fill(leaf(-14 + i * 8, -2 + j * 8, -10 + i * 8, -8 + j * 8, 2f), p.main.dark(0.1f))
            }
            curtain(-10f, 1f, -6f, 74f, 24f, p.cream, p.main, dots)
            curtain(80f, -1f, -6f, 74f, 24f, p.cream, p.main, dots)
            // vine draped over the rod
            line(curve(-12, -6, 35, 8, 82, -6), p.leaf.dark(0.25f), 0.6f)
            listOf(0, 12, 24, 36, 48, 60, 72).forEachIndexed { i, x ->
                val y = -6f + 14f * (1f - ((x - 35f) / 47f).let { it * it }) * 0.5f
                shape(leaf(x, y, x + 4, y + if (i % 2 == 0) 9 else -8, 3), if (i % 2 == 0) p.leaf else p.leaf.light(0.2f), 0.5f)
            }
            // flower box
            for (i in 0 until 6) {
                val x = 4 + i * 12.5f
                shape(leaf(x + 2, 80, x - 2, 72, 3), p.leaf, 0.5f)
            }
            for (i in 0 until 5) {
                val x = 10 + i * 12.5f
                shape(flower(x, 74 + (i % 2) * 2, 7), p.pop, 0.5f)
                dot(x, 74f + (i % 2) * 2, 1.8f, p.accent2)
            }
            shape(rect(-4, 78, 74, 92, 3), p.accent)
            line(lines(-4, 82, 74, 82), Ink, 0.4f)
        }
        Theme.RETRO -> {
            rod(-12, 86, -6, p.accent)
            shape(rect(0, 0, 74, 84, 28), p.main)
            val g = rect(6, 6, 68, 78, 22)
            fill(g, p.skyLow)
            clip(g) {
                val bands = listOf(p.sky.light(0.2f), p.sky, p.skyLow.mix(p.sky, 0.4f), p.skyLow)
                bands.forEachIndexed { i, c -> fill(rect(0, 6 + i * 12, 80, 18 + i * 12), c) }
                val sun = oval(37, 58, 16)
                fill(sun, p.cream)
                for (i in 0 until 3) fill(rect(18, 52 + i * 5, 56, 53.6f + i * 5), p.skyLow)
                shape(hill(6, 68, 80, 66, 37, 58, 66), p.accent.light(0.1f), 0.5f)
            }
            line(g, Ink, 0.5f)
            bars(p.main, rect(35, 6, 39, 78))
            shine(curve(12, 30, 12, 14, 24, 10))
            val stripes: Pen.() -> Unit = {
                for (i in 0..14) fill(rect(-20 + i * 8, -10, -16.5f + i * 8, 90), p.cream, 0.55f)
            }
            curtain(-10f, 1f, -6f, 84f, 25f, p.pop, p.accent, stripes)
            curtain(84f, -1f, -6f, 84f, 25f, p.pop, p.accent, stripes)
        }
        Theme.NIGHT -> {
            rod(-12, 82, -6, p.accent.dark(0.15f))
            shape(arch(0, 0, 70, 84), p.wood)
            val g = arch(6, 6, 64, 78)
            sky(g, p.sky, p.skyLow, 6, 78)
            clip(g) {
                glow(46, 26, 22, p.glow, 0.4f)
                shape(crescent(46, 26, 9), p.accent2, 0.5f)
                listOf(Triple(18, 30, 3.2f), Triple(28, 18, 2.2f), Triple(54, 50, 2.6f), Triple(20, 58, 2f), Triple(40, 64, 1.8f))
                    .forEach { (x, y, r) -> fill(sparkle(x, y, r * 1.1f), p.accent) }
                for ((x, y) in listOf(36 to 40, 14 to 44, 58 to 66, 30 to 52)) dot(x, y, 0.8f, p.cream)
            }
            line(g, Ink, 0.5f)
            bars(p.wood, rect(33, 6, 37, 78), rect(6, 46, 64, 50))
            shape(rect(-5, 76, 75, 84, 2.5f), p.wood.light(0.1f))
            val stars: Pen.() -> Unit = {
                for (i in 0..8) for (j in 0..10) if ((i + j) % 3 == 0) fill(star(-14 + i * 7, -4 + j * 8, 1.8f), p.accent)
            }
            curtain(-10f, 1f, -6f, 78f, 25f, p.main, p.accent, stars)
            curtain(80f, -1f, -6f, 78f, 25f, p.main, p.accent, stars)
        }
    }
}

// ---------------------------------------------------------------- CLOCK

private fun Pen.clockFace(cx: Float, cy: Float, r: Float, face: Color, tick: Color, hand: Color = Ink, ticks: Int = 12) {
    shape(oval(cx, cy, r), face, 0.6f)
    val c = Offset(cx, cy)
    for (i in 0 until ticks) {
        val deg = i * 360f / ticks
        val major = (i * 12 / ticks) % 3 == 0
        val a = polar(c, r * (if (major) 0.68f else 0.76f), deg); val b = polar(c, r * 0.86f, deg)
        line(lines(a.x, a.y, b.x, b.y), tick, if (major) 0.6f else 0.4f)
    }
    val h = polar(c, r * 0.45f, 215f); val m = polar(c, r * 0.68f, -30f)
    line(lines(cx, cy, h.x, h.y), hand, 0.85f)
    line(lines(cx, cy, m.x, m.y), hand, 0.65f)
    dot(cx, cy, r * 0.09f, hand)
}

internal fun Pen.clock(t: Theme, p: Pal) {
    when (t) {
        Theme.BASIC -> {
            shape(oval(40, 40, 34), p.wood)
            shape(oval(40, 40, 29), p.main, 0.5f)
            clockFace(40f, 40f, 25f, p.cream, Ink)
            shine(curve(13, 34, 15, 18, 30, 10))
        }
        Theme.COZY -> {
            // pendulum and pinecone weights behind the house
            line(lines(40, 86, 40, 104), Ink, 0.6f)
            shape(oval(40, 106, 6), p.accent.dark(0.1f), 0.8f)
            for (x in listOf(27, 53)) {
                line(lines(x, 86, x, 100), Ink, 0.45f)
                shape(oval(x, 106, 4, 7), p.wood.dark(0.25f), 0.7f)
                line(lines(x - 3, 104, x + 3, 104), Ink, 0.35f); line(lines(x - 3, 108, x + 3, 108), Ink, 0.35f)
            }
            shape(rect(10, 28, 70, 88, 4), p.wood)
            line(lines(10, 80, 70, 80), Ink, 0.4f)
            shape(poly(0, 34, 40, 2, 80, 34, r = 4), p.main)
            for (row in 0 until 3) {
                val y = 16 + row * 7f
                val half = (y - 2) * 40f / 32f
                line(lines(40 - half + 3, y, 40 + half - 3, y), p.pop, 0.4f)
            }
            shape(arch(35, 15, 45, 28), p.wood.dark(0.35f), 0.6f)
            fill(heart(40, 23, 2.6f), p.main.light(0.4f))
            clockFace(40f, 54f, 17f, p.cream, Ink)
            shape(heart(40, 82.5f, 3.4f), p.main, 0.5f)
        }
        Theme.GARDEN -> {
            shape(leaf(30, 66, 10, 84, 6), p.main, 0.7f)
            shape(leaf(50, 66, 70, 84, 6), p.main.dark(0.1f), 0.7f)
            line(lines(40, 70, 40, 84), p.leaf.dark(0.3f), 0.7f)
            for (i in 0 until 10) {
                val c = polar(Offset(40f, 40f), 28, i * 36f)
                shape(oval(c.x, c.y, 11), if (i % 2 == 0) p.pop else p.pop.light(0.25f), 0.7f)
            }
            clockFace(40f, 40f, 23f, p.cream, p.leaf.dark(0.3f), Ink)
        }
        Theme.RETRO -> {
            val c = Offset(40f, 40f)
            for (i in 0 until 12) {
                val deg = i * 30f
                val len = if (i % 2 == 0) 42f else 35f
                val a = polar(c, 18, deg - 6); val b = polar(c, 18, deg + 6); val tip = polar(c, len, deg)
                shape(poly(listOf(a, tip, b), 1), if (i % 2 == 0) p.accent else p.accent.light(0.25f), 0.6f)
                val ball = polar(c, len + 2, deg)
                shape(oval(ball.x, ball.y, if (i % 2 == 0) 3.6f else 2.8f), if (i % 2 == 0) p.pop else p.main, 0.55f)
            }
            shape(oval(40, 40, 22), p.main)
            clockFace(40f, 40f, 16f, p.cream, Ink, ticks = 4)
        }
        Theme.NIGHT -> {
            for ((x, y) in listOf(28 to 84, 50 to 80, 62 to 72)) {
                line(lines(x, 60, x, y - 4), p.accent.dark(0.2f), 0.4f)
                shape(star(x, y, 4.4f), p.accent, 0.55f)
            }
            shape(crescent(32, 40, 30, 0.5f), p.accent2)
            line(curve(14, 36, 17, 39, 20, 36), Ink, 0.5f)
            fill(oval(16, 44, 2.6f, 1.6f), p.pop.mix(Color(0xFFF2A0B0), 0.7f), 0.6f)
            clockFace(48f, 40f, 22f, p.main, p.accent, p.accent2)
            fill(sparkle(30, 12, 3), p.accent)
        }
    }
}

// ---------------------------------------------------------------- SHELF

/** Shelf plank: thin top face plus front edge; items stand on y = 58. */
private fun Pen.plank(l: Number, r: Number, c: Color, rad: Number = 2.5f) {
    shape(poly(l.f + 3, 55, r.f - 3, 55, r, 60, l, 60, r = 1), c.light(0.3f), 0.6f)
    shape(rect(l, 59, r, 67, rad), c)
}

private fun Pen.book(x: Number, w: Number, h: Number, c: Color, band: Color, tilt: Float = 0f) {
    val x0 = x.f; val w0 = w.f; val h0 = h.f
    if (tilt == 0f) {
        val b = shape(rect(x0, 58 - h0, x0 + w0, 58, 1.2f), c, 0.8f)
        clip(b) {
            fill(rect(x0, 58 - h0 + 3, x0 + w0, 58 - h0 + 5), band)
            fill(rect(x0, 58 - 6, x0 + w0, 58 - 4), band)
        }
        line(b, Ink, 0.8f)
    } else {
        // leaning book pivoting on its bottom-right corner
        val o = Offset(x0 + w0, 58f)
        fun r(dx: Float, dy: Float) = polar(o, kotlin.math.sqrt(dx * dx + dy * dy), kotlin.math.atan2(dy, dx) * 180f / kotlin.math.PI.toFloat() + tilt)
        shape(poly(listOf(r(0f, 0f), r(-w0, 0f), r(-w0, -h0), r(0f, -h0)), 1.2f), c, 0.8f)
        val a = r(-w0, -h0 + 4); val b = r(0f, -h0 + 4)
        line(lines(a.x, a.y, b.x, b.y), band, 0.6f)
    }
}

/** Small pot standing on the shelf (bottom at [by]), returns the soil y. */
internal fun Pen.pot(cx: Number, by: Number, wt: Number, wb: Number, h: Number, c: Color, deco: (Pen.() -> Unit)? = null): Float {
    val x = cx.f; val b = by.f; val top = b - h.f; val rim = h.f * 0.22f
    val body = poly(pts(x - wt.f / 2, top + rim, x + wt.f / 2, top + rim, x + wb.f / 2, b, x - wb.f / 2, b), 2.5f)
    fill(body, c)
    clip(body) {
        if (deco != null) deco()
        fill(rect(x + wt.f * 0.2f, top, x + wt.f, b + 2), c.dark(0.15f))
    }
    line(body, Ink, 1f)
    shape(rect(x - wt.f / 2 - 2, top, x + wt.f / 2 + 2, top + rim, 2), c.light(0.12f), 0.8f)
    return top
}

internal fun Pen.shelf(t: Theme, p: Pal) {
    when (t) {
        Theme.BASIC -> {
            for (x in listOf(14, 86)) shape(poly(x - 4, 66, x + 7, 66, x - 3, 78, r = 1.5f), p.wood.dark(0.15f), 0.8f)
            book(10, 7, 26, p.pop, p.cream)
            book(17, 6, 22, p.main.dark(0.1f), p.cream)
            book(23, 7, 28, p.cream, p.pop)
            book(30, 6, 23, p.main, p.cream, 18f)
            // succulent in a pot
            for (a in listOf(-150, -120, -90, -60, -30)) {
                val tip = polar(Offset(56f, 44f), 10, a)
                shape(leaf(56f, 44f, tip.x, tip.y, 2.5f), p.leaf, 0.5f)
            }
            pot(56, 58, 14, 11, 14, p.main)
            // tiny photo frame
            shape(rect(70, 38, 86, 58, 1.5f), p.wood.dark(0.1f), 0.8f)
            shape(rect(73, 41, 83, 55), p.sky, 0.4f)
            shape(hill(73, 83, 55, 52, 78, 44, 52), p.pop, 0.35f)
            plank(0, 100, p.wood)
        }
        Theme.COZY -> {
            for (x in listOf(14, 86)) {
                shape(poly(x - 4, 66, x + 4, 66, x + 4, 72, x - 4, 76, r = 1.5f), p.wood.dark(0.2f), 0.8f)
            }
            book(8, 7, 26, p.main, p.cream)
            book(15, 6, 23, p.pop, p.accent)
            book(21, 8, 27, p.accent, p.main)
            // mug with steam
            line(curve(45, 38, 42, 33, 46, 28), Ink, 0.4f, 0.5f); line(curve(50, 38, 47, 33, 51, 28), Ink, 0.4f, 0.5f)
            line(oval(55, 50, 4, 4.5f), Ink, 2.2f); line(oval(55, 50, 4, 4.5f), p.main, 1f)
            shape(rect(40, 41, 55, 58, 3), p.cream)
            fill(heart(47.5f, 49, 3), p.main)
            // candle jar
            glow(68, 40, 16, p.glow, 0.5f)
            shape(rect(63, 43, 73, 58, 2.5f), p.accent.dark(0.05f), 0.8f)
            shape(leaf(68, 43, 68, 35, 2), Sun, 0.5f)
            // yarn ball with needles
            line(lines(78, 36, 92, 54), p.wood.dark(0.3f), 0.8f); line(lines(94, 38, 80, 54), p.wood.dark(0.3f), 0.8f)
            shape(oval(86, 50, 8), p.main.light(0.15f))
            for (o in listOf(-4, 0, 4)) line(curve(80, 46 + o, 86, 40 + o, 92, 47 + o), p.pop, 0.4f)
            line(curve(84, 58, 92, 63, 98, 72), p.main, 0.5f)
            plank(0, 100, p.wood)
        }
        Theme.GARDEN -> {
            line(lines(4, 60, 50, 6, 96, 60), p.wood.dark(0.2f), 0.6f)
            dot(50, 6, 2.6f)
            // trailing vine
            line(curve(80, 52, 98, 70, 90, 92), p.leaf.dark(0.25f), 0.55f)
            for ((i, xy) in listOf(88 to 62, 95 to 72, 94 to 82, 90 to 91).withIndex()) {
                shape(leaf(xy.first, xy.second, xy.first + if (i % 2 == 0) 6 else -6, xy.second + 4, 2.6f), p.main, 0.5f)
            }
            plank(0, 100, p.wood)
            // fern
            for (a in listOf(-160, -130, -100, -80, -50, -20)) {
                val tip = polar(Offset(22f, 42f), 16, a)
                shape(leaf(22f, 42f, tip.x, tip.y, 3), p.main, 0.5f)
            }
            pot(22, 58, 16, 12, 15, p.accent)
            // tiny cactus
            shape(rect(46, 32, 54, 48, 4), p.leaf, 0.7f)
            dot(50, 31, 2.2f, p.pop)
            pot(50, 58, 13, 10, 12, p.accent.light(0.15f))
            for (a in listOf(-150, -110, -70, -30)) {
                val tip = polar(Offset(80f, 44f), 11, a)
                shape(leaf(80f, 44f, tip.x, tip.y, 2.6f), p.main.light(0.15f), 0.5f)
            }
            pot(80, 58, 15, 11, 14, p.accent.dark(0.08f))
            line(curve(84, 46, 92, 52, 92, 62), p.leaf.dark(0.25f), 0.55f)
        }
        Theme.RETRO -> {
            for (x in listOf(16, 84)) shape(oval(x, 70, 4), p.accent2.dark(0.1f), 0.8f)
            // records in sleeves
            shape(poly(listOf(at(6, 58), at(4, 30), at(30, 28), at(32, 58)), 1.5f), p.pop)
            shape(oval(34, 44, 11), Color(0xFF3C2E2B), 0.8f)
            line(oval(34, 44, 7), Color.White, 0.3f, 0.3f)
            dot(34, 44, 3.5f, p.main)
            shape(rect(12, 32, 30, 58, 1.5f), p.main)
            for (i in 0 until 3) fill(rect(12, 38 + i * 6, 30, 40 + i * 6), p.pop, 0.8f)
            line(rect(12, 32, 30, 58, 1.5f), Ink, 1f)
            // mini lava lamp
            shape(poly(46, 58, 58, 58, 55, 50, 49, 50, r = 1), p.accent2.dark(0.15f), 0.8f)
            shape(poly(49, 50, 55, 50, 57, 32, 47, 32, r = 2), p.pop.light(0.35f), 0.8f)
            fill(oval(51, 44, 2.6f, 3.6f), p.main); fill(oval(53, 37, 1.8f, 2.2f), p.main)
            shape(poly(47, 32, 57, 32, 54, 26, 50, 26, r = 1), p.accent2.dark(0.15f), 0.8f)
            // round vase with dried stems
            line(curve(80, 44, 76, 32, 70, 26), p.wood.dark(0.2f), 0.5f)
            line(curve(82, 44, 84, 30, 90, 24), p.wood.dark(0.2f), 0.5f)
            shape(oval(70, 26, 3, 4), p.accent2, 0.5f); shape(oval(90, 24, 3, 4), p.accent2, 0.5f)
            val v = oval(81, 50, 10, 9)
            fill(v, p.cream)
            clip(v) { fill(rect(70, 48, 92, 52), p.main); fill(rect(70, 53, 92, 55), p.pop) }
            line(v, Ink, 1f)
            plank(0, 100, p.accent, 4)
        }
        Theme.NIGHT -> {
            // star garland under the plank
            line(curve(4, 64, 50, 86, 96, 64), p.accent.dark(0.2f), 0.4f)
            for (x in listOf(16, 32, 50, 68, 84)) {
                val y = 64 + 22 * (1 - ((x - 50f) / 46f).let { it * it }) * 0.5f
                glow(x, y, 6, p.glow, 0.5f)
                shape(star(x, y + 1, 3.4f), p.accent, 0.5f)
            }
            book(8, 7, 25, p.main, p.accent)
            book(15, 7, 28, p.pop, p.accent)
            book(22, 6, 21, p.main.dark(0.2f), p.accent, 14f)
            // crystal ball on stand
            glow(52, 40, 20, p.pop, 0.5f)
            shape(poly(44, 58, 60, 58, 57, 51, 47, 51, r = 1.5f), p.accent.dark(0.1f), 0.8f)
            shape(oval(52, 40, 11), p.pop.light(0.4f))
            fill(sparkle(55, 38, 3.2f), Color.White)
            shine(curve(45, 38, 45, 32, 50, 30))
            // candle
            glow(78, 33, 14, p.glow, 0.6f)
            shape(rect(73, 40, 83, 58, 2), p.accent2, 0.8f)
            line(curve(76, 40, 76, 44, 77, 45), Ink, 0.4f)
            shape(leaf(78, 39, 78, 31, 2.2f), Sun, 0.5f)
            plank(0, 100, p.wood)
        }
    }
}
