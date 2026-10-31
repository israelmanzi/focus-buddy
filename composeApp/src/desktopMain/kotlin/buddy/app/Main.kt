package buddy.app

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import buddy.app.ui.App
import kotlinx.coroutines.MainScope
import java.io.File

object DesktopPlatform : Platform {
    override val name = "desktop"
    override val dataDir: File = File(
        System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() } ?: (System.getProperty("user.home") + "/.local/share"),
        "focus-buddy",
    )
    override val leavingAppInterrupts = false
    override val backupDir = File(System.getProperty("user.home"), "Documents/Focus Buddy")
    override fun playSound(sound: Sound) = DesktopSound.play(sound)
    override fun setAmbient(on: Boolean) = DesktopSound.ambient(on)
}

fun main() {
    System.getenv("BUDDY_PREVIEW")?.let { renderPreviews(File(it)); renderScreens(File(it), File(it, "profiles")); return }
    val controller = AppController(DesktopPlatform, MainScope())
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Focus Buddy",
            state = rememberWindowState(size = DpSize(440.dp, 860.dp)),
        ) {
            App(controller)
        }
    }
}
