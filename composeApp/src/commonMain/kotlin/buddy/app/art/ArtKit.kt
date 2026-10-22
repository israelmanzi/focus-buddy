package buddy.app.art

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * A tiny vector "sticker" kit: art is recorded as a list of ops in free design units, its bounds are
 * measured, and at draw time it is scaled to fit the target rect. Line widths are multiples of the
 * outline width, so every item gets the same outline weight regardless of its design size.
 */

/** The one outline colour used by all decor art. */
internal val Ink = Color(0xFF4A342E)

internal fun Color.mix(o: Color, t: Float) = lerp(this, o, t)
internal fun Color.light(t: Float = 0.35f) = lerp(this, Color(0xFFFFFBF2), t)
internal fun Color.dark(t: Float = 0.2f) = lerp(this, Ink, t)
internal fun Color.a(alpha: Float) = copy(alpha = alpha)

internal sealed interface Op
internal class FillOp(val path: Path, val brush: Brush, val alpha: Float) : Op
internal class LineOp(val path: Path, val color: Color, val width: Float, val alpha: Float) : Op
internal class ClipGroup(val clip: Path, val ops: List<Op>) : Op

internal class Sticker(val ops: List<Op>, val bounds: Rect)

internal class Pen {
    val ops = ArrayList<Op>()
    private var l = Float.MAX_VALUE
    private var t = Float.MAX_VALUE
    private var r = -Float.MAX_VALUE
    private var b = -Float.MAX_VALUE

    private fun grow(p: Path) {
        val bb = p.getBounds()
        l = min(l, bb.left); t = min(t, bb.top); r = max(r, bb.right); b = max(b, bb.bottom)
    }

    fun fill(p: Path, c: Color, alpha: Float = 1f, bounded: Boolean = true) {
        ops += FillOp(p, SolidColor(c), alpha); if (bounded) grow(p)
    }

    /** Gradient fills (glows, skies) do not count towards the fitted bounds. */
    fun fill(p: Path, brush: Brush, bounded: Boolean = false) { ops += FillOp(p, brush, 1f); if (bounded) grow(p) }

    /** Stroke [p]; [w] is a multiple of the outline width. */
    fun line(p: Path, c: Color = Ink, w: Number = 0.55f, alpha: Float = 1f) {
        ops += LineOp(p, c, w.f, alpha); grow(p)
    }

    /** Filled and outlined. */
    fun shape(p: Path, c: Color, w: Number = 1f): Path { fill(p, c); line(p, Ink, w); return p }

    fun clip(p: Path, block: Pen.() -> Unit) {
        val sub = Pen().apply(block)
        ops += ClipGroup(p, sub.ops)
    }

    fun build() = Sticker(ops, if (l > r) Rect(0f, 0f, 1f, 1f) else Rect(l, t, r, b))
}

