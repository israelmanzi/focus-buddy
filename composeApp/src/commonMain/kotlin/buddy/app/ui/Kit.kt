package buddy.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

// ---------------------------------------------------------------- surfaces

/**
 * The hand-drawn sticker treatment: rounded fill with an ink outline. [onClick] makes it tappable;
 * add padding after it so the touch target hugs the sticker.
 */
fun Modifier.sticker(
    shape: Shape = Shapes.card,
    color: Color = Ink.card,
    stroke: Dp = Strokes.line,
    strokeColor: Color = Ink.outline,
    onClick: (() -> Unit)? = null,
): Modifier {
    val base = clip(shape).background(color).border(stroke, strokeColor, shape)
    return if (onClick == null) base else base.clickable(onClick = onClick)
}

/** Clickable without ripple, for scrims and whole-row hit areas. */
@Composable
fun Modifier.tap(onClick: () -> Unit): Modifier =
    clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)

/** Paper card with an outline, used for every panel. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    color: Color = Ink.paper,
    padding: Dp = Space.xxl,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.sticker(Shapes.panel, color, Strokes.heavy).padding(padding),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

/** Full-screen dimmed overlay that closes when tapping outside its content. */
@Composable
fun Scrim(onDismiss: (() -> Unit)?, content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Ink.dim).let { if (onDismiss == null) it else it.tap(onDismiss) },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.tap {}) { content() }
    }
}

/**
 * A centred sheet over a scrim. Its height is capped to the window (minus [outerPadding]) so
 * short windows scroll instead of clipping; wide windows keep the phone-sized card.
 */
@Composable
fun SheetCard(
    onDismiss: (() -> Unit)?,
    maxWidth: Dp = Sizes.sheetWidth,
    outerPadding: Dp = Space.xl,
    padding: Dp = Space.xxl,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scrim(onDismiss) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(outerPadding), contentAlignment = Alignment.Center) {
            Panel(
                Modifier.widthIn(max = maxWidth).heightIn(max = minOf(maxHeight, Sizes.sheetHeight)),
                padding = padding,
                content = content,
            )
        }
    }
}

/** Vertically scrollable column used for sheet bodies. */
@Composable
fun ScrollColumn(
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    content: @Composable ColumnScope.() -> Unit,
) = Column(modifier.verticalScroll(rememberScrollState()), horizontalAlignment = horizontalAlignment, content = content)

// ---------------------------------------------------------------- buttons & chips

/** Chunky sticker-style button with a drop shadow. */
@Composable
fun ChunkyButton(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Ink.butter,
    textColor: Color = Ink.outline,
    enabled: Boolean = true,
    fontSize: TextUnit = Type.button,
    padding: PaddingValues = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
    onClick: () -> Unit,
) {
    Box(modifier) {
        Box(Modifier.matchParentSize().padding(top = Space.xs).clip(Shapes.card).background(Ink.outline))
        Box(
            Modifier
                .padding(bottom = Space.xs)
                .clip(Shapes.card)
                .background(if (enabled) color else Ink.disabled)
                .border(Strokes.bold, Ink.outline, Shapes.card)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, style = label(fontSize, color = if (enabled) textColor else Ink.muted))
        }
    }
}

/** A selectable pill. [selected] uses the full colour; otherwise it is a translucent wash. */
@Composable
fun Chip(text: String, color: Color, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        text,
        style = label(Type.small, color = if (selected) Ink.onSelected else Ink.outline),
        maxLines = 1,
        modifier = modifier
            .sticker(
                shape = Shapes.pill,
                color = if (selected) color else color.copy(alpha = 0.25f),
                stroke = if (selected) Strokes.bold else Strokes.thin,
                onClick = onClick,
            )
            .padding(horizontal = Space.xl, vertical = Space.sm),
    )
}

/** Small segmented switch used for sheet tabs. Shrinks a little on narrow windows. */
@Composable
fun Tabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val compact = options.size >= 5 && maxWidth < 330.dp
        val hPad = if (compact) Space.md else Space.lg
        val gap = if (compact) Space.xs else Space.sm
        val font = if (compact) Type.foot else Type.small
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            options.forEachIndexed { i, o ->
                Text(
                    o,
                    style = label(font, color = if (i == selected) Ink.onSelected else Ink.outline),
                    maxLines = 1,
                    modifier = Modifier
                        .sticker(
                            shape = Shapes.pill,
                            color = if (i == selected) Ink.leaf else Ink.paperDeep,
                            stroke = if (i == selected) Strokes.bold else Strokes.thin,
                            onClick = { onSelect(i) },
                        )
                        .padding(horizontal = hPad, vertical = Space.sm),
                )
            }
        }
    }
}

