package buddy.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import buddy.app.AppController
import buddy.core.DayMark
import buddy.core.Stats
import buddy.core.StatsRange
import kotlinx.datetime.TimeZone

@Composable
fun StatsSheet(controller: AppController, onClose: () -> Unit) {
    val state by controller.state.collectAsState()
    val stats = remember(state) { Stats(state, TimeZone.currentSystemDefault(), controller.now()) }
    var tab by remember { mutableStateOf(0) }

    Scrim(onClose) {
        Panel(Modifier.widthIn(max = 440.dp).padding(12.dp)) {
            Title("Focus stats")
            Spacer(Modifier.height(8.dp))
            Tabs(listOf("Summary", "Day", "Week", "Month", "Year"), tab, { tab = it })
            Spacer(Modifier.height(10.dp))
            Column(Modifier.height(470.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                if (tab == 0) Summary(stats) else RangeView(controller, stats, StatsRange.entries[tab - 1])
            }
            Spacer(Modifier.height(8.dp))
            CloseButton(onClose)
        }
    }
}

@Composable
private fun Summary(stats: Stats) {
    Text("Last 28 days", style = label(15.sp))
    Spacer(Modifier.height(6.dp))
    val days = stats.tracker()
    days.chunked(7).forEach { week ->
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
            week.forEach { (date, mark) ->
                val shape = RoundedCornerShape(8.dp)
                Box(
                    Modifier.size(36.dp).clip(shape).background(
                        when (mark) { DayMark.NONE -> Color(0xFFEDE3D3); DayMark.FOCUS -> Ink.leaf; DayMark.DEEP -> Ink.leafDark },
                    ).border(2.dp, Ink.outline, shape),
                    contentAlignment = Alignment.Center,
                ) { Text("${date.day}", style = label(11.sp, color = if (mark == DayMark.NONE) Ink.muted else Color.White)) }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Legend(Color(0xFFEDE3D3), "none"); Legend(Ink.leaf, "focus"); Legend(Ink.leafDark, "deep focus")
    }
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatBox("Focused", formatMinutes(stats.totalMinutes), Modifier.weight(1f))
        StatBox("Sessions", "${stats.totalSessions}", Modifier.weight(1f))
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatBox("Streak", "${stats.currentStreak} d", Modifier.weight(1f))
        StatBox("Best streak", "${stats.bestStreak} d", Modifier.weight(1f))
    }
}

@Composable
private fun Legend(c: Color, text: String) = Row(verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(c).border(1.dp, Ink.outline, RoundedCornerShape(3.dp)))
    Spacer(Modifier.width(4.dp)); Text(text, style = label(11.sp, FontWeight.Medium, Ink.muted))
}

@Composable
private fun StatBox(title: String, value: String, modifier: Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Column(modifier.clip(shape).background(Color.White).border(2.dp, Ink.outline, shape).padding(10.dp)) {
        Text(title, style = label(12.sp, FontWeight.Medium, Ink.muted))
        Text(value, style = label(24.sp, FontWeight.Black))
    }
}

@Composable
private fun RangeView(controller: AppController, stats: Stats, range: StatsRange) {
    val bars = stats.bars(range)
    val measurer = rememberTextMeasurer()
    Text("Total: ${formatMinutes(stats.rangeTotal(range))}", style = label(18.sp))
    Spacer(Modifier.height(8.dp))
    Canvas(Modifier.fillMaxWidth().aspectRatio(1.6f)) {
        val max = (bars.maxOfOrNull { it.minutes } ?: 0).coerceAtLeast(30)
        val labelH = 18.dp.toPx()
        val chartH = size.height - labelH
        val slot = size.width / bars.size
        val bw = slot * 0.62f
        drawLine(Ink.outline, Offset(0f, chartH), Offset(size.width, chartH), 2.dp.toPx())
        bars.forEachIndexed { i, b ->
            val h = chartH * b.minutes / max
            val x = i * slot + (slot - bw) / 2
            if (b.minutes > 0) {
                drawRoundRect(Ink.leaf, Offset(x, chartH - h), Size(bw, h), CornerRadius(bw * 0.25f))
                drawRoundRect(Ink.outline, Offset(x, chartH - h), Size(bw, h), CornerRadius(bw * 0.25f), style = Stroke(1.5.dp.toPx()))
            }
            if (bars.size <= 12 || i % 5 == 0) {
                val tl = measurer.measure(b.label, label(10.sp, FontWeight.Medium, Ink.muted))
                drawText(tl, topLeft = Offset(i * slot + (slot - tl.size.width) / 2, chartH + 3.dp.toPx()))
            }
        }
        val top = measurer.measure(formatMinutes(max), label(10.sp, FontWeight.Medium, Ink.muted))
        drawText(top, topLeft = Offset(0f, 0f))
    }
    Spacer(Modifier.height(12.dp))
    val tags = stats.byTag(range)
    val state = controller.state.value
    if (tags.isEmpty()) Text("No focus sessions in this period yet.", style = label(14.sp, FontWeight.Medium, Ink.muted))
    tags.forEach { (tag, min) ->
        val info = state.tags[tag]
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(14.dp).clip(RoundedCornerShape(50)).background(Color(info?.color ?: 0xFF999999)).border(1.5.dp, Ink.outline, RoundedCornerShape(50)))
            Spacer(Modifier.width(8.dp))
            Text(info?.name ?: tag, style = label(14.sp), modifier = Modifier.weight(1f))
            Text(formatMinutes(min), style = label(14.sp))
        }
    }
}
