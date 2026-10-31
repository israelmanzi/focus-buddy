package buddy.app

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import buddy.app.ui.App
import buddy.resources.Res
import buddy.resources.focus_buddy
import kotlinx.coroutines.MainScope
import org.jetbrains.compose.resources.painterResource
import java.awt.Dimension
import java.awt.Toolkit
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

    /** Freedesktop notification; silently does nothing where notify-send or a daemon is missing. */
    override fun showNotification(title: String, body: String) {
        runCatching {
            val icon = File(System.getProperty("user.home"), ".local/share/focus-buddy-app/icon.png")
            val cmd = buildList {
                add("notify-send"); add("-a"); add("Focus Buddy"); add("-t"); add("8000")
                if (icon.exists()) { add("-i"); add(icon.path) }
                add(title); add(body)
            }
            ProcessBuilder(cmd).start()
        }
    }

    private var inhibitor: Process? = null

    /** Block idle sleep while focusing (logind); released when the session ends. */
    fun keepAwake(on: Boolean) {
        if (on) {
            if (inhibitor?.isAlive == true) return
            inhibitor = runCatching {
                ProcessBuilder(
                    "systemd-inhibit", "--what=idle:sleep", "--mode=block",
                    "--who=Focus Buddy", "--why=Focus session in progress", "sleep", "infinity",
                ).redirectErrorStream(true).start()
            }.getOrNull()
        } else {
            inhibitor?.destroy()
            inhibitor = null
        }
    }
}

/** Remembered window geometry in a tiny text file (x y w h). */
private class WindowGeometry(private val file: File) {
    fun load(): Pair<WindowPosition, DpSize> {
        val p = runCatching { file.readText().trim().split(' ').map { it.toFloat() } }.getOrNull()
        return if (p != null && p.size >= 4) WindowPosition.Absolute(p[0].dp, p[1].dp) to DpSize(p[2].dp, p[3].dp)
        else WindowPosition.PlatformDefault to DpSize(440.dp, 860.dp)
    }

    fun savePoints(location: java.awt.Point, size: java.awt.Dimension) {
        runCatching { file.writeText("${location.x} ${location.y} ${size.width} ${size.height}") }
    }
}

private fun loadIconImage(): java.awt.Image? = runCatching {
    val stream = object {}.javaClass.getResourceAsStream("/composeResources/buddy.resources/drawable/focus_buddy.png")
        ?: File(System.getProperty("user.home"), ".local/share/focus-buddy-app/icon.png").inputStream()
    javax.imageio.ImageIO.read(stream)
}.getOrNull()

/** X11 WM_CLASS, so the dock shows "focus-buddy" instead of a Java thread. Needs --add-opens java.desktop/sun.awt.X11. */
private fun setApplicationClassName(name: String) {
    runCatching {
        val toolkit = Toolkit.getDefaultToolkit()
        val field = toolkit.javaClass.getDeclaredField("awtAppClassName")
        field.isAccessible = true
        field.set(toolkit, name)
    }
}

fun main() {
    System.getenv("BUDDY_PREVIEW")?.let { renderPreviews(File(it)); renderScreens(File(it), File(it, "profiles")); return }
    setApplicationClassName("focus-buddy")

    val controller = AppController(DesktopPlatform, MainScope())
    val geometry = WindowGeometry(File(DesktopPlatform.dataDir, "window.txt"))
    val (initialPos, initialSize) = geometry.load()

    val trayEnabled = System.getenv("BUDDY_NO_TRAY") == null

    application {
        var awtWindow by remember { mutableStateOf<java.awt.Window?>(null) }
        var tray by remember { mutableStateOf<TraySni?>(null) }
        val windowState = rememberWindowState(position = initialPos, size = initialSize)
        val icon: Painter = painterResource(Res.drawable.focus_buddy)

        fun saveGeometry() = awtWindow?.let { geometry.savePoints(it.location, it.size) }
        fun quit() {
            saveGeometry()
            DesktopPlatform.keepAwake(false)
            runCatching { tray?.close() }
            Runtime.getRuntime().halt(0)
        }
        // Minimise rather than hide/dispose: destroying or hiding a Skiko/X11 window crashes inside
        // libX11 on this Fedora + Mesa combination (XVisualIDFromVisual).
        fun closeWindow() {
            saveGeometry()
            quit()
        }
        fun restore() {
            val w = awtWindow ?: return
            if (w is java.awt.Frame) w.extendedState = java.awt.Frame.NORMAL
            w.isVisible = true
            w.toFront()
        }

        // Keep the screen awake only while a session is running and the user asked for it.
        LaunchedEffect(Unit) {
            controller.phase.collect { phase ->
                DesktopPlatform.keepAwake(phase is Phase.Running && controller.settings.value.screenOn)
            }
        }
        DisposableEffect(Unit) {
            onDispose {
                saveGeometry()
                DesktopPlatform.keepAwake(false)
                runCatching { tray?.close() }
            }
        }

        // StatusNotifierItem tray (real ARGB icon in XFCE and other SNI panels). Optional: if the
        // session bus or the transport is unavailable the app simply runs without a tray.
        if (trayEnabled) LaunchedEffect(Unit) {
            tray = runCatching {
                TraySni(
                    icon = loadIconImage(),
                    restore = { java.awt.EventQueue.invokeLater { restore() } },
                    isRunning = { controller.phase.value is Phase.Running },
                    startSession = {
                        java.awt.EventQueue.invokeLater {
                            restore()
                            controller.startSession(
                                controller.settings.value.defaultMinutes,
                                controller.settings.value.countUp,
                                controller.settings.value.lastTag,
                                false,
                            )
                        }
                    },
                    stopSession = { java.awt.EventQueue.invokeLater { controller.stopSession() } },
                    quit = { quit() },
                    menuOrigin = { awtWindow },
                )
            }.getOrNull()
        }

        Window(
            onCloseRequest = { closeWindow() },
            state = windowState,
            title = "Focus Buddy",
            icon = icon,
        ) {
            LaunchedEffect(Unit) {
                awtWindow = window
                window.minimumSize = Dimension(360, 560)
                // Painter -> Image conversion flattens the icon's transparency; load the PNG with
                // ImageIO so the round icon stays round in the dock and window switcher.
                loadIconImage()?.let { window.iconImage = it }
            }
            App(controller)
        }

    }
}
