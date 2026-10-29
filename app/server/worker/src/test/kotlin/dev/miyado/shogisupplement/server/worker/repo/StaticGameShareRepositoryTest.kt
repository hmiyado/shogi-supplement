package dev.miyado.shogisupplement.server.worker.repo

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class StaticGameShareRepositoryTest {
    @Test fun failedStorageDeletionIsRetriedAndAcknowledgedOnlyOnSuccess() = runTest {
        var removes = 0
        var updates = 0
        val client = HttpClient(MockEngine { request ->
            val body = when {
                request.url.encodedPath.endsWith("collect_unfinished_game_shares") -> "null"
                request.method == HttpMethod.Get -> """[{"object_id":"AbCdEfGhIjKlMnOpQrStUv","expires_at":"2000-01-01T00:00:00Z"}]"""
                request.method == HttpMethod.Delete && request.url.encodedPath.contains("/storage/") -> {
                    removes++
                    if (removes == 1) return@MockEngine respond("{}",HttpStatusCode.ServiceUnavailable,headersOf(HttpHeaders.ContentType,"application/json"))
                    "[]"
                }
                request.method == HttpMethod.Patch || request.method == HttpMethod.Delete -> {updates++;"[]"}
                else -> error("unexpected request")
            }
            respond(body,HttpStatusCode.OK,headersOf(HttpHeaders.ContentType,"application/json"))
        }) {install(ContentNegotiation) {json(supabaseJson)}}
        val repo = SupabaseStaticGameShareRepository(client,"https://local.example","test")
        assertEquals(0,repo.cleanup())
        assertEquals(1,repo.cleanup())
        assertEquals(2,removes)
        assertEquals(2,updates)
        client.close()
    }
}
