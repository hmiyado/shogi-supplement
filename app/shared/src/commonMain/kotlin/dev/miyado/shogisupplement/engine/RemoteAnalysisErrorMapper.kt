package dev.miyado.shogisupplement.engine

import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.util.formatResetAtJst

/**
 * [RemoteAnalysisException] を日本語メッセージへマッピングする。
 * [dev.miyado.shogisupplement.auth.AuthErrorMapper]（匿名認証エラー）と同じ
 * 「型付き例外→日本語」の役割分担。[AnalysisOrchestrator.Outcome.Failed.message] の生成、
 * iOSのエラーダイアログ表示の両方で使う。
 *
 * 401 (Unauthorized) はここに来る時点で [AuthRetryingAnalyzer] によるセッション再取得も
 * 尽きた後、つまり「自動復旧を試みても解決しなかった」状態を指す。
 */
object RemoteAnalysisErrorMapper {
    fun map(e: RemoteAnalysisException): String = when (e) {
        // 401の理由はワーカーのエラー本文で区別する。App Check起因（トークン欠落・無効）は
        // セッションと無関係で、リトライやセッション再取得では直らないため専用文言にする
        is RemoteAnalysisException.Unauthorized ->
            if (e.message?.contains("app check") == true) {
                AppStrings.SERVER_ANALYSIS_ERROR_APP_CHECK
            } else {
                AppStrings.SERVER_ANALYSIS_ERROR_UNAUTHORIZED
            }
        is RemoteAnalysisException.Banned -> AppStrings.SERVER_ANALYSIS_ERROR_BANNED
        is RemoteAnalysisException.QuotaExceeded ->
            AppStrings.serverAnalysisErrorQuotaExceeded(formatResetAtJst(e.resetAt))
        is RemoteAnalysisException.BadRequest -> AppStrings.SERVER_ANALYSIS_ERROR_BAD_REQUEST
        is RemoteAnalysisException.EngineFailure -> AppStrings.SERVER_ANALYSIS_ERROR_ENGINE_FAILURE
        is RemoteAnalysisException.ConnectionLost -> AppStrings.SERVER_ANALYSIS_ERROR_CONNECTION_LOST
    }
}
