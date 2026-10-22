package buddy.app.art

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import buddy.core.Catalog.Theme
import kotlin.math.cos
import kotlin.math.sin

// Floor items stand on (or lie flat on) the 2:1 isometric floor.

private val Flame = Color(0xFFF8C85A)
private val Chrome = Color(0xFFDCD6CE)
private val Pink = Color(0xFFF29AAE)

/** Rug top [top] with a thin darker edge below it. */
private fun Pen.slab(top: Path, c: Color, edge: Color = c.dark(0.3f)) {
    shape(top.moved(0, 3), edge)
    shape(top, c)
}

// ---------------------------------------------------------------- RUG

internal fun Pen.rug(t: Theme, p: Pal) {
    val iso = Iso()
    when (t) {
        Theme.BASIC -> {
            for (y in 0..10) {
                val yy = 4 + y * 3.8f
                line(iso.floor(lines(0, yy, -5, yy)), Ink, 0.5f)
                line(iso.floor(lines(72, yy, 77, yy)), Ink, 0.5f)
            }
            val top = iso.floor(rect(0, 0, 72, 46, 4))
            slab(top, p.main)
            line(iso.floor(rect(6, 6, 66, 40, 3)), p.cream, 0.9f)
            line(iso.floor(rect(10, 10, 62, 36, 2)), p.pop, 0.55f)
            shape(iso.floor(poly(36, 15, 44, 23, 36, 31, 28, 23, r = 1)), p.pop, 0.5f)
        }
        Theme.COZY -> {
            val base = oval(0, 0, 40, 28)
            slab(iso.floor(base), p.main)
            val cs = listOf(p.cream, p.pop, p.main.light(0.25f), p.cream, p.main)
            listOf(0.82f, 0.64f, 0.46f, 0.3f, 0.14f).forEachIndexed { i, k ->
                shape(iso.floor(oval(0, 0, 40 * k, 28 * k)), cs[i], 0.4f)
            }
        }
        Theme.GARDEN -> {
            val petals = union(*Array(8) { i ->
                val c = polar(Offset.Zero, 24, i * 45f); oval(c.x, c.y, 13)
            }, oval(0, 0, 26))
            slab(iso.floor(petals), p.main)
            for (i in 0 until 8) {
                val a = polar(Offset.Zero, 16, i * 45f); val b = polar(Offset.Zero, 31, i * 45f)
                line(iso.floor(lines(a.x, a.y, b.x, b.y)), p.main.dark(0.2f), 0.45f)
            }
            shape(iso.floor(oval(0, 0, 14)), p.pop, 0.55f)
            shape(iso.floor(oval(0, 0, 6)), p.accent2, 0.45f)
        }
        Theme.RETRO -> {
            val top = iso.floor(rect(0, 0, 72, 48, 14))
            slab(top, p.main)
            val cs = listOf(p.cream, p.pop, p.cream, p.accent)
            cs.forEachIndexed { i, c ->
                val k = 6f * (i + 1)
                shape(iso.floor(rect(k, k, 72 - k, 48 - k, maxOf(14 - k * 0.5f, 2f))), c, 0.4f)
            }
        }
        Theme.NIGHT -> {
            for (i in 0 until 16) { // tassels around the edge
                val a = polar(Offset.Zero, 40, i * 22.5f).let { Offset(it.x, it.y * 0.72f) }
                val b = polar(Offset.Zero, 44, i * 22.5f).let { Offset(it.x, it.y * 0.72f) }
                line(iso.floor(lines(a.x, a.y, b.x, b.y)), p.accent.dark(0.15f), 0.6f)
            }
            slab(iso.floor(oval(0, 0, 40, 29)), p.main)
            line(iso.floor(oval(0, 0, 34, 24)), p.accent, 0.5f)
            shape(iso.floor(crescent(-6, 4, 10, 0.5f)), p.accent2, 0.5f)
            listOf(Triple(14, -8, 4f), Triple(-16, -12, 3f), Triple(20, 10, 3f), Triple(4, -16, 2.4f), Triple(-22, 8, 2.4f))
                .forEach { (x, y, r) -> fill(iso.floor(star(x, y, r)), p.accent) }
        }
    }
}

