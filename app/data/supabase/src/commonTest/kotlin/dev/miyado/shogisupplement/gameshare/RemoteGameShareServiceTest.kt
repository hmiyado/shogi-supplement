package dev.miyado.shogisupplement.gameshare

import dev.miyado.shogisupplement.auth.AuthRepository
import dev.miyado.shogisupplement.auth.AuthUser
import dev.miyado.shogisupplement.board.ShogiBoard
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.fail
import kotlin.test.assertNull

class RemoteGameShareServiceTest {
    private val startSfen = ShogiBoard().toSfen()
    private val id = "AbCdEfGhIjKlMnOpQrStUv"

    private class FakeAuth(private val token: String?) : AuthRepository {
        override val currentUser: StateFlow<AuthUser?> = MutableStateFlow(token?.let { AuthUser("user-1") })
        override suspend fun signInAnonymously(): Result<Unit> = Result.success(Unit)
        override suspend fun accessToken(): String? = token
        override suspend fun refreshSession(): Result<Unit> = Result.success(Unit)
        override suspend fun signOut(): Result<Unit> = Result.success(Unit)
        override suspend fun deleteAccount(): Result<Unit> = Result.success(Unit)
        override suspend fun importSession(refreshToken: String): Result<Unit> = Result.success(Unit)
    }

    private fun draft() = GameShareDraft(
        gameKey = "game-1",
        initialSfen = startSfen,
        mainline = listOf("7g7f", "3c3d"),
        selectedPly = 2,
        branch = null,
        userSide = "sente",
        flip = true,
        winner = null,
        endReason = "投了",
    )

    private fun service(
        token: String? = "access-token",
        appCheck: (suspend () -> String?)? = null,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> io.ktor.client.request.HttpResponseData,
    ): RemoteGameShareService {
        val client = HttpClient(MockEngine { request -> handler(request) })
        return RemoteGameShareService(
            baseUrl = "https://worker.example",
            authRepository = FakeAuth(token),
            platform = "web",
            httpClient = client,
            appCheckTokenProvider = appCheck,
        )
    }

    private fun reader(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> io.ktor.client.request.HttpResponseData,
    ) = StaticGameShareReader("https://storage.example", HttpClient(MockEngine { request -> handler(request) }))

    @Test
    fun createSendsBearerAndMinimalSnapshot() = runTest {
        var authorization: String? = null
        var appCheck: String? = null
        var body: String? = null
        val service = service(appCheck = { "app-check-token" }) { request ->
            authorization = request.headers[HttpHeaders.Authorization]
            appCheck = request.headers[dev.miyado.shogisupplement.api.ApiHeaders.APP_CHECK]
            body = (request.body as io.ktor.http.content.OutgoingContent.ByteArrayContent)
                .bytes().decodeToString()
            respond(
                content = ByteReadChannel(snapshotJson()),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val result = assertIs<GameShareResult.Success<GameShareSummary>>(service.create(draft()))

        assertEquals("Bearer access-token", authorization)
        assertEquals("app-check-token", appCheck)
        assertEquals(id, result.value.id)
        val json = Json.parseToJsonElement(body!!).jsonObject
        assertEquals("game-1", json["game_key"]?.jsonPrimitive?.content)
        assertEquals("sente", json["user_side"]?.jsonPrimitive?.content)
        assertEquals(2, json["mainline"]?.jsonArray?.size)
        assertEquals(false, json.containsKey("sente_name"))
    }

    @Test
    fun invalidDraftIsRejectedBeforeNetwork() = runTest {
        var called = false
        val service = service { _ ->
            called = true
            respond(ByteReadChannel("{}"), HttpStatusCode.OK)
        }

        val result = service.create(draft().copy(userSide = "black"))

        assertIs<GameShareResult.Invalid>(result)
        assertEquals(false, called)
    }

    @Test
    fun missingSessionIsUnauthorizedWithoutNetwork() = runTest {
        var called = false
        val service = service(token = null) { _ ->
            called = true
            respond(ByteReadChannel("{}"), HttpStatusCode.OK)
        }

        assertIs<GameShareResult.Unauthorized>(service.create(draft()))
        assertEquals(false, called)
    }

    @Test
    fun missingStaticObjectIsNotFound() = runTest {
        val missing = reader { request ->
            assertEquals("https://storage.example/storage/v1/object/public/game-shares/$id.json", request.url.toString())
            respond(ByteReadChannel("{}"), HttpStatusCode.NotFound)
        }
        assertIs<GameShareResult.NotFound>(missing.get(id))
    }

    @Test
    fun appCheckExceptionIsContained() = runTest {
        val service = service(appCheck = { error("bridge unavailable") }) { error("must not request") }
        assertIs<GameShareResult.Failure>(service.create(draft()))
    }

    @Test
    fun getMapsAnonymousSnapshotWithoutNames() = runTest {
        var authorization: String? = null
        val service = reader { request ->
            authorization = request.headers[HttpHeaders.Authorization]
            respond(
                content = ByteReadChannel(snapshotJson()),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val result = assertIs<GameShareResult.Success<GameShareSnapshot>>(service.get(id))

        assertNull(authorization)
        assertEquals("sente", result.value.userSide)
        assertEquals(listOf("7g7f", "3c3d"), result.value.mainline)
    }

    @Test
    fun publicGetNeverTouchesAppCheckOrAuth() = runTest {
        val client = HttpClient(MockEngine { request ->
            assertEquals(null, request.headers[HttpHeaders.Authorization])
            assertEquals(null, request.headers[dev.miyado.shogisupplement.api.ApiHeaders.APP_CHECK])
            respond(
                content = ByteReadChannel(snapshotJson()),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        })
        val service = StaticGameShareReader("https://storage.example", client)

        assertIs<GameShareResult.Success<GameShareSnapshot>>(service.get(id))
    }

    @Test
    fun malformedSuccessBodyBecomesFailure() = runTest {
        val service = service {
            respond(
                content = ByteReadChannel("not-json"),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        assertIs<GameShareResult.Failure>(service.create(draft()))
        val malformedReader = reader { respond(ByteReadChannel("not-json"), HttpStatusCode.OK) }
        assertIs<GameShareResult.Failure>(malformedReader.get(id))
    }

    @Test
    fun cancellationPropagates() = runTest {
        val service = reader { throw CancellationException("cancelled") }
        try {
            service.get(id)
            fail("cancellation must propagate")
        } catch (expected: CancellationException) {
            assertEquals("cancelled", expected.message)
        }
    }

    private fun snapshotJson() = """
        {
          "id":"$id",
          "version":1,
          "initial_sfen":"$startSfen",
          "mainline":["7g7f","3c3d"],
          "selected_ply":2,
          "branch":null,
          "user_side":"sente",
          "flip":true,
          "winner":null,
          "end_reason":"投了"
        }
    """.trimIndent()
}
