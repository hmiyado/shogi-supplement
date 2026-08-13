package dev.miyado.shogisupplement.engine

/** [RemoteAnalysisException]の各サブタイプは対応させ、その他は[Unknown]に集約する。 */
sealed class AnalysisFailureReason {
    /** HTTP 401: [dev.miyado.shogisupplement.auth.AuthRepository.refreshSession] による自動復旧
     * （[AuthRetryingAnalyzer]）も失敗した後の認可エラー。 */
    data object Unauthorized : AnalysisFailureReason()

    /** HTTP 403: user_bans に登録済み（BAN）。 */
    data object Banned : AnalysisFailureReason()

    /**
     * HTTP 429: 当日クォータ超過。
     * @property resetAt サーバーが返す翌日リセット時刻（ISO-8601 UTC文字列。文字列のまま透過する）
     */
    data class QuotaExceeded(val resetAt: String) : AnalysisFailureReason()

    /** HTTP 400: リクエスト不正（想定外。moves_usiが空など）。 */
    data object BadRequest : AnalysisFailureReason()

    /** HTTP 426: アプリ版情報のbuildがapp_policy.min_build未満。 */
    data object UpgradeRequired : AnalysisFailureReason()

    /** サーバー側エンジン失敗（NDJSON終端の error 行）。 */
    data object EngineFailure : AnalysisFailureReason()

    /** 再POSTの上限回数に達しても復旧できなかった接続断。 */
    data object ConnectionLost : AnalysisFailureReason()

    /** [RemoteAnalysisException] 以外（KIFパース失敗・DB保存失敗・端末エンジン内部エラー等）。 */
    data object Unknown : AnalysisFailureReason()

    companion object {
        /** 例外から失敗理由を判定する。[RemoteAnalysisException] 以外はすべて [Unknown]。 */
        fun from(e: Throwable): AnalysisFailureReason = when (e) {
            is RemoteAnalysisException.Unauthorized -> Unauthorized
            is RemoteAnalysisException.Banned -> Banned
            is RemoteAnalysisException.QuotaExceeded -> QuotaExceeded(e.resetAt)
            is RemoteAnalysisException.BadRequest -> BadRequest
            is RemoteAnalysisException.UpgradeRequired -> UpgradeRequired
            is RemoteAnalysisException.EngineFailure -> EngineFailure
            is RemoteAnalysisException.ConnectionLost -> ConnectionLost
            else -> Unknown
        }
    }
}
