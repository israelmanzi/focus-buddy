package buddy.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import buddy.app.AppController
import buddy.app.Phase
import buddy.app.art.Mood
import buddy.app.art.PipView
import buddy.core.Rules
import kotlinx.coroutines.delay

fun formatClock(totalSec: Long): String {
    val s = totalSec.coerceAtLeast(0)
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${sec.toString().padStart(2, '0')}"
    else "${m.toString().padStart(2, '0')}:${sec.toString().padStart(2, '0')}"
}

fun formatMinutes(min: Int) = if (min >= 60) "${min / 60}h ${(min % 60).toString().padStart(2, '0')}m" else "${min}m"

@Composable
fun rememberNow(controller: AppController, periodMs: Long = 250): Long {
    var now by remember { mutableLongStateOf(controller.now()) }
    LaunchedEffect(Unit) { while (true) { now = controller.now(); delay(periodMs) } }
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

    Scrim(onClose) {
        Panel(Modifier.widthIn(max = 420.dp).padding(16.dp)) {
            PipView(state.style, Mood.READING, Modifier.size(120.dp, 110.dp))
            // clock face
            Panel(color = Color(0xFF8FC1B5), padding = 14.dp) {
                Text(
                    if (countUp) "00:00" else formatClock(minutes.toLong() * 60).let { if (minutes >= 60) it.dropLast(3) else it },
                    style = label(54.sp, FontWeight.Black, Color.White),
                )
                Text(if (countUp) "stopwatch" else if (minutes >= 60) "hours : minutes" else "minutes", style = label(13.sp, color = Color.White))
                if (!countUp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RoundStep("−") { minutes = (minutes - Rules.STEP_MINUTES).coerceAtLeast(Rules.MIN_MINUTES.toFloat()) }
                        Slider(
                            value = minutes, onValueChange = { minutes = (it / Rules.STEP_MINUTES).toInt() * Rules.STEP_MINUTES.toFloat() },
                            valueRange = Rules.MIN_MINUTES.toFloat()..Rules.MAX_MINUTES.toFloat(),
                            modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                            colors = SliderDefaults.colors(thumbColor = Ink.butter, activeTrackColor = Ink.outline, inactiveTrackColor = Color.White.copy(alpha = 0.5f)),
                        )
                        RoundStep("+") { minutes = (minutes + Rules.STEP_MINUTES).coerceAtMost(Rules.MAX_MINUTES.toFloat()) }
                    }
                }
                Tabs(listOf("Countdown", "Stopwatch"), if (countUp) 1 else 0, { countUp = it == 1 }, Modifier.padding(top = 8.dp))
            }
            Spacer(Modifier.height(12.dp))
            // tags
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Tag", style = label(14.sp))
                state.tags.values.filter { !it.archived }.forEach { t ->
                    Chip(t.name, Color(t.color), selected = t.id == tag) { tag = t.id }
                }
                if (newTag == null) Chip("+ new", Ink.paperDeep, false) { newTag = "" }
            }
            newTag?.let { nt ->
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextInput(nt, { newTag = it.take(24) }, "Tag name", Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    ChunkyButton("Add", fontSize = 14.sp, padding = androidx.compose.foundation.layout.PaddingValues(12.dp, 8.dp), enabled = nt.isNotBlank()) {
                        val color = tagColors[state.tags.size % tagColors.size]
                        controller.upsertTag(null, nt.trim(), color)
                        newTag = null
                        controller.state.value.tags.values.lastOrNull { it.name == nt.trim() }?.let { tag = it.id }
                    }
                }
            }
            if (p.supportsDeepFocus) {
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Deep Focus", style = label(15.sp))
                        Text(
                            when {
                                !deep -> "Leaving the app ends the session after ${settings.graceSec}s"
                                !p.deepFocusReady() -> "Needs permissions — tap Set up"
                                settings.packages.isEmpty() -> "No apps chosen yet (Settings)"
                                settings.allowMode -> "Only ${settings.packages.size} allowed apps"
                                else -> "Blocks ${settings.packages.size} apps"
                            },
                            style = label(12.sp, FontWeight.Normal, Ink.muted),
                        )
                    }
                    if (deep && !p.deepFocusReady()) {
                        ChunkyButton("Set up", fontSize = 13.sp, padding = androidx.compose.foundation.layout.PaddingValues(10.dp, 6.dp)) { p.requestDeepFocusPermissions() }
                        Spacer(Modifier.width(8.dp))
                    }
                    BuddySwitch(deep) { deep = it }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                ChunkyButton("Back", color = Ink.berry, textColor = Color.White, fontSize = 16.sp) { onClose() }
                ChunkyButton("Start", color = Ink.leaf, textColor = Color.White, fontSize = 24.sp) {
                    controller.startSession(minutes.toInt(), countUp, tag, deep && p.deepFocusReady())
                    onClose()
                }
            }
        }
    }
}

