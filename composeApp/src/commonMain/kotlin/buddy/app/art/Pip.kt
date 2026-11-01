package buddy.app.art

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import buddy.core.CompanionStyle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

enum class Mood { IDLE, WALK, PHONE, READING, SAD, HAPPY, BREAK, SLEEP }

private val OUTLINE = Color(0xFF4A342E)
private val BLUSH = Color(0xFFF2A29A)

data class SkinColors(val body: Color, val shade: Color, val eye: Color = OUTLINE)

fun skinColors(skin: String): SkinColors = when (skin) {
    "mint" -> SkinColors(Color(0xFFCDEBD3), Color(0xFFA9D6B4))
    "peach" -> SkinColors(Color(0xFFFFD3B6), Color(0xFFF2B494))
    "lavender" -> SkinColors(Color(0xFFDCCFF2), Color(0xFFC1B0E3))
    "sky" -> SkinColors(Color(0xFFC6E2F5), Color(0xFFA3C9E6))
    "cocoa" -> SkinColors(Color(0xFFB88A6A), Color(0xFF9C7052))
    "midnight" -> SkinColors(Color(0xFF4A5680), Color(0xFF384266), Color(0xFFFBF3E4))
    "honey" -> SkinColors(Color(0xFFF4C95D), Color(0xFFE0AE3C))
    "ghost" -> SkinColors(Color(0xEEF8F8FF), Color(0xDDE2E2F0))
    else -> SkinColors(Color(0xFFF6E2C4), Color(0xFFE8CBA4))
}

/**
 * False while the host says the scene is off screen (Android activity stopped). The animation
 * clock stops then, so a backgrounded app draws nothing.
 */
val LocalAnimationActive = androidx.compose.runtime.staticCompositionLocalOf { true }

/** Frames per second for the scene clocks; hosts may lower it (Android uses 6). */
val LocalAnimationFps = androidx.compose.runtime.staticCompositionLocalOf { 8 }

/**
 * Seconds since first composition, ticking [fps] times a second. A delay loop rather than
 * withFrameNanos: awaiting frames keeps the compositor running at the display rate even when
 * nothing changed, which burns a whole core while the app sits idle.
 */
@Composable
fun rememberSeconds(fps: Int? = null, active: Boolean = true): State<Float> {
    val rate = fps ?: LocalAnimationFps.current
    val s = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(rate, active) {
        if (!active) return@LaunchedEffect
        val step = 1000L / rate
        while (true) {
            delay(step)
            s.floatValue += 1f / rate
        }
    }
    return s
}

@Composable
fun PipView(style: CompanionStyle, mood: Mood, modifier: Modifier = Modifier, facing: Int = 1, fps: Int = 8) {
    val t = rememberSeconds(fps = fps)
    Canvas(modifier) {
        drawPip(Offset(size.width / 2, size.height * 0.97f), size.height * 0.9f, style, mood, t.value, facing)
    }
}

/**
 * Draws Pip standing on [feet] (bottom-centre), [height] tall including hat room.
 * [t] is a running time in seconds that drives all animation.
 */
