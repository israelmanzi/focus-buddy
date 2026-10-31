package buddy.app.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
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
        Panel(
            Modifier.widthIn(max = Sizes.pickerWidth).fillMaxWidth().padding(Space.lg),
            padding = Space.xl,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Title(slot.kind.label, Type.heading)
                Spacer(Modifier.weight(1f))
                AcornCount(state.acorns)
            }
            Spacer(Modifier.height(Space.md))
            Row {
                Tabs(Catalog.Theme.entries.map { it.label }, theme.ordinal, { theme = Catalog.Theme.entries[it] })
            }
            Spacer(Modifier.height(Space.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
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
            Spacer(Modifier.height(Space.lg))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.md), verticalAlignment = Alignment.CenterVertically) {
                ChunkyButton("Close", color = Ink.paperDeep, fontSize = Type.lead, padding = PaddingValues(14.dp, Space.lg)) { onClose() }
                if (current != null) ChunkyButton("Clear", color = Ink.berry, textColor = Ink.onSelected, fontSize = Type.lead, padding = PaddingValues(14.dp, Space.lg)) {
                    controller.equip(slot.id, null); onClose()
                }
                val c = chosen
                when {
                    c == null || c == current -> {}
                    state.owns(c.id) -> ChunkyButton("Place", color = Ink.leaf, textColor = Ink.onSelected, fontSize = Type.lead, padding = PaddingValues(14.dp, Space.lg)) {
                        controller.equip(slot.id, c.id); onClose()
                    }
                    state.canAfford(c.id) -> ChunkyButton("Buy · ${c.price}", color = Ink.leaf, textColor = Ink.onSelected, fontSize = Type.lead, padding = PaddingValues(14.dp, Space.lg)) {
                        if (controller.buy(c.id)) controller.equip(slot.id, c.id); onClose()
                    }
                    else -> ChunkyButton("Need ${c.price - state.acorns} more", enabled = false, fontSize = Type.body, padding = PaddingValues(14.dp, Space.lg)) {}
                }
            }
        }
    }
}

@Composable
fun RoomsSheet(controller: AppController, currentRoom: String, onPick: (String) -> Unit, onClose: () -> Unit) {
    val state by controller.state.collectAsState()
    SheetCard(onClose, outerPadding = Space.xxl) {
        Title("Rooms")
        Spacer(Modifier.height(Space.lg))
        ScrollColumn(Modifier.weight(1f, fill = false)) {
            Catalog.rooms.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Space.lg), modifier = Modifier.padding(bottom = Space.lg)) {
                    row.forEach { room ->
                        val unlocked = state.roomUnlocked(room.id)
                        Column(
                            Modifier.weight(1f)
                                .sticker(Shapes.card, if (room.id == currentRoom) Ink.selected else Ink.card, Strokes.bold)
                                .padding(Space.md),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Canvas(Modifier.fillMaxWidth().aspectRatio(1.3f)) {
                                drawRoomShell(RoomGeometry(size), room.id)
                                if (!unlocked) drawRect(Ink.paper.copy(alpha = 0.4f))
                            }
                            Text(room.name, style = label(Type.body))
                            Spacer(Modifier.height(Space.xs))
                            when {
                                unlocked -> ChunkyButton(
                                    if (room.id == currentRoom) "Here" else "Go",
                                    color = Ink.leaf, textColor = Ink.onSelected,
                                    fontSize = Type.body, padding = PaddingValues(Space.xxl, Space.sm),
                                ) { onPick(room.id) }
                                state.canAfford("room:${room.id}") -> ChunkyButton(
                                    "Unlock · ${room.price}",
                                    fontSize = Type.small, padding = PaddingValues(Space.lg, Space.sm),
                                ) { if (controller.buy("room:${room.id}")) onPick(room.id) }
                                else -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = Space.md)) {
                                    AcornPrice(room.price, iconSize = 16.dp, fontSize = Type.small, color = Ink.muted)
                                }
                            }
                        }
                    }
                }
            }
        }
        CloseButton(onClose)
    }
}
