package dev.miyado.shogisupplement.webApp

import dev.miyado.shogisupplement.webApp.report.WebReportData

/**
 * ページ全体の表示状態。null許容フィールドは「未確定」を表す:
 * - assetsAvailable: null=確認中、true/false=確認済み
 * - report: null=解析前/解析中、非null=結果表示中（ReportScreenへ切り替える）
 */
data class KentoUiState(
    val assetsAvailable: Boolean? = null,
    val kifText: String = "",
    val inputError: String? = null,
    val analyzing: Boolean = false,
    val progressDone: Int = 0,
    val progressTotal: Int = 0,
    val report: WebReportData? = null,
)