fun DrawScope.drawPip(feet: Offset, height: Float, style: CompanionStyle, mood: Mood, t: Float, facing: Int = 1) {
    val u = height / 100f
    val c = skinColors(style.skin)
    val sw = 2.6f * u
    val cx = feet.x
    val bounce = when (mood) {
        Mood.HAPPY -> -abs(sin(t * 5f)) * 10f * u
        Mood.WALK -> -abs(sin(t * 8f)) * 2.5f * u
        else -> 0f
    }
    val breath = 1f + 0.018f * sin(t * 2f * PI.toFloat() / 3.2f)
    val base = feet.y + bounce

    scale(if (facing < 0) -1f else 1f, 1f, pivot = Offset(cx, base)) {
        // shadow
        drawOval(Color(0x22000000), Offset(cx - 30 * u, feet.y - 4 * u), Size(60 * u, 8 * u))

        // feet
        val step = if (mood == Mood.WALK) sin(t * 8f) * 3f * u else 0f
        for ((i, dx) in listOf(-13f, 13f).withIndex()) {
            val lift = if (i == 0) step.coerceAtMost(0f) else (-step).coerceAtMost(0f)
            outlinedOval(Rect(Offset(cx + dx * u - 8 * u, base - 8 * u + lift), Size(16 * u, 8 * u)), c.shade, sw)
        }

        scale(1f, breath, pivot = Offset(cx, base)) {
            // ears
            for (dx in listOf(-19f, 19f)) {
                outlinedOval(Rect(Offset(cx + dx * u - 8 * u, base - 66 * u), Size(16 * u, 14 * u)), c.body, sw)
                drawOval(BLUSH.copy(alpha = 0.6f), Offset(cx + dx * u - 4 * u, base - 63 * u), Size(8 * u, 7 * u))
            }
            // body
            val body = Path().apply {
                moveTo(cx - 34 * u, base - 12 * u)
                cubicTo(cx - 39 * u, base - 50 * u, cx - 21 * u, base - 63 * u, cx, base - 63 * u)
                cubicTo(cx + 21 * u, base - 63 * u, cx + 39 * u, base - 50 * u, cx + 34 * u, base - 12 * u)
                cubicTo(cx + 31 * u, base - 2 * u, cx - 31 * u, base - 2 * u, cx - 34 * u, base - 12 * u)
                close()
            }
            drawPath(body, c.body)
            // belly shade + highlight
            drawOval(c.shade.copy(alpha = 0.55f), Offset(cx - 22 * u, base - 22 * u), Size(44 * u, 16 * u))
            drawOval(Color.White.copy(alpha = 0.45f), Offset(cx - 24 * u, base - 54 * u), Size(10 * u, 7 * u))
            drawPath(body, OUTLINE, style = Stroke(sw))

            drawFace(cx, base, u, sw, c, mood, t, style.face)
            drawNeck(style.neck, cx, base, u, sw)
            drawArmsAndProp(cx, base, u, sw, c, mood, t)
            drawHat(style.hat, cx, base, u, sw, t)
        }

        when (mood) {
            Mood.SLEEP -> for (i in 0..2) {
                val p = ((t * 0.5f + i / 3f) % 1f)
                drawZ(Offset(cx + (26 + p * 14) * u, base - (70 + p * 30) * u), (5 + 3 * p) * u, sw * 0.8f, 1f - p)
            }
            Mood.HAPPY -> listOf(Offset(-44f, -60f), Offset(44f, -52f), Offset(-36f, -86f), Offset(38f, -84f)).forEachIndexed { i, p ->
                val tw = 0.6f + 0.4f * sin(t * 6f + i * 1.7f)
                sparkle(Offset(cx + p.x * u, base + p.y * u), 4.5f * u * tw)
            }
            else -> {}
        }
    }
}

private fun DrawScope.outlinedOval(r: Rect, fill: Color, sw: Float) {
    drawOval(fill, r.topLeft, r.size)
    drawOval(OUTLINE, r.topLeft, r.size, style = Stroke(sw))
}

