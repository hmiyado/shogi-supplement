package dev.miyado.shogisupplement.server.worker.repo

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/**
 * PostgREST（Supabase）向けのHTTPをMockEngineで差し替えたテスト。
 *
 * Why not フェイクリポジトリ: 検証したいのは実PostgRESTのレスポンス形状の解釈と、
 * 実際に送信されるJSONの中身なので、リポジトリ自体を差し替えると何も確かめられない。
 */
class SupabaseAnalysisJobRepositoryTest {

    private val supabaseUrl = "https://example.supabase.co"
    private val serviceRoleKey = "test-service-role-key"

    private fun repository(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): SupabaseAnalysisJobRepository {
        val engine = MockEngine { request -> handler(request) }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(supabaseJson) }
        }
        return SupabaseAnalysisJobRepository(client, supabaseUrl, serviceRoleKey)
    }

    private fun HttpRequestData.bodyText(): String =
        (body as OutgoingContent.ByteArrayContent).bytes().decodeToString()

    @Test
    fun `countToday issues a HEAD with Prefer count=exact and reads the total from Content-Range`() = runTest {
        var capturedMethod: HttpMethod? = null
        var capturedPrefer: String? = null
        val repo = repository { request ->
            capturedMethod = request.method
            capturedPrefer = request.headers[HttpHeaders.Prefer]
            respond(
                content = ByteReadChannel(ByteArray(0)),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentRange, "0-2/37"),
            )
        }

        val count = repo.countToday("user-1")

        assertEquals(37, count)
        assertEquals(HttpMethod.Head, capturedMethod)
        assertEquals("count=exact", capturedPrefer)
    }

    @Test
    fun `countToday filters on mode=game via the moves_usi JSON operator`() = runTest {
        var capturedModeParam: String? = null
        val repo = repository { request ->
            capturedModeParam = request.url.parameters["moves_usi->>mode"]
            respond(
                content = ByteReadChannel(ByteArray(0)),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentRange, "0-0/0"),
            )
        }

        repo.countToday("user-1")

        assertEquals("eq.game", capturedModeParam)
    }

    @Test
    fun `countTodayPosition filters on mode=position and is independent from countToday`() = runTest {
        var capturedModeParam: String? = null
        val repo = repository { request ->
            capturedModeParam = request.url.parameters["moves_usi->>mode"]
            respond(
                content = ByteReadChannel(ByteArray(0)),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentRange, "0-4/5"),
            )
        }

        val count = repo.countTodayPosition("user-1")

        assertEquals("eq.position", capturedModeParam)
        assertEquals(5, count)
    }

    @Test
    fun `countToday returns 0 when Content-Range reports an empty range`() = runTest {
        val repo = repository { _ ->
            respond(
                content = ByteReadChannel(ByteArray(0)),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentRange, "*/0"),
            )
        }

        assertEquals(0, repo.countToday("user-1"))
    }

    @Test
    fun `countToday fails fast when Content-Range is missing`() = runTest {
        val repo = repository { _ ->
            respond(content = ByteReadChannel(ByteArray(0)), status = HttpStatusCode.OK)
        }

        assertFailsWith<IllegalStateException> { repo.countToday("user-1") }
    }

    // モックはselectで要求されたカラムだけを返す（実PostgRESTのふるまい）。
    @Test
    fun `find only requests columns that JobRow can fully deserialize`() = runTest {
        val repo = repository { request ->
            val selected = request.url.parameters["select"]?.split(",").orEmpty()
            val row = JsonObject(
                selected.associateWith { column ->
                    when (column) {
                        "id" -> JsonPrimitive("job-1")
                        "user_id" -> JsonPrimitive("user-1")
                        "moves_hash" -> JsonPrimitive("hash-1")
                        "status" -> JsonPrimitive("done")
                        "result_json", "engine_meta" -> JsonNull
                        "error" -> JsonNull
                        else -> error("unexpected select column: $column")
                    }
                },
            )
            respond(
                content = ByteReadChannel(JsonArray(listOf(row)).toString()),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val record = repo.find("user-1", "hash-1")

        assertNotNull(record)
        assertEquals("job-1", record.id)
        assertEquals(AnalysisJobStatus.DONE, record.status)
    }

    @Test
    fun `markDone sends status even though it has a default value`() = runTest {
        var body: String? = null
        val repo = repository { request ->
            body = request.bodyText()
            respond(content = ByteReadChannel(ByteArray(0)), status = HttpStatusCode.NoContent)
        }

        repo.markDone("job-1", resultJson = JsonPrimitive(1), engineMeta = JsonPrimitive(2))

        val json = Json.parseToJsonElement(body!!).jsonObject
        assertEquals("done", json["status"]?.jsonPrimitive?.content)
    }

    @Test
    fun `markError sends status even though it has a default value`() = runTest {
        var body: String? = null
        val repo = repository { request ->
            body = request.bodyText()
            respond(content = ByteReadChannel(ByteArray(0)), status = HttpStatusCode.NoContent)
        }

        repo.markError("job-1", "boom")

        val json = Json.parseToJsonElement(body!!).jsonObject
        assertEquals("error", json["status"]?.jsonPrimitive?.content)
    }

    @Test
    fun `createRunning sends status even though it has a default value`() = runTest {
        var body: String? = null
        val repo = repository { request ->
            body = request.bodyText()
            respond(
                content = ByteReadChannel(
                    """[{"id":"job-1","user_id":"user-1","moves_hash":"hash-1","status":"running"}]""",
                ),
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        repo.createRunning("user-1", "hash-1", JsonPrimitive("moves"))

        val rows = Json.parseToJsonElement(body!!).jsonArray
        assertEquals("running", rows.first().jsonObject["status"]?.jsonPrimitive?.content)
    }

    @Test
    fun `resetToRunning sends status and clears error and finished_at instead of an empty body`() = runTest {
        var body: String? = null
        val repo = repository { request ->
            body = request.bodyText()
            respond(content = ByteReadChannel(ByteArray(0)), status = HttpStatusCode.NoContent)
        }

        repo.resetToRunning("job-1")

        val json = Json.parseToJsonElement(body!!).jsonObject
        assertEquals("running", json["status"]?.jsonPrimitive?.content)
        assertEquals(JsonNull, json["error"])
        assertEquals(JsonNull, json["finished_at"])
    }
}
