package dev.miyado.shogisupplement.auth

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.createSupabaseClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** [SupabaseAuthRepository] の匿名サインアップと、引き継ぎコード復元のセッション差し替えのテスト。 */
class SupabaseAuthRepositoryTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private val refreshedSession = """
        {
          "access_token": "new-jwt",
          "refresh_token": "rt-2",
          "expires_in": 3600,
          "token_type": "bearer",
          "user": { "id": "user-9", "aud": "authenticated" }
        }
    """.trimIndent()

    private val anonymousSession = """
        {
          "access_token": "jwt-1",
          "refresh_token": "rt-1",
          "expires_in": 3600,
          "token_type": "bearer",
          "user": { "id": "user-1", "aud": "authenticated" }
        }
    """.trimIndent()

    private fun repository(engine: MockEngine, signupPlatform: String? = null): SupabaseAuthRepository {
        val client = createSupabaseClient(supabaseUrl = "https://example.supabase.co", supabaseKey = "anon-key") {
            httpEngine = engine
            install(Auth) {
                autoLoadFromStorage = false
                sessionManager = MemorySessionManager()
            }
        }
        return SupabaseAuthRepository(client, signupPlatform = signupPlatform)
    }

    @Test
    fun `importSessionが成功して戻った時点でアクセストークンが差し替わっている`() = runTest {
        val engine = MockEngine { request ->
            assertTrue(
                request.url.toString().contains("grant_type=refresh_token"),
                "リフレッシュトークンでセッションを取り直すはず（実際は ${request.url}）",
            )
            respond(content = ByteReadChannel(refreshedSession), status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val repository = repository(engine)

        val result = repository.importSession("rt-1")

        assertTrue(result.isSuccess)
        assertEquals("new-jwt", repository.accessToken())
    }

    @Test
    fun `リフレッシュが拒否されると失敗を返しセッションは差し替わらない`() = runTest {
        val engine = MockEngine { _ ->
            respond(
                content = ByteReadChannel("""{"error":"invalid_grant"}"""),
                status = HttpStatusCode.BadRequest,
                headers = jsonHeaders,
            )
        }
        val repository = repository(engine)

        val result = repository.importSession("rt-1")

        assertTrue(result.isFailure)
        assertNull(repository.accessToken())
    }

    @Test
    fun `匿名サインアップのリクエストにプラットフォームが載る`() = runTest {
        var body: String? = null
        val engine = MockEngine { request ->
            body = request.body.toByteArray().decodeToString()
            respond(content = ByteReadChannel(anonymousSession), status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val repository = repository(engine, signupPlatform = "ios-dev")

        val result = repository.signInAnonymously()

        assertTrue(result.isSuccess)
        assertTrue(
            body.orEmpty().contains("\"platform\"") && body.orEmpty().contains("ios-dev"),
            "サインアップのbodyにplatformが入るはず（実際は $body）",
        )
    }

    @Test
    fun `プラットフォーム未指定なら余計なデータを送らない`() = runTest {
        var body: String? = null
        val engine = MockEngine { request ->
            body = request.body.toByteArray().decodeToString()
            respond(content = ByteReadChannel(anonymousSession), status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val repository = repository(engine)

        val result = repository.signInAnonymously()

        assertTrue(result.isSuccess)
        assertTrue(
            !body.orEmpty().contains("\"platform\""),
            "platformを渡していないので送らないはず（実際は $body）",
        )
    }
}
