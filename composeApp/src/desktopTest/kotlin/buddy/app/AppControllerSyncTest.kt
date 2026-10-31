package buddy.app

import buddy.app.data.LocalSettings
import buddy.app.data.Store
import buddy.core.BuddyJson
import buddy.core.Event
import buddy.core.Payload
import buddy.core.SyncRequest
import buddy.core.SyncResponse
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.Collections
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Drives [AppController.syncNow] against a real (local) HTTP server so cursor and batching logic
 *  is exercised over the wire. */
class AppControllerSyncTest {
    private lateinit var dir: File
    private lateinit var scope: CoroutineScope
    private lateinit var server: HttpServer
    private val requests = Collections.synchronizedList(mutableListOf<SyncRequest>())
    private val paths = Collections.synchronizedList(mutableListOf<String>())
    private val all = Collections.synchronizedList(mutableListOf<Event>())

    private class FakePlatform(override val dataDir: File) : Platform {
        override val name = "test"
        override val leavingAppInterrupts = false
    }

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("buddy-sync-test").toFile()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    }

    @AfterTest
    fun tearDown() {
        server.stop(0)
        scope.cancel()
        dir.deleteRecursively()
    }

    private fun startServer(pageLimit: Int, onRequest: (SyncRequest) -> SyncResponse) {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/sync") { exchange ->
            paths += exchange.requestURI.path
            val req = BuddyJson.decodeFromString(SyncRequest.serializer(), exchange.requestBody.readBytes().decodeToString())
            requests += req
            val res = onRequest(req)
            val bytes = BuddyJson.encodeToString(SyncResponse.serializer(), res).toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
    }

    /** Mirror of the real server's seq/cursor paging. */
    private fun pagingServer(pageLimit: Int) = startServer(pageLimit) { req ->
        for (e in req.events) if (all.none { it.id == e.id }) all += e
        val from = (if (req.since > all.size) 0 else req.since).toInt()
        val page = all.drop(from).take(pageLimit)
        val cursor = if (page.size >= pageLimit) (from + page.size).toLong() else all.size.toLong()
        SyncResponse(cursor, page)
    }

    private fun controller(autoSync: Boolean = false): AppController {
        Store(dir).saveSettings(LocalSettings(autoSync = autoSync))
        return AppController(FakePlatform(dir), scope) { 1_000_000L }
    }

    private fun newEvent(i: Int) = Event("e$i", "seed", i.toLong(), Payload.Buy("item-$i"))

    @Test
    fun pullsEveryPageEvenWhenTheServerPagesSmallerThanTheOldClientPageSize() {
        all += (1..5).map(::newEvent)
        pagingServer(pageLimit = 2)
        val c = controller()
        c.updateSettings { it.copy(syncUrl = "http://127.0.0.1:${server.address.port}/") }

        runBlocking { c.syncNow() }

        // 0->2, 2->4, 4->5, 5->empty.
        assertEquals(listOf(0L, 2L, 4L, 5L), requests.map { it.since })
        assertTrue(requests.all { it.events.isEmpty() })
        val pulled = BuddyJson.decodeFromString(ListSerializer(Event.serializer()), c.exportBackup())
        assertEquals(5, pulled.size)
        assertTrue(c.sync.value is SyncStatus.Ok)
        // The trailing slash in the configured URL must not produce //v1/sync.
        assertEquals(listOf("/v1/sync"), paths.distinct())
    }

    @Test
    fun staleCursorAfterServerResetStillPullsEverything() {
        all += (1..5).map(::newEvent)
        pagingServer(pageLimit = 2)
        Store(dir).saveMeta(buddy.app.data.DeviceMeta("dev", syncCursor = 100))
        val c = controller()
        c.updateSettings { it.copy(syncUrl = "http://127.0.0.1:${server.address.port}") }

        runBlocking { c.syncNow() }

        // The cursor moves backwards (100 -> 2); the client must keep following it.
        assertEquals(listOf(100L, 2L, 4L, 5L), requests.map { it.since })
        val pulled = BuddyJson.decodeFromString(ListSerializer(Event.serializer()), c.exportBackup())
        assertEquals(5, pulled.size)
    }

    @Test
    fun uploadsALargeBacklogInBatchesAndClearsPending() {
        startServer(pageLimit = 10) { req -> SyncResponse(req.since, emptyList()) }
        val c = controller()

        val backlog = (1..1_500).map { newEvent(it) }
        val json = BuddyJson.encodeToString(ListSerializer(Event.serializer()), backlog)
        assertEquals(1_500, c.importBackup(json))
        c.updateSettings { it.copy(syncUrl = "http://127.0.0.1:${server.address.port}") }

        runBlocking { c.syncNow() }

        assertEquals(listOf(1_000, 500), requests.map { it.events.size })
        assertTrue(Store(dir).loadMeta().pending.isEmpty())
        assertTrue(c.sync.value is SyncStatus.Ok)
    }
}
