package buddy.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import buddy.app.AppController
import buddy.app.art.drawDecor
import buddy.app.art.drawRoomShell
import buddy.app.art.RoomGeometry
import buddy.core.Catalog

@Composable
fun SlotPicker(
    controller: AppController,
    roomId: String,
    slot: Catalog.Slot,
    onPreview: (Catalog.DecorItem?) -> Unit,
    onClose: () -> Unit,
) {
    val state by controller.state.collectAsState()
    val current = state.equipped[slot.id]?.let(Catalog::decor)
    var theme by remember(slot.id) { mutableStateOf(current?.theme ?: Catalog.Theme.BASIC) }
    var chosen by remember(slot.id) { mutableStateOf(current) }
    LaunchedEffect(chosen) { onPreview(chosen) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Panel(Modifier.widthIn(max = 460.dp).fillMaxWidth().padding(10.dp), padding = 12.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Title(slot.kind.label, 20.sp)
                Box(Modifier.weight(1f))
                AcornCount(state.acorns)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                Tabs(Catalog.Theme.entries.map { it.label }, theme.ordinal, { theme = Catalog.Theme.entries[it] })
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (0 until Catalog.VARIANTS).forEach { v ->
                    val item = Catalog.DecorItem(slot.kind, theme, v)
                    ItemCell(
                        selected = chosen == item,
                        footer = when {
                            state.equipped[slot.id] == item.id -> "placed"
                            state.owns(item.id) -> "owned"
                            else -> null
                        },
                        price = item.price.takeIf { !state.owns(item.id) },
                        modifier = Modifier.weight(1f),
                        onClick = { chosen = item },
                    ) { drawDecor(item, Offset(size.width * 0.1f, size.height * 0.1f), Size(size.width * 0.8f, size.height * 0.8f)) }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ChunkyButton("Close", color = Ink.paperDeep, fontSize = 15.sp, padding = PaddingValues(14.dp, 10.dp)) { onClose() }
                if (current != null) ChunkyButton("Clear", color = Ink.berry, textColor = Color.White, fontSize = 15.sp, padding = PaddingValues(14.dp, 10.dp)) {
                    controller.equip(slot.id, null); onClose()
                }
                val c = chosen
                when {
                    c == null || c == current -> {}
                    state.owns(c.id) -> ChunkyButton("Place", color = Ink.leaf, textColor = Color.White, fontSize = 15.sp, padding = PaddingValues(14.dp, 10.dp)) {
                        controller.equip(slot.id, c.id); onClose()
                    }
                    state.canAfford(c.id) -> ChunkyButton("Buy · ${c.price}", color = Ink.leaf, textColor = Color.White, fontSize = 15.sp, padding = PaddingValues(14.dp, 10.dp)) {
                        if (controller.buy(c.id)) controller.equip(slot.id, c.id); onClose()
                    }
                    else -> ChunkyButton("Need ${c.price - state.acorns} more", enabled = false, fontSize = 14.sp, padding = PaddingValues(14.dp, 10.dp)) {}
                }
            }
        }
    }
}

@Composable
fun ItemCell(
    selected: Boolean,
    footer: String?,
    price: Int?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    art: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier.clip(shape).background(if (selected) Color(0xFFDDEBC8) else Color.White)
            .border(if (selected) 3.dp else 2.dp, Ink.outline, shape).clickable(onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(1f), onDraw = art)
        when {
            price != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                AcornIcon(Modifier.size(14.dp)); Text(" $price", style = label(12.sp))
            }
            footer != null -> Text(footer, style = label(11.sp, FontWeight.Medium, Ink.muted))
            else -> Text(" ", style = label(11.sp))
        }
    }
}

@Composable
fun RoomsSheet(controller: AppController, currentRoom: String, onPick: (String) -> Unit, onClose: () -> Unit) {
    val state by controller.state.collectAsState()
    Scrim(onClose) {
        Panel(Modifier.widthIn(max = 440.dp).padding(16.dp)) {
            Title("Rooms")
            Spacer(Modifier.height(10.dp))
            Catalog.rooms.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                    row.forEach { room ->
                        val unlocked = state.roomUnlocked(room.id)
                        val shape = RoundedCornerShape(14.dp)
                        Column(
                            Modifier.weight(1f).clip(shape).background(if (room.id == currentRoom) Color(0xFFDDEBC8) else Color.White)
                                .border(2.5.dp, Ink.outline, shape).padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Canvas(Modifier.fillMaxWidth().aspectRatio(1.3f)) {
                                drawRoomShell(RoomGeometry(size), room.id)
                                if (!unlocked) drawRect(Color(0x66FBF3E4))
                            }
                            Text(room.name, style = label(14.sp))
                            Spacer(Modifier.height(4.dp))
                            when {
                                unlocked -> ChunkyButton(if (room.id == currentRoom) "Here" else "Go", color = Ink.leaf, textColor = Color.White, fontSize = 14.sp, padding = PaddingValues(16.dp, 6.dp)) { onPick(room.id) }
                                state.canAfford("room:${room.id}") -> ChunkyButton("Unlock · ${room.price}", fontSize = 13.sp, padding = PaddingValues(10.dp, 6.dp)) {
                                    if (controller.buy("room:${room.id}")) onPick(room.id)
                                }
                                else -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                                    AcornIcon(Modifier.size(16.dp)); Text(" ${room.price}", style = label(13.sp, color = Ink.muted))
                                }
                            }
                        }
                    }
                }
            }
            CloseButton(onClose)
        }
    }
}