private fun DrawScope.drawFace(cx: Float, base: Float, u: Float, sw: Float, c: SkinColors, mood: Mood, t: Float, face: String?) {
    val ey = base - 38 * u
    val blink = ((t + 2f) % 4.3f) < 0.13f
    val lookDown = mood == Mood.READING || mood == Mood.PHONE
    val eyeY = if (lookDown) ey + 3 * u else ey
    for (dx in listOf(-11f, 11f)) {
        val e = Offset(cx + dx * u, eyeY)
        when {
            mood == Mood.HAPPY -> drawArc(c.eye, 200f, 140f, false, Offset(e.x - 4 * u, e.y - 2 * u), Size(8 * u, 7 * u), style = Stroke(sw, cap = StrokeCap.Round))
            mood == Mood.SLEEP || blink -> drawLine(c.eye, Offset(e.x - 3.5f * u, e.y), Offset(e.x + 3.5f * u, e.y), sw, StrokeCap.Round)
            mood == Mood.SAD -> {
                drawCircle(c.eye, 3f * u, e)
                drawLine(c.eye, Offset(e.x - 4 * u * (dx / 11f), e.y - 8 * u), Offset(e.x + 3 * u * (dx / 11f), e.y - 6 * u), sw * 0.8f, StrokeCap.Round)
            }
            else -> {
                drawCircle(c.eye, 3.2f * u, e)
                drawCircle(Color.White, 1.1f * u, Offset(e.x + 1 * u, e.y - 1.2f * u))
            }
        }
    }
    val blushScale = if (face == "face.blush") 1.6f else 1f
    for (dx in listOf(-20f, 20f)) {
        drawOval(BLUSH.copy(alpha = 0.75f), Offset(cx + dx * u - 4.5f * u * blushScale, base - 32 * u), Size(9 * u * blushScale, 5 * u))
    }
    val my = base - 31 * u
    when (mood) {
        Mood.SAD -> {
            drawArc(OUTLINE, 200f, 140f, false, Offset(cx - 4 * u, my), Size(8 * u, 6 * u), style = Stroke(sw * 0.8f, cap = StrokeCap.Round))
            val tear = (t * 0.8f) % 1f
            drawCircle(Color(0xFF8EC5E8), 2.2f * u, Offset(cx + 13 * u, eyeY + 4 * u + tear * 14 * u), alpha = 1f - tear)
        }
        Mood.HAPPY, Mood.BREAK -> {
            val m = Path().apply {
                moveTo(cx - 4 * u, my - 1 * u); quadraticTo(cx, my + 6 * u, cx + 4 * u, my - 1 * u); close()
            }
            drawPath(m, Color(0xFFC0504D)); drawPath(m, OUTLINE, style = Stroke(sw * 0.7f))
        }
        Mood.SLEEP -> drawCircle(OUTLINE, 1.6f * u, Offset(cx, my + 1 * u), style = Stroke(sw * 0.6f))
        else -> {
            val m = Path().apply {
                moveTo(cx - 4 * u, my); quadraticTo(cx - 2 * u, my + 2.5f * u, cx, my)
                quadraticTo(cx + 2 * u, my + 2.5f * u, cx + 4 * u, my)
            }
            drawPath(m, OUTLINE, style = Stroke(sw * 0.7f, cap = StrokeCap.Round))
        }
    }
    when (face) {
        "face.round" -> {
            for (dx in listOf(-11f, 11f)) {
                drawCircle(Color.White.copy(alpha = 0.25f), 7 * u, Offset(cx + dx * u, ey))
                drawCircle(OUTLINE, 7 * u, Offset(cx + dx * u, ey), style = Stroke(sw * 0.8f))
            }
            drawLine(OUTLINE, Offset(cx - 4 * u, ey), Offset(cx + 4 * u, ey), sw * 0.8f)
        }
        "face.shades" -> {
            for (dx in listOf(-11f, 11f)) {
                drawRoundRect(Color(0xFF2B2B3A), Offset(cx + dx * u - 7 * u, ey - 5 * u), Size(14 * u, 10 * u), androidx.compose.ui.geometry.CornerRadius(4 * u))
                drawLine(Color.White.copy(alpha = 0.6f), Offset(cx + dx * u - 4 * u, ey - 2 * u), Offset(cx + dx * u - 1 * u, ey - 4 * u), sw * 0.6f)
            }
            drawLine(OUTLINE, Offset(cx - 4 * u, ey - 2 * u), Offset(cx + 4 * u, ey - 2 * u), sw * 0.8f)
        }
        "face.blush" -> {
            sparkle(Offset(cx - 26 * u, base - 40 * u), 2.5f * u)
            sparkle(Offset(cx + 26 * u, base - 40 * u), 2.5f * u)
        }
    }
}