// ---------------------------------------------------------------- PLANT

internal fun Pen.plant(t: Theme, p: Pal) {
    shadow(30, 100, 22, 6)
    when (t) {
        Theme.BASIC -> {
            val base = Offset(30f, 70f)
            val ang = listOf(-165, -140, -115, -90, -65, -40, -15)
            val len = listOf(26, 34, 40, 44, 40, 34, 26)
            ang.forEachIndexed { i, a ->
                val tip = polar(base, len[i], a)
                val c = if (i % 2 == 0) p.leaf else p.leaf.dark(0.12f)
                shape(leaf(base, tip, 7), c, 0.6f)
                val mid = polar(base, len[i] * 0.75f, a)
                line(lines(base.x, base.y, mid.x, mid.y), p.leaf.dark(0.35f), 0.35f)
            }
            shape(oval(30, 99, 20, 5), p.main.dark(0.12f), 0.8f)
            pot(30, 98, 34, 26, 32, p.main) { fill(rect(0, 80, 60, 84), p.pop) }
        }
        Theme.COZY -> {
            for ((a, l) in listOf(-130 to 30, -95 to 36, -60 to 30)) {
                val tip = polar(Offset(30f, 68f), l, a)
                line(lines(30, 68, tip.x, tip.y), p.leaf.dark(0.3f), 0.5f)
                shape(heart(tip.x, tip.y, 7), p.leaf, 0.6f)
            }
            shape(heart(30, 46, 6), p.leaf.light(0.15f), 0.6f)
            pot(30, 98, 36, 28, 34, p.main) {
                for (row in 0 until 7) {
                    val y = 70 + row * 4.4f
                    val zig = (0..10).flatMap { listOf(6 + it * 5f, if (it % 2 == 0) y else y + 3f) }
                    line(lines(*zig.toTypedArray()), p.main.dark(0.25f), 0.35f)
                }
            }
            // trailing vines in front
            for (side in listOf(-1f, 1f)) {
                val x0 = 30 + side * 12; val x1 = 30 + side * 24
                line(curve(x0, 66, x1 + side * 4, 74, x1, 100), p.leaf.dark(0.3f), 0.5f)
                for (k in 0 until 3) {
                    val y = 74 + k * 9f
                    shape(heart(x1 + side * (3 - k), y, 4.4f), if (k % 2 == 0) p.leaf else p.leaf.light(0.15f), 0.5f)
                }
            }
        }
        Theme.GARDEN -> {
            val stems = listOf(at(16, 38), at(31, 24), at(45, 36), at(24, 52), at(40, 52))
            for (s in stems) line(curve(30f, 70f, (30 + s.x) / 2f, s.y + 10, s.x, s.y), p.leaf.dark(0.25f), 0.5f)
            for ((a, l) in listOf(-160 to 22, -125 to 26, -55 to 26, -20 to 22)) {
                val tip = polar(Offset(30f, 70f), l, a)
                shape(leaf(Offset(30f, 70f), tip, 5), p.main, 0.55f)
            }
            stems.forEachIndexed { i, s ->
                val r = if (i < 3) 9f else 7f
                shape(flower(s.x, s.y, r), if (i % 2 == 0) p.pop else p.pop.light(0.3f), 0.55f)
                dot(s.x, s.y, r * 0.3f, p.accent2)
            }
            pot(30, 98, 34, 26, 30, p.accent) { fill(rect(0, 84, 60, 88), p.accent.dark(0.12f)) }
        }
        Theme.RETRO -> {
            val body = union(
                rect(22, 24, 38, 72, 8),
                rect(9, 34, 18, 56, 4.5f), rect(12, 48, 26, 56, 4),
                rect(42, 28, 51, 48, 4.5f), rect(34, 40, 48, 48, 4),
            )
            shape(body, p.leaf)
            clip(body) {
                fill(rect(31, 20, 40, 80), p.leaf.dark(0.12f))
                fill(rect(16, 30, 18, 60), p.leaf.dark(0.12f)); fill(rect(48, 26, 51, 50), p.leaf.dark(0.12f))
            }
            line(lines(30, 30, 30, 66), p.leaf.dark(0.3f), 0.4f)
            for ((x, y) in listOf(25 to 34, 35 to 40, 25 to 50, 35 to 58, 13 to 42, 46 to 34, 27 to 62)) {
                line(lines(x - 1, y, x + 1, y - 1.5f), Ink, 0.35f)
            }
            shape(flower(30, 22, 8, 6), Pink, 0.55f)
            dot(30, 22, 2.2f, p.cream)
            pot(30, 98, 30, 24, 34, p.main) {
                fill(rect(0, 74, 60, 79), p.cream); fill(rect(0, 82, 60, 86), p.pop); fill(rect(0, 89, 60, 92), p.cream)
            }
        }
        Theme.NIGHT -> {
            val base = Offset(30f, 70f)
            val orbs = listOf(at(14, 30), at(32, 18), at(48, 34))
            for (o in orbs) {
                line(curve(base.x, base.y, (base.x + o.x) / 2 + 4, o.y + 16, o.x, o.y + 5), p.leaf.dark(0.3f), 0.5f)
            }
            for ((a, l) in listOf(-160 to 24, -130 to 28, -100 to 22, -70 to 26, -30 to 24)) {
                shape(leaf(base, polar(base, l, a), 5.5f), p.leaf, 0.55f)
            }
            for (o in orbs) {
                glow(o.x, o.y, 16, p.glow, 0.55f)
                shape(oval(o.x, o.y, 5.5f), p.accent2, 0.6f)
                fill(oval(o.x - 1.5f, o.y - 1.5f, 1.6f), Color.White, 0.9f)
            }
            fill(sparkle(50, 16, 3), p.accent); fill(sparkle(10, 48, 2.4f), p.accent)
            pot(30, 98, 32, 26, 32, p.main) {
                fill(star(22, 84, 3.4f), p.accent); fill(star(36, 90, 2.4f), p.accent); fill(crescent(32, 78, 3.4f), p.accent2)
            }
        }
    }
}

