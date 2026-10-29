package dev.miyado.shogisupplement.server.worker

import io.ktor.client.request.header
import io.ktor.client.request.options
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * ブラウザのプリフライトが通る条件を保証する。本番ドメインは常に通り、localhostは
 * ALLOW_LOCALHOST_CORS を立てたときだけ、ポートを問わず通る。
 */
class CorsTest {

    private fun preflight(
        allowLocalhost: Boolean,
        origin: String,
        assert: (HttpStatusCode) -> Unit,
    ) = testApplication {
        application {
            install(CORS) { configureWebOrigins(allowLocalhost = allowLocalhost) }
            routing { post("/v1/transfer") { call.respondText("ok") } }
        }
        val response = client.options("/v1/transfer") {
            header(HttpHeaders.Origin, origin)
            header(HttpHeaders.AccessControlRequestMethod, "POST")
            header(HttpHeaders.AccessControlRequestHeaders, "content-type,x-app-build,x-app-platform")
        }
        assert(response.status)
    }

    @Test
    fun `本番ドメインは常に通る`() {
        preflight(allowLocalhost = false, origin = "https://shogi-supplement.miyado.dev") {
            assertEquals(HttpStatusCode.OK, it)
        }
    }

    @Test
    fun `localhostは既定では通らない`() {
        preflight(allowLocalhost = false, origin = "http://localhost:8123") {
            assertEquals(HttpStatusCode.Forbidden, it)
        }
    }

    @Test
    fun `localhostを許可するとポートを問わず通る`() {
        for (origin in listOf("http://localhost:8000", "http://localhost:8123", "http://localhost")) {
            preflight(allowLocalhost = true, origin = origin) {
                assertEquals(HttpStatusCode.OK, it, "$origin が通らない")
            }
        }
    }

    @Test
    fun `localhostを許可しても無関係なオリジンは通らない`() {
        for (origin in listOf("https://evil.example", "http://localhost.evil.example", "https://localhost:8000")) {
            preflight(allowLocalhost = true, origin = origin) {
                assertEquals(HttpStatusCode.Forbidden, it, "$origin が通ってしまう")
            }
        }
    }
}