private fun DrawScope.drawNeck(neck: String?, cx: Float, base: Float, u: Float, sw: Float) {
    val y = base - 24 * u
    when (neck) {
        "neck.scarf" -> {
            val band = Path().apply {
                moveTo(cx - 33 * u, y - 4 * u); quadraticTo(cx, y + 5 * u, cx + 33 * u, y - 4 * u)
                lineTo(cx + 33 * u, y + 3 * u); quadraticTo(cx, y + 12 * u, cx - 33 * u, y + 3 * u); close()
            }
            drawPath(band, Color(0xFFE07A5F)); drawPath(band, OUTLINE, style = Stroke(sw * 0.9f))
            val tail = Path().apply {
                moveTo(cx + 14 * u, y + 4 * u); lineTo(cx + 22 * u, y + 20 * u); lineTo(cx + 12 * u, y + 21 * u); lineTo(cx + 8 * u, y + 6 * u); close()
            }
            drawPath(tail, Color(0xFFD0654B)); drawPath(tail, OUTLINE, style = Stroke(sw * 0.9f))
            for (i in -2..2) drawLine(Color(0xFFF2CC8F), Offset(cx + i * 12 * u, y - 1 * u + abs(i) * -1.5f * u), Offset(cx + i * 12 * u, y + 6 * u + abs(i) * -1.5f * u), sw * 0.8f)
        }
        "neck.bow" -> {
            for (s in listOf(-1f, 1f)) {
                val w = Path().apply { moveTo(cx, y); lineTo(cx + s * 10 * u, y - 6 * u); lineTo(cx + s * 10 * u, y + 6 * u); close() }
                drawPath(w, Color(0xFFB04A5A)); drawPath(w, OUTLINE, style = Stroke(sw * 0.8f))
            }
            drawCircle(Color(0xFF8E3646), 2.6f * u, Offset(cx, y)); drawCircle(OUTLINE, 2.6f * u, Offset(cx, y), style = Stroke(sw * 0.7f))
        }
        "neck.bandana" -> {
            val tri = Path().apply { moveTo(cx - 24 * u, y - 4 * u); lineTo(cx + 24 * u, y - 4 * u); lineTo(cx, y + 14 * u); close() }
            drawPath(tri, Color(0xFF5B8DB8)); drawPath(tri, OUTLINE, style = Stroke(sw * 0.9f))
            for (p in listOf(Offset(cx - 8 * u, y), Offset(cx + 8 * u, y), Offset(cx, y + 7 * u))) drawCircle(Color.White, 1.3f * u, p)
        }
        "neck.bell" -> {
            drawArc(Color(0xFFC0504D), 20f, 140f, false, Offset(cx - 32 * u, y - 18 * u), Size(64 * u, 24 * u), style = Stroke(4 * u))
            drawCircle(Color(0xFFF4C95D), 5 * u, Offset(cx, y + 9 * u)); drawCircle(OUTLINE, 5 * u, Offset(cx, y + 9 * u), style = Stroke(sw * 0.8f))
            drawLine(OUTLINE, Offset(cx - 3 * u, y + 10 * u), Offset(cx + 3 * u, y + 10 * u), sw * 0.7f)
        }
    }
}

