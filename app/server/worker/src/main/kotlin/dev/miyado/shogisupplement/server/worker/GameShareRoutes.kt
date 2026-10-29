package dev.miyado.shogisupplement.server.worker

import dev.miyado.shogisupplement.api.ApiHeaders
import dev.miyado.shogisupplement.api.analysis.ErrorJson
import dev.miyado.shogisupplement.api.gameshare.CreateGameShareRequest
import dev.miyado.shogisupplement.api.gameshare.GameShareLimits
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

private val gameShareLog = LoggerFactory.getLogger("dev.miyado.shogisupplement.server.worker.GameShareRoutes")
private val gameShareJson = Json { ignoreUnknownKeys = true }

fun Routing.registerGameShareRoutes(service: GameShareService) {
    post("/v1/game-shares") {
        val body = call.receiveBodyWithin(GameShareLimits.MAX_BODY_BYTES.toLong())
        if (body == null) {
            call.respond(HttpStatusCode.PayloadTooLarge, ErrorJson("request body too large"))
            return@post
        }
        val request = try {
            gameShareJson.decodeFromString(CreateGameShareRequest.serializer(), body)
        } catch (e: Exception) {
            gameShareLog.debug("invalid game share body", e)
            call.respond(HttpStatusCode.BadRequest, ErrorJson("invalid request body"))
            return@post
        }
        when (
            val outcome = service.create(
                request = request,
                authorizationHeader = call.request.headers[HttpHeaders.Authorization],
                appCheckHeader = call.request.headers[ApiHeaders.APP_CHECK],
                platformHeader = call.request.headers[ApiHeaders.APP_PLATFORM],
                buildHeader = call.request.headers[ApiHeaders.APP_BUILD],
            )
        ) {
            is CreateGameShareOutcome.Success -> call.respond(HttpStatusCode.OK, outcome.snapshot)
            CreateGameShareOutcome.Invalid ->
                call.respond(HttpStatusCode.BadRequest, ErrorJson("invalid game share"))
            is CreateGameShareOutcome.Unauthorized ->
                call.respond(HttpStatusCode.Unauthorized, ErrorJson(outcome.reason))
            CreateGameShareOutcome.UpgradeRequired ->
                call.respond(HttpStatusCode.UpgradeRequired, ErrorJson("app update required"))
            CreateGameShareOutcome.SourceMissing ->
                call.respond(HttpStatusCode.Conflict, ErrorJson("source game unavailable"))
        }
    }
}
