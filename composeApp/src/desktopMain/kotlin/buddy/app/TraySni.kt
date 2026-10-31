package buddy.app

import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.Struct
import org.freedesktop.dbus.TypeRef
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.DBusProperties
import org.freedesktop.dbus.annotations.DBusProperty
import org.freedesktop.dbus.annotations.DBusProperty.Access
import org.freedesktop.dbus.annotations.Position
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.errors.UnknownProperty
import org.freedesktop.dbus.interfaces.DBus
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.types.Variant
import java.awt.Component
import java.awt.EventQueue
import java.awt.Frame
import java.awt.Image
import java.awt.MenuItem
import java.awt.MouseInfo
import java.awt.PopupMenu
import java.awt.RenderingHints
import java.awt.image.BufferedImage

/**
 * One ARGB32 pixmap as the StatusNotifierItem spec wants it: `(iiay)` with the pixels in
 * 0xAARRGGBB order (big-endian per pixel), which is what KDE/GTK hosts expect for icons with alpha.
 */
class SniPixmap(
    @JvmField @field:Position(0) var width: Int,
    @JvmField @field:Position(1) var height: Int,
    @JvmField @field:Position(2) var pixels: ByteArray,
) : Struct()

class SniToolTip(
    @JvmField @field:Position(0) var iconName: String,
    @JvmField @field:Position(1) var iconPixmap: List<SniPixmap>,
    @JvmField @field:Position(2) var title: String,
    @JvmField @field:Position(3) var subtitle: String,
) : Struct()

private interface SniPixmapList : TypeRef<List<SniPixmap>>

/**
 * The KDE StatusNotifierItem interface. Properties are declared here for introspection; their values
 * are served by [TraySni]'s [Properties] implementation (Get / GetAll), which is what panels call.
 */
@DBusInterfaceName("org.kde.StatusNotifierItem")
@DBusProperties(
    value = [
        DBusProperty(name = "Category", type = String::class, access = Access.READ),
        DBusProperty(name = "Id", type = String::class, access = Access.READ),
        DBusProperty(name = "Title", type = String::class, access = Access.READ),
        DBusProperty(name = "Status", type = String::class, access = Access.READ),
        DBusProperty(name = "WindowId", type = Int::class, access = Access.READ),
        DBusProperty(name = "IconName", type = String::class, access = Access.READ),
        DBusProperty(name = "IconPixmap", type = SniPixmapList::class, access = Access.READ),
        DBusProperty(name = "OverlayIconName", type = String::class, access = Access.READ),
        DBusProperty(name = "OverlayIconPixmap", type = SniPixmapList::class, access = Access.READ),
        DBusProperty(name = "AttentionIconName", type = String::class, access = Access.READ),
        DBusProperty(name = "AttentionIconPixmap", type = SniPixmapList::class, access = Access.READ),
        DBusProperty(name = "AttentionMovieName", type = String::class, access = Access.READ),
        DBusProperty(name = "ToolTip", type = SniToolTip::class, access = Access.READ),
        DBusProperty(name = "ItemIsMenu", type = Boolean::class, access = Access.READ),
        DBusProperty(name = "Menu", type = DBusPath::class, access = Access.READ),
    ]
)
interface SniItem : DBusInterface {
    fun Activate(x: Int, y: Int)
    fun SecondaryActivate(x: Int, y: Int)
    fun ContextMenu(x: Int, y: Int)
}

@DBusInterfaceName("org.kde.StatusNotifierWatcher")
private interface SniWatcher : DBusInterface {
    fun RegisterStatusNotifierItem(service: String)
    fun RegisterStatusNotifierHost(service: String)
}

/**
 * StatusNotifierItem tray for the desktop app. Exports [SniItem] on the session bus, registers it with
 * the panel's StatusNotifierWatcher, and re-registers whenever the watcher (re)appears, so a panel
 * restart does not lose the icon.
 *
 * Everything here is best-effort: construction throws if no session bus is reachable, and Main guards
 * it, so the app keeps working without a tray.
 */
