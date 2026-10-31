package buddy.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import buddy.app.AppController
import buddy.app.Phase
import buddy.app.art.Mood
import buddy.app.art.PipView
import buddy.app.data.RunningSession
import buddy.core.Rules
import kotlinx.coroutines.delay

fun formatClock(totalSec: Long): String {
    val s = totalSec.coerceAtLeast(0)
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${sec.toString().padStart(2, '0')}"
    else "${m.toString().padStart(2, '0')}:${sec.toString().padStart(2, '0')}"
}

fun formatMinutes(min: Int) = if (min >= 60) "${min / 60}h ${(min % 60).toString().padStart(2, '0')}m" else "${min}m"

/**
 * A 250 ms clock. The [State] is returned unread so only the leaf that displays time recomposes.
 */
@Composable
private fun rememberNowState(controller: AppController, periodMs: Long = 250): State<Long> {
    val now = remember { mutableLongStateOf(controller.now()) }
    LaunchedEffect(Unit) { while (true) { now.longValue = controller.now(); delay(periodMs) } }
    return now
}

val tagColors = listOf(0xFF7FB069, 0xFFE07A5F, 0xFF6C9BD2, 0xFFF2CC8F, 0xFFB48EAD, 0xFF81B29A, 0xFFF4A261, 0xFF8D99AE)

@Composable
fun FocusSetup(controller: AppController, onClose: () -> Unit) {
    val settings by controller.settings.collectAsState()
    val state by controller.state.collectAsState()
    var minutes by remember { mutableStateOf(settings.defaultMinutes.toFloat()) }
    var countUp by remember { mutableStateOf(settings.countUp) }
    var tag by remember { mutableStateOf(settings.lastTag.takeIf { it in state.tags } ?: Rules.DEFAULT_TAG) }
    var deep by remember { mutableStateOf(settings.deepFocus && controller.platform.supportsDeepFocus) }
    var newTag by remember { mutableStateOf<String?>(null) }
    val p = controller.platform

    SheetCard(onClose, maxWidth = 420.dp, outerPadding = Space.xxl, padding = Space.xxl) {
        ScrollColumn {
            PipView(state.style, Mood.READING, Modifier.size(120.dp, 110.dp))
            // clock face
            Panel(color = Ink.clockFace, padding = 14.dp) {
                Text(
                    if (countUp) "00:00" else formatClock(minutes.toLong() * 60).let { if (minutes >= 60) it.dropLast(3) else it },
                    style = label(Type.clockSetup, FontWeight.Black, Ink.onSelected),
                )
                Text(
                    if (countUp) "stopwatch" else if (minutes >= 60) "hours : minutes" else "minutes",
                    style = label(Type.small, color = Ink.onSelected),
                )
                if (!countUp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RoundStep("−") { minutes = (minutes - Rules.STEP_MINUTES).coerceAtLeast(Rules.MIN_MINUTES.toFloat()) }
                        Slider(
                            value = minutes,
                            onValueChange = { minutes = (it / Rules.STEP_MINUTES).toInt() * Rules.STEP_MINUTES.toFloat() },
                            valueRange = Rules.MIN_MINUTES.toFloat()..Rules.MAX_MINUTES.toFloat(),
                            modifier = Modifier.weight(1f).padding(horizontal = Space.sm),
                            colors = SliderDefaults.colors(
                                thumbColor = Ink.butter, activeTrackColor = Ink.outline,
                                inactiveTrackColor = Ink.onSelected.copy(alpha = 0.5f),
                            ),
                        )
                        RoundStep("+") { minutes = (minutes + Rules.STEP_MINUTES).coerceAtMost(Rules.MAX_MINUTES.toFloat()) }
                    }
                }
                Tabs(listOf("Countdown", "Stopwatch"), if (countUp) 1 else 0, { countUp = it == 1 }, Modifier.padding(top = Space.md))
            }
            Spacer(Modifier.height(Space.xl))
            // tags
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Tag", style = label(Type.body))
                state.tags.values.filter { !it.archived }.forEach { t ->
                    Chip(t.name, Color(t.color), selected = t.id == tag) { tag = t.id }
                }
                if (newTag == null) Chip("+ new", Ink.paperDeep, false) { newTag = "" }
            }
            newTag?.let { nt ->
                Row(Modifier.padding(top = Space.md), verticalAlignment = Alignment.CenterVertically) {
                    TextInput(nt, { newTag = it.take(24) }, "Tag name", Modifier.weight(1f))
                    Spacer(Modifier.width(Space.md))
                    ChunkyButton("Add", fontSize = Type.body, padding = PaddingValues(Space.xl, Space.md), enabled = nt.isNotBlank()) {
                        val color = tagColors[state.tags.size % tagColors.size]
                        controller.upsertTag(null, nt.trim(), color)
                        newTag = null
                        controller.state.value.tags.values.lastOrNull { it.name == nt.trim() }?.let { tag = it.id }
                    }
                }
            }
            if (p.supportsDeepFocus) {
                Row(Modifier.fillMaxWidth().padding(top = Space.lg), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Deep Focus", style = label(Type.lead))
                        Text(
                            when {
                                !deep -> "Leaving the app ends the session after ${settings.graceSec}s"
                                !p.deepFocusReady() -> "Needs permissions — tap Set up"
                                settings.packages.isEmpty() -> "No apps chosen yet (Settings)"
                                settings.allowMode -> "Only ${settings.packages.size} allowed apps"
                                else -> "Blocks ${settings.packages.size} apps"
                            },
                            style = label(Type.foot, FontWeight.Normal, Ink.muted),
                        )
                    }
                    if (deep && !p.deepFocusReady()) {
                        ChunkyButton("Set up", fontSize = Type.small, padding = PaddingValues(Space.lg, Space.sm)) { p.requestDeepFocusPermissions() }
                        Spacer(Modifier.width(Space.md))
                    }
                    BuddySwitch(deep) { deep = it }
                }
            }
            Spacer(Modifier.height(Space.xxl))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.xl), verticalAlignment = Alignment.CenterVertically) {
                ChunkyButton("Back", color = Ink.berry, textColor = Ink.onSelected, fontSize = Type.bodyLarge) { onClose() }
                ChunkyButton("Start", color = Ink.leaf, textColor = Ink.onSelected, fontSize = Type.titleLarge) {
                    controller.startSession(minutes.toInt(), countUp, tag, deep && p.deepFocusReady())
                    onClose()
                }
            }
        }
    }
}