internal fun DrawScope.render(ops: List<Op>, unit: Float) {
    for (op in ops) when (op) {
        is FillOp -> drawPath(op.path, op.brush, alpha = op.alpha)
        is LineOp -> drawPath(
            op.path, op.color, alpha = op.alpha,
            style = Stroke(op.width * unit, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        is ClipGroup -> clipPath(op.clip) { render(op.ops, unit) }
    }
}

// ---------------------------------------------------------------- path builders
// Builders take Number so drawing code can use plain literals; stickers are built once and cached.

internal val Number.f: Float get() = toFloat()

internal fun rect(l: Number, t: Number, r: Number, b: Number, rad: Number = 0) = Path().apply {
    if (rad.f > 0f) addRoundRect(RoundRect(l.f, t.f, r.f, b.f, CornerRadius(rad.f))) else addRect(Rect(l.f, t.f, r.f, b.f))
}

internal fun oval(cx: Number, cy: Number, rx: Number, ry: Number = rx) =
    Path().apply { addOval(Rect(cx.f - rx.f, cy.f - ry.f, cx.f + rx.f, cy.f + ry.f)) }

internal fun pts(vararg v: Number) = List(v.size / 2) { Offset(v[it * 2].f, v[it * 2 + 1].f) }

/** Closed polygon with softly rounded corners. */
internal fun poly(p: List<Offset>, r: Number = 0) = Path().apply {
    val rr = r.f
    if (rr <= 0f) {
        moveTo(p[0].x, p[0].y); for (i in 1 until p.size) lineTo(p[i].x, p[i].y); close(); return@apply
    }
    val n = p.size
    for (i in 0 until n) {
        val prev = p[(i - 1 + n) % n]; val cur = p[i]; val next = p[(i + 1) % n]
        val d1 = prev - cur; val l1 = d1.getDistance()
        val d2 = next - cur; val l2 = d2.getDistance()
        val a = cur + d1 / l1 * min(rr, l1 / 2f)
        val b = cur + d2 / l2 * min(rr, l2 / 2f)
        if (i == 0) moveTo(a.x, a.y) else lineTo(a.x, a.y)
        quadraticTo(cur.x, cur.y, b.x, b.y)
    }
    close()
}

internal fun poly(vararg v: Number, r: Number = 0) = poly(pts(*v), r)

/** Open polyline. */
internal fun lines(vararg v: Number) = lines(pts(*v))

internal fun lines(p: List<Offset>) = Path().apply {
    moveTo(p[0].x, p[0].y); for (i in 1 until p.size) lineTo(p[i].x, p[i].y)
}

/** Open quadratic curve. */
internal fun curve(x0: Number, y0: Number, cx: Number, cy: Number, x1: Number, y1: Number) =
    Path().apply { moveTo(x0.f, y0.f); quadraticTo(cx.f, cy.f, x1.f, y1.f) }

internal fun curve(a: Offset, c: Offset, b: Offset) = curve(a.x, a.y, c.x, c.y, b.x, b.y)

internal fun Path.moved(dx: Number, dy: Number) = Path().apply { addPath(this@moved, Offset(dx.f, dy.f)) }

internal fun Path.minus(o: Path) = Path.combine(PathOperation.Difference, this, o)
internal fun Path.plus(o: Path) = Path.combine(PathOperation.Union, this, o)

internal fun union(vararg p: Path): Path = p.reduce { a, b -> a.plus(b) }

/** Leaf from [b] to [t] with half-width [w]. */
internal fun leaf(b: Offset, t: Offset, w: Number) = Path().apply {
    val d = t - b; val len = d.getDistance(); val n = Offset(-d.y / len, d.x / len) * (w.f * 1.3f)
    val m = b + d * 0.5f
    moveTo(b.x, b.y)
    quadraticTo(m.x + n.x, m.y + n.y, t.x, t.y)
    quadraticTo(m.x - n.x, m.y - n.y, b.x, b.y)
    close()
}

internal fun leaf(bx: Number, by: Number, tx: Number, ty: Number, w: Number) = leaf(Offset(bx.f, by.f), Offset(tx.f, ty.f), w)

internal fun at(x: Number, y: Number) = Offset(x.f, y.f)

internal fun polar(c: Offset, r: Number, deg: Number) =
    Offset(c.x + r.f * cos(deg.f * PI.toFloat() / 180f), c.y + r.f * sin(deg.f * PI.toFloat() / 180f))

internal fun star(cx: Number, cy: Number, r: Number, inner: Float = 0.48f, n: Int = 5): Path =
    poly(List(n * 2) { polar(at(cx, cy), if (it % 2 == 0) r.f else r.f * inner, -90f + it * 180f / n) }, r.f * 0.12f)

/** Four-point sparkle. */
internal fun sparkle(cx: Number, cy: Number, r: Number) = star(cx, cy, r, 0.34f, 4)

internal fun heart(cx: Number, cy: Number, s: Number) = Path().apply {
    val x = cx.f; val y = cy.f; val k = s.f
    moveTo(x, y + k * 0.9f)
    cubicTo(x - k * 1.25f, y + k * 0.05f, x - k * 0.75f, y - k * 0.95f, x, y - k * 0.35f)
    cubicTo(x + k * 0.75f, y - k * 0.95f, x + k * 1.25f, y + k * 0.05f, x, y + k * 0.9f)
    close()
}

/** Crescent opening to the upper right. */
internal fun crescent(cx: Number, cy: Number, r: Number, cut: Float = 0.42f): Path {
    val x = cx.f; val y = cy.f; val rr = r.f
    return oval(x, y, rr).minus(oval(x + rr * cut, y - rr * cut * 0.6f, rr * 0.82f))
}

/** Little cloud made of three puffs. */
internal fun cloud(cx: Number, cy: Number, s: Number): Path {
    val x = cx.f; val y = cy.f; val k = s.f
    return union(
        oval(x - k * 0.55f, y + k * 0.1f, k * 0.42f), oval(x, y - k * 0.12f, k * 0.55f),
        oval(x + k * 0.6f, y + k * 0.12f, k * 0.38f), rect(x - k * 0.9f, y + k * 0.05f, x + k * 0.9f, y + k * 0.5f, k * 0.25f),
    )
}

internal fun flower(cx: Number, cy: Number, r: Number, n: Int = 5): Path =
    union(*Array(n) { polar(at(cx, cy), r.f * 0.6f, -90f + it * 360f / n).let { c -> oval(c.x, c.y, r.f * 0.44f) } })

/** Arch: rectangle with a semicircular top. */
internal fun arch(l: Number, t: Number, r: Number, b: Number) = Path().apply {
    val w = r.f - l.f
    moveTo(l.f, b.f); lineTo(l.f, t.f + w / 2f)
    arcTo(Rect(l.f, t.f, r.f, t.f + w), 180f, 180f, false)
    lineTo(r.f, b.f); close()
}

// ---------------------------------------------------------------- affine planes / isometric helpers

/** Map a 2D design-space path (u, v) into screen space: o + u·U + v·V. */
internal fun Path.mapped(o: Offset, u: Offset, v: Offset): Path = Path().apply {
    addPath(this@mapped)
    transform(Matrix().apply {
        values[0] = u.x; values[1] = u.y; values[4] = v.x; values[5] = v.y; values[12] = o.x; values[13] = o.y
    })
}

/**
 * 2:1 isometric projection. x runs to the lower right, y to the lower left, z up; the origin is the
 * back corner of a footprint. Furniture "fronts" are the y = max face (lower left), sides the x = max face.
 */
internal class Iso(val ox: Float = 0f, val oy: Float = 0f) {
    fun p(x: Number, y: Number, z: Number = 0) = Offset(ox + x.f - y.f, oy + (x.f + y.f) * 0.5f - z.f)

    /** A path drawn in floor coords (x, y) at height z. */
    fun floor(path: Path, z: Number = 0) = path.mapped(p(0, 0, z), Offset(1f, 0.5f), Offset(-1f, 0.5f))

    /** A path drawn on the plane y = [y], in (u = x, v = -z) coords. */
    fun front(path: Path, y: Number) = path.mapped(p(0, y), Offset(1f, 0.5f), Offset(0f, 1f))

    /** A path drawn on the plane x = [x], in (u = y, v = -z) coords. */
    fun side(path: Path, x: Number) = path.mapped(p(x, 0), Offset(-1f, 0.5f), Offset(0f, 1f))
}

/** Isometric box with lit front (y-face), darker side (x-face), light top. Returns the silhouette. */
internal fun Pen.box(
    iso: Iso, x: Number, y: Number, z: Number, w: Number, d: Number, h: Number, c: Color,
    top: Color = c.light(0.38f), side: Color = c.dark(0.2f), r: Number = 1.2f, edge: Number = 0.5f,
): Path {
    val x0 = x.f; val y0 = y.f; val z0 = z.f
    val x1 = x0 + w.f; val y1 = y0 + d.f; val z1 = z0 + h.f
    val sil = poly(
        listOf(iso.p(x0, y0, z1), iso.p(x1, y0, z1), iso.p(x1, y0, z0), iso.p(x1, y1, z0), iso.p(x0, y1, z0), iso.p(x0, y1, z1)), r,
    )
    fill(sil, c)
    clip(sil) {
        fill(poly(listOf(iso.p(x0, y0, z1), iso.p(x1, y0, z1), iso.p(x1, y1, z1), iso.p(x0, y1, z1))), top)
        fill(poly(listOf(iso.p(x1, y0, z1), iso.p(x1, y1, z1), iso.p(x1, y1, z0), iso.p(x1, y0, z0))), side)
    }
    if (edge.f > 0f) {
        line(lines(listOf(iso.p(x0, y1, z1), iso.p(x1, y1, z1), iso.p(x1, y0, z1))), Ink, edge)
        line(lines(listOf(iso.p(x1, y1, z1), iso.p(x1, y1, z0))), Ink, edge)
    }
    line(sil, Ink, 1f)
    return sil
}

/** Upright cylinder silhouette: bottom ellipse centred at (cx, by). */
internal fun cylinderPath(cx: Number, by: Number, rx: Number, h: Number, ry: Number = rx.f * 0.5f) = Path().apply {
    val x = cx.f; val b = by.f; val a = rx.f; val hh = h.f; val e = ry.f
    moveTo(x - a, b - hh)
    lineTo(x - a, b)
    arcTo(Rect(x - a, b - e, x + a, b + e), 180f, -180f, false)
    lineTo(x + a, b - hh)
    arcTo(Rect(x - a, b - hh - e, x + a, b - hh + e), 0f, -180f, false)
    close()
}

internal fun Pen.cylinder(
    cx: Number, by: Number, rx: Number, h: Number, c: Color, top: Color = c.light(0.38f), ry: Number = rx.f * 0.5f,
): Path {
    val sil = cylinderPath(cx, by, rx, h, ry)
    fill(sil, c)
    clip(sil) { fill(rect(cx.f + rx.f * 0.4f, by.f - h.f - ry.f * 3, cx.f + rx.f * 2, by.f + ry.f * 2), c.dark(0.16f)) }
    shape(oval(cx, by.f - h.f, rx, ry), top, 0.5f)
    line(sil, Ink, 1f)
    return sil
}

/** Soft contact shadow on the floor. */
internal fun Pen.shadow(cx: Number, cy: Number, rx: Number, ry: Number = rx.f * 0.45f) = fill(oval(cx, cy, rx, ry), Ink, 0.16f)

/** Radial light glow (does not affect bounds). */
internal fun Pen.glow(cx: Number, cy: Number, r: Number, c: Color, strength: Float = 0.55f) =
    fill(oval(cx, cy, r), Brush.radialGradient(listOf(c.a(strength), c.a(strength * 0.4f), c.a(0f)), at(cx, cy), r.f))

/** Small white shine stroke. */
internal fun Pen.shine(path: Path, alpha: Float = 0.75f) = line(path, Color.White, 0.55f, alpha)

/** Filled dot. */
internal fun Pen.dot(cx: Number, cy: Number, r: Number, c: Color = Ink) = fill(oval(cx, cy, r), c)
