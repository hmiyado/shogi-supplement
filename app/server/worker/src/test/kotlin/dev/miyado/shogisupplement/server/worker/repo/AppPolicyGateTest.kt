package dev.miyado.shogisupplement.server.worker.repo

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [SupabaseAppPolicyGate] のPostgREST解釈・TTLキャッシュ・fail-openをMockEngineで確かめる。
 *
 * [ForceUpdateJudge] のブロック条件そのもの（build < min_build の境界値等）は
 * app/analysis の ForceUpdateJudgeTest がすでに網羅しているため、ここでは
 * このゲート固有の関心（PostgREST応答→AppPolicyRow変換、キャッシュのTTL、取得失敗時の挙動）
 * だけを確認する。
 */
class AppPolicyGateTest {

    private val supabaseUrl = "https://example.supabase.co"
    private val serviceRoleKey = "test-service-role-key"

    private val iosRow = """{"platform":"ios","min_build":10,"store_url":"https://example.com","message":null}"""

    private class MutableClock(var instant: Instant) : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId) = this
        override fun instant() = instant
    }

    private fun gate(
        clock: Clock = Clock.fixed(Instant.parse("2026-08-06T00:00:00Z"), ZoneOffset.UTC),
        cacheTtlMs: Long = 60_000,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): SupabaseAppPolicyGate {
        val engine = MockEngine { request -> handler(request) }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(supabaseJson) }
        }
        return SupabaseAppPolicyGate(client, supabaseUrl, serviceRoleKey, clock, cacheTtlMs)
    }

    @Test
    fun `build below min_build is blocked`() = runTest {
        val g = gate { _ ->
            respond(
                content = ByteReadChannel("[$iosRow]"),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        assertTrue(g.isBlocked("ios", build = 5))
    }

    @Test
    fun `build at or above min_build is not blocked`() = runTest {
        val g = gate { _ ->
            respond(
                content = ByteReadChannel("[$iosRow]"),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        assertFalse(g.isBlocked("ios", build = 10))
    }

    @Test
    fun `platform with no matching row is not blocked`() = runTest {
        val g = gate { _ ->
            respond(
                content = ByteReadChannel("[$iosRow]"),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        assertFalse(g.isBlocked("android", build = 1))
    }

    @Test
    fun `fetch failure with no prior cache fails open (not blocked)`() = runTest {
        val g = gate { _ -> respond(content = ByteReadChannel(ByteArray(0)), status = HttpStatusCode.InternalServerError) }
        assertFalse(g.isBlocked("ios", build = 1))
    }

    @Test
    fun `within TTL a second call does not re-fetch`() = runTest {
        val fetchCount = AtomicInteger(0)
        val g = gate(cacheTtlMs = 60_000) { _ ->
            fetchCount.incrementAndGet()
            respond(
                content = ByteReadChannel("[$iosRow]"),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        g.isBlocked("ios", build = 5)
        g.isBlocked("ios", build = 5)

        assertEquals(1, fetchCount.get(), "TTL内は2回目のリクエストでDBへ再往復しないはず")
    }

    @Test
    fun `after TTL expiry a fresh fetch is issued`() = runTest {
        val fetchCount = AtomicInteger(0)
        val clock = MutableClock(Instant.parse("2026-08-06T00:00:00Z"))
        val g = gate(clock = clock, cacheTtlMs = 60_000) { _ ->
            fetchCount.incrementAndGet()
            respond(
                content = ByteReadChannel("[$iosRow]"),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        g.isBlocked("ios", build = 5)
        clock.instant = clock.instant.plusMillis(60_001)
        g.isBlocked("ios", build = 5)

        assertEquals(2, fetchCount.get(), "TTL失効後は再フェッチするはず")
    }

    @Test
    fun `fetch failure after TTL expiry falls back to the stale cache instead of failing open`() = runTest {
        val clock = MutableClock(Instant.parse("2026-08-06T00:00:00Z"))
        var fail = false
        val g = gate(clock = clock, cacheTtlMs = 60_000) { _ ->
            if (fail) {
                respond(content = ByteReadChannel(ByteArray(0)), status = HttpStatusCode.InternalServerError)
            } else {
                respond(
                    content = ByteReadChannel("[$iosRow]"),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }

        // 1回目: 成功してキャッシュに乗る（min_build=10のiOS行）。
        assertTrue(g.isBlocked("ios", build = 5))

        // TTL失効後、DB障害が起きても直近のキャッシュ（ブロック対象という判定）を使い続けるはず。
        clock.instant = clock.instant.plusMillis(60_001)
        fail = true
        assertTrue(g.isBlocked("ios", build = 5), "取得失敗時は直近キャッシュへフォールバックするはず（fail-openより優先）")
    }
}
