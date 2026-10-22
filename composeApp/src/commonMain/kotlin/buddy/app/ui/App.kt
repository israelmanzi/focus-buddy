package buddy.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import buddy.app.AppController
import buddy.app.Phase
import buddy.core.Catalog

enum class Sheet { NONE, FOCUS, SHOP, EDITOR, STATS, SETTINGS, ROOMS }

private val idleLines = listOf(
    "Ready when you are!", "Let's focus together?", "I found a good book…", "One little session?",
    "Tea, then focus?", "I believe in us!",
)

@Composable
fun App(controller: AppController, initialSheet: Sheet = Sheet.NONE, initialDecorating: Boolean = false) {
    BuddyTheme {
        val state by controller.state.collectAsState()
        val phase by controller.phase.collectAsState()
        var sheet by remember { mutableStateOf(initialSheet) }
        var roomId by rememberSaveable { mutableStateOf("living") }
        var decorating by remember { mutableStateOf(initialDecorating) }
        var pickingSlot by remember { mutableStateOf<Catalog.Slot?>(null) }
        var preview by remember { mutableStateOf<Pair<String, Catalog.DecorItem?>?>(null) }
        var line by remember { mutableStateOf(idleLines.random()) }

        val busy = phase !is Phase.Idle
        val actor = rememberWanderingPip(enabled = !busy && !decorating && sheet == Sheet.NONE)

        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFBFE3E0), Color(0xFFE9F4EC))))) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                // top bar
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    AcornCount(state.acorns)
                    Box(Modifier.weight(1f))
                    SyncDot(controller)
                }
                SpeechBubble(
                    when {
                        decorating -> "Tap a + to decorate ${Catalog.room(roomId)?.name?.lowercase()}"
                        else -> line
                    },
                )
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    RoomScene(
                        roomId, state.equipped, state.style, actor, Modifier.fillMaxSize(),
                        showPip = !decorating, decorating = decorating, selectedSlot = pickingSlot?.id, preview = preview,
                        onSlotTap = { pickingSlot = it },
                        onPipTap = { line = idleLines.filter { it != line }.random(); actor.mood = buddy.app.art.Mood.HAPPY },
                    )
                }
                // bottom controls
                if (decorating) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        ChunkyButton("Done decorating", color = Ink.leaf, textColor = Color.White) { decorating = false; pickingSlot = null; preview = null }
                    }
                } else {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.Bottom) {
                        Column {
                            IconButton(Glyph.ROOMS, "Rooms", Color(0xFFDDEFEA)) { sheet = Sheet.ROOMS }
                            IconButton(Glyph.STATS, "Stats", Color(0xFFFCEBD0)) { sheet = Sheet.STATS }
                            IconButton(Glyph.SHOP, "Shop", Color(0xFFFADCD3)) { sheet = Sheet.SHOP }
                        }
                        Box(Modifier.weight(1f).padding(bottom = 12.dp), contentAlignment = Alignment.Center) {
                            ChunkyButton(
                                "Focus", color = Ink.leaf, textColor = Color.White, fontSize = 30.sp,
                                padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 40.dp, vertical = 16.dp),
                            ) { sheet = Sheet.FOCUS }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            IconButton(Glyph.DECORATE, "Decorate", Color(0xFFDCE6F5)) { decorating = true }
                            IconButton(Glyph.BUDDY, controller.state.value.style.name, Color(0xFFF6E2C4)) { sheet = Sheet.EDITOR }
                            IconButton(Glyph.SETTINGS, "Settings", Color(0xFFE8E2DA)) { sheet = Sheet.SETTINGS }
                        }
                    }
                }
            }

            // overlays
            pickingSlot?.let { slot ->
                SlotPicker(controller, roomId, slot, onPreview = { preview = slot.id to it }) { pickingSlot = null; preview = null }
            }
            when (sheet) {
                Sheet.FOCUS -> FocusSetup(controller) { sheet = Sheet.NONE }
                Sheet.SHOP -> ShopSheet(controller) { sheet = Sheet.NONE }
                Sheet.EDITOR -> EditorSheet(controller) { sheet = Sheet.NONE }
                Sheet.STATS -> StatsSheet(controller) { sheet = Sheet.NONE }
                Sheet.SETTINGS -> SettingsSheet(controller) { sheet = Sheet.NONE }
                Sheet.ROOMS -> RoomsSheet(controller, roomId, onPick = { roomId = it; sheet = Sheet.NONE }) { sheet = Sheet.NONE }
                Sheet.NONE -> {}
            }
            when (val p = phase) {
                is Phase.Running -> RunningScreen(controller, p)
                is Phase.Rewarded -> RewardCard(controller, p)
                is Phase.Interrupted -> InterruptedCard(controller, p)
                Phase.BreakOffer -> BreakOffer(controller)
                is Phase.OnBreak -> BreakScreen(controller, p)
                Phase.Idle -> {}
            }
        }
    }
}

@Composable
fun SpeechBubble(text: String) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 40.dp), contentAlignment = Alignment.Center) {
        val shape = RoundedCornerShape(22.dp)
        Text(
            text, textAlign = TextAlign.Center, style = label(16.sp),
            modifier = Modifier.widthIn(max = 360.dp).clip(shape).background(Color.White.copy(alpha = 0.85f))
                .border(2.dp, Ink.outline.copy(alpha = 0.25f), shape).padding(horizontal = 18.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun SyncDot(controller: AppController) {
    val s by controller.sync.collectAsState()
    val (color, text) = when (val v = s) {
        buddy.app.SyncStatus.Off -> return
        buddy.app.SyncStatus.Syncing -> Ink.butter to "syncing"
        is buddy.app.SyncStatus.Ok -> Ink.leaf to "synced"
        is buddy.app.SyncStatus.Failed -> Ink.berry to "offline"
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.padding(2.dp).clip(RoundedCornerShape(50)).background(color).padding(5.dp))
        Text(text, style = label(12.sp, color = Ink.muted))
    }
}
