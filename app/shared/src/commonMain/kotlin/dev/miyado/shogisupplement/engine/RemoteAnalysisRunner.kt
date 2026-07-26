package dev.miyado.shogisupplement.engine

import dev.miyado.shogisupplement.blunder.Score
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject

/**
 * `POST /v1/analyses`（サーバー解析ワーカー。app/server/worker）を叩くクライアント。
 * [AnalysisRunner.analyzeGame] と同一シグネチャの [analyzeGame] を持つ。エンジンを端末に同梱しないiOS構成で
 * [AnalysisOrchestrator] の呼び出し側がこれへ差し替えられるようにするための実装で、
 * [AnalysisOrchestrator] 自体はこのクラスの追加だけでは変更しない。
 *
 * リクエスト/レスポンスのJSON形式は app/server/worker の実装
 * （Models.kt・Routes.kt・AnalysisService.kt）に厳密に合わせる。ここでの型定義はその
 * サーバー側モデルの独立した写し（サーバー実装は逆に:sharedへ依存する側なので、
 * :sharedからサーバーモジュールへ依存を張れない。フィールド名・NDJSON行の判別方法が
 * 変わったら両側を一緒に直す必要がある）。
 *
 * @property baseUrl ワーカーのベースURL（例: "https://analysis-worker-xxx.a.run.app"）。
 *   実値をコードに書かず、呼び出し側の設定から注入する
 * @property accessTokenProvider 呼び出しごとにSupabase JWTを取得する関数。
 *   トークンが有効期限切れの場合の更新は呼び出し側（AuthRepository等）の責務とし、
 *   このクラスはトークンの中身を解釈しない
 * @property httpClient 注入可能なHTTPクライアント。既定値はエンジン自動解決
 *   （supabase-kt等の既存コードと同じ方針。androidApp=okhttp/iosMain=darwinが
 *   クラスパス上のエンジンを提供する）。テストはMockEngineベースのクライアントを渡す
 * @property maxRetries 切断時に同一リクエストを再POSTする上限回数（無限リトライにしない）
 * @property retryBackoffMs 再POSTまでの待機時間の基準値（試行回数に比例して線形に伸ばす。
 *   指数バックオフにしないのは、サーバー側の完了待ちが最大280秒のポーリングで
 *   律速されるため、クライアント側で凝ったバックオフを組んでも短縮効果が薄いから）
 */
class RemoteAnalysisRunner(
    private val baseUrl: String,
    private val accessTokenProvider: suspend () -> String,
    private val httpClient: HttpClient = HttpClient(),
    private val maxRetries: Int = 3,
    private val retryBackoffMs: Long = 1_000,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 1局の全局面を解析し、局面ごとの結果を返す。
     *
     * サーバーは moves_hash（moves の内容から算出）で冪等なので、通信切断時は同一の
     * [moves] で再POSTするだけで安全に復旧できる（解析済みなら即返却、実行中なら
     * サーバー側が完了を待って返却する）。切断とみなすのは「ストリームが最終行
     * （result/error）を受け取る前に終わった」場合と、下位のHTTP例外（ネットワーク断・
     * 5xx等）。401/403/429/400や終端error行は再試行しても状況が変わらないため、
     * 型付きの [RemoteAnalysisException] として即座に呼び出し側へ伝える。
     *
     * @param moves 棋譜の USI 手列
     * @param onProgress (done, total) の進捗コールバック。NDJSONのprogress行を中継する
     * @return 局面インデックス順の結果リスト（各要素 = その局面の MultiPV 結果）
     */
    suspend fun analyzeGame(
        moves: List<String>,
        onProgress: ((done: Int, total: Int) -> Unit)? = null,
    ): List<List<PvInfo>> {
        var lastDisconnect: Exception? = null
        val totalAttempts = maxRetries + 1
        repeat(totalAttempts) { attempt ->
            if (attempt > 0) {
                delay(retryBackoffMs * attempt)
            }
            try {
                return requestOnce(moves, onProgress)
            } catch (e: RemoteAnalysisException) {
                // 呼び出し側の判断が要るエラー（認可・クォータ・不正リクエスト・エンジン失敗）は
                // リトライしても直らないので即座に伝播する。
                throw e
            } catch (e: Exception) {
                // ここに来るのはネットワーク断・ストリームが終端行なしで終わった場合のみ。
                // moves_hash冪等のため同一リクエストの再POSTは安全（二重解析にならない）。
                lastDisconnect = e
            }
        }
        throw RemoteAnalysisException.ConnectionLost(
            "サーバー解析への接続が${totalAttempts}回の試行後も回復しませんでした: ${lastDisconnect?.message}",
            lastDisconnect,
        )
    }

    private suspend fun requestOnce(
        moves: List<String>,
        onProgress: ((done: Int, total: Int) -> Unit)?,
    ): List<List<PvInfo>> {
        val token = accessTokenProvider()
        val response = httpClient.post("$baseUrl/v1/analyses") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(RemoteAnalysisRequestJson.serializer(), RemoteAnalysisRequestJson(moves)))
        }

        when (response.status) {
            HttpStatusCode.Unauthorized ->
                throw RemoteAnalysisException.Unauthorized(readErrorMessage(response))
            HttpStatusCode.Forbidden ->
                throw RemoteAnalysisException.Banned
            HttpStatusCode.TooManyRequests ->
                throw RemoteAnalysisException.QuotaExceeded(readResetAt(response))
            HttpStatusCode.BadRequest ->
                throw RemoteAnalysisException.BadRequest(readErrorMessage(response))
            else -> Unit
        }
        if (!response.status.isSuccess()) {
            // 想定外のステータス（5xx等、ワーカーの一時的な不調）は切断と同列に扱い再試行対象にする。
            error("unexpected status ${response.status}")
        }

        return consumeStream(response, onProgress)
    }

    /** NDJSON応答を1行ずつ読み、progress行を中継しつつ最終行(result/error)を待つ。 */
    private suspend fun consumeStream(
        response: HttpResponse,
        onProgress: ((done: Int, total: Int) -> Unit)?,
    ): List<List<PvInfo>> {
        val channel = response.bodyAsChannel()
        while (true) {
            val line = channel.readLine() ?: break
            if (line.isBlank()) continue
            val obj = json.parseToJsonElement(line).jsonObject
            when {
                "result" in obj -> {
                    val resultJson = json.decodeFromJsonElement(RemoteAnalysisResultJson.serializer(), obj)
                    return resultJson.result.map { pvList -> pvList.map { it.toPvInfo() } }
                }
                "error" in obj -> {
                    val errorJson = json.decodeFromJsonElement(RemoteErrorJson.serializer(), obj)
                    throw RemoteAnalysisException.EngineFailure(errorJson.error)
                }
                "progress" in obj -> {
                    val progressJson = json.decodeFromJsonElement(RemoteProgressJson.serializer(), obj)
                    onProgress?.invoke(progressJson.progress, progressJson.total)
                }
                else -> Unit // 未知の行は前方互換のため無視する
            }
        }
        // result/error のどちらの最終行も受け取らずチャネルが閉じた＝ストリーム途中の切断。
        error("stream ended before a terminal line (result/error) was received")
    }

    private suspend fun readErrorMessage(response: HttpResponse): String =
        runCatching { json.decodeFromString<RemoteErrorJson>(response.bodyAsText()).error }
            .getOrDefault(response.status.description)

    private suspend fun readResetAt(response: HttpResponse): String =
        runCatching { json.decodeFromString<RemoteQuotaExceededJson>(response.bodyAsText()).resetAt }
            .getOrDefault("")
}

