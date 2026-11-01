package buddy.server

import buddy.core.BuddyJson
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.bearer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.contentLength
import io.ktor.server.request.receive
import io.ktor.server.plugins.origin
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.slf4j.LoggerFactory
import java.security.MessageDigest

/**
 * Max events per response. When a client is further behind than this, the response cursor is
 * the seq of the last returned event, so the client simply gets the rest on its next sync(s).
 */
const val PAGE_LIMIT = 5000

/** Max request body; requests must carry Content-Length (the Ktor/OkHttp client always does). */
const val BODY_LIMIT = 5L * 1024 * 1024

private val log = LoggerFactory.getLogger("buddy.server")

/**
 * Small per-client token bucket. The server may be reachable from the public internet (Tailscale
 * Funnel), so a leaked key should not allow hammering it. Keys are bounded to avoid a spoofed
 * X-Forwarded-For growing the map.
 */
internal class RateLimiter(private val perMinute: Int, private val now: () -> Long = System::currentTimeMillis) {
    private class Bucket(var tokens: Double, var updated: Long)
    private val buckets = object : LinkedHashMap<String, Bucket>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bucket>) = size > 1024
    }

    fun allow(key: String): Boolean = synchronized(buckets) {
        val t = now()
        val b = buckets.getOrPut(key) { Bucket(perMinute.toDouble(), t) }
        val refill = (t - b.updated) / 60_000.0 * perMinute
        b.tokens = (b.tokens + refill).coerceAtMost(perMinute.toDouble())
        b.updated = t
        if (b.tokens < 1.0) false else { b.tokens -= 1.0; true }
    }
}

/**
 * Client address as seen through the Funnel proxy. The trusted proxy appends the real peer last,
 * so take the last entry: a client-supplied prefix must not choose its own bucket.
 */
private fun clientKey(call: io.ktor.server.application.ApplicationCall): String =
    call.request.headers["X-Forwarded-For"]?.substringAfterLast(',')?.trim()?.takeIf { it.isNotEmpty() }
        ?: call.request.origin.remoteHost

/**
 * Wire-identical to [buddy.core.SyncRequest], but events are kept as raw JSON objects so the
 * server stores them opaquely and doesn't need redeploying when new payload types appear.
 */
@Serializable
private class WireSyncRequest(val since: Long = 0, val events: List<JsonObject> = emptyList())

/** Constant-time token check (hashing first also hides the expected length). */
internal fun tokenMatches(given: String, expected: String): Boolean {
    fun sha(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))
    return MessageDigest.isEqual(sha(given), sha(expected))
}

/** Returns the event id if [e] has the shape of a [buddy.core.Event], else null. */
private fun eventId(e: JsonObject): String? {
    val id = (e["id"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
    if (id.isBlank() || id.length > 200) return null
    if ((e["device"] as? JsonPrimitive)?.isString != true) return null
    if ((e["ts"] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull == null) return null
    val payload = e["payload"] as? JsonObject ?: return null
    if ((payload["type"] as? JsonPrimitive)?.isString != true) return null
    return id
}

fun Application.buddyModule(
    token: String,
    store: EventStore,
    pageLimit: Int = PAGE_LIMIT,
    bodyLimit: Long = BODY_LIMIT,
    ratePerMinute: Int = 60,
) {
    require(token.isNotEmpty()) { "token must not be empty" }
    val limiter = RateLimiter(ratePerMinute)
    install(ContentNegotiation) { json(BuddyJson) }
    install(Authentication) {
        bearer("buddy") {
            realm = "focus-buddy"
            authenticate { cred -> if (tokenMatches(cred.token, token)) UserIdPrincipal("buddy") else null }
        }
    }
    routing {
        get("/health") { call.respondText("ok") }

        authenticate("buddy") {
            post("/v1/sync") {
                if (!limiter.allow(clientKey(call))) {
                    return@post call.respondText("Too many requests", status = HttpStatusCode.TooManyRequests)
                }
                val len = call.request.contentLength()
                    ?: return@post call.respondText("Content-Length required", status = HttpStatusCode.LengthRequired)
                if (len > bodyLimit) {
                    return@post call.respondText("Body too large", status = HttpStatusCode.PayloadTooLarge)
                }
                val req = call.receive<WireSyncRequest>()
                val incoming = req.events.map { e ->
                    val id = eventId(e)
                        ?: return@post call.respondText("Malformed event", status = HttpStatusCode.BadRequest)
                    id to e.toString()
                }
                val page = withContext(Dispatchers.IO) { store.sync(req.since, incoming, pageLimit) }
                log.info(
                    "sync since={} in={} new={} out={} cursor={}",
                    req.since, incoming.size, page.inserted, page.events.size, page.cursor,
                )
                // Stored rows are already JSON objects; splice them in rather than re-parsing.
                val body = buildString {
                    append("{\"cursor\":").append(page.cursor).append(",\"events\":[")
                    page.events.forEachIndexed { i, r -> if (i > 0) append(','); append(r.json) }
                    append("]}")
                }
                call.respondText(body, ContentType.Application.Json)
            }
        }
    }
}