private fun DrawScope.drawArmsAndProp(cx: Float, base: Float, u: Float, sw: Float, c: SkinColors, mood: Mood, t: Float) {
    fun arm(x: Float, y: Float, angle: Float) = rotate(angle, Offset(x, y)) {
        outlinedOval(Rect(Offset(x - 5 * u, y - 7 * u), Size(10 * u, 14 * u)), c.body, sw * 0.9f)
    }
    when (mood) {
        Mood.READING -> {
            val bx = cx; val by = base - 20 * u
            // book: two pages on a cover
            val cover = Path().apply {
                moveTo(bx - 20 * u, by - 9 * u); lineTo(bx, by - 6 * u); lineTo(bx + 20 * u, by - 9 * u)
                lineTo(bx + 20 * u, by + 7 * u); lineTo(bx, by + 10 * u); lineTo(bx - 20 * u, by + 7 * u); close()
            }
            drawPath(cover, Color(0xFF7FB069)); drawPath(cover, OUTLINE, style = Stroke(sw * 0.9f))
            for (s in listOf(-1f, 1f)) {
                val pg = Path().apply {
                    moveTo(bx, by - 7 * u); lineTo(bx + s * 18 * u, by - 10 * u); lineTo(bx + s * 18 * u, by + 5 * u); lineTo(bx, by + 8 * u); close()
                }
                drawPath(pg, Color(0xFFFFFBF2)); drawPath(pg, OUTLINE, style = Stroke(sw * 0.6f))
                for (l in 0..2) drawLine(Color(0xFFBFB2A6), Offset(bx + s * 4 * u, by - 4 * u + l * 4 * u), Offset(bx + s * 14 * u, by - 6 * u + l * 4 * u), sw * 0.4f)
            }
            // page flip every 6s
            val ph = (t % 6f) / 0.7f
            if (ph < 1f) {
                val x = 18 * u * (1f - 2f * ph)
                val pg = Path().apply {
                    moveTo(bx, by - 7 * u); lineTo(bx + x, by - 10 * u - sin(ph * PI.toFloat()) * 5 * u)
                    lineTo(bx + x, by + 5 * u - sin(ph * PI.toFloat()) * 5 * u); lineTo(bx, by + 8 * u); close()
                }
                drawPath(pg, Color(0xFFFFF3DD)); drawPath(pg, OUTLINE, style = Stroke(sw * 0.6f))
            }
            arm(bx - 21 * u, by + 1 * u, 30f); arm(bx + 21 * u, by + 1 * u, -30f)
        }
        Mood.PHONE -> {
            val px = cx + 2 * u; val py = base - 22 * u
            drawRoundRect(Color(0xFF3C3F58), Offset(px - 6 * u, py - 10 * u), Size(12 * u, 18 * u), androidx.compose.ui.geometry.CornerRadius(2.5f * u))
            drawRoundRect(Color(0xFF9FD3F0), Offset(px - 4.5f * u, py - 8 * u), Size(9 * u, 13 * u), androidx.compose.ui.geometry.CornerRadius(1.5f * u))
            drawRoundRect(OUTLINE, Offset(px - 6 * u, py - 10 * u), Size(12 * u, 18 * u), androidx.compose.ui.geometry.CornerRadius(2.5f * u), style = Stroke(sw * 0.8f))
            val scroll = (t * 1.5f) % 1f
            drawLine(Color.White, Offset(px - 3 * u, py - 6 * u + scroll * 9 * u), Offset(px + 3 * u, py - 6 * u + scroll * 9 * u), sw * 0.7f)
            arm(px - 8 * u, py + 3 * u, 35f); arm(px + 8 * u, py + 3 * u, -35f)
        }
        Mood.BREAK -> {
            val mx = cx + 14 * u; val my = base - 20 * u
            drawRoundRect(Color(0xFFFFFBF2), Offset(mx - 7 * u, my - 8 * u), Size(14 * u, 13 * u), androidx.compose.ui.geometry.CornerRadius(3 * u))
            drawRoundRect(OUTLINE, Offset(mx - 7 * u, my - 8 * u), Size(14 * u, 13 * u), androidx.compose.ui.geometry.CornerRadius(3 * u), style = Stroke(sw * 0.8f))
            drawArc(OUTLINE, -90f, 180f, false, Offset(mx + 4 * u, my - 5 * u), Size(7 * u, 7 * u), style = Stroke(sw * 0.8f))
            drawRect(Color(0xFFB5763A), Offset(mx - 5.5f * u, my - 6.5f * u), Size(11 * u, 3 * u))
            for (i in 0..1) {
                val p = (t * 0.6f + i * 0.5f) % 1f
                val sx = mx - 2 * u + i * 4 * u + sin(p * 6f) * 1.5f * u
                drawLine(Color.White.copy(alpha = 0.8f * (1 - p)), Offset(sx, my - 10 * u - p * 10 * u), Offset(sx, my - 14 * u - p * 10 * u), sw * 0.7f, StrokeCap.Round)
            }
            arm(mx - 9 * u, my, 20f); arm(cx - 30 * u, base - 24 * u, 15f)
        }
        Mood.SAD -> {
            arm(cx - 31 * u, base - 22 * u, 10f); arm(cx + 31 * u, base - 22 * u, -10f)
        }
        Mood.HAPPY -> {
            val wave = sin(t * 10f) * 15f
            arm(cx - 34 * u, base - 44 * u, 150f + wave); arm(cx + 34 * u, base - 44 * u, -150f - wave)
        }
        Mood.SLEEP -> {
            arm(cx - 30 * u, base - 20 * u, 30f); arm(cx + 30 * u, base - 20 * u, -30f)
        }
        else -> {
            val sway = sin(t * 2f) * 6f
            arm(cx - 32 * u, base - 24 * u, 18f + sway); arm(cx + 32 * u, base - 24 * u, -18f + sway)
        }
    }
}

