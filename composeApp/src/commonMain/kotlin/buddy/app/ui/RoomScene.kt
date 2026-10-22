package buddy.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import buddy.app.art.Mood
import buddy.app.art.RoomGeometry
import buddy.app.art.drawPip
import buddy.app.art.drawRoomShell
import buddy.app.art.drawSlotItem
import buddy.app.art.rememberSeconds
import buddy.core.Catalog
import buddy.core.CompanionStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.random.Random

/** Where Pip is on the floor (iso coords) and what it is doing. */
class PipActor {
    val i = Animatable(0.6f)
    val j = Animatable(0.6f)
    var mood by mutableStateOf(Mood.IDLE)
    var facing by mutableStateOf(1)
}

@Composable
fun rememberWanderingPip(enabled: Boolean): PipActor {
    val actor = remember { PipActor() }
    LaunchedEffect(enabled) {
        if (!enabled) { actor.mood = Mood.IDLE; return@LaunchedEffect }
        while (true) {
            when (Random.nextInt(10)) {
                in 0..4 -> {
                    val ti = Random.nextFloat() * 0.5f + 0.35f
                    val tj = Random.nextFloat() * 0.5f + 0.35f
                    // screen x grows with i and shrinks with j
                    actor.facing = if ((ti - actor.i.value) - (tj - actor.j.value) >= 0) 1 else -1
                    val dist = abs(ti - actor.i.value) + abs(tj - actor.j.value)
                    val ms = (dist * 5000).toInt().coerceAtLeast(400)
                    actor.mood = Mood.WALK
                    launch { actor.i.animateTo(ti, tween(ms, easing = LinearEasing)) }
                    actor.j.animateTo(tj, tween(ms, easing = LinearEasing))
                    actor.mood = Mood.IDLE
                    delay(1500L + Random.nextLong(2500))
                }
                in 5..6 -> { actor.mood = Mood.PHONE; delay(4000L + Random.nextLong(3000)) }
                7 -> { actor.mood = Mood.SLEEP; delay(6000L) }
                else -> { actor.mood = Mood.IDLE; delay(2500L) }
            }
        }
    }
    return actor
}

/**
 * The isometric room with its decor and Pip. In decorate mode every slot shows a tappable marker.
 * [preview] temporarily shows an item in a slot (decorate picker).
 */
@Composable
fun RoomScene(
    roomId: String,
    equipped: Map<String, String>,
    style: CompanionStyle,
    actor: PipActor,
    modifier: Modifier = Modifier,
    showPip: Boolean = true,
    decorating: Boolean = false,
    selectedSlot: String? = null,
    preview: Pair<String, Catalog.DecorItem?>? = null,
    onSlotTap: (Catalog.Slot) -> Unit = {},
    onPipTap: () -> Unit = {},
) {
    val room = Catalog.room(roomId) ?: return
    val t = rememberSeconds()
    var geometry by remember { mutableStateOf<RoomGeometry?>(null) }
    Canvas(
        modifier.pointerInput(roomId, decorating) {
            detectTapGestures { pos ->
                val g = geometry ?: return@detectTapGestures
                if (decorating) {
                    room.slots.minByOrNull { (g.slotRect(it, roomId).center - pos).getDistance() }
                        ?.takeIf { (g.slotRect(it, roomId).center - pos).getDistance() < g.w * 0.14f }
                        ?.let(onSlotTap)
                } else {
                    val feet = g.floor(actor.i.value, actor.j.value)
                    if (abs(pos.x - feet.x) < g.w * 0.12f && pos.y in feet.y - g.w * 0.28f..feet.y) onPipTap()
                }
            }
        },
    ) {
        val g = RoomGeometry(size).also { if (geometry?.size != size) geometry = it }
        drawRoomShell(g, roomId)

        fun itemFor(slot: Catalog.Slot): Catalog.DecorItem? {
            if (preview != null && preview.first == slot.id) return preview.second
            return equipped[slot.id]?.let(Catalog::decor)
        }

        val wall = room.slots.filter { g.isWall(it, roomId) }
        val rugs = room.slots.filter { g.isRug(it, roomId) }
        val standing = room.slots.filter { !g.isWall(it, roomId) && !g.isRug(it, roomId) }

        for (s in wall + rugs) itemFor(s)?.let { drawSlotItem(g, s, roomId, it) }

        // depth-sort floor items and Pip by their base y
        val pipFeet = g.floor(actor.i.value, actor.j.value)
        val drawables = standing.map { s -> g.slotRect(s, roomId).bottom to { itemFor(s)?.let { drawSlotItem(g, s, roomId, it) }; Unit } } +
            (if (showPip) listOf(pipFeet.y to { drawPip(pipFeet, g.w * 0.3f, style, actor.mood, t.value, actor.facing) }) else emptyList())
        drawables.sortedBy { it.first }.forEach { it.second() }

        if (decorating) {
            for (s in room.slots) {
                val c = g.slotRect(s, roomId).center
                val r = g.w * 0.045f
                val sel = s.id == selectedSlot
                val pulse = if (sel) 1f else 0.92f + 0.08f * kotlin.math.sin(t.value * 4f)
                drawCircle(if (sel) Ink.berry else Ink.leaf, r * pulse, c)
                drawCircle(Ink.outline, r * pulse, c, style = Stroke(g.w * 0.007f))
                drawLine(Color.White, Offset(c.x - r * 0.5f, c.y), Offset(c.x + r * 0.5f, c.y), g.w * 0.012f)
                drawLine(Color.White, Offset(c.x, c.y - r * 0.5f), Offset(c.x, c.y + r * 0.5f), g.w * 0.012f)
            }
        }
    }
}
