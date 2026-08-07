package dev.miyado.shogisupplement.server.worker

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger as LogbackLogger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import dev.miyado.shogisupplement.server.worker.fakes.FakeIpRateLimiter
import dev.miyado.shogisupplement.server.worker.fakes.FakeTransferSecretRepository
import dev.miyado.shogisupplement.server.worker.fakes.FakeTransferSessionIssuer
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * ルート層（Ktor HTTP変換）の統合テスト。分岐網羅はTransferServiceTest（Ktor非依存）が担うため、
 * ここではHTTPステータス・レスポンス形状・ログ非混入だけを確認する（RoutesTestと同じ方針）。
 */
@OptIn(ExperimentalEncodingApi::class)
class TransferRoutesTest {

    private val rawKAuth = ByteArray(32) { it.toByte() }
    private val kAuthBase64 = Base64.encode(rawKAuth)
    private val kAuthHashBase64 = Base64.encode(MessageDigest.getInstance("SHA-256").digest(rawKAuth))

    private fun buildService(
        byHash: Map<String, String> = mapOf(kAuthHashBase64 to "user-1"),
        rateLimiter: FakeIpRateLimiter = FakeIpRateLimiter(allow = true),
    ) = TransferService(
        transferSecretRepository = FakeTransferSecretRepository(byHash),
        sessionIssuer = FakeTransferSessionIssuer(),
        rateLimiter = rateLimiter,
    )

    @Test
    fun `一致するk_authは200でaccess_token_refresh_tokenを返す`() = testApplication {
        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            routing { registerTransferRoutes(buildService()) }
        }
        val response = client.post("/v1/transfer") {
            contentType(ContentType.Application.Json)
            setBody("""{"k_auth":"$kAuthBase64"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("access-for-user-1", body["access_token"]?.jsonPrimitive?.content)
        assertEquals("refresh-for-user-1", body["refresh_token"]?.jsonPrimitive?.content)
    }

    @Test
    fun `一致しないk_authは404で理由を出し分けない`() = testApplication {
        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            routing { registerTransferRoutes(buildService(byHash = emptyMap())) }
        }
        val response = client.post("/v1/transfer") {
            contentType(ContentType.Application.Json)
            setBody("""{"k_auth":"$kAuthBase64"}""")
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `k_authが欠けたリクエストボディは400`() = testApplication {
        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            routing { registerTransferRoutes(buildService()) }
        }
        val response = client.post("/v1/transfer") {
            contentType(ContentType.Application.Json)
            setBody("""{}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `base64として不正なk_authは400`() = testApplication {
        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            routing { registerTransferRoutes(buildService()) }
        }
        val response = client.post("/v1/transfer") {
            contentType(ContentType.Application.Json)
            setBody("""{"k_auth":"!!!not-base64!!!"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `IPレート制限超過は429`() = testApplication {
        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            routing { registerTransferRoutes(buildService(rateLimiter = FakeIpRateLimiter(allow = false))) }
        }
        val response = client.post("/v1/transfer") {
            contentType(ContentType.Application.Json)
            setBody("""{"k_auth":"$kAuthBase64"}""")
        }
        assertEquals(HttpStatusCode.TooManyRequests, response.status)
    }

    // ── ログ非混入 ────────────────────────────────────────────────────────

    private lateinit var logAppender: ListAppender<ILoggingEvent>
    private lateinit var rootLogger: LogbackLogger

    @BeforeTest
    fun attachLogAppender() {
        rootLogger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as LogbackLogger
        logAppender = ListAppender()
        logAppender.start()
        rootLogger.addAppender(logAppender)
        rootLogger.level = Level.ALL
    }

    @AfterTest
    fun detachLogAppender() {
        rootLogger.detachAppender(logAppender)
    }

    @Test
    fun `成功リクエストのログにk_authもハッシュも出ない`() = testApplication {
        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(CallLogging)
            routing { registerTransferRoutes(buildService()) }
        }
        val response = client.post("/v1/transfer") {
            contentType(ContentType.Application.Json)
            setBody("""{"k_auth":"$kAuthBase64"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertNoLeakedSecret()
    }

    @Test
    fun `失敗リクエストのログにもk_authもハッシュも出ない`() = testApplication {
        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(CallLogging)
            routing { registerTransferRoutes(buildService(byHash = emptyMap())) }
        }
        val response = client.post("/v1/transfer") {
            contentType(ContentType.Application.Json)
            setBody("""{"k_auth":"$kAuthBase64"}""")
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertNoLeakedSecret()
    }

    private fun assertNoLeakedSecret() {
        val messages = logAppender.list.map { it.formattedMessage }
        assertTrue(messages.isNotEmpty(), "CallLoggingが何も出力していない（テストのセットアップ自体を疑う）")
        assertFalse(messages.any { it.contains(kAuthBase64) }, "ログにk_auth（生値）が出ている: $messages")
        assertFalse(messages.any { it.contains(kAuthHashBase64) }, "ログにkey_auth_hashが出ている: $messages")
    }
}
