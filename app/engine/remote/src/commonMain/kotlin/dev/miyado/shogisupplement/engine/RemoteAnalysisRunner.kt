package dev.miyado.shogisupplement.engine

import dev.miyado.shogisupplement.api.ApiHeaders
import dev.miyado.shogisupplement.api.analysis.AnalysisRequest
import dev.miyado.shogisupplement.api.analysis.AnalysisResultJson
import dev.miyado.shogisupplement.api.analysis.ErrorJson
import dev.miyado.shogisupplement.api.analysis.PositionResultJson
import dev.miyado.shogisupplement.api.analysis.ProgressJson
import dev.miyado.shogisupplement.api.analysis.QuotaExceededJson
import dev.miyado.shogisupplement.api.analysis.toPvInfo
import dev.miyado.shogisupplement.policy.currentBuildNumber
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readLine
import kotlinx.coroutines.delay
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject

/**
 * @property platform `app_policy.platform`の語彙（"android" / "ios"）。
 * @property retryBackoffMs 試行回数に比例する待機時間。最大280秒のサーバーポーリングが律速のため指数化しない。
 * @property appCheckTokenProvider nullはApp Checkヘッダーを省略し、例外は解析失敗として伝播する。
 */
class RemoteAnalysisRunner(
    private val baseUrl: String,
    private val accessTokenProvider: suspend () -> String,
    private val platform: String,
    private val httpClient: HttpClient = HttpClient(),
    private val maxRetries: Int = 3,
    private val retryBackoffMs: Long = 1_000,
    private val appCheckTokenProvider: (suspend () -> String?)? = null,
) : GameAnalyzer {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * `moves_hash`の冪等性を前提に、切断時だけ同じリクエストを再送する。
     * 認可・クォータ・不正・更新要求のHTTPエラーと終端error行は再送せず[RemoteAnalysisException]として伝播する。
     */
    override suspend fun analyzeGame(
        moves: List<String>,
        onPositionResult: ((ply: Int, pvs: List<PvInfo>) -> Unit)?,
        onProgress: ((done: Int, total: Int) -> Unit)?,
    ): List<List<PvInfo>> = executeWithRetry(
        AnalysisRequest(movesUsi = moves),
        onProgress = onProgress,
        onPositionResult = onPositionResult,
    )

    /** `sfen`から`moves`を進めた単一局面へ、[analyzeGame]と同じ再送・冪等性を適用する。 */
    suspend fun analyzePosition(sfen: String, moves: List<String> = emptyList()): List<PvInfo> {
        val perPosition = executeWithRetry(
            AnalysisRequest(sfen = sfen, moves = moves),
            onProgress = null,
            onPositionResult = null,
        )
        return perPosition.firstOrNull() ?: emptyList()
    }

    private suspend fun executeWithRetry(
        request: AnalysisRequest,
        onProgress: ((done: Int, total: Int) -> Unit)?,
        onPositionResult: ((ply: Int, pvs: List<PvInfo>) -> Unit)?,
    ): List<List<PvInfo>> {
        var lastDisconnect: Exception? = null
        val totalAttempts = maxRetries + 1
        repeat(totalAttempts) { attempt ->
            if (attempt > 0) {
                delay(retryBackoffMs * attempt)
            }
            try {
                return requestOnce(request, onProgress, onPositionResult)
            } catch (e: RemoteAnalysisException) {
                // 認可・クォータ・不正リクエスト・エンジン失敗はリトライしても直らないため即座に伝播する。
                throw e
            } catch (e: kotlinx.coroutines.CancellationException) {
                // キャンセルをConnectionLost扱いでリトライすると、キャンセル済みスコープで
                // 再POSTを試みることになる。中断はそのまま伝播させる
                throw e
            } catch (e: Exception) {
                // ネットワーク断・ストリームが終端行なしで終わった場合のみここに来る。再POSTで復旧を試みる。
                lastDisconnect = e
            }
        }
        throw RemoteAnalysisException.ConnectionLost(
            "サーバー解析への接続が${totalAttempts}回の試行後も回復しませんでした: ${lastDisconnect?.message}",
            lastDisconnect,
        )
    }

    private suspend fun requestOnce(
        request: AnalysisRequest,
        onProgress: ((done: Int, total: Int) -> Unit)?,
        onPositionResult: ((ply: Int, pvs: List<PvInfo>) -> Unit)?,
    ): List<List<PvInfo>> {
        val token = accessTokenProvider()
        val appCheckToken = appCheckTokenProvider?.invoke()
        // preparePost+execute を使う: post() はレスポンス本文を最後まで読み切ってから返すため、
        // 進捗行が解析完了後にまとめて届いてしまい、ストリーミングの意味が無くなる。
        return httpClient.preparePost("$baseUrl/v1/analyses") {
            header("Authorization", "Bearer $token")
            if (appCheckToken != null) {
                header(ApiHeaders.APP_CHECK, appCheckToken)
            }
            // Why not appCheckTokenのように取得失敗時だけ省く: 欠如時はサーバー側が
            // fail-open/1.0クライアント互換としてスキップするだけなので、条件付きにする理由が無い。
            header(ApiHeaders.APP_PLATFORM, platform)
            header(ApiHeaders.APP_BUILD, currentBuildNumber().toString())
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(AnalysisRequest.serializer(), request))
        }.execute { response ->
            when (response.status) {
                HttpStatusCode.Unauthorized ->
                    throw RemoteAnalysisException.Unauthorized(readErrorMessage(response))
                HttpStatusCode.Forbidden ->
                    throw RemoteAnalysisException.Banned
                HttpStatusCode.TooManyRequests ->
                    throw RemoteAnalysisException.QuotaExceeded(readResetAt(response))
                HttpStatusCode.BadRequest, HttpStatusCode.PayloadTooLarge ->
                    throw RemoteAnalysisException.BadRequest(readErrorMessage(response))
                HttpStatusCode.UpgradeRequired ->
                    throw RemoteAnalysisException.UpgradeRequired(readErrorMessage(response))
                else -> Unit
            }
            if (!response.status.isSuccess()) {
                // 5xx等の想定外ステータスは切断と同列に扱い再試行対象にする。
                error("unexpected status ${response.status}")
            }

            consumeStream(response, onProgress, onPositionResult)
        }
    }

    private suspend fun consumeStream(
        response: HttpResponse,
        onProgress: ((done: Int, total: Int) -> Unit)?,
        onPositionResult: ((ply: Int, pvs: List<PvInfo>) -> Unit)?,
    ): List<List<PvInfo>> {
        val channel = response.bodyAsChannel()
        // position行は並列ワーカーの完了順で届くため、最終result行との重複通知を防ぐ。
        val deliveredPlies = mutableSetOf<Int>()
        while (true) {
            val line = channel.readLine() ?: break
            if (line.isBlank()) continue
            val obj = json.parseToJsonElement(line).jsonObject
            when {
                "result" in obj -> {
                    val resultJson = json.decodeFromJsonElement(AnalysisResultJson.serializer(), obj)
                    val positions = resultJson.result.map { pvList -> pvList.map { it.toPvInfo() } }
                    positions.forEachIndexed { ply, pvs ->
                        if (deliveredPlies.add(ply)) onPositionResult?.invoke(ply, pvs)
                    }
                    return positions
                }
                "position" in obj -> {
                    val positionJson = json.decodeFromJsonElement(PositionResultJson.serializer(), obj)
                    val (ply, pvs) = positionJson.position
                    deliveredPlies.add(ply)
                    onPositionResult?.invoke(ply, pvs.map { it.toPvInfo() })
                }
                "error" in obj -> {
                    val errorJson = json.decodeFromJsonElement(ErrorJson.serializer(), obj)
                    throw RemoteAnalysisException.EngineFailure(errorJson.error)
                }
                "progress" in obj -> {
                    val progressJson = json.decodeFromJsonElement(ProgressJson.serializer(), obj)
                    onProgress?.invoke(progressJson.progress, progressJson.total)
                }
                else -> Unit // 未知の行は前方互換のため無視する
            }
        }
        error("stream ended before a terminal line (result/error) was received")
    }

    private suspend fun readErrorMessage(response: HttpResponse): String =
        runCatching { json.decodeFromString<ErrorJson>(response.bodyAsText()).error }
            .getOrDefault(response.status.description)

    private suspend fun readResetAt(response: HttpResponse): String =
        runCatching { json.decodeFromString<QuotaExceededJson>(response.bodyAsText()).resetAt }
            .getOrDefault("")
}
