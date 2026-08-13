package dev.miyado.shogisupplement.server.worker

import dev.miyado.shogisupplement.api.ApiHeaders
import dev.miyado.shogisupplement.api.analysis.AnalysisRequest
import dev.miyado.shogisupplement.api.analysis.ErrorJson
import dev.miyado.shogisupplement.api.analysis.QuotaExceededJson
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("dev.miyado.shogisupplement.server.worker.Routes")
private val NDJSON = ContentType.parse("application/x-ndjson")

// HTTP変換の薄い層のみを担い、認可・冪等・解析の実処理は[AnalysisService]に委譲する。
fun Routing.registerAnalysisRoutes(service: AnalysisService) {
    get("/health") {
        call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
    }

    post("/v1/analyses") {
        val authorizationHeader = call.request.headers[HttpHeaders.Authorization]
        val appCheckHeader = call.request.headers[ApiHeaders.APP_CHECK]
        val platformHeader = call.request.headers[ApiHeaders.APP_PLATFORM]
        val buildHeader = call.request.headers[ApiHeaders.APP_BUILD]

        val request = try {
            call.receive<AnalysisRequest>()
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, ErrorJson("invalid request body: ${e.message}"))
            return@post
        }

        when (
            val outcome = service.handle(authorizationHeader, request, appCheckHeader, platformHeader, buildHeader)
        ) {
            is AnalysisRequestOutcome.UpgradeRequired ->
                call.respond(HttpStatusCode.UpgradeRequired, ErrorJson("app update required"))

            is AnalysisRequestOutcome.Unauthorized ->
                call.respond(HttpStatusCode.Unauthorized, ErrorJson(outcome.reason))

            is AnalysisRequestOutcome.Banned ->
                call.respond(HttpStatusCode.Forbidden, ErrorJson("banned"))

            is AnalysisRequestOutcome.QuotaExceeded ->
                call.respond(
                    HttpStatusCode.TooManyRequests,
                    QuotaExceededJson(resetAt = outcome.resetAt.toString()),
                )

            is AnalysisRequestOutcome.BadRequest ->
                call.respond(HttpStatusCode.BadRequest, ErrorJson(outcome.reason))

            is AnalysisRequestOutcome.Stream -> {
                // ここで200 OKが確定し、以降ヘッダ送信済みのためHTTPステータスは変更できない。
                // エンジン側の例外はNDJSON末尾の{"error": ...}行で伝える。
                call.respondTextWriter(contentType = NDJSON) {
                    try {
                        outcome.emit { line ->
                            write(line)
                            flush()
                        }
                    } catch (e: Exception) {
                        log.error("streaming analysis failed", e)
                        write(Json.encodeToString(ErrorJson.serializer(), ErrorJson(e.message ?: "internal error")))
                        write("\n")
                        flush()
                    }
                }
            }
        }
    }
}
