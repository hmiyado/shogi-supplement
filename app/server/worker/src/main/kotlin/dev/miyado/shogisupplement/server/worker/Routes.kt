package dev.miyado.shogisupplement.server.worker

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

/**
 * ルーティング定義。HTTP変換の薄い層のみを担い、認可・冪等・解析の実処理は
 * [AnalysisService] に委譲する（Ktorに依存しないユニットテストを可能にするための分離）。
 */
fun Routing.registerAnalysisRoutes(service: AnalysisService) {
    get("/healthz") {
        call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
    }

    post("/v1/analyses") {
        val authorizationHeader = call.request.headers[HttpHeaders.Authorization]

        val request = try {
            call.receive<AnalysisRequest>()
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, ErrorJson("invalid request body: ${e.message}"))
            return@post
        }

        when (val outcome = service.handle(authorizationHeader, request)) {
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
                // ここで200 OKが確定する。以降エンジン側で例外が起きてもHTTPステータスは
                // 変更できない（ヘッダ送信済みのため）ので、AnalysisService側がNDJSON末尾に
                // {"error": ...} 行を書く形でエラーを伝える。
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