private fun DrawScope.drawHat(hat: String?, cx: Float, base: Float, u: Float, sw: Float, t: Float) {
    val top = base - 62 * u
    fun shape(fill: Color, block: Path.() -> Unit) {
        val p = Path().apply(block); drawPath(p, fill); drawPath(p, OUTLINE, style = Stroke(sw * 0.9f))
    }
    when (hat) {
        "hat.beanie" -> {
            shape(Color(0xFF6C9BD2)) {
                moveTo(cx - 25 * u, top + 8 * u); cubicTo(cx - 25 * u, top - 18 * u, cx + 25 * u, top - 18 * u, cx + 25 * u, top + 8 * u); close()
            }
            drawRoundRect(Color(0xFF4F7FB8), Offset(cx - 27 * u, top + 2 * u), Size(54 * u, 9 * u), androidx.compose.ui.geometry.CornerRadius(4 * u))
            drawRoundRect(OUTLINE, Offset(cx - 27 * u, top + 2 * u), Size(54 * u, 9 * u), androidx.compose.ui.geometry.CornerRadius(4 * u), style = Stroke(sw * 0.9f))
            drawCircle(Color(0xFFFBF3E4), 6 * u, Offset(cx, top - 13 * u)); drawCircle(OUTLINE, 6 * u, Offset(cx, top - 13 * u), style = Stroke(sw * 0.9f))
        }
        "hat.beret" -> rotate(-12f, Offset(cx, top)) {
            drawOval(Color(0xFFB04A5A), Offset(cx - 26 * u, top - 8 * u), Size(50 * u, 16 * u))
            drawOval(OUTLINE, Offset(cx - 26 * u, top - 8 * u), Size(50 * u, 16 * u), style = Stroke(sw * 0.9f))
            drawLine(OUTLINE, Offset(cx, top - 8 * u), Offset(cx + 2 * u, top - 13 * u), sw)
        }
        "hat.flower" -> {
            val fc = Offset(cx + 18 * u, top + 2 * u)
            for (i in 0 until 5) {
                val a = i * 72f * PI.toFloat() / 180f + t * 0.3f
                val p = Offset(fc.x + sin(a) * 5 * u, fc.y + kotlin.math.cos(a) * 5 * u)
                drawCircle(Color(0xFFF7A1B5), 4 * u, p); drawCircle(OUTLINE, 4 * u, p, style = Stroke(sw * 0.6f))
            }
            drawCircle(Color(0xFFF4C95D), 3 * u, fc); drawCircle(OUTLINE, 3 * u, fc, style = Stroke(sw * 0.6f))
        }
        "hat.cap" -> {
            shape(Color(0xFFE07A5F)) {
                moveTo(cx - 24 * u, top + 6 * u); cubicTo(cx - 24 * u, top - 16 * u, cx + 24 * u, top - 16 * u, cx + 24 * u, top + 6 * u); close()
            }
            shape(Color(0xFFC9604A)) {
                moveTo(cx + 10 * u, top + 6 * u); quadraticTo(cx + 34 * u, top + 2 * u, cx + 38 * u, top + 8 * u); quadraticTo(cx + 24 * u, top + 11 * u, cx + 10 * u, top + 8 * u); close()
            }
            drawCircle(Color(0xFFFBF3E4), 4 * u, Offset(cx - 2 * u, top - 3 * u))
        }
        "hat.wizard" -> {
            shape(Color(0xFF5B4B9A)) {
                moveTo(cx - 24 * u, top + 6 * u); lineTo(cx + 6 * u, top - 40 * u); lineTo(cx + 24 * u, top + 6 * u); close()
            }
            drawOval(Color(0xFF4A3C82), Offset(cx - 32 * u, top + 1 * u), Size(64 * u, 10 * u))
            drawOval(OUTLINE, Offset(cx - 32 * u, top + 1 * u), Size(64 * u, 10 * u), style = Stroke(sw * 0.9f))
            sparkle(Offset(cx - 2 * u, top - 14 * u), 3.5f * u, Color(0xFFF4C95D))
            sparkle(Offset(cx + 9 * u, top - 4 * u), 2.5f * u, Color(0xFFF4C95D))
        }
        "hat.crown" -> shape(Color(0xFFF4C95D)) {
            moveTo(cx - 18 * u, top + 4 * u); lineTo(cx - 20 * u, top - 14 * u); lineTo(cx - 9 * u, top - 5 * u)
            lineTo(cx, top - 18 * u); lineTo(cx + 9 * u, top - 5 * u); lineTo(cx + 20 * u, top - 14 * u); lineTo(cx + 18 * u, top + 4 * u); close()
        }.also { drawCircle(Color(0xFFE07A5F), 2.5f * u, Offset(cx, top - 3 * u)) }
        "hat.mushroom" -> {
            shape(Color(0xFFD9534F)) {
                moveTo(cx - 30 * u, top + 6 * u); cubicTo(cx - 30 * u, top - 22 * u, cx + 30 * u, top - 22 * u, cx + 30 * u, top + 6 * u)
                quadraticTo(cx, top + 10 * u, cx - 30 * u, top + 6 * u); close()
            }
            for (p in listOf(Offset(-14f, -4f), Offset(4f, -10f), Offset(17f, -1f), Offset(-3f, 2f))) {
                drawCircle(Color(0xFFFFFBF2), 3.6f * u, Offset(cx + p.x * u, top + p.y * u))
            }
        }
    }
}

private fun DrawScope.sparkle(c: Offset, r: Float, color: Color = Color(0xFFF4C95D)) {
    val p = Path().apply {
        moveTo(c.x, c.y - r); quadraticTo(c.x, c.y, c.x + r, c.y); quadraticTo(c.x, c.y, c.x, c.y + r)
        quadraticTo(c.x, c.y, c.x - r, c.y); quadraticTo(c.x, c.y, c.x, c.y - r); close()
    }
    drawPath(p, color)
}

private fun DrawScope.drawZ(at: Offset, s: Float, sw: Float, alpha: Float) {
    val p = Path().apply { moveTo(at.x, at.y); lineTo(at.x + s, at.y); lineTo(at.x, at.y + s); lineTo(at.x + s, at.y + s) }
    translate(0f, 0f) { drawPath(p, OUTLINE.copy(alpha = alpha.coerceIn(0f, 1f)), style = Stroke(sw, cap = StrokeCap.Round)) }
}
