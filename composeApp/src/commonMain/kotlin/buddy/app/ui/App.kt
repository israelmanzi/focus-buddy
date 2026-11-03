package buddy.app.ui

import androidx.compose.foundation.Canvas
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
import kotlinx.coroutines.delay
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import buddy.app.AppController
import buddy.app.Phase
import buddy.app.Strings
import buddy.core.Catalog

enum class Sheet { NONE, FOCUS, SHOP, EDITOR, STATS, SETTINGS, ROOMS }

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
        var line by remember { mutableStateOf(Strings.Home.idleLines.random()) }

        // The sky follows the wall clock and the season; a minute is plenty of resolution.
        var clockNow by remember { mutableStateOf(controller.now()) }
        LaunchedEffect(Unit) {
            while (true) {
                clockNow = controller.now()
                delay(60_000)
            }
        }
        val sky = skyFor(clockNow)

        // Silent update check once per launch; the chip is the prompt.
        var update by remember { mutableStateOf<buddy.app.UpdateInfo?>(null) }
        LaunchedEffect(Unit) {
            update = runCatching { controller.platform.checkForUpdate() }.getOrNull()
        }

        val busy = phase !is Phase.Idle
        val focused = androidx.compose.ui.platform.LocalWindowInfo.current.isWindowFocused
        val hostActive = buddy.app.art.LocalAnimationActive.current
        val sceneActive = hostActive && focused
        val actor = rememberWanderingPip(enabled = !busy && !decorating && sheet == Sheet.NONE && sceneActive)

        // Keyboard: Esc closes whatever is open, Space opens the focus sheet (desktop-friendly).
        val keyboard = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { keyboard.requestFocus() } }

        // Full rate only while Pip is moving; breathing and twinkling read fine at a third of it.
        val moving = actor.mood == buddy.app.art.Mood.WALK || actor.mood == buddy.app.art.Mood.HAPPY ||
            actor.mood == buddy.app.art.Mood.PHONE
        // One frame a second is enough for breathing and twinkling; movement uses the full rate.
        val sceneFps = if (moving) buddy.app.art.LocalAnimationFps.current
        else maxOf(1, buddy.app.art.LocalAnimationFps.current / 6)
        val skyTime = buddy.app.art.rememberSeconds(fps = sceneFps, active = sceneActive)
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
                .background(Brush.verticalGradient(listOf(sky.top, sky.bottom))),
            contentAlignment = Alignment.TopCenter,
        ) {
            Canvas(Modifier.fillMaxSize()) { drawSkyDecor(sky, size, skyTime.value) }
            Column(
                Modifier.fillMaxHeight().widthIn(max = Sizes.appWidth).fillMaxWidth().safeDrawingPadding(),
            ) {
                // top bar
                Row(Modifier.fillMaxWidth().padding(Space.xl), verticalAlignment = Alignment.CenterVertically) {
                    AcornCount(state.acorns)
                    Spacer(Modifier.weight(1f))
                    SyncDot(controller, night = sky.isNight)
                }
                SpeechBubble(
                    when {
                        decorating -> Strings.Home.decorateRoom(Catalog.room(roomId)?.name?.lowercase().orEmpty())
                        else -> line
                    },
                )
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    // The session and break screens are opaque; drawing the room behind them is wasted.
                    if (!busy) RoomScene(
                        roomId, state.equipped, state.style, actor, Modifier.fillMaxSize(),
                        showPip = !decorating, decorating = decorating, selectedSlot = pickingSlot?.id, preview = preview,
                        onSlotTap = { pickingSlot = it },
                        onPipTap = { line = Strings.Home.idleLines.filter { it != line }.random(); actor.mood = buddy.app.art.Mood.HAPPY },
                        night = sky.isNight,
                        animate = sceneActive || busy,
                        time = skyTime,
                    )
                }
                // bottom controls
                if (decorating) {
                    Box(Modifier.fillMaxWidth().padding(Space.xxl), contentAlignment = Alignment.Center) {
                        ChunkyButton(Strings.Home.doneDecorating, color = Ink.leaf, textColor = Ink.onSelected) { decorating = false; pickingSlot = null; preview = null }
                    }
                } else {
                    Row(Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.lg), verticalAlignment = Alignment.Bottom) {
                        Column {
                            IconButton(Glyph.ROOMS, Strings.Home.rooms, Ink.roomsTint, if (sky.isNight) Ink.paper else Ink.outline) { sheet = Sheet.ROOMS }
                            IconButton(Glyph.STATS, Strings.Home.stats, Ink.statsTint, if (sky.isNight) Ink.paper else Ink.outline) { sheet = Sheet.STATS }
                            IconButton(Glyph.SHOP, Strings.Home.shop, Ink.shopTint, if (sky.isNight) Ink.paper else Ink.outline) { sheet = Sheet.SHOP }
                        }
                        Box(Modifier.weight(1f).padding(bottom = Space.xl), contentAlignment = Alignment.Center) {
                            ChunkyButton(
                                Strings.Home.focus, color = Ink.leaf, textColor = Ink.onSelected, fontSize = Type.focus,
                                padding = PaddingValues(horizontal = 40.dp, vertical = Space.xxl),
                            ) { sheet = Sheet.FOCUS }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            IconButton(Glyph.DECORATE, Strings.Home.decorate, Ink.decorateTint, if (sky.isNight) Ink.paper else Ink.outline) { decorating = true }
                            IconButton(Glyph.BUDDY, controller.state.value.style.name, Ink.buddyTint, if (sky.isNight) Ink.paper else Ink.outline) { sheet = Sheet.EDITOR }
                            IconButton(Glyph.SETTINGS, Strings.Home.settings, Ink.settingsTint, if (sky.isNight) Ink.paper else Ink.outline) { sheet = Sheet.SETTINGS }
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
private fun SyncDot(controller: AppController, night: Boolean) {
    val s by controller.sync.collectAsState()
    val (color, text) = when (val v = s) {
        buddy.app.SyncStatus.Off -> return
        buddy.app.SyncStatus.Syncing -> Ink.butter to Strings.Sync.dotSyncing
        is buddy.app.SyncStatus.Ok -> Ink.leaf to Strings.Sync.dotSynced
        is buddy.app.SyncStatus.Failed -> Ink.berry to Strings.Sync.dotOffline
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        Box(Modifier.padding(Space.xxs).clip(Shapes.pill).background(color).padding(5.dp))
        Text(text, style = label(Type.foot, color = Ink.muted))
    }
}


/** Sun, moon and stars; drawn on the window background above the app gradient. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSkyDecor(
    sky: Sky,
    size: androidx.compose.ui.geometry.Size,
    t: Float,
) {
    if (sky.isNight) {
        val stars = listOf(
            0.12f to 0.08f, 0.24f to 0.18f, 0.33f to 0.06f, 0.47f to 0.12f, 0.58f to 0.04f,
            0.69f to 0.16f, 0.78f to 0.07f, 0.88f to 0.14f, 0.18f to 0.26f, 0.52f to 0.24f,
            0.82f to 0.27f, 0.38f to 0.21f, 0.64f to 0.28f, 0.94f to 0.22f,
        )
        stars.forEachIndexed { i, (fx, fy) ->
            val twinkle = 0.35f + 0.55f * (0.5f + 0.5f * kotlin.math.sin(t * 1.3f + i))
            drawCircle(
                Color(0xFFFBF3E4).copy(alpha = twinkle),
                size.minDimension * 0.004f,
                Offset(fx * size.width, fy * size.height),
            )
        }
        val c = Offset(size.width * 0.82f, size.height * 0.10f)
        val r = size.minDimension * 0.04f
        drawCircle(Color(0x22F4EBD0), r * 1.8f, c)
        drawCircle(Color(0xFFF4EBD0), r, c)
        drawCircle(sky.top, r * 0.92f, Offset(c.x + r * 0.55f, c.y - r * 0.25f))
    } else {
        val c = Offset(size.width * 0.82f, size.height * 0.10f)
        val r = size.minDimension * 0.035f
        drawCircle(sky.light.copy(alpha = 0.25f), r * 1.9f, c)
        drawCircle(sky.light, r, c)
    }
}