/**
 * [RemoteAnalysisRunner.analyzeGame] が返す型付きエラー。
 *
 * UIでの文言化はこのクラスの範囲外（呼び出し側の責務）。ここでは呼び出し側が
 * `when`で分岐できることだけを保証する。
 */
sealed class RemoteAnalysisException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** HTTP 401: Supabase JWTが無効・期限切れ。呼び出し側は再認証を促す。 */
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

    /** NDJSON終端の `{"error": ...}` 行（ストリーム途中のエンジン失敗。HTTPは200のまま）。 */
    class EngineFailure(message: String) : RemoteAnalysisException(message)

    /** 再POSTの上限回数に達しても復旧できなかった接続断。 */
    class ConnectionLost(message: String, cause: Throwable? = null) : RemoteAnalysisException(message, cause)
}

/** POST /v1/analyses のリクエストボディ。server/worker の AnalysisRequest と一致させる。 */
@Serializable
private data class RemoteAnalysisRequestJson(@SerialName("moves_usi") val movesUsi: List<String>)

/** server/worker の ScoreJson と一致させる。 */
@Serializable
private data class RemoteScoreJson(val type: String, val value: Int)

private fun RemoteScoreJson.toScore(): Score = when (type) {
    "mate" -> Score.Mate(value)
    else -> Score.Cp(value)
}

/** server/worker の PvInfoJson と一致させる。 */
@Serializable
private data class RemotePvInfoJson(val multipv: Int, val score: RemoteScoreJson, val pv: List<String>, val nodes: Long)

private fun RemotePvInfoJson.toPvInfo(): PvInfo = PvInfo(multipv = multipv, score = score.toScore(), pv = pv, nodes = nodes)

/** NDJSON最終行（正常時）。server/worker の AnalysisResultJson と一致させる。engine_metaは使わないため読み捨てる。 */
@Serializable
private data class RemoteAnalysisResultJson(val result: List<List<RemotePvInfoJson>>)

/** NDJSON進捗行。server/worker の ProgressJson と一致させる。 */
@Serializable
private data class RemoteProgressJson(val progress: Int, val total: Int)

/** NDJSON/JSONエラー行・エラー応答共通。server/worker の ErrorJson と一致させる。 */
@Serializable
private data class RemoteErrorJson(val error: String)

/** 429応答本文。server/worker の QuotaExceededJson と一致させる。 */
@Serializable
private data class RemoteQuotaExceededJson(@SerialName("reset_at") val resetAt: String)