@Composable
private fun RoundStep(text: String, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).clip(RoundedCornerShape(50)).background(Ink.butter).border(2.5.dp, Ink.outline, RoundedCornerShape(50)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = label(22.sp, FontWeight.Black)) }
}

@Composable
fun Chip(text: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Text(
        text, style = label(13.sp, color = if (selected) Color.White else Ink.outline),
        modifier = Modifier.clip(shape).background(if (selected) color else color.copy(alpha = 0.25f))
            .border(if (selected) 2.5.dp else 1.5.dp, Ink.outline, shape).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
fun BuddySwitch(checked: Boolean, onChange: (Boolean) -> Unit) = Switch(
    checked, onChange,
    colors = SwitchDefaults.colors(checkedTrackColor = Ink.leaf, checkedThumbColor = Color.White, uncheckedTrackColor = Ink.paperDeep, uncheckedThumbColor = Ink.muted, uncheckedBorderColor = Ink.outline),
)

@Composable
fun TextInput(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier, secret: Boolean = false) {
    val shape = RoundedCornerShape(10.dp)
    Box(modifier.clip(shape).background(Color.White).border(2.dp, Ink.outline, shape).padding(horizontal = 10.dp, vertical = 8.dp)) {
        if (value.isEmpty()) Text(hint, style = label(14.sp, FontWeight.Normal, Ink.muted))
        BasicTextField(
            value, onChange, singleLine = true, textStyle = label(14.sp, FontWeight.Medium),
            visualTransformation = if (secret) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun RunningScreen(controller: AppController, phase: Phase.Running) {
    val rs = phase.session
    val now = rememberNow(controller)
    val state by controller.state.collectAsState()
    val elapsed = (now - rs.startedAt) / 1000
    val shown = if (rs.countUp) elapsed else rs.plannedMin * 60L - elapsed
    val freeLeft = controller.freeCancelLeft(rs)
    val tagInfo = state.tags[rs.tag]

    Box(Modifier.fillMaxSize().background(Color(0xFF302A26)).clickable(enabled = false) {}) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(24.dp))
            Text(
                if (rs.awaySince != null) "Come back… I'm waiting!" else listOf("Shh… reading time.", "We've got this.", "Page by page…")[((elapsed / 20) % 3).toInt()],
                style = label(18.sp, color = Color(0xFFFBF3E4)), textAlign = TextAlign.Center,
            )
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                PipView(state.style, Mood.READING, Modifier.size(260.dp, 260.dp))
            }
            tagInfo?.let { Chip(it.name, Color(it.color), true) {} }
            Spacer(Modifier.height(10.dp))
            Text(formatClock(shown), style = label(64.sp, FontWeight.Black, Color.White))
            if (rs.deepFocus) Text("Deep Focus on", style = label(13.sp, color = Ink.butter))
            Spacer(Modifier.height(24.dp))
            when {
                freeLeft > 0 -> ChunkyButton("Cancel ($freeLeft)", color = Ink.paperDeep) { controller.stopSession() }
                rs.countUp -> ChunkyButton(if (elapsed >= Rules.MIN_MINUTES * 60) "Done!" else "Stop (under ${Rules.MIN_MINUTES} min earns nothing)", color = Ink.leaf, textColor = Color.White, fontSize = if (elapsed >= Rules.MIN_MINUTES * 60) 20.sp else 14.sp) { controller.stopSession() }
                else -> GiveUpButton { controller.stopSession() }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun GiveUpButton(onConfirm: () -> Unit) {
    var confirming by remember { mutableStateOf(false) }
    if (!confirming) ChunkyButton("Stop focusing", color = Ink.berry, textColor = Color.White) { confirming = true }
    else Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        ChunkyButton("Keep going", color = Ink.leaf, textColor = Color.White, fontSize = 16.sp) { confirming = false }
        ChunkyButton("Give up", color = Ink.berry, textColor = Color.White, fontSize = 16.sp) { onConfirm() }
    }
}

@Composable
fun RewardCard(controller: AppController, phase: Phase.Rewarded) {
    val state by controller.state.collectAsState()
    Scrim(null) {
        Panel(Modifier.widthIn(max = 360.dp).padding(24.dp)) {
            PipView(state.style, Mood.HAPPY, Modifier.size(160.dp, 150.dp))
            Title("We did it!")
            Text("${formatMinutes(phase.minutes)} of focus", style = label(15.sp, FontWeight.Medium, Ink.muted))
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("+", style = label(28.sp, FontWeight.Black, Ink.acorn))
                AcornCount(phase.acorns, big = true)
            }
            Spacer(Modifier.height(16.dp))
            ChunkyButton("Yay!", color = Ink.leaf, textColor = Color.White) { controller.dismissResult() }
        }
    }
}

@Composable
fun InterruptedCard(controller: AppController, phase: Phase.Interrupted) {
    val state by controller.state.collectAsState()
    Scrim(null) {
        Panel(Modifier.widthIn(max = 360.dp).padding(24.dp)) {
            PipView(state.style, Mood.SAD, Modifier.size(160.dp, 150.dp))
            Title("Oh no…")
            Text(
                if (phase.focusedMin > 0) "We focused for ${formatMinutes(phase.focusedMin)}, but the session ended early. No acorns this time — let's try again?"
                else "The session ended before it really started. Let's try again?",
                style = label(15.sp, FontWeight.Medium, Ink.muted), textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            ChunkyButton("Okay", color = Ink.butter) { controller.dismissResult() }
        }
    }
}

@Composable
fun BreakOffer(controller: AppController) {
    val state by controller.state.collectAsState()
    val settings by controller.settings.collectAsState()
    Scrim(null) {
        Panel(Modifier.widthIn(max = 360.dp).padding(24.dp)) {
            PipView(state.style, Mood.BREAK, Modifier.size(140.dp, 130.dp))
            Title("Tea break?", 24.sp)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 10, 15).forEach { m ->
                    ChunkyButton("$m min", color = if (m == settings.breakMinutes) Ink.leaf else Ink.butter, textColor = if (m == settings.breakMinutes) Color.White else Ink.outline, fontSize = 16.sp) { controller.startBreak(m) }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("No thanks", style = label(15.sp, color = Ink.muted), modifier = Modifier.clickable { controller.dismissResult() }.padding(8.dp))
        }
    }
}

@Composable
fun BreakScreen(controller: AppController, phase: Phase.OnBreak) {
    val now = rememberNow(controller)
    val state by controller.state.collectAsState()
    Box(Modifier.fillMaxSize().background(Color(0xFFE9F4EC))) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(24.dp))
            Text("Break time — stretch, sip, breathe.", style = label(18.sp), textAlign = TextAlign.Center)
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                PipView(state.style, Mood.BREAK, Modifier.size(240.dp, 240.dp))
            }
            Text(formatClock((phase.endsAt - now) / 1000), style = label(56.sp, FontWeight.Black, Ink.leafDark))
            Spacer(Modifier.height(24.dp))
            ChunkyButton("End break", color = Ink.butter) { controller.endBreak() }
            Spacer(Modifier.height(24.dp))
        }
    }
}
