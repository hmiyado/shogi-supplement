package dev.miyado.shogisupplement.auth

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.createSupabaseClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** [SupabaseAuthRepository.importSession]（引き継ぎコード復元のセッション差し替え）のテスト。 */
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

    private fun repository(engine: MockEngine): SupabaseAuthRepository {
        val client = createSupabaseClient(supabaseUrl = "https://example.supabase.co", supabaseKey = "anon-key") {
            httpEngine = engine
            install(Auth) {
                autoLoadFromStorage = false
                sessionManager = MemorySessionManager()
            }
        }
        return SupabaseAuthRepository(client)
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
}