@Composable
fun RunningScreen(controller: AppController, phase: Phase.Running) {
    val rs = phase.session
    val now = rememberNowState(controller)
    val state by controller.state.collectAsState()
    val tagInfo = state.tags[rs.tag]

    Box(Modifier.fillMaxSize().background(Ink.night).clickable(enabled = false) {}) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(Space.xxxl), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(Space.xxxl))
            RunningHint(rs, now)
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                PipView(state.style, Mood.READING, Modifier.size(260.dp, 260.dp))
            }
            tagInfo?.let { Chip(it.name, Color(it.color), true) {} }
            Spacer(Modifier.height(Space.lg))
            RunningClock(rs, now)
            if (rs.deepFocus) Text("Deep Focus on", style = label(Type.small, color = Ink.butter))
            Spacer(Modifier.height(Space.xxxl))
            RunningStop(controller, rs, now)
            Spacer(Modifier.height(Space.xxxl))
        }
    }
}

@Composable
private fun RunningHint(rs: RunningSession, now: State<Long>) {
    val elapsed = (now.value - rs.startedAt) / 1000
    Text(
        if (rs.awaySince != null) "Come back… I'm waiting!"
        else listOf("Shh… reading time.", "We've got this.", "Page by page…")[((elapsed / 20) % 3).toInt()],
        style = label(Type.button, color = Ink.paper), textAlign = TextAlign.Center,
    )
}

@Composable
private fun RunningClock(rs: RunningSession, now: State<Long>) {
    val elapsed = (now.value - rs.startedAt) / 1000
    val shown = if (rs.countUp) elapsed else rs.plannedMin * 60L - elapsed
    Text(formatClock(shown), style = label(Type.clock, FontWeight.Black, Ink.onSelected))
}

@Composable
private fun RunningStop(controller: AppController, rs: RunningSession, now: State<Long>) {
    val elapsed = (now.value - rs.startedAt) / 1000
    val freeLeft = controller.freeCancelLeft(rs)
    when {
        freeLeft > 0 -> ChunkyButton("Cancel ($freeLeft)", color = Ink.paperDeep) { controller.stopSession() }
        rs.countUp -> ChunkyButton(
            if (elapsed >= Rules.MIN_MINUTES * 60) "Done!" else "Stop (under ${Rules.MIN_MINUTES} min earns nothing)",
            color = Ink.leaf, textColor = Ink.onSelected,
            fontSize = if (elapsed >= Rules.MIN_MINUTES * 60) Type.heading else Type.body,
        ) { controller.stopSession() }
        else -> GiveUpButton { controller.stopSession() }
    }
}

