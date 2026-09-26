package dev.miyado.shogisupplement.webApp

import dev.miyado.shogisupplement.navigation.AppDestination

import dev.miyado.shogisupplement.webApp.report.ParsedInput
import dev.miyado.shogisupplement.webApp.report.WebReportData

/**
 * ページ全体の表示状態。null許容フィールドは「未確定」を表す:
 * - assetsAvailable: null=確認中、true/false=確認済み
 * - pendingSideSelection: null=側選択ダイアログ非表示、非null=パース済みでダイアログ表示中
 *   （解析はまだ開始していない。ダイアログの確定/キャンセルまでこの値を保持する）
 * - report: null=解析前/解析中、非null=結果表示中（ReportScreenへ切り替える）
 */
data class KentoUiState(
    val restoredReport: Boolean = false,
    val assetsAvailable: Boolean? = null,
    val kifText: String = "",
    val inputError: String? = null,
    val reanalysisError: String? = null,
    val pendingSideSelection: ParsedInput? = null,
    val analyzing: Boolean = false,
    val progressDone: Int = 0,
    val progressTotal: Int = 0,
    val report: WebReportData? = null,
    val analysisRequestId: String? = null,
    val savedReportAvailable: Boolean = false,
    val savedGames: List<dev.miyado.shogisupplement.db.GameRecord>? = null,
    val confirmDiscardStudy: Boolean = false,
) {
    val destination: AppDestination
        get() = when {
            analyzing -> AppDestination.ANALYZING
            report != null -> AppDestination.REPORT
            savedGames != null -> AppDestination.KENTO_LIBRARY
            else -> AppDestination.KENTO_INPUT
        }
}
