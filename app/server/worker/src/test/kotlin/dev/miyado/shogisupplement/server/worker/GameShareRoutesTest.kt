package dev.miyado.shogisupplement.server.worker

import dev.miyado.shogisupplement.server.worker.fakes.FakeAuthVerifier
import io.ktor.server.testing.testApplication
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.client.request.*
import io.ktor.http.*
import kotlin.test.*

class GameShareRoutesTest {
    @Test fun staticReadsAndManagementRoutesAreAbsent() = testApplication {
        application { install(ContentNegotiation) {json()};routing {registerGameShareRoutes(GameShareService(GameShareServiceTest.Repository(),FakeAuthVerifier()))} }
        assertEquals(HttpStatusCode.MethodNotAllowed,client.get("/v1/game-shares").status)
        assertEquals(HttpStatusCode.NotFound,client.get("/v1/game-shares/AbCdEfGhIjKlMnOpQrStUv").status)
        assertEquals(HttpStatusCode.NotFound,client.delete("/v1/game-shares/AbCdEfGhIjKlMnOpQrStUv").status)
    }
}