class TraySni(
    icon: Image?,
    private val restore: () -> Unit,
    private val isRunning: () -> Boolean,
    private val startSession: () -> Unit,
    private val stopSession: () -> Unit,
    private val quit: () -> Unit,
    private val menuOrigin: () -> Component?,
) : SniItem, Properties {

    private val busName = "org.kde.StatusNotifierItem-${ProcessHandle.current().pid()}-focusbuddy"
    private val pixmaps: List<SniPixmap> = listOfNotNull(icon?.let { sniPixmap(it, 22) }, icon?.let { sniPixmap(it, 48) })
    private val fallbackPixmaps: List<SniPixmap> = emptyList()

    private var connection: DBusConnection? = null
    private var watcherSignal: AutoCloseable? = null
    private var menuHost: Frame? = null
    private var currentMenu: PopupMenu? = null

    @Volatile
    private var closed = false

    init {
        val conn = DBusConnectionBuilder.forSessionBus().build()
        connection = conn
        conn.exportObject(PATH, this)
        conn.requestBusName(busName)
        // Panels host the watcher themselves; watch for it appearing (including panel restarts).
        watcherSignal = runCatching {
            conn.addSigHandler(DBus.NameOwnerChanged::class.java) { signal ->
                if (signal.name == WATCHER_NAME && signal.newOwner.isNotEmpty()) registerWithWatcher()
            }
        }.getOrNull()
        registerWithWatcher()
    }

    override fun getObjectPath(): String = PATH

    override fun Activate(x: Int, y: Int) = restore()

    override fun SecondaryActivate(x: Int, y: Int) = restore()

    override fun ContextMenu(x: Int, y: Int) = EventQueue.invokeLater { showMenu() }

    // ---- org.freedesktop.DBus.Properties (values are Variants so the signature carries the real type) ----

    private fun properties(): Map<String, Variant<*>> = linkedMapOf(
        "Category" to Variant("ApplicationStatus"),
        "Id" to Variant("focus-buddy"),
        "Title" to Variant("Focus Buddy"),
        "Status" to Variant("Active"),
        "WindowId" to Variant(0),
        "IconName" to Variant(""),
        "IconPixmap" to Variant(pixmaps, "a(iiay)"),
        "OverlayIconName" to Variant(""),
        "OverlayIconPixmap" to Variant(fallbackPixmaps, "a(iiay)"),
        "AttentionIconName" to Variant(""),
        "AttentionIconPixmap" to Variant(fallbackPixmaps, "a(iiay)"),
        "AttentionMovieName" to Variant(""),
        "ToolTip" to Variant(SniToolTip("", emptyList(), "Focus Buddy", ""), "(sa(iiay)ss)"),
        "ItemIsMenu" to Variant(false),
        "Menu" to Variant(DBusPath(NO_MENU), "o"),
    )

    @Suppress("UNCHECKED_CAST")
    override fun <A> Get(@Suppress("UNUSED_PARAMETER") interfaceName: String, propertyName: String): A =
        (properties()[propertyName] ?: throw UnknownProperty("No such property '$propertyName'")) as A

    override fun GetAll(@Suppress("UNUSED_PARAMETER") interfaceName: String): Map<String, Variant<*>> = properties()

    override fun <A> Set(@Suppress("UNUSED_PARAMETER") interfaceName: String, propertyName: String, value: A) {
        throw UnknownProperty("Property '$propertyName' is read-only")
    }

    // ---- watcher ----

    private fun registerWithWatcher() {
        val conn = connection ?: return
        runCatching {
            conn.getRemoteObject(WATCHER_NAME, WATCHER_PATH, SniWatcher::class.java)
                .RegisterStatusNotifierItem(busName)
        }
    }

    /** Unexports the item, releases the bus name (the watcher drops it on NameOwnerChanged) and closes the connection. */
    fun close() {
        if (closed) return
        closed = true
        runCatching { watcherSignal?.close() }
        watcherSignal = null
        val conn = connection ?: return
        connection = null
        runCatching { conn.unExportObject(PATH) }
        runCatching { conn.releaseBusName(busName) }
        runCatching { conn.close() }
    }

    // ---- context menu ----

    private fun showMenu() {
        if (closed) return
        val pointer = MouseInfo.getPointerInfo()?.location ?: return
        val menu = buildMenu()
        val origin = menuOrigin()?.takeIf { it.isShowing } ?: fallbackOrigin(pointer)
        val originOnScreen = origin.locationOnScreen
        currentMenu?.let { previous -> (previous.parent as? java.awt.Container)?.remove(previous) }
        currentMenu = menu
        runCatching {
            origin.add(menu)
            menu.show(origin, pointer.x - originOnScreen.x, pointer.y - originOnScreen.y)
        }
    }

    private fun buildMenu(): PopupMenu = PopupMenu().apply {
        add(MenuItem("Open Focus Buddy").apply { addActionListener { restore() } })
        addSeparator()
        if (isRunning()) {
            add(MenuItem("Stop session").apply { addActionListener { stopSession() } })
        } else {
            add(MenuItem("Start a focus session").apply { addActionListener { startSession() } })
        }
        addSeparator()
        add(MenuItem("Quit").apply { addActionListener { quit() } })
    }

    /**
     * AWT popup menus need a showing parent. When the app window is not showing we keep a 1x1
     * fully transparent frame at the pointer as the parent, so the menu still opens at the cursor.
     */
    private fun fallbackOrigin(pointer: java.awt.Point): Frame {
        val frame = menuHost ?: Frame().apply {
            isUndecorated = true
            setSize(1, 1)
            runCatching { opacity = 0f }
            menuHost = this
        }
        frame.setLocation(pointer)
        if (!frame.isShowing) frame.isVisible = true
        return frame
    }

    private companion object {
        const val PATH = "/StatusNotifierItem"
        const val WATCHER_NAME = "org.kde.StatusNotifierWatcher"
        const val WATCHER_PATH = "/StatusNotifierWatcher"
        const val NO_MENU = "/NO_DBUSMENU"

        /** Scale [source] into a transparent ARGB buffer and emit the spec's network-order bytes. */
        fun sniPixmap(source: Image, size: Int): SniPixmap {
            val buffer = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
            val g = buffer.createGraphics()
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
                g.drawImage(source, 0, 0, size, size, null)
            } finally {
                g.dispose()
            }
            val bytes = ByteArray(size * size * 4)
            var i = 0
            for (y in 0 until size) {
                for (x in 0 until size) {
                    val argb = buffer.getRGB(x, y)
                    bytes[i++] = ((argb ushr 24) and 0xFF).toByte()
                    bytes[i++] = ((argb ushr 16) and 0xFF).toByte()
                    bytes[i++] = ((argb ushr 8) and 0xFF).toByte()
                    bytes[i++] = (argb and 0xFF).toByte()
                }
            }
            return SniPixmap(size, size, bytes)
        }
    }
}
