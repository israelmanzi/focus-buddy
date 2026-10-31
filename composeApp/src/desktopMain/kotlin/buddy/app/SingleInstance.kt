package buddy.app

import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.file.StandardOpenOption
import javax.swing.SwingUtilities

/**
 * One app per data directory. The first process holds an exclusive lock on app.lock and listens on
 * app.sock; later launches ask it to raise its window and exit. Two processes sharing one event log
 * would corrupt cursors and duplicate ids.
 */
class SingleInstance(private val dir: File) {
    private val lockFile = File(dir, "app.lock")
    private val socketFile = File(dir, "app.sock")
    private var channel: FileChannel? = null
    private var lock: FileLock? = null
    private var server: ServerSocketChannel? = null

    /** Returns false when another instance is already running; that instance is asked to show itself. */
    fun acquire(): Boolean {
        dir.mkdirs()
        val ch = FileChannel.open(lockFile.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
        val acquired = try {
            ch.tryLock()
        } catch (_: OverlappingFileLockException) {
            null
        }
        if (acquired == null) {
            runCatching { ch.close() }
            requestShow()
            return false
        }
        channel = ch
        lock = acquired
        return true
    }

    /** Serves "show" requests from later launches. Call once the window exists. */
    fun listen(onShow: () -> Unit) {
        runCatching {
            socketFile.delete()
            val srv = ServerSocketChannel.open(StandardProtocolFamily.UNIX)
            srv.bind(UnixDomainSocketAddress.of(socketFile.toPath()))
            server = srv
            Thread({
                while (srv.isOpen) {
                    val client = runCatching { srv.accept() }.getOrNull() ?: break
                    client.use { runCatching { it.read(ByteBuffer.allocate(32)) } }
                    SwingUtilities.invokeLater(onShow)
                }
            }, "focus-buddy-instance").apply { isDaemon = true }.start()
        }
    }

    fun close() {
        runCatching { server?.close() }
        server = null
        runCatching { socketFile.delete() }
        runCatching { lock?.release() }
        runCatching { channel?.close() }
        lock = null
        channel = null
    }

    private fun requestShow() {
        runCatching {
            SocketChannel.open(UnixDomainSocketAddress.of(socketFile.toPath())).use { s ->
                s.write(ByteBuffer.wrap("show\n".toByteArray()))
            }
        }
    }
}