@Composable
private fun GiveUpButton(onConfirm: () -> Unit) {
    var confirming by remember { mutableStateOf(false) }
    if (!confirming) ChunkyButton("Stop focusing", color = Ink.berry, textColor = Ink.onSelected) { confirming = true }
    else Row(horizontalArrangement = Arrangement.spacedBy(Space.lg), verticalAlignment = Alignment.CenterVertically) {
        ChunkyButton("Keep going", color = Ink.leaf, textColor = Ink.onSelected, fontSize = Type.bodyLarge) { confirming = false }
        ChunkyButton("Give up", color = Ink.berry, textColor = Ink.onSelected, fontSize = Type.bodyLarge) { onConfirm() }
    }
}

@Composable
fun RewardCard(controller: AppController, phase: Phase.Rewarded) {
    val state by controller.state.collectAsState()
    SheetCard(null, maxWidth = Sizes.dialogWidth, outerPadding = Space.xxxl) {
        ScrollColumn {
            PipView(state.style, Mood.HAPPY, Modifier.size(160.dp, 150.dp))
            Title("We did it!")
            Text("${formatMinutes(phase.minutes)} of focus", style = label(Type.lead, FontWeight.Medium, Ink.muted))
            Spacer(Modifier.height(Space.xl))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("+", style = label(28.sp, FontWeight.Black, Ink.acorn))
                AcornCount(phase.acorns, big = true)
            }
            Spacer(Modifier.height(Space.xxl))
            ChunkyButton("Yay!", color = Ink.leaf, textColor = Ink.onSelected) { controller.dismissResult() }
        }
    }
}

@Composable
fun InterruptedCard(controller: AppController, phase: Phase.Interrupted) {
    val state by controller.state.collectAsState()
    SheetCard(null, maxWidth = Sizes.dialogWidth, outerPadding = Space.xxxl) {
        ScrollColumn {
            PipView(state.style, Mood.SAD, Modifier.size(160.dp, 150.dp))
            Title("Oh no…")
            Text(
                if (phase.focusedMin > 0) "We focused for ${formatMinutes(phase.focusedMin)}, but the session ended early. No acorns this time — let's try again?"
                else "The session ended before it really started. Let's try again?",
                style = label(Type.lead, FontWeight.Medium, Ink.muted), textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Space.xxl))
            ChunkyButton("Okay", color = Ink.butter) { controller.dismissResult() }
        }
    }
}

@Composable
fun BreakOffer(controller: AppController) {
    val state by controller.state.collectAsState()
    val settings by controller.settings.collectAsState()
    SheetCard(null, maxWidth = Sizes.dialogWidth, outerPadding = Space.xxxl) {
        ScrollColumn {
            PipView(state.style, Mood.BREAK, Modifier.size(140.dp, 130.dp))
            Title("Tea break?", Type.titleLarge)
            Spacer(Modifier.height(Space.xl))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                listOf(5, 10, 15).forEach { m ->
                    ChunkyButton(
                        "$m min",
                        color = if (m == settings.breakMinutes) Ink.leaf else Ink.butter,
                        textColor = if (m == settings.breakMinutes) Ink.onSelected else Ink.outline,
                        fontSize = Type.bodyLarge,
                    ) { controller.startBreak(m) }
                }
            }
            Spacer(Modifier.height(Space.lg))
            Text("No thanks", style = label(Type.lead, color = Ink.muted), modifier = Modifier.clickable { controller.dismissResult() }.padding(Space.md))
        }
    }
}

@Composable
fun BreakScreen(controller: AppController, phase: Phase.OnBreak) {
    val now = rememberNowState(controller)
    val state by controller.state.collectAsState()
    Box(Modifier.fillMaxSize().background(Ink.bgBottom)) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(Space.xxxl), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(Space.xxxl))
            Text("Break time — stretch, sip, breathe.", style = label(Type.button), textAlign = TextAlign.Center)
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                PipView(state.style, Mood.BREAK, Modifier.size(240.dp, 240.dp))
            }
            BreakClock(phase, now)
            Spacer(Modifier.height(Space.xxxl))
            ChunkyButton("End break", color = Ink.butter) { controller.endBreak() }
            Spacer(Modifier.height(Space.xxxl))
        }
    }
}

@Composable
private fun BreakClock(phase: Phase.OnBreak, now: State<Long>) {
    Text(formatClock((phase.endsAt - now.value) / 1000), style = label(Type.clockBreak, FontWeight.Black, Ink.leafDark))
}
