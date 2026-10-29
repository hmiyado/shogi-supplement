package dev.miyado.shogisupplement.engine

import org.jetbrains.compose.resources.getString
import dev.miyado.shogisupplement.ui.generated.resources.*
import dev.miyado.shogisupplement.util.formatResetAtJst

object RemoteAnalysisErrorMapper {
    suspend fun map(e: RemoteAnalysisException): String = when (e) {
        // 401の理由はワーカーのエラー本文で区別する。App Check起因（トークン欠落・無効）は
        // セッションと無関係で、リトライやセッション再取得では直らないため専用文言にする
        is RemoteAnalysisException.Unauthorized ->
            if (e.message?.contains("app check") == true) {
                getString(Res.string.server_analysis_error_app_check)
            } else {
                getString(Res.string.server_analysis_error_unauthorized)
            }
        is RemoteAnalysisException.Banned -> getString(Res.string.server_analysis_error_banned)
        is RemoteAnalysisException.QuotaExceeded ->
            getString(Res.string.server_analysis_error_quota, formatResetAtJst(e.resetAt))
        is RemoteAnalysisException.BadRequest -> getString(Res.string.server_analysis_error_bad_request)
        // Why not専用文言を新設: 「アップデートが必要です」画面の本文としてdocs/wording.md
        // （「強制アップデート画面」節）で既に確定済みの文言をそのまま流用する。新しい表現を
        // 増やすと表記揺れのもとになるため。
        is RemoteAnalysisException.UpgradeRequired -> getString(Res.string.force_update_body)
        is RemoteAnalysisException.EngineFailure -> getString(Res.string.server_analysis_error_engine_failure)
        is RemoteAnalysisException.ConnectionLost -> getString(Res.string.server_analysis_error_connection_lost)
    }
}

suspend fun AnalysisOrchestrator.Outcome.Failed.displayMessage(): String =
    remoteError?.let { RemoteAnalysisErrorMapper.map(it) }
        ?: message.ifEmpty { getString(Res.string.unknown_error) }
