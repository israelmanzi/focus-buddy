package buddy.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
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

        // Keyboard: Esc closes whatever is open, Space opens the focus sheet (desktop-friendly).
        val keyboard = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { keyboard.requestFocus() } }

        Box(
            Modifier.fillMaxSize()
                .focusRequester(keyboard)
                .focusable()
                .onPreviewKeyEvent { ev ->
                    if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when {
                        ev.key == Key.Escape -> {
                            when {
                                pickingSlot != null -> pickingSlot = null
                                sheet != Sheet.NONE -> sheet = Sheet.NONE
                                decorating -> decorating = false
                                else -> return@onPreviewKeyEvent false
                            }
                            true
                        }
                        ev.key == Key.Spacebar && !busy && sheet == Sheet.NONE -> {
                            sheet = Sheet.FOCUS
                            true
                        }
                        else -> false
                    }
                }
                .background(Brush.verticalGradient(listOf(Ink.bgTop, Ink.bgBottom))),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier.fillMaxHeight().widthIn(max = Sizes.appWidth).fillMaxWidth().safeDrawingPadding(),
            ) {
                // top bar
                Row(Modifier.fillMaxWidth().padding(Space.xl), verticalAlignment = Alignment.CenterVertically) {
                    AcornCount(state.acorns)
                    Spacer(Modifier.weight(1f))
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
                    Box(Modifier.fillMaxWidth().padding(Space.xxl), contentAlignment = Alignment.Center) {
                        ChunkyButton("Done decorating", color = Ink.leaf, textColor = Ink.onSelected) { decorating = false; pickingSlot = null; preview = null }
                    }
                } else {
                    Row(Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.lg), verticalAlignment = Alignment.Bottom) {
                        Column {
                            IconButton(Glyph.ROOMS, "Rooms", Ink.roomsTint) { sheet = Sheet.ROOMS }
                            IconButton(Glyph.STATS, "Stats", Ink.statsTint) { sheet = Sheet.STATS }
                            IconButton(Glyph.SHOP, "Shop", Ink.shopTint) { sheet = Sheet.SHOP }
                        }
                        Box(Modifier.weight(1f).padding(bottom = Space.xl), contentAlignment = Alignment.Center) {
                            ChunkyButton(
                                "Focus", color = Ink.leaf, textColor = Ink.onSelected, fontSize = Type.focus,
                                padding = PaddingValues(horizontal = 40.dp, vertical = Space.xxl),
                            ) { sheet = Sheet.FOCUS }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            IconButton(Glyph.DECORATE, "Decorate", Ink.decorateTint) { decorating = true }
                            IconButton(Glyph.BUDDY, controller.state.value.style.name, Ink.buddyTint) { sheet = Sheet.EDITOR }
                            IconButton(Glyph.SETTINGS, "Settings", Ink.settingsTint) { sheet = Sheet.SETTINGS }
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
private fun SyncDot(controller: AppController) {
    val s by controller.sync.collectAsState()
    val (color, text) = when (val v = s) {
        buddy.app.SyncStatus.Off -> return
        buddy.app.SyncStatus.Syncing -> Ink.butter to "syncing"
        is buddy.app.SyncStatus.Ok -> Ink.leaf to "synced"
        is buddy.app.SyncStatus.Failed -> Ink.berry to "offline"
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        Box(Modifier.padding(Space.xxs).clip(Shapes.pill).background(color).padding(5.dp))
        Text(text, style = label(Type.foot, color = Ink.muted))
    }
}