// ---------------------------------------------------------------- LAMP

internal fun Pen.lamp(t: Theme, p: Pal) {
    shadow(25, 129, 17, 5)
    when (t) {
        Theme.BASIC -> {
            glow(25, 50, 46, p.glow, 0.45f)
            cylinder(25, 127, 12, 3, p.wood.dark(0.1f))
            shape(rect(23.3f, 44, 26.7f, 125, 1.6f), p.wood.dark(0.15f), 0.8f)
            fill(oval(25, 46, 19, 4), p.glow)
            shape(poly(13, 12, 37, 12, 45, 46, 5, 46, r = 3), p.main)
            fill(rect(7, 40, 43, 43), p.pop, 0f)
            line(lines(6.5f, 40, 43.5f, 40), p.pop, 0.7f)
            shine(lines(13, 18, 9, 36))
            line(lines(36, 46, 36, 60), Ink, 0.4f); dot(36, 61, 1.6f, p.wood.dark(0.2f))
        }
        Theme.COZY -> {
            glow(25, 50, 54, p.glow, 0.6f)
            cylinder(25, 127, 13, 4, p.wood.dark(0.2f))
            shape(rect(23, 44, 27, 124, 2), p.wood, 0.8f)
            for (y in listOf(66, 92, 112)) shape(oval(25, y, 4.5f, 3), p.wood.dark(0.1f), 0.6f)
            fill(oval(25, 45, 20, 4.5f), p.glow)
            // pom-pom fringe
            for (i in 0 until 7) {
                val x = 6 + i * 6.3f
                line(lines(x, 44, x, 49), p.pop, 0.4f)
                shape(oval(x, 50.5f, 2.3f), p.pop, 0.45f)
            }
            val shade = Path().apply {
                moveTo(14f, 12f); lineTo(36f, 12f)
                quadraticTo(40f, 30f, 46f, 44f)
                for (i in 0 until 6) { val x = 46f - i * 7f; quadraticTo(x - 3.5f, 49f, x - 7f, 44f) }
                quadraticTo(10f, 30f, 14f, 12f); close()
            }
            fill(shade, p.main)
            clip(shade) { for (i in 0 until 6) fill(rect(9 + i * 6.5f, 0, 11 + i * 6.5f, 60), p.accent, 0.35f) }
            line(shade, Ink, 1f)
            shape(rect(13, 9.5f, 37, 13.5f, 2), p.pop, 0.6f)
            shine(curve(15, 18, 13, 30, 9, 38))
        }
        Theme.GARDEN -> {
            // flowers at the foot
            for ((x, y) in listOf(13 to 120, 36 to 122)) {
                shape(leaf(x, y + 6, x - 5, y - 2, 2.5f), p.leaf, 0.5f)
                shape(flower(x, y - 4, 7), p.pop, 0.5f); dot(x, y - 4, 1.8f, p.accent2)
            }
            val post = p.main.dark(0.3f)
            cylinder(24, 127, 9, 4, post)
            shape(rect(21, 34, 27, 124, 2), post, 0.9f)
            shape(oval(24, 31, 3.5f), post, 0.7f)
            // vine winding up the post
            val vine = Path().apply {
                moveTo(24f, 122f)
                for (i in 0 until 8) { val y = 122f - i * 11f; quadraticTo(if (i % 2 == 0) 31f else 17f, y - 5.5f, 24f, y - 11f) }
            }
            line(vine, p.leaf.dark(0.25f), 0.5f)
            for (i in 0 until 7) {
                val y = 116 - i * 11f; val s = if (i % 2 == 0) 1 else -1
                shape(leaf(24 + s * 4, y, 24 + s * 11, y - 4, 2.6f), p.leaf.light(if (i % 2 == 0) 0f else 0.2f), 0.5f)
            }
            // arm and hanging lantern
            line(curve(24, 42, 34, 32, 46, 36), post, 1.1f)
            line(lines(46, 36, 46, 42), Ink, 0.5f)
            glow(46, 58, 26, p.glow, 0.6f)
            val glass = rect(38, 48, 54, 68, 1.5f)
            shape(glass, p.glow.light(0.3f))
            glow(46, 61, 8, Flame, 0.8f)
            shape(rect(44, 59, 48, 66, 1), p.cream, 0.5f)
            shape(leaf(46, 59, 46, 53, 1.8f), Flame, 0.4f)
            line(lines(46, 48, 46, 56), post, 0.5f)
            shape(rect(36, 67, 56, 71, 1.5f), p.main, 0.8f)
            shape(poly(35, 49, 57, 49, 46, 41, r = 1.5f), p.main, 0.8f)
        }
        Theme.RETRO -> {
            glow(25, 50, 46, p.glow, 0.5f)
            shape(rect(23, 44, 27, 106, 2), Chrome, 0.8f)
            shine(lines(24.2f, 52, 24.2f, 98), 0.8f)
            val base = Path().apply {
                moveTo(8f, 127f); cubicTo(20f, 124f, 22f, 116f, 22.5f, 102f); lineTo(27.5f, 102f)
                cubicTo(28f, 116f, 30f, 124f, 42f, 127f); close()
            }
            shape(base, p.main)
            shape(oval(25, 127, 17, 4), p.main.dark(0.1f), 0.8f)
            fill(oval(25, 46, 21, 4.5f), p.glow)
            val dome = Path().apply { moveTo(0f, 46f); arcTo(androidx.compose.ui.geometry.Rect(0f, 4f, 50f, 88f), 180f, 180f, false); close() }
            fill(dome, p.main)
            clip(dome) { fill(rect(0, 32, 50, 37), p.pop); fill(rect(0, 39, 50, 41), p.cream) }
            line(dome, Ink, 1f)
            shape(rect(-1.5f, 43.5f, 51.5f, 48, 2.2f), p.main.dark(0.12f), 0.8f)
            shine(curve(6, 30, 8, 14, 20, 8))
        }
        Theme.NIGHT -> {
            glow(25, 36, 56, p.glow, 0.6f)
            cylinder(25, 127, 13, 4, p.main)
            shape(rect(23.5f, 56, 26.5f, 124, 1.5f), p.accent.dark(0.15f), 0.8f)
            val moon = oval(25, 36, 20)
            fill(moon, p.accent2)
            clip(moon) {
                fill(oval(35, 40, 19), p.accent2.dark(0.1f))
                fill(oval(17, 28, 4.5f), p.accent2.dark(0.12f)); fill(oval(30, 46, 3.5f), p.accent2.dark(0.15f))
                fill(oval(31, 24, 2.5f), p.accent2.dark(0.12f)); fill(oval(14, 42, 2.5f), p.accent2.dark(0.1f))
            }
            line(moon, Ink, 1f)
            shine(curve(10, 30, 12, 20, 20, 17))
            shape(rect(16, 54, 34, 59, 2.5f), p.main, 0.8f)
            fill(sparkle(50, 14, 3.4f), p.accent); fill(sparkle(2, 56, 2.6f), p.accent); fill(sparkle(48, 70, 2.2f), p.accent)
        }
    }
}

