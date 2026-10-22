package buddy.server

import buddy.core.BuddyJson
import buddy.core.Event
import buddy.core.Payload
import buddy.core.SyncRequest
import buddy.core.SyncResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import kotlin.io.path.deleteIfExists
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyncServerTest {
    private val token = "test-token-0123456789abcdef"
    private val dbFile = Files.createTempFile("buddy-test", ".db")
    private val store = EventStore(dbFile.toString())

    @AfterTest
    fun cleanup() {
        store.close()
        listOf("", "-wal", "-shm").forEach { dbFile.resolveSibling(dbFile.fileName.toString() + it).deleteIfExists() }
    }

    private fun ev(id: String, device: String = "A", ts: Long = 1_000) =
        Event(id, device, ts, Payload.Buy("item-$id"))

    private fun app(pageLimit: Int = PAGE_LIMIT, bodyLimit: Long = BODY_LIMIT, block: suspend ApplicationTestBuilder.() -> Unit) =
        testApplication {
            application { buddyModule(token, store, pageLimit, bodyLimit) }
            block()
        }

    private suspend fun HttpClient.rawSync(body: String, auth: String? = token): HttpResponse =
        post("/v1/sync") {
            if (auth != null) bearerAuth(auth)
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    private suspend fun HttpClient.sync(since: Long, events: List<Event> = emptyList()): SyncResponse {
        val res = rawSync(BuddyJson.encodeToString(SyncRequest.serializer(), SyncRequest(since, events)))
        assertEquals(HttpStatusCode.OK, res.status, res.bodyAsText())
        return BuddyJson.decodeFromString(SyncResponse.serializer(), res.bodyAsText())
    }

    @Test
    fun healthNeedsNoAuth() = app {
        val res = client.get("/health")
        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals("ok", res.bodyAsText())
    }

    @Test
    fun authRequired() = app {
        val body = BuddyJson.encodeToString(SyncRequest.serializer(), SyncRequest(0, listOf(ev("x"))))
        assertEquals(HttpStatusCode.Unauthorized, client.rawSync(body, auth = null).status)
        assertEquals(HttpStatusCode.Unauthorized, client.rawSync(body, auth = "wrong").status)
        assertEquals(HttpStatusCode.Unauthorized, client.rawSync(body, auth = token + "x").status)
        assertEquals(0, store.count())
        assertEquals(HttpStatusCode.OK, client.rawSync(body).status)
        assertEquals(1, store.count())
    }

    @Test
    fun tokenCompare() {
        assertTrue(tokenMatches("abc", "abc"))
        assertTrue(!tokenMatches("abd", "abc"))
        assertTrue(!tokenMatches("", "abc"))
    }

    @Test
    fun dedupeById() = app {
        val e1 = ev("e1"); val e2 = ev("e2")
        val r1 = client.sync(0, listOf(e1, e2, e1))
        assertEquals(listOf(e1, e2), r1.events)
        assertEquals(2, r1.cursor)
        val r2 = client.sync(0, listOf(e2, e1))
        assertEquals(listOf(e1, e2), r2.events)
        assertEquals(2, r2.cursor)
        val r3 = client.sync(2, listOf(e1))
        assertEquals(emptyList(), r3.events)
        assertEquals(2, r3.cursor)
        assertEquals(2, store.count())
    }

    @Test
    fun cursorPaging() = app(pageLimit = 3) {
        val all = (1..7).map { ev("p$it", ts = it.toLong()) }
        val first = client.sync(0, all)
        assertEquals(all.take(3), first.events)
        assertEquals(3, first.cursor)
        val second = client.sync(first.cursor)
        assertEquals(all.subList(3, 6), second.events)
        assertEquals(6, second.cursor)
        val third = client.sync(second.cursor)
        assertEquals(all.subList(6, 7), third.events)
        assertEquals(7, third.cursor)
        val done = client.sync(third.cursor)
        assertEquals(emptyList(), done.events)
        assertEquals(7, done.cursor)
    }

    @Test
    fun twoDevicesConverge() = app {
        val a = listOf(ev("a1", "A", 10), ev("a2", "A", 20))
        val b = listOf(ev("b1", "B", 15))
        val ra = client.sync(0, a)
        assertEquals(a, ra.events)
        val rb = client.sync(0, b)
        assertEquals(a + b, rb.events)
        val ra2 = client.sync(ra.cursor)
        assertEquals(b, ra2.events)
        val deviceA = (a + ra2.events).toSet()
        val deviceB = (b + rb.events).toSet()
        assertEquals(deviceA, deviceB)
        assertEquals(ra2.cursor, rb.cursor)
    }

    @Test
    fun cursorFromResetServerStartsOver() = app {
        client.sync(0, listOf(ev("r1"), ev("r2")))
        val res = client.sync(999)
        assertEquals(listOf("r1", "r2"), res.events.map { it.id })
        assertEquals(2, res.cursor)
    }

    @Test
    fun unknownPayloadTypesAreStoredOpaquely() = app {
        val raw = """{"since":0,"events":[{"id":"n1","device":"A","ts":5,"payload":{"type":"future","x":1}}]}"""
        val res = client.rawSync(raw)
        assertEquals(HttpStatusCode.OK, res.status)
        assertTrue(res.bodyAsText().contains("\"type\":\"future\""))
    }

    @Test
    fun rejectsBadInput() = app(bodyLimit = 2_000) {
        assertEquals(HttpStatusCode.BadRequest, client.rawSync("not json").status)
        assertEquals(HttpStatusCode.BadRequest, client.rawSync("""{"since":0,"events":[{"id":"x"}]}""").status)
        val big = BuddyJson.encodeToString(SyncRequest.serializer(), SyncRequest(0, (1..50).map { ev("big-$it") }))
        assertEquals(HttpStatusCode.PayloadTooLarge, client.rawSync(big).status)
        assertEquals(0, store.count())
    }
}
