package dev.miyado.shogisupplement.engine

/**
 * サーバー解析が返す失敗の種類。投げるのはサーバー解析の実装だが、
 * 失敗をどう扱うかを決めるのはuse case側（[AnalysisFailureReason]）なのでここに置く。
 */
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

    /** HTTP 426: アプリ版情報のbuildがapp_policy.min_build未満。 */
    class UpgradeRequired(message: String) : RemoteAnalysisException(message)

    /** NDJSON終端の `{"error": ...}` 行（ストリーム途中のエンジン失敗。HTTPは200のまま）。 */
    class EngineFailure(message: String) : RemoteAnalysisException(message)

    /** 再POSTの上限回数に達しても復旧できなかった接続断。 */
    class ConnectionLost(message: String, cause: Throwable? = null) : RemoteAnalysisException(message, cause)
}
