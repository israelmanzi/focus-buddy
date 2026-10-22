package buddy.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Ink {
    val outline = Color(0xFF4A342E)
    val paper = Color(0xFFFBF3E4)
    val paperDeep = Color(0xFFF3E3C7)
    val sky = Color(0xFFCDE8E5)
    val leaf = Color(0xFF7FB069)
    val leafDark = Color(0xFF5E8C4A)
    val berry = Color(0xFFE07A5F)
    val butter = Color(0xFFF2CC8F)
    val acorn = Color(0xFFB5763A)
    val dim = Color(0x99302420)
    val muted = Color(0xFF8C7468)
}

@Composable
fun BuddyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Ink.leafDark, onPrimary = Color.White, background = Ink.paper, surface = Ink.paper,
            onSurface = Ink.outline, onBackground = Ink.outline, secondary = Ink.berry,
        ),
        content = content,
    )
}

fun label(size: TextUnit = 16.sp, weight: FontWeight = FontWeight.Bold, color: Color = Ink.outline) =
    TextStyle(fontSize = size, fontWeight = weight, color = color, letterSpacing = 0.5.sp)

/** Chunky sticker-style button with a drop shadow. */
@Composable
fun ChunkyButton(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Ink.butter,
    textColor: Color = Ink.outline,
    enabled: Boolean = true,
    fontSize: TextUnit = 18.sp,
    padding: PaddingValues = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(modifier) {
        Box(Modifier.matchParentSize().padding(top = 4.dp).clip(shape).background(Ink.outline))
        Box(
            Modifier
                .padding(bottom = 4.dp)
                .clip(shape)
                .background(if (enabled) color else Color(0xFFD9CFC2))
                .border(2.5.dp, Ink.outline, shape)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, style = label(fontSize, color = if (enabled) textColor else Ink.muted))
        }
    }
}

/** Paper card with an outline, used for every panel. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    color: Color = Ink.paper,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.clip(shape).background(color).border(3.dp, Ink.outline, shape).padding(padding),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

/** Full-screen dimmed overlay that closes when tapping outside its content. */
@Composable
fun Scrim(onDismiss: (() -> Unit)?, content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Ink.dim).let {
            if (onDismiss == null) it
            else it.clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
        },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.clickable(remember { MutableInteractionSource() }, indication = null) {}) { content() }
    }
}

@Composable
fun Title(text: String, size: TextUnit = 26.sp) = Text(text, style = label(size, FontWeight.ExtraBold))

@Composable
fun AcornCount(amount: Int, modifier: Modifier = Modifier, big: Boolean = false) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier.clip(shape).background(Color.White).border(2.5.dp, Ink.outline, shape)
            .padding(start = 6.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AcornIcon(Modifier.size(if (big) 34.dp else 26.dp))
        Spacer(Modifier.width(6.dp))
        Text("$amount", style = label(if (big) 24.sp else 18.sp))
    }
}

@Composable
fun AcornIcon(modifier: Modifier = Modifier) = Canvas(modifier) { drawAcorn(Offset.Zero, size) }

fun DrawScope.drawAcorn(topLeft: Offset, s: Size) {
    val w = s.width; val h = s.height; val x = topLeft.x; val y = topLeft.y
    val sw = w * 0.07f
    val nut = Path().apply {
        moveTo(x + w * 0.22f, y + h * 0.45f)
        cubicTo(x + w * 0.2f, y + h * 0.8f, x + w * 0.45f, y + h * 0.95f, x + w * 0.5f, y + h * 0.97f)
        cubicTo(x + w * 0.55f, y + h * 0.95f, x + w * 0.8f, y + h * 0.8f, x + w * 0.78f, y + h * 0.45f)
        close()
    }
    drawPath(nut, Ink.acorn); drawPath(nut, Ink.outline, style = Stroke(sw))
    val cap = Path().apply {
        moveTo(x + w * 0.12f, y + h * 0.48f)
        cubicTo(x + w * 0.1f, y + h * 0.2f, x + w * 0.9f, y + h * 0.2f, x + w * 0.88f, y + h * 0.48f)
        close()
    }
    drawPath(cap, Color(0xFF7A5230)); drawPath(cap, Ink.outline, style = Stroke(sw))
    drawLine(Ink.outline, Offset(x + w * 0.5f, y + h * 0.26f), Offset(x + w * 0.56f, y + h * 0.08f), sw * 1.3f)
    drawCircle(Color.White.copy(alpha = 0.5f), w * 0.06f, Offset(x + w * 0.36f, y + h * 0.62f))
}

@Composable
fun Tabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEachIndexed { i, o ->
            val shape = RoundedCornerShape(10.dp)
            Box(
                Modifier.clip(shape)
                    .background(if (i == selected) Ink.leaf else Ink.paperDeep)
                    .border(2.dp, Ink.outline, shape)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) { Text(o, style = label(13.sp, color = if (i == selected) Color.White else Ink.outline)) }
        }
    }
}

@Composable
fun CloseButton(onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(RoundedCornerShape(50)).background(Ink.berry)
            .border(3.dp, Ink.outline, RoundedCornerShape(50)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text("✕", style = label(22.sp, FontWeight.Black, Color.White)) }
}