@Composable
fun CloseButton(onClick: () -> Unit) {
    Box(
        Modifier.size(Sizes.closeButton)
            .sticker(Shapes.pill, Ink.berry, Strokes.heavy, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text("✕", style = label(Type.title, FontWeight.Black, Ink.onSelected)) }
}

@Composable
fun RoundStep(text: String, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).sticker(Shapes.pill, Ink.butter, Strokes.bold, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = label(Type.title, FontWeight.Black)) }
}

// ---------------------------------------------------------------- text & fields

@Composable
fun Title(text: String, size: TextUnit = Type.display) = Text(text, style = label(size, FontWeight.ExtraBold))

@Composable
fun SpeechBubble(text: String) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 40.dp), contentAlignment = Alignment.Center) {
        Text(
            text, textAlign = TextAlign.Center, style = label(Type.bodyLarge),
            modifier = Modifier.widthIn(max = Sizes.bubbleWidth)
                .clip(Shapes.bubble)
                .background(Ink.bubble)
                .border(Strokes.line, Ink.outline.copy(alpha = 0.25f), Shapes.bubble)
                .padding(horizontal = 18.dp, vertical = Space.lg),
        )
    }
}

@Composable
fun TextInput(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier, secret: Boolean = false) {
    Box(
        modifier.sticker(Shapes.field, Ink.card, Strokes.line).padding(horizontal = Space.lg, vertical = Space.md),
    ) {
        if (value.isEmpty()) Text(hint, style = label(Type.body, FontWeight.Normal, Ink.muted))
        BasicTextField(
            value, onChange, singleLine = true, textStyle = label(Type.body, FontWeight.Medium),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun BuddySwitch(checked: Boolean, onChange: (Boolean) -> Unit) = Switch(
    checked, onChange,
    colors = SwitchDefaults.colors(
        checkedTrackColor = Ink.leaf, checkedThumbColor = Ink.card,
        uncheckedTrackColor = Ink.paperDeep, uncheckedThumbColor = Ink.muted, uncheckedBorderColor = Ink.outline,
    ),
)

// ---------------------------------------------------------------- acorns & swatches

@Composable
fun AcornCount(amount: Int, modifier: Modifier = Modifier, big: Boolean = false) {
    Row(
        modifier.sticker(Shapes.pill, Ink.card, Strokes.bold)
            .padding(start = Space.sm, end = 14.dp, top = Space.xs, bottom = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AcornIcon(Modifier.size(if (big) 34.dp else 26.dp))
        Spacer(Modifier.width(Space.sm))
        Text("$amount", style = label(if (big) Type.titleLarge else Type.button))
    }
}

/** Acorn icon followed by a price; used on shop tiles and room cards. */
@Composable
fun AcornPrice(price: Int, iconSize: Dp = 14.dp, fontSize: TextUnit = Type.foot, color: Color = Ink.outline) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AcornIcon(Modifier.size(iconSize))
        Spacer(Modifier.width(Space.xs))
        Text("$price", style = label(fontSize, color = color))
    }
}

/** Round colour swatch for a tag. */
@Composable
fun TagDot(color: Color, size: Dp = 14.dp) {
    Box(Modifier.size(size).sticker(Shapes.pill, color, Strokes.thin))
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

// ---------------------------------------------------------------- item tiles

/** A square art tile with a caption or price footer, used by the shop and decorate picker. */
@Composable
fun ItemCell(
    selected: Boolean,
    footer: String?,
    price: Int?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    art: DrawScope.() -> Unit,
) {
    Column(
        modifier.sticker(
            shape = Shapes.tile,
            color = if (selected) Ink.selected else Ink.card,
            stroke = if (selected) Strokes.heavy else Strokes.line,
            onClick = onClick,
        ).padding(Space.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(1f), onDraw = art)
        when {
            price != null -> AcornPrice(price, iconSize = 14.dp, fontSize = Type.foot)
            footer != null -> Text(footer, style = label(Type.caption, FontWeight.Medium, Ink.muted))
            else -> Text(" ", style = label(Type.caption))
        }
    }
}