// ---------------------------------------------------------------- SEAT

/** Box armchair; [plush] is the corner rounding. */
private fun Pen.armchair(iso: Iso, c: Color, legs: Color, plush: Float) {
    for ((x, y) in listOf(38 to 1, 1 to 33, 38 to 33)) box(iso, x, y, 0, 4, 4, 6, legs, r = 0.6f)
    box(iso, 0, 0, 5, 44, 38, 11, c, r = plush)
    box(iso, 0, 0, 16, 44, 9, 26, c, r = plush)
    box(iso, 0, 9, 16, 8, 29, 13, c, r = plush)
    box(iso, 8, 9, 16, 28, 29, 7, c.light(0.12f), r = plush)
    box(iso, 36, 9, 16, 8, 29, 13, c, r = plush)
}

internal fun Pen.seat(t: Theme, p: Pal) {
    val iso = Iso()
    when (t) {
        Theme.BASIC -> {
            shadow(iso.p(22, 19).x, iso.p(22, 19).y + 2, 40, 18)
            armchair(iso, p.main, p.wood.dark(0.1f), 2.5f)
            shape(oval(iso.p(22, 12, 30).x, iso.p(22, 12, 30).y, 8, 7), p.pop, 0.7f)
        }
        Theme.COZY -> {
            shadow(iso.p(22, 19).x, iso.p(22, 19).y + 2, 40, 18)
            armchair(iso, p.main, p.wood.dark(0.2f), 4f)
            for (x in listOf(10, 22, 34)) { val b = iso.p(x, 9, 34); dot(b.x, b.y, 1.2f, p.main.dark(0.35f)) }
            // round knit cushion
            val c = iso.p(20, 12, 30)
            shape(oval(c.x, c.y, 9, 8.5f), p.cream, 0.8f)
            for (k in listOf(-4, 0, 4)) line(curve(c.x - 6, c.y + k, c.x, c.y + k - 3, c.x + 6, c.y + k), p.main, 0.35f)
            // plaid throw draped over the seat front
            val zt = 23.2f; val yf = 38.4f
            val drapeTop = iso.floor(rect(12, 24, 30, yf, 1.5f), zt)
            val drapeFront = iso.front(Path().apply {
                moveTo(12f, -zt); lineTo(30f, -zt); lineTo(30f, -6f)
                quadraticTo(25.5f, -3f, 21f, -6f); quadraticTo(16.5f, -3f, 12f, -6f); close()
            }, yf)
            for (d in listOf(drapeTop, drapeFront)) {
                fill(d, p.accent)
                clip(d) {
                    for (i in 0..4) {
                        val x = 13.5f + i * 4f
                        fill(iso.floor(rect(x, 24, x + 1.5f, yf), zt), p.pop, 0.55f)
                        fill(iso.front(rect(x, -zt - 1, x + 1.5f, 0), yf), p.pop, 0.55f)
                    }
                    for (j in 0..3) {
                        fill(iso.floor(rect(12, 26 + j * 4f, 30, 27.2f + j * 4f), zt), p.pop, 0.4f)
                        fill(iso.front(rect(12, -21 + j * 4f, 30, -19.8f + j * 4f), yf), p.pop, 0.4f)
                    }
                }
                line(d, Ink, 0.8f)
            }
            for (i in 0..5) {
                val a = iso.p(13 + i * 3.4f, yf, if (i % 2 == 0) 5.6f else 4.2f)
                line(lines(a.x, a.y, a.x, a.y + 2.5f), Ink, 0.4f)
            }
        }
        Theme.GARDEN -> {
            shadow(iso.p(32, 12).x, iso.p(32, 12).y + 2, 48, 18)
            val w = p.wood
            for (x in listOf(2, 58)) box(iso, x, 0, 0, 4, 4, 40, w, r = 0.8f)
            box(iso, 2, 0, 24, 60, 3, 6, w, r = 1)
            box(iso, 2, 0, 33, 60, 3, 6, w, r = 1)
            for ((x, y) in listOf(2 to 20, 58 to 20)) box(iso, x, y, 0, 4, 4, 14, w.dark(0.1f), r = 0.6f)
            box(iso, 0, 3, 14, 64, 21, 4, w, r = 1)
            for (i in 1..4) line(lines(listOf(iso.p(0, 3 + i * 4.2f, 18), iso.p(64, 3 + i * 4.2f, 18))), Ink, 0.35f, 0.6f)
            box(iso, 3, 5, 18, 58, 18, 4, p.main, r = 2.5f)
            // flower pillows
            for ((x, c) in listOf(14 to p.pop, 46 to p.main.light(0.3f))) {
                val o = iso.p(x, 6, 31)
                shape(rect(o.x - 8, o.y - 7, o.x + 8, o.y + 8, 5), c, 0.8f)
                fill(flower(o.x, o.y, 6), if (c == p.pop) p.cream else p.pop)
                dot(o.x, o.y, 1.6f, p.accent2)
            }
        }
        Theme.RETRO -> {
            shadow(30, 96, 22, 6)
            cylinder(30, 95, 16, 3, Chrome)
            shape(rect(27, 80, 33, 93, 2), Chrome, 0.8f)
            val shell = oval(30, 44, 31, 40)
            shape(shell, p.main)
            val open = oval(30, 48, 22, 28)
            shape(open, p.main.dark(0.3f), 0.8f)
            clip(open) { fill(oval(30, 30, 24, 22), p.main.dark(0.38f)) }
            shape(oval(30, 40, 15, 16), p.pop.light(0.15f), 0.7f)
            line(curve(22, 34, 30, 30, 38, 34), p.pop.dark(0.2f), 0.4f)
            shape(oval(30, 63, 19, 9), p.pop, 0.8f)
            line(curve(13, 63, 30, 68, 47, 63), p.pop.dark(0.25f), 0.45f)
            shine(curve(4, 40, 6, 18, 20, 8))
        }
        Theme.NIGHT -> {
            shadow(36, 78, 34, 8)
            val bag = Path().apply {
                moveTo(8f, 72f); cubicTo(0f, 48f, 16f, 18f, 36f, 18f); cubicTo(56f, 18f, 72f, 46f, 66f, 70f)
                cubicTo(62f, 84f, 14f, 86f, 8f, 72f); close()
            }
            fill(bag, p.main)
            clip(bag) {
                fill(oval(56, 60, 22, 30), p.main.dark(0.15f))
                for ((x, y) in listOf(16 to 40, 28 to 66, 56 to 36, 48 to 72, 62 to 56, 20 to 58, 40 to 26)) {
                    fill(star(x, y, 2.6f), p.accent, 0.9f)
                }
            }
            line(bag, Ink, 1f)
            line(curve(14, 54, 36, 66, 60, 52), Ink, 0.5f, 0.7f)
            shine(curve(12, 50, 14, 34, 24, 26))
            shape(crescent(36, 46, 13, 0.45f), p.accent2, 0.8f)
            line(curve(27, 46, 29, 48.5f, 31, 46), Ink, 0.45f)
            fill(oval(29, 51, 2.2f, 1.4f), Pink, 0.6f)
        }
    }
}

