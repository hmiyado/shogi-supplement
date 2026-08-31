package dev.miyado.shogisupplement.server.worker

import dev.miyado.shogisupplement.api.ApiHeaders
import dev.miyado.shogisupplement.api.analysis.ErrorJson
import dev.miyado.shogisupplement.api.transfer.TransferRequest
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.plugins.origin
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post

// JWTを検証しない。未ログイン端末が引き継ぎを開始するため。
fun Routing.registerTransferRoutes(service: TransferService) {
    post("/v1/transfer") {
        val appCheckHeader = call.request.headers[ApiHeaders.APP_CHECK]
        val platformHeader = call.request.headers[ApiHeaders.APP_PLATFORM]
        val buildHeader = call.request.headers[ApiHeaders.APP_BUILD]
        // Why not 先頭: クライアントが詐称できるため、Cloud Runが追記したX-Forwarded-For末尾を使う。
        val callerIp = call.request.headers[HttpHeaders.XForwardedFor]
            ?.substringAfterLast(',')
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: call.request.origin.remoteHost

        val request = try {
            call.receive<TransferRequest>()
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, ErrorJson("invalid request body"))
            return@post
        }

        when (
            val outcome = service.handle(request, callerIp, appCheckHeader, platformHeader, buildHeader)
        ) {
            is TransferOutcome.Success ->
                call.respond(HttpStatusCode.OK, outcome.session)

            TransferOutcome.BadRequest ->
                call.respond(HttpStatusCode.BadRequest, ErrorJson("invalid k_auth"))

            is TransferOutcome.Unauthorized ->
                call.respond(HttpStatusCode.Unauthorized, ErrorJson(outcome.reason))

            TransferOutcome.UpgradeRequired ->
                call.respond(HttpStatusCode.UpgradeRequired, ErrorJson("app update required"))

            TransferOutcome.RateLimited ->
                call.respond(HttpStatusCode.TooManyRequests, ErrorJson("rate limited"))

            // 列挙攻撃へ照合結果の手がかりを与えない。
            TransferOutcome.NotFound ->
                call.respond(HttpStatusCode.NotFound, ErrorJson("not found"))
        }
    }
}
