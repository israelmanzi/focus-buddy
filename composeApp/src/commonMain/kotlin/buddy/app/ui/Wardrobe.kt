package buddy.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import buddy.app.AppController
import buddy.app.art.Mood
import buddy.app.art.PipView
import buddy.app.art.drawPip
import buddy.core.Catalog
import buddy.core.CompanionStyle

private enum class WardrobeTab(val title: String) { SKIN("Colours"), HAT("Hats"), NECK("Neck"), FACE("Face") }

/** One option in a wardrobe grid: the owned-item id, a style to preview, and its price if not owned. */
private data class Option(val itemId: String?, val name: String, val preview: CompanionStyle, val price: Int?)

@Composable
fun ShopSheet(controller: AppController, onClose: () -> Unit) = Wardrobe(controller, shop = true, onClose)

@Composable
fun EditorSheet(controller: AppController, onClose: () -> Unit) = Wardrobe(controller, shop = false, onClose)

@Composable
private fun Wardrobe(controller: AppController, shop: Boolean, onClose: () -> Unit) {
    val state by controller.state.collectAsState()
    var draft by remember { mutableStateOf(state.style) }
    var tab by remember { mutableStateOf(WardrobeTab.SKIN) }
    var selected by remember { mutableStateOf<Option?>(null) }

    fun options(): List<Option> = when (tab) {
        WardrobeTab.SKIN -> Catalog.skins.map { s ->
            Option("skin:${s.id}", s.name, draft.copy(skin = s.id), s.price.takeIf { !state.owns("skin:${s.id}") })
        }
        else -> {
            val slot = Catalog.OutfitSlot.valueOf(tab.name)
            listOf(Option(null, "None", draft.with(slot, null), null)) + Catalog.outfits.filter { it.slot == slot }.map { o ->
                Option("outfit:${o.id}", o.name, draft.with(slot, o.id), o.price.takeIf { !state.owns("outfit:${o.id}") })
            }
        }
    }

    Scrim(onClose) {
        Panel(Modifier.widthIn(max = 440.dp).padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Title(if (shop) "Shop" else "Edit ${state.style.name}", 24.sp)
                Box(Modifier.weight(1f))
                AcornCount(state.acorns)
            }
            if (!shop) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Name", style = label(14.sp)); Spacer(Modifier.width(8.dp))
                    TextInput(draft.name, { draft = draft.copy(name = it.take(24)) }, "Pip", Modifier.weight(1f))
                }
            }
            PipView(selected?.preview ?: draft, if (shop) Mood.HAPPY else Mood.IDLE, Modifier.size(150.dp, 140.dp))
            Tabs(WardrobeTab.entries.map { it.title }, tab.ordinal, { tab = WardrobeTab.entries[it]; selected = null })
            Spacer(Modifier.height(8.dp))
            Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                options().chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                        row.forEach { o ->
                            val wearing = (if (tab == WardrobeTab.SKIN) "skin:${draft.skin}" else draft.of(tab)?.let { "outfit:$it" }) == o.itemId
                            ItemCell(
                                selected = selected == o || (selected == null && wearing),
                                footer = if (wearing) "wearing" else o.name,
                                price = o.price,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    selected = o
                                    if (o.price == null && !shop) draft = o.preview
                                },
                            ) { drawPip(Offset(size.width / 2, size.height * 0.95f), size.height * 0.85f, o.preview, Mood.IDLE, 0.5f) }
                        }
                        repeat(4 - row.size) { Box(Modifier.weight(1f)) }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val sel = selected
                if (sel?.price != null && sel.itemId != null) {
                    if (state.canAfford(sel.itemId)) ChunkyButton("Buy ${sel.name} · ${sel.price}", color = Ink.leaf, textColor = Color.White, fontSize = 15.sp, padding = PaddingValues(14.dp, 10.dp)) {
                        if (controller.buy(sel.itemId)) {
                            draft = sel.preview
                            controller.setStyle(draft.name, draft.skin, draft.hat, draft.neck, draft.face)
                            selected = null
                        }
                    } else Text("Need ${sel.price - state.acorns} more acorns", style = label(14.sp, FontWeight.Medium, Ink.muted))
                } else if (shop) {
                    Text("Earn acorns by focusing — everything here is free to earn.", style = label(13.sp, FontWeight.Medium, Ink.muted))
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (shop) CloseButton(onClose)
                else {
                    ChunkyButton("Cancel", color = Ink.berry, textColor = Color.White, fontSize = 16.sp) { onClose() }
                    ChunkyButton("Save", color = Ink.leaf, textColor = Color.White, fontSize = 16.sp) {
                        controller.setStyle(draft.name, draft.skin, draft.hat, draft.neck, draft.face); onClose()
                    }
                }
            }
        }
    }
}

private fun CompanionStyle.with(slot: Catalog.OutfitSlot, id: String?) = when (slot) {
    Catalog.OutfitSlot.HAT -> copy(hat = id)
    Catalog.OutfitSlot.NECK -> copy(neck = id)
    Catalog.OutfitSlot.FACE -> copy(face = id)
}

private fun CompanionStyle.of(tab: WardrobeTab): String? = when (tab) {
    WardrobeTab.HAT -> hat
    WardrobeTab.NECK -> neck
    WardrobeTab.FACE -> face
    WardrobeTab.SKIN -> skin
}
