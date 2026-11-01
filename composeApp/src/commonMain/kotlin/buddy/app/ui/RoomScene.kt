package buddy.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas as GCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
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
import kotlin.math.min
import kotlin.random.Random

/**
 * Width/height ratio at which the room just fits vertically. Wider than this and the floor/walls
 * would be clipped in short windows, so [RoomScene] narrows the scene instead.
 */
private const val ROOM_FIT_RATIO = 1.15f

/** Where Pip is on the floor (iso coords) and what it is doing. */
class PipActor {
    var i by mutableFloatStateOf(0.6f)
    var j by mutableFloatStateOf(0.6f)
    var mood by mutableStateOf(Mood.IDLE)
    var facing by mutableStateOf(1)
}

/** Walk steps reuse the scene clock's cadence. */

@Composable
fun rememberWanderingPip(enabled: Boolean): PipActor {
    val actor = remember { PipActor() }
    val fps = buddy.app.art.LocalAnimationFps.current
    LaunchedEffect(enabled, fps) {
        if (!enabled) { actor.mood = Mood.IDLE; return@LaunchedEffect }
        while (true) {
            when (Random.nextInt(10)) {
                in 0..4 -> {
                    val ti = Random.nextFloat() * 0.5f + 0.35f
                    val tj = Random.nextFloat() * 0.5f + 0.35f
                    // screen x grows with i and shrinks with j
                    actor.facing = if ((ti - actor.i) - (tj - actor.j) >= 0) 1 else -1
                    val i0 = actor.i
                    val j0 = actor.j
                    val dist = abs(ti - i0) + abs(tj - j0)
                    val steps = ((dist * 5_000).toLong() / (1000L / fps)).coerceAtLeast(4L)
                    actor.mood = Mood.WALK
                    // Step the walk on the same 12 fps cadence as the clock: Animatable would
                    // request a vsync frame every 16 ms and keep the whole window repainting.
                    for (n in 1..steps) {
                        val f = n.toFloat() / steps
                        actor.i = i0 + (ti - i0) * f
                        actor.j = j0 + (tj - j0) * f
                        delay(1000L / fps)
                    }
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

/** A decor slot paired with its floor depth, computed once per scene size. */
/** Room shells are pure vector paths; rasterise once per size instead of every frame. */
// Bounded: a phone only ever needs the current room, but resizes and previews can add entries.
private val shellCache = object : LinkedHashMap<Triple<String, Int, Int>, ImageBitmap>(4, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Triple<String, Int, Int>, ImageBitmap>) = size > 4
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.shellBitmap(g: RoomGeometry, roomId: String): ImageBitmap {
    val w = g.size.width.toInt().coerceAtLeast(1)
    val h = g.size.height.toInt().coerceAtLeast(1)
    return shellCache.getOrPut(Triple(roomId, w, h)) {
        val bmp = ImageBitmap(w, h)
        val canvas = GCanvas(bmp)
        CanvasDrawScope().draw(this, layoutDirection, canvas, androidx.compose.ui.geometry.Size(w.toFloat(), h.toFloat())) {
            drawRoomShell(g, roomId)
        }
        bmp
    }
}

private data class Placed(val slot: Catalog.Slot, val bottom: Float)

/**
 * The isometric room with its decor and Pip. In decorate mode every slot shows a tappable marker.
 * [preview] temporarily shows an item in a slot (decorate picker).
 *
 * The scene is drawn every frame (Pip breathes), so everything static — geometry, slot lists,
 * decor lookup, draw order — is remembered rather than rebuilt in the draw lambda.
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
    night: Boolean = false,
    animate: Boolean = true,
    time: State<Float>? = null,
) {
    val room = Catalog.room(roomId) ?: return
    // Prefer the app's shared clock so sky and room invalidate on the same frames.
    val t = time ?: rememberSeconds(active = animate)
    var sceneSize by remember { mutableStateOf(Size.Zero) }
    val geometry = remember(sceneSize) { RoomGeometry(sceneSize) }

    // Slot placement only depends on the room and the scene size.
    val wallItems = remember(roomId, equipped, preview, geometry) {
        val g = geometry
        room.slots.filter { g.isWall(it, roomId) }.mapNotNull { s ->
            itemFor(s, equipped, preview)?.let { s to it }
        } + room.slots.filter { g.isRug(it, roomId) }.mapNotNull { s ->
            itemFor(s, equipped, preview)?.let { s to it }
        }
    }
    val floorItems = remember(roomId, equipped, preview, geometry) {
        val g = geometry
        room.slots.filter { !g.isWall(it, roomId) && !g.isRug(it, roomId) }
            .map { Placed(it, g.slotRect(it, roomId).bottom) }
            .sortedBy { it.bottom }
    }
    val slotRects = remember(roomId, geometry) { room.slots.associateWith { geometry.slotRect(it, roomId) } }

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        // Keep the phone-proportioned room: narrow the canvas in short/wide windows.
        val fitWidth = minOf(maxWidth, maxHeight * ROOM_FIT_RATIO)
        Canvas(
            Modifier
                .width(fitWidth)
                .fillMaxHeight()
                .onSizeChanged { sceneSize = Size(it.width.toFloat(), it.height.toFloat()) }
                .pointerInput(roomId, decorating, geometry) {
                    detectTapGestures { pos ->
                        if (decorating) {
                            room.slots
                                .minByOrNull { slotRects[it]?.center?.minus(pos)?.getDistance() ?: Float.MAX_VALUE }
                                ?.takeIf { (slotRects[it]?.center?.minus(pos)?.getDistance() ?: Float.MAX_VALUE) < geometry.w * 0.14f }
                                ?.let(onSlotTap)
                        } else {
                            val feet = geometry.floor(actor.i, actor.j)
                            if (abs(pos.x - feet.x) < geometry.w * 0.12f && pos.y in feet.y - geometry.w * 0.28f..feet.y) onPipTap()
                        }
                    }
                },
        ) {
            val g = geometry
            drawImage(shellBitmap(g, roomId), Offset.Zero)
            for ((slot, item) in wallItems) drawSlotItem(g, slot, roomId, item, night = night)

            // Depth-sort the moving Pip against the pre-sorted floor items.
            val pipFeet = g.floor(actor.i, actor.j)
            var drawnPip = false
            for (placed in floorItems) {
                if (showPip && !drawnPip && placed.bottom > pipFeet.y) {
                    drawPip(pipFeet, g.w * 0.3f, style, actor.mood, t.value, actor.facing)
                    drawnPip = true
                }
                val item = itemFor(placed.slot, equipped, preview) ?: continue
                drawSlotItem(g, placed.slot, roomId, item, night = night)
            }
            if (showPip && !drawnPip) drawPip(pipFeet, g.w * 0.3f, style, actor.mood, t.value, actor.facing)

            if (decorating) {
                val pulse = 0.92f + 0.08f * kotlin.math.sin(t.value * 4f)
                for (s in room.slots) {
                    val c = slotRects[s]?.center ?: continue
                    val r = g.w * 0.045f
                    val sel = s.id == selectedSlot
                    val scale = if (sel) 1f else pulse
                    drawCircle(if (sel) Ink.berry else Ink.leaf, r * scale, c)
                    drawCircle(Ink.outline, r * scale, c, style = Stroke(g.w * 0.007f))
                    drawLine(Color.White, Offset(c.x - r * 0.5f, c.y), Offset(c.x + r * 0.5f, c.y), g.w * 0.012f)
                    drawLine(Color.White, Offset(c.x, c.y - r * 0.5f), Offset(c.x, c.y + r * 0.5f), g.w * 0.012f)
                }
            }
        }
    }
}

private fun itemFor(slot: Catalog.Slot, equipped: Map<String, String>, preview: Pair<String, Catalog.DecorItem?>?): Catalog.DecorItem? {
    if (preview != null && preview.first == slot.id) return preview.second
    return equipped[slot.id]?.let(Catalog::decor)
}
