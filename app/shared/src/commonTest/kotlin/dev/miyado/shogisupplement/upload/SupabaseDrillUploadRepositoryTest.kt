package dev.miyado.shogisupplement.upload

import dev.miyado.shogisupplement.crypto.TransferSecretStore
import dev.miyado.shogisupplement.db.BlunderRecord
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

/** [SupabaseUploadRepository] のドリル同期部分をPostgrest HTTPで検証する。 */
class SupabaseDrillUploadRepositoryTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private class FakeTransferSecretStore : TransferSecretStore {
        override suspend fun load(): ByteArray? = null
        override suspend fun save(secret: ByteArray) = Unit
        override suspend fun clear() = Unit
    }

    private fun repository(engine: MockEngine): SupabaseUploadRepository {
        val client = createSupabaseClient(
            supabaseUrl = "https://example.supabase.co",
            supabaseKey = "anon-key",
        ) {
            httpEngine = engine
            install(Postgrest)
        }
        return SupabaseUploadRepository(client, FakeTransferSecretStore())
    }

    private fun problem(ply: Long = 41L) = BlunderRecord(
        id = 7L,
        gameId = 3L,
        ply = ply,
        side = "sente",
        moveUsi = "B*3d",
        bestUsi = "2f6f",
        lossWp = 0.225,
        sfenBefore = "lnsgkgsnl/1r5b1/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL b - 1",
        category = "駒損",
        diffMaterial = -11L,
        punishChecks = 0L,
        tookMovedPiece = false,
        missedMateIn = null,
        verdict = "○ 出題対象",
        note = "テスト問題",
        problemType = "手筋",
        priority = 2.5,
        secondUsi = "2g2f",
        secondCp = 123L,
    )

    private fun HttpRequestData.bodyText(): String =
        (body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString().orEmpty()

    @Test
    fun `問題が空ならHTTPリクエストを送らず成功する`() = runTest {
        var requestCount = 0
        val engine = MockEngine { _ ->
            requestCount++
            respond(content = ByteReadChannel(""), status = HttpStatusCode.InternalServerError)
        }

        val result = repository(engine).syncDrillProblems("user-1", "hash-1", emptyList())

        assertEquals(UploadResult.Success, result)
        assertEquals(0, requestCount)
    }

    @Test
    fun `問題upsertは複合キーのignore-duplicates指定と全ペイロードを送る`() = runTest {
        var request: HttpRequestData? = null
        val engine = MockEngine { received ->
            request = received
            respond(content = ByteReadChannel(""), status = HttpStatusCode.Created, headers = jsonHeaders)
        }

        val result = repository(engine).syncDrillProblems("user-1", "hash-1", listOf(problem()))

        assertEquals(UploadResult.Success, result)
        val sent = request ?: error("request was not sent")
        assertEquals(HttpMethod.Post, sent.method)
        assertTrue(sent.url.toString().contains("drill_problems"))
        assertEquals("user_id,content_hash,ply", sent.url.parameters["on_conflict"])
        assertTrue(sent.headers[HttpHeaders.Prefer].orEmpty().contains("resolution=ignore-duplicates"))
        val body = sent.bodyText()
        assertTrue(body.contains("\"user_id\":\"user-1\""))
        assertTrue(body.contains("\"content_hash\":\"hash-1\""))
        assertTrue(body.contains("\"ply\":41"))
        assertTrue(body.contains("\"side\":\"sente\""))
        assertTrue(body.contains("\"sfen_before\":"))
        assertTrue(body.contains("\"move_usi\":\"B*3d\""))
        assertTrue(body.contains("\"best_usi\":\"2f6f\""))
        assertTrue(body.contains("\"loss_wp\":0.225"))
        assertTrue(body.contains("\"category\":\"駒損\""))
        assertTrue(body.contains("\"verdict\":\"○ 出題対象\""))
        assertTrue(body.contains("\"note\":\"テスト問題\""))
        assertTrue(body.contains("\"problem_type\":\"手筋\""))
        assertTrue(body.contains("\"priority\":2.5"))
        assertTrue(body.contains("\"second_usi\":\"2g2f\""))
        assertTrue(body.contains("\"second_cp\":123"))
    }

    @Test
    fun `解答送信は問題upsert問題IDselect解答upsertの順に行う`() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val bodies = mutableListOf<String>()
        val engine = MockEngine { received ->
            requests += received
            bodies += received.bodyText()
            when (requests.size) {
                1, 3 -> respond(content = ByteReadChannel(""), status = HttpStatusCode.Created, headers = jsonHeaders)
                2 -> respond(
                    content = ByteReadChannel("[{\"id\":\"problem-uuid\"}]"),
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
                else -> error("unexpected request ${requests.size}")
            }
        }

        val result = repository(engine).uploadDrillAttempt(
            userId = "user-1",
            contentHash = "hash-1",
            problem = problem(),
            attempt = DrillAttemptUpload(
                syncId = "attempt-uuid",
                userMoveUsi = "B*3d",
                isCorrect = false,
                lossWp = 0.5,
                attemptedAt = 1_780_000_000L,
            ),
        )

        assertEquals(UploadResult.Success, result)
        assertEquals(listOf(HttpMethod.Post, HttpMethod.Get, HttpMethod.Post), requests.map { it.method })
        assertTrue(requests[0].url.toString().contains("drill_problems"))
        assertTrue(requests[1].url.toString().contains("drill_problems"))
        assertTrue(requests[2].url.toString().contains("drill_attempts"))
        assertEquals("eq.user-1", requests[1].url.parameters["user_id"])
        assertEquals("eq.hash-1", requests[1].url.parameters["content_hash"])
        assertEquals("eq.41", requests[1].url.parameters["ply"])
        assertEquals("user_id,client_attempt_id", requests[2].url.parameters["on_conflict"])
        assertTrue(requests[2].headers[HttpHeaders.Prefer].orEmpty().contains("resolution=ignore-duplicates"))
        assertTrue(bodies[2].contains("\"user_id\":\"user-1\""))
        assertTrue(bodies[2].contains("\"problem_id\":\"problem-uuid\""))
        assertTrue(bodies[2].contains("\"client_attempt_id\":\"attempt-uuid\""))
        assertTrue(bodies[2].contains("\"user_move_usi\":\"B*3d\""))
        assertTrue(bodies[2].contains("\"is_correct\":false"))
        assertTrue(bodies[2].contains("\"loss_wp\":0.5"))
        assertTrue(bodies[2].contains("\"attempted_at\":\"${Instant.fromEpochSeconds(1_780_000_000L)}\""))
    }

    @Test
    fun `解答upsertの40923505duplicateは既存行として成功扱いする`() = runTest {
        val duplicateResponses = listOf(
            HttpStatusCode.Conflict to "{\"message\":\"conflict\"}",
            HttpStatusCode.InternalServerError to "{\"code\":\"23505\"}",
            HttpStatusCode.BadRequest to "{\"message\":\"duplicate key\"}",
        )

        for ((status, body) in duplicateResponses) {
            var requestCount = 0
            val engine = MockEngine { _ ->
                requestCount++
                when (requestCount) {
                    1 -> respond(content = ByteReadChannel(""), status = HttpStatusCode.Created, headers = jsonHeaders)
                    2 -> respond(
                        content = ByteReadChannel("[{\"id\":\"problem-uuid\"}]"),
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders,
                    )
                    3 -> respond(content = ByteReadChannel(body), status = status, headers = jsonHeaders)
                    else -> error("unexpected request $requestCount")
                }
            }

            val result = repository(engine).uploadDrillAttempt(
                userId = "user-1",
                contentHash = "hash-1",
                problem = problem(),
                attempt = DrillAttemptUpload("attempt-uuid", "B*3d", false, null, 1_780_000_000L),
            )

            assertEquals(UploadResult.Success, result, "status=$status body=$body")
            assertEquals(3, requestCount)
        }
    }
}
