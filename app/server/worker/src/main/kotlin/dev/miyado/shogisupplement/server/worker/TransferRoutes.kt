package dev.miyado.shogisupplement.server.worker

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

private const val APP_CHECK_HEADER = "X-Firebase-AppCheck"
private const val PLATFORM_HEADER = "X-App-Platform"
private const val BUILD_HEADER = "X-App-Build"

// HTTP変換の薄い層のみを担う。認可・発行の実処理は[TransferService]に委譲する。
// JWT認証は無し（未ログイン端末から叩く前提）。
fun Routing.registerTransferRoutes(service: TransferService) {
    post("/v1/transfer") {
        val appCheckHeader = call.request.headers[APP_CHECK_HEADER]
        val platformHeader = call.request.headers[PLATFORM_HEADER]
        val buildHeader = call.request.headers[BUILD_HEADER]
        // Cloud Runはロードバランサ経由のためcall.request.origin.remoteHostは内部IPになる。
        // X-Forwarded-Forの先頭（最初にプロキシへ到達したクライアントIP）を優先し、
        // 無ければ（ローカル実行等）origin.remoteHostにフォールバックする。
        val callerIp = call.request.headers[HttpHeaders.XForwardedFor]
            ?.substringBefore(',')
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: call.request.origin.remoteHost

        val request = try {
            call.receive<TransferRequest>()
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, ErrorJson("invalid request body: ${e.message}"))
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

            // 「一致するコードが無い」旨を漏らさない汎用エラー（理由を出し分けると
            // ブルートフォースの手がかりを与えてしまう）。
            TransferOutcome.NotFound ->
                call.respond(HttpStatusCode.NotFound, ErrorJson("not found"))
        }
    }
}
