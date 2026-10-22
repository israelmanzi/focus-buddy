package buddy.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class Glyph { ROOMS, STATS, SHOP, DECORATE, BUDDY, SETTINGS, SYNC }

@Composable
fun IconButton(glyph: Glyph, text: String, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick).padding(4.dp)) {
        val shape = RoundedCornerShape(16.dp)
        Box(
            Modifier.size(56.dp).clip(shape).background(color).border(2.5.dp, Ink.outline, shape),
            contentAlignment = Alignment.Center,
        ) { Canvas(Modifier.size(34.dp)) { drawGlyph(glyph) } }
        Text(text, style = label(11.sp), modifier = Modifier.padding(top = 2.dp))
    }
}

fun DrawScope.drawGlyph(g: Glyph) {
    val w = size.width; val h = size.height; val sw = w * 0.08f
    val o = Ink.outline
    fun stroke(p: Path, fill: Color? = null) { fill?.let { drawPath(p, it) }; drawPath(p, o, style = Stroke(sw, cap = StrokeCap.Round)) }
    when (g) {
        Glyph.ROOMS -> {
            stroke(Path().apply { moveTo(w * .22f, h * .95f); lineTo(w * .22f, h * .12f); lineTo(w * .78f, h * .12f); lineTo(w * .78f, h * .95f); close() }, Color(0xFF9BC9C2))
            drawCircle(o, w * .05f, Offset(w * .66f, h * .55f))
        }
        Glyph.STATS -> {
            listOf(.45f, .7f, .3f, .85f).forEachIndexed { i, v ->
                val x = w * (.12f + i * .2f)
                drawRoundRect(listOf(Ink.berry, Ink.leaf, Ink.butter, Ink.leaf)[i], Offset(x, h * (.95f - v)), Size(w * .15f, h * v), CornerRadius(w * .03f))
                drawRoundRect(o, Offset(x, h * (.95f - v)), Size(w * .15f, h * v), CornerRadius(w * .03f), style = Stroke(sw * .7f))
            }
        }
        Glyph.SHOP -> {
            stroke(Path().apply { moveTo(w * .15f, h * .45f); lineTo(w * .15f, h * .92f); lineTo(w * .85f, h * .92f); lineTo(w * .85f, h * .45f) }, Ink.paper)
            for (i in 0..3) {
                val x = w * (.08f + i * .21f)
                stroke(Path().apply { moveTo(x, h * .2f); lineTo(x + w * .21f, h * .2f); lineTo(x + w * .21f, h * .4f); quadraticTo(x + w * .105f, h * .55f, x, h * .4f); close() }, if (i % 2 == 0) Ink.berry else Color.White)
            }
        }
        Glyph.DECORATE -> rotate(35f) {
            drawRoundRect(Color(0xFFB5763A), Offset(w * .43f, h * .45f), Size(w * .14f, h * .5f), CornerRadius(w * .05f))
            drawRoundRect(o, Offset(w * .43f, h * .45f), Size(w * .14f, h * .5f), CornerRadius(w * .05f), style = Stroke(sw * .8f))
            stroke(Path().apply { moveTo(w * .3f, h * .45f); lineTo(w * .3f, h * .15f); quadraticTo(w * .5f, h * .02f, w * .7f, h * .15f); lineTo(w * .7f, h * .45f); close() }, Color(0xFF6C9BD2))
        }
        Glyph.BUDDY -> {
            drawCircle(Color(0xFFF6E2C4), w * .12f, Offset(w * .28f, h * .25f)); drawCircle(o, w * .12f, Offset(w * .28f, h * .25f), style = Stroke(sw * .8f))
            drawCircle(Color(0xFFF6E2C4), w * .12f, Offset(w * .72f, h * .25f)); drawCircle(o, w * .12f, Offset(w * .72f, h * .25f), style = Stroke(sw * .8f))
            drawOval(Color(0xFFF6E2C4), Offset(w * .1f, h * .2f), Size(w * .8f, h * .72f)); drawOval(o, Offset(w * .1f, h * .2f), Size(w * .8f, h * .72f), style = Stroke(sw))
            drawCircle(o, w * .05f, Offset(w * .37f, h * .52f)); drawCircle(o, w * .05f, Offset(w * .63f, h * .52f))
            drawCircle(Color(0xFFF2A29A), w * .06f, Offset(w * .25f, h * .64f)); drawCircle(Color(0xFFF2A29A), w * .06f, Offset(w * .75f, h * .64f))
        }
        Glyph.SETTINGS -> {
            val c = Offset(w / 2, h / 2)
            val p = Path()
            for (i in 0 until 16) {
                val a = i * PI.toFloat() / 8
                val r = if (i % 2 == 0) w * .45f else w * .33f
                val pt = Offset(c.x + cos(a) * r, c.y + sin(a) * r)
                if (i == 0) p.moveTo(pt.x, pt.y) else p.lineTo(pt.x, pt.y)
            }
            p.close(); stroke(p, Color(0xFFD9CFC2))
            drawCircle(Ink.paper, w * .13f, c); drawCircle(o, w * .13f, c, style = Stroke(sw))
        }
        Glyph.SYNC -> {
            drawArc(o, 200f, 230f, false, Offset(w * .15f, h * .15f), Size(w * .7f, h * .7f), style = Stroke(sw, cap = StrokeCap.Round))
            stroke(Path().apply { moveTo(w * .78f, h * .18f); lineTo(w * .8f, h * .4f); lineTo(w * .58f, h * .36f) })
        }
    }
}
