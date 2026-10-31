package buddy.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import buddy.app.AppController
import buddy.app.Strings
import buddy.core.DayMark
import buddy.core.Stats
import buddy.core.StatsRange
import kotlinx.datetime.TimeZone

@Composable
fun StatsSheet(controller: AppController, onClose: () -> Unit) {
    val state by controller.state.collectAsState()
    val stats = remember(state) { Stats(state, TimeZone.currentSystemDefault(), controller.now()) }
    var tab by remember { mutableStateOf(0) }

    SheetCard(onClose) {
        Title(Strings.Stats.title)
        Spacer(Modifier.height(Space.md))
        Tabs(Strings.Stats.tabs, tab, { tab = it })
        Spacer(Modifier.height(Space.lg))
        ScrollColumn(Modifier.weight(1f, fill = false)) {
            if (tab == 0) Summary(stats) else RangeView(controller, stats, StatsRange.entries[tab - 1])
        }
        Spacer(Modifier.height(Space.md))
        CloseButton(onClose)
    }
}

@Composable
private fun Summary(stats: Stats) {
    Text(Strings.Stats.last28Days, style = label(Type.lead))
    Spacer(Modifier.height(Space.sm))
    val days = stats.tracker()
    days.chunked(7).forEach { week ->
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), modifier = Modifier.padding(bottom = Space.sm)) {
            week.forEach { (date, mark) ->
                Box(
                    Modifier.size(36.dp)
                        .sticker(Shapes.cell, when (mark) { DayMark.NONE -> Ink.dayOff; DayMark.FOCUS -> Ink.leaf; DayMark.DEEP -> Ink.leafDark }),
                    contentAlignment = Alignment.Center,
                ) { Text("${date.day}", style = label(Type.caption, color = if (mark == DayMark.NONE) Ink.muted else Ink.onSelected)) }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Space.xl), verticalAlignment = Alignment.CenterVertically) {
        Legend(Ink.dayOff, Strings.Stats.legendNone); Legend(Ink.leaf, Strings.Stats.legendFocus); Legend(Ink.leafDark, Strings.Stats.legendDeep)
    }
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(Space.lg)) {
        StatBox(Strings.Stats.focused, formatMinutes(stats.totalMinutes), Modifier.weight(1f))
        StatBox(Strings.Stats.sessions, "${stats.totalSessions}", Modifier.weight(1f))
    }
    Spacer(Modifier.height(Space.lg))
    Row(horizontalArrangement = Arrangement.spacedBy(Space.lg)) {
        StatBox(Strings.Stats.streak, Strings.Stats.days(stats.currentStreak), Modifier.weight(1f))
        StatBox(Strings.Stats.bestStreak, Strings.Stats.days(stats.bestStreak), Modifier.weight(1f))
    }
}

@Composable
private fun Legend(c: Color, text: String) = Row(verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(Space.xl).sticker(Shapes.dot, c, Strokes.hair))
    Spacer(Modifier.width(Space.xs)); Text(text, style = label(Type.caption, FontWeight.Medium, Ink.muted))
}

@Composable
private fun StatBox(title: String, value: String, modifier: Modifier) {
    Column(modifier.sticker(Shapes.card, Ink.card, Strokes.line).padding(Space.lg)) {
        Text(title, style = label(Type.foot, FontWeight.Medium, Ink.muted))
        Text(value, style = label(Type.titleLarge, FontWeight.Black))
    }
}

@Composable
private fun RangeView(controller: AppController, stats: Stats, range: StatsRange) {
    val bars = stats.bars(range)
    val measurer = rememberTextMeasurer()
    Text(Strings.Stats.total(formatMinutes(stats.rangeTotal(range))), style = label(Type.button))
    Spacer(Modifier.height(Space.md))
    Canvas(Modifier.fillMaxWidth().aspectRatio(1.6f)) {
        val max = (bars.maxOfOrNull { it.minutes } ?: 0).coerceAtLeast(30)
        val labelH = 18.dp.toPx()
        val chartH = size.height - labelH
        val slot = size.width / bars.size
        val bw = slot * 0.62f
        drawLine(Ink.outline, Offset(0f, chartH), Offset(size.width, chartH), Strokes.line.toPx())
        bars.forEachIndexed { i, b ->
            val h = chartH * b.minutes / max
            val x = i * slot + (slot - bw) / 2
            if (b.minutes > 0) {
                drawRoundRect(Ink.leaf, Offset(x, chartH - h), Size(bw, h), CornerRadius(bw * 0.25f))
                drawRoundRect(Ink.outline, Offset(x, chartH - h), Size(bw, h), CornerRadius(bw * 0.25f), style = Stroke(Strokes.thin.toPx()))
            }
            if (bars.size <= 12 || i % 5 == 0) {
                val tl = measurer.measure(b.label, label(Type.micro, FontWeight.Medium, Ink.muted))
                drawText(tl, topLeft = Offset(i * slot + (slot - tl.size.width) / 2, chartH + Space.xxs.toPx()))
            }
        }
        val top = measurer.measure(formatMinutes(max), label(Type.micro, FontWeight.Medium, Ink.muted))
        drawText(top, topLeft = Offset(0f, 0f))
    }
    Spacer(Modifier.height(Space.xl))
    val tags = stats.byTag(range)
    val state = controller.state.value
    if (tags.isEmpty()) Text(Strings.Stats.empty, style = label(Type.body, FontWeight.Medium, Ink.muted))
    tags.forEach { (tag, min) ->
        val info = state.tags[tag]
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            TagDot(Color(info?.color ?: 0xFF999999))
            Spacer(Modifier.width(Space.md))
            Text(info?.name ?: tag, style = label(Type.body), modifier = Modifier.weight(1f))
            Text(formatMinutes(min), style = label(Type.body))
        }
    }
}
