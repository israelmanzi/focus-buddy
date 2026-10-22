package buddy.app.sync

import buddy.core.BuddyJson
import buddy.core.SyncRequest
import buddy.core.SyncResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json

class SyncClient {
    private val http = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(BuddyJson) }
        install(HttpTimeout) { requestTimeoutMillis = 20_000; connectTimeoutMillis = 8_000 }
    }

    /** `baseUrl` like `http://100.64.0.5:8787`. Throws on network or HTTP errors. */
    suspend fun sync(baseUrl: String, token: String, req: SyncRequest): SyncResponse {
        val res = http.post(baseUrl.trimEnd('/') + "/v1/sync") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(req)
        }
        if (!res.status.isSuccess()) error("Sync failed: HTTP ${res.status.value}")
        return res.body()
    }
}
