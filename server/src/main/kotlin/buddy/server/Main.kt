package buddy.server

import io.ktor.server.application.ApplicationStopped
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import org.slf4j.LoggerFactory
import kotlin.system.exitProcess

/**
 * Env config:
 *  BUDDY_TOKEN (required), BUDDY_HOST (127.0.0.1), BUDDY_PORT (8787), BUDDY_DB (./focus-buddy.db)
 */
fun main() {
    val log = LoggerFactory.getLogger("buddy.server")
    val env = System.getenv()
    fun fail(msg: String): Nothing {
        System.err.println("focus-buddy-server: $msg")
        exitProcess(2)
    }

    val token = env["BUDDY_TOKEN"].orEmpty().trim()
    if (token.isEmpty()) fail("BUDDY_TOKEN is empty; refusing to start")
    if (token.length < 16) log.warn("BUDDY_TOKEN is shorter than 16 characters")
    val host = env["BUDDY_HOST"]?.trim()?.ifEmpty { null } ?: "127.0.0.1"
    val portStr = env["BUDDY_PORT"]?.trim()?.ifEmpty { null } ?: "8787"
    val port = portStr.toIntOrNull()?.takeIf { it in 1..65535 } ?: fail("invalid BUDDY_PORT: $portStr")
    val db = env["BUDDY_DB"]?.trim()?.ifEmpty { null } ?: "./focus-buddy.db"

    val store = EventStore(db)
    log.info("db={} events={} listening on http://{}:{}", db, store.count(), host, port)
    embeddedServer(CIO, port = port, host = host) {
        monitor.subscribe(ApplicationStopped) { store.close() }
        buddyModule(token, store)
    }.start(wait = true)
}