// ---------------------------------------------------------------- TABLE

internal fun Pen.table(t: Theme, p: Pal) {
    when (t) {
        Theme.BASIC -> {
            val iso = Iso()
            shadow(0, 19, 36, 17)
            box(iso, 28, 2, 0, 4, 4, 30, p.wood.dark(0.1f), r = 0.6f)
            box(iso, 2, 2, 9, 30, 30, 3, p.wood, r = 1)
            for ((x, y) in listOf(2 to 28, 28 to 28)) box(iso, x, y, 0, 4, 4, 30, p.wood.dark(0.1f), r = 0.6f)
            box(iso, 0, 0, 30, 34, 34, 4, p.wood, r = 1.5f)
            // little vase with a sprig
            val c = iso.p(17, 17, 34)
            line(curve(c.x, c.y - 8, c.x - 2, c.y - 16, c.x + 2, c.y - 24), p.leaf.dark(0.3f), 0.5f)
            shape(leaf(c.x - 1, c.y - 14, c.x - 8, c.y - 18, 2.4f), p.leaf, 0.5f)
            shape(leaf(c.x + 1, c.y - 19, c.x + 8, c.y - 23, 2.4f), p.leaf, 0.5f)
            shape(oval(c.x + 2, c.y - 26, 3.4f), p.pop, 0.5f)
            shape(rect(c.x - 5, c.y - 12, c.x + 5, c.y, 4), p.main)
            shape(oval(c.x, c.y - 11, 3, 1.2f), p.main.dark(0.2f), 0.4f)
        }
        Theme.COZY -> {
            shadow(30, 60, 30, 7)
            val cloth = Path().apply {
                moveTo(2f, 30f); lineTo(1f, 56f)
                val n = 8
                for (i in 0 until n) {
                    val a0 = kotlin.math.PI.toFloat() * (1f - i.toFloat() / n); val a1 = kotlin.math.PI.toFloat() * (1f - (i + 1f) / n)
                    val x1 = 30f + 29f * cos(a1); val y1 = 56f + 10f * sin(a1)
                    val am = (a0 + a1) / 2f
                    quadraticTo(30f + 29f * cos(am), 56f + 10f * sin(am) + 5f, x1, y1)
                }
                lineTo(58f, 30f); close()
            }
            fill(cloth, p.main)
            clip(cloth) {
                for (i in 0..8) fill(rect(i * 7f, 20, i * 7f + 2.5f, 80), p.pop, 0.35f)
                for (i in 0..8) fill(rect(0, 34 + i * 6f, 60, 36 + i * 6f), p.pop, 0.35f)
                fill(rect(38, 20, 62, 80), p.main.dark(0.15f), 0.6f)
            }
            line(cloth, Ink, 1f)
            shape(oval(30, 30, 28, 13), p.main.light(0.2f), 0.6f)
            shape(oval(30, 31, 18, 8), p.cream, 0.5f)
            // mug with steam
            line(curve(28, 18, 25, 13, 29, 8), Ink, 0.4f, 0.5f); line(curve(33, 18, 30, 13, 34, 8), Ink, 0.4f, 0.5f)
            line(oval(37, 26, 3, 3.5f), Ink, 2.2f); line(oval(37, 26, 3, 3.5f), p.cream, 1f)
            cylinder(31, 31, 6, 11, p.cream)
            fill(heart(31, 26, 2.4f), p.main)
        }
        Theme.GARDEN -> {
            shadow(30, 64, 32, 8)
            // roots
            for ((x, w) in listOf(8 to 7, 50 to 7, 26 to 6)) shape(oval(x, 64, w, 4), p.wood.dark(0.25f), 0.7f)
            val stump = cylinder(30, 62, 24, 30, p.wood.dark(0.2f), p.wood.light(0.15f))
            clip(stump) {
                for (x in listOf(13, 22, 36, 46)) line(curve(x, 40, x + 2, 52, x - 1, 70), Ink, 0.4f, 0.6f)
            }
            line(oval(30, 32, 16, 8), p.wood.dark(0.15f), 0.4f)
            line(oval(30, 32, 8, 4), p.wood.dark(0.15f), 0.4f)
            fill(oval(13, 34, 6, 3), p.main); fill(oval(46, 36, 5, 2.5f), p.main)
            // tiny mushroom
            shape(rect(52, 54, 56, 64, 1.5f), p.cream, 0.6f)
            val cap = Path().apply { moveTo(46f, 56f); arcTo(androidx.compose.ui.geometry.Rect(46f, 46f, 62f, 66f), 180f, 180f, false); close() }
            shape(cap, p.pop, 0.7f)
            dot(51, 52, 1.2f, p.cream); dot(57, 53, 1f, p.cream)
            // potted flower on top
            line(lines(32, 26, 32, 12), p.leaf.dark(0.3f), 0.5f)
            shape(leaf(32, 20, 25, 15, 2.4f), p.leaf, 0.5f)
            shape(flower(32, 10, 7), p.pop, 0.5f); dot(32, 10, 2f, p.accent2)
            pot(32, 36, 12, 9, 11, p.accent)
        }
        Theme.RETRO -> {
            shadow(30, 68, 22, 6)
            shape(oval(30, 66, 18, 6.5f), p.main.dark(0.1f))
            shape(oval(30, 64.5f, 16, 5), p.main.light(0.2f), 0.5f)
            shape(Path().apply {
                moveTo(22f, 64f); quadraticTo(28f, 58f, 28f, 36f); lineTo(32f, 36f); quadraticTo(32f, 58f, 38f, 64f); close()
            }, p.main, 0.8f)
            cylinder(30, 34, 28, 4, p.main, p.cream)
            // striped vase with a flower
            line(curve(30, 22, 28, 12, 32, 4), p.leaf.dark(0.3f), 0.5f)
            shape(leaf(30, 14, 23, 10, 2.4f), p.leaf, 0.5f)
            shape(flower(32, 4, 7, 6), p.pop, 0.5f); dot(32, 4, 2f, p.cream)
            val v = Path().apply {
                moveTo(26f, 21f); lineTo(34f, 21f); quadraticTo(34f, 24f, 37f, 27f); quadraticTo(39f, 33f, 34f, 34f)
                lineTo(26f, 34f); quadraticTo(21f, 33f, 23f, 27f); quadraticTo(26f, 24f, 26f, 21f); close()
            }
            fill(v, p.accent2)
            clip(v) { fill(rect(20, 27, 40, 29.5f), p.pop); fill(rect(20, 30.5f, 40, 32), p.accent) }
            line(v, Ink, 0.9f)
        }
        Theme.NIGHT -> {
            shadow(30, 66, 26, 7)
            for ((x0, x1) in listOf(14 to 8, 46 to 52)) line(curve(x0, 38, x1 - (x1 - x0), 54, x1, 64), p.accent.dark(0.2f), 1.1f)
            line(lines(30, 40, 30, 66), p.accent.dark(0.25f), 1.1f)
            cylinder(30, 36, 26, 5, p.main)
            // candle trio
            glow(30, 18, 30, p.glow, 0.65f)
            for ((x, h) in listOf(22 to 10, 30 to 16, 38 to 7)) {
                val base = 34f + (if (x == 30) 1f else 0f)
                shape(rect(x - 3.5f, base - h, x + 3.5f, base, 1.5f), p.accent2, 0.7f)
                shape(leaf(x, base - h - 1, x, base - h - 8, 2), Flame, 0.45f)
            }
            fill(sparkle(50, 14, 3), p.accent); fill(sparkle(10, 22, 2.2f), p.accent)
        }
    }
}
