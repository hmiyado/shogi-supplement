package dev.miyado.shogisupplement.engine

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
 * `POST /v1/analyses`（app/server/worker）を叩く [GameAnalyzer] 実装。
 *
 * リクエスト/レスポンスのJSON形式は [dev.miyado.shogisupplement.api.analysis] のDTOをサーバーと直接
 * 共有する（フィールド名を手作業で突き合わせる必要はない）。
 *
 * @property baseUrl ワーカーのベースURL
 * @property accessTokenProvider 呼び出しごとにSupabase JWTを取得する関数。トークン更新は
 *   呼び出し側の責務
 * @property platform 強制アップデート検証用のX-App-Platformヘッダ値（"android"/"ios"。
 *   app_policyテーブルのplatform列と同じ語彙。[dev.miyado.shogisupplement.supabase.SupabaseServices]
 *   のplatformパラメータと同じく呼び出し側が明示する）。ビルド番号は[currentBuildNumber]
 *   （expect/actual）でこのクラス自身が解決するため引数に取らない。
 * @property maxRetries 切断時に同一リクエストを再POSTする上限回数
 * @property retryBackoffMs 再POSTまでの待機時間の基準値（試行回数に比例。指数バックオフに
 *   しないのは、サーバー側の完了待ちが最大280秒のポーリングで律速されるため）
 * @property appCheckTokenProvider Firebase App Checkトークンを取得する関数。iOS側は
 *   `AppCheckTokenBridge::getToken`（:ui iosMain）を渡す（IosMainController/DrillDemoFactory
 *   参照）。nullのまま、またはトークン取得が失敗（例外/null）した場合はヘッダ自体を
 *   付けない＝サーバー側の段階導入（FIREBASE_PROJECT_NUMBER未設定）と同じく検証は素通りになる。
 *   ここで例外を握りつぶさないのは意図的: 呼び出し側（SDK組み込み後）が失敗を検知できるよう、
 *   nullを返す/返さないの判断自体は呼び出し側の関数の責務に留める。
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
     * サーバーは moves_hash で冪等なので、通信切断時は同一の [moves] で再POSTするだけで
     * 安全に復旧できる。切断とみなすのはストリームが最終行(result/error)を受け取る前に
     * 終わった場合とHTTP例外のみ。401/403/429/400・終端error行は再試行せず
     * [RemoteAnalysisException] として即座に伝える。
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

    /**
     * ドリルの二次判定（曖昧領域）向けの単発局面解析。サーバー側は sfen+moves モード
     * （EngineInput.Position）で処理し、1局面ぶんのMultiPV結果だけを返す。
     * リトライ・冪等（moves_hash）の仕組みは [analyzeGame] と共通（[executeWithRetry] 参照）。
     *
     * @param sfen  出発局面のSFEN
     * @param moves sfen 後にさらに進める USI 手列（省略可）
     */
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
                header("X-Firebase-AppCheck", appCheckToken)
            }
            // Why not appCheckTokenのように取得失敗時だけ省く: 欠如時はサーバー側が
            // fail-open/1.0クライアント互換としてスキップするだけなので、条件付きにする理由が無い。
            header("X-App-Platform", platform)
            header("X-App-Build", currentBuildNumber().toString())
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
                HttpStatusCode.BadRequest ->
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
        // position行で既に発火済みのplyを覚えておく（並列ワーカーの完了順で届くため
        // ply順とは限らない）。最終result行のforEachIndexedで同じplyを再度発火すると、
        // 呼び出し側から見て「局面ごとに1回」の前提が崩れる。
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

// [RemoteAnalysisRunner.analyzeGame] が返す型付きエラー。UIでの文言化は呼び出し側の責務。
sealed class RemoteAnalysisException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** HTTP 401: Supabase JWTが無効・期限切れ。 */
    class Unauthorized(message: String) : RemoteAnalysisException(message)

    /** HTTP 403: user_bansに登録済み（BAN）。 */
    data object Banned : RemoteAnalysisException("banned")

    /**
     * HTTP 429: 当日クォータ超過。
     * @property resetAt サーバーが返す翌日リセット時刻（ISO-8601・JST日境界。文字列のまま透過する）
     */
    class QuotaExceeded(val resetAt: String) : RemoteAnalysisException("quota exceeded (reset_at=$resetAt)")

    /** HTTP 400: リクエスト不正（想定外。moves_usiが空など）。 */
    class BadRequest(message: String) : RemoteAnalysisException(message)

    /** HTTP 426: X-App-Buildがapp_policy.min_build未満（Worker側の強制アップデート検証）。 */
    class UpgradeRequired(message: String) : RemoteAnalysisException(message)

    /** NDJSON終端の `{"error": ...}` 行（ストリーム途中のエンジン失敗。HTTPは200のまま）。 */
    class EngineFailure(message: String) : RemoteAnalysisException(message)

    /** 再POSTの上限回数に達しても復旧できなかった接続断。 */
    class ConnectionLost(message: String, cause: Throwable? = null) : RemoteAnalysisException(message, cause)
}
