package dev.miyado.shogisupplement.repertoire

import dev.miyado.shogisupplement.crypto.TransferSecretStore
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class SupabaseRepertoireRemoteTest {
    @Test fun encryptedPayloadRoundTripsAndRetryKeepsVersion() = runTest {
        val requests = mutableListOf<JsonObject>()
        val engine = MockEngine { request ->
            val response = if (request.method == HttpMethod.Post) {
                val body = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                val json = Json.parseToJsonElement(body).jsonObject
                assertFalse(body.contains("角道を開ける"))
                requests.add(json)
                "true"
            } else {
                assertTrue(request.url.parameters["user_id"] in listOf("eq.alice", "eq.bob"))
                val latest = requests.last()
                buildJsonArray { add(buildJsonObject {
                    put("id", "one"); put("kind", "line")
                    put("version", latest.getValue("p_version"))
                    put("payload_enc", latest.getValue("p_payload_enc"))
                }) }.toString()
            }
            respond(response, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val client = createSupabaseClient("https://example.supabase.co", "anon-key") { httpEngine = engine; install(Postgrest) }
        val store = object : TransferSecretStore {
            override suspend fun load() = ByteArray(16) { 7 }
            override suspend fun save(secret: ByteArray) {}
            override suspend fun clear() {}
        }
        try {
            val remote = SupabaseRepertoireRemote(client, store)
            val payload = RepertoireCodec.encode(RepertoireDocument("角道を開ける", dev.miyado.shogisupplement.board.ShogiBoard().toSfen()))
            val entry = RepertoireEntry("one", "line", payload, 1, null, true, "a".repeat(64))
            val first = remote.put("alice", entry)
            val retry = remote.put("alice", entry)
            assertEquals(first, retry)
            assertNotEquals(requests[0]["p_payload_enc"], requests[1]["p_payload_enc"])
            assertEquals(payload, remote.list("alice").single().payload)
            assertFails { remote.list("bob") }
        } finally { client.close() }
    }
}
