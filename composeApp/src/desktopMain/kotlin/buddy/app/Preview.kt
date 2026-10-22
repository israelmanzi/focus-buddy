package buddy.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.use
import buddy.app.art.Mood
import buddy.app.art.PipView
import buddy.core.CompanionStyle
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

/** Renders [content] to a PNG; used for visual checks during development (`BUDDY_PREVIEW=dir`). */
fun renderPng(file: File, widthDp: Int, heightDp: Int, nanos: Long = 1_000_000_000L, content: @Composable () -> Unit) {
    ImageComposeScene(widthDp * 2, heightDp * 2, Density(2f), content = content).use { scene ->
        scene.render(0)
        val img = scene.render(nanos)
        file.parentFile.mkdirs()
        file.writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
    }
}

fun renderPreviews(dir: File) {
    renderPng(File(dir, "pip-moods.png"), 8 * 110, 2 * 140) {
        Column(Modifier.background(Color(0xFFFBF3E4))) {
            val styles = listOf(
                CompanionStyle(),
                CompanionStyle(skin = "mint", hat = "hat.beanie", neck = "neck.scarf", face = "face.round"),
            )
            styles.forEach { st ->
                Row {
                    Mood.entries.forEach { m ->
                        Column {
                            PipView(st, m, Modifier.size(110.dp, 120.dp))
                            Text(m.name, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
    renderPng(File(dir, "pip-outfits.png"), 7 * 110, 2 * 130) {
        Column(Modifier.background(Color(0xFFFBF3E4))) {
            val skins = listOf("classic", "peach", "lavender", "sky", "cocoa", "midnight", "honey", "ghost")
            val hats = listOf("hat.beret", "hat.flower", "hat.cap", "hat.wizard", "hat.crown", "hat.mushroom", null)
            val necks = listOf("neck.bow", "neck.bandana", "neck.bell", null, "neck.scarf", null, "neck.bow")
            val faces = listOf(null, "face.shades", null, "face.blush", null, "face.round", null)
            for (row in 0..1) Row {
                for (i in 0 until 7) {
                    Box(Modifier.size(110.dp, 130.dp)) {
                        PipView(CompanionStyle(skin = skins[(i + row * 4) % skins.size], hat = hats[i], neck = necks[i], face = faces[i]), if (row == 0) Mood.IDLE else Mood.HAPPY, Modifier.size(110.dp, 130.dp))
                    }
                }
            }
        }
    }
}

/** A throwaway profile with three weeks of history, for screen previews. */
fun previewController(dir: File): AppController {
    dir.deleteRecursively()
    val platform = object : Platform {
        override val name = "preview"
        override val dataDir = dir
        override val leavingAppInterrupts = true
        override val supportsDeepFocus = true
    }
    val day = 86_400_000L
    val now = System.currentTimeMillis()
    val events = buildList {
        var ts = now - 21 * day
        var n = 0
        while (ts < now - day / 2) {
            val min = listOf(25, 50, 15, 90, 30)[n % 5]
            add(buddy.core.Event("seed$n", "seed", ts + min * 60_000L, buddy.core.Payload.Session(ts, ts + min * 60_000L, min, min * 60L, if (n % 3 == 0) "study" else "focus", completed = n % 7 != 3, deepFocus = n % 4 == 0)))
            ts += day * (if (n % 5 == 2) 2 else 1) / 2
            n++
        }
        add(buddy.core.Event("t1", "seed", now - 22 * day, buddy.core.Payload.Tag("study", "Study", 0xFF6C9BD2)))
        val buys = listOf("decor:lamp.cozy.1", "decor:wall_art.garden.2", "decor:clock.retro.0", "decor:shelf.basic.1", "skin:peach", "outfit:hat.beret", "outfit:neck.scarf", "room:study")
        buys.forEachIndexed { i, b -> add(buddy.core.Event("b$i", "seed", now - day / 3 + i, buddy.core.Payload.Buy(b))) }
        add(buddy.core.Event("e1", "seed", now - day / 4, buddy.core.Payload.Equip("living.lamp", "decor:lamp.cozy.1")))
        add(buddy.core.Event("e2", "seed", now - day / 4 + 1, buddy.core.Payload.Equip("living.wall_art", "decor:wall_art.garden.2")))
        add(buddy.core.Event("e3", "seed", now - day / 4 + 2, buddy.core.Payload.Equip("living.clock", "decor:clock.retro.0")))
        add(buddy.core.Event("e4", "seed", now - day / 4 + 3, buddy.core.Payload.Equip("living.shelf", "decor:shelf.basic.1")))
        add(buddy.core.Event("s1", "seed", now - day / 4 + 4, buddy.core.Payload.Style("Mochi", "peach", hat = "hat.beret", neck = "neck.scarf")))
    }
    val c = AppController(platform, kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined))
    c.importBackup(buddy.core.BuddyJson.encodeToString(kotlinx.serialization.builtins.ListSerializer(buddy.core.Event.serializer()), events))
    return c
}

fun renderScreens(dir: File, scratch: File) {
    val shots = listOf<Pair<String, (AppController) -> Unit>>(
        "home" to {}, "focus" to {}, "decorate" to {}, "shop" to {}, "editor" to {}, "stats" to {}, "settings" to {}, "rooms" to {},
        "running" to { it.startSession(25, false, "focus", false) },
    )
    for ((name, setup) in shots) {
        val c = previewController(File(scratch, name))
        setup(c)
        val sheet = when (name) {
            "focus" -> buddy.app.ui.Sheet.FOCUS; "shop" -> buddy.app.ui.Sheet.SHOP; "editor" -> buddy.app.ui.Sheet.EDITOR
            "stats" -> buddy.app.ui.Sheet.STATS; "settings" -> buddy.app.ui.Sheet.SETTINGS; "rooms" -> buddy.app.ui.Sheet.ROOMS
            else -> buddy.app.ui.Sheet.NONE
        }
        renderPng(File(dir, "screen-$name.png"), 400, 820, 3_000_000_000L) {
            buddy.app.ui.App(c, sheet, initialDecorating = name == "decorate")
        }
    }
}
