package dev.miyado.shogisupplement.ui.report

import dev.miyado.shogisupplement.db.BlunderRecord
import dev.miyado.shogisupplement.db.GameRecord
import dev.miyado.shogisupplement.db.PositionEvalRow

/**
 * レポート画面が描くデータ。
 * Why not 形勢の表示単位を持つ: 読み込み時点で固定すると、設定の変更に追随できなくなる。
 */
data class ReportScreenState(
    val game: GameRecord,
    val reports: List<BlunderRecord>,
    val flip: Boolean = false,
    val strengthDisplayText: String? = null,
    val positionEvals: List<PositionEvalRow> = emptyList(),
    /** エンジン一致率の値表示（例:「62%(31/50)」）。null = 非表示。 */
    val matchRateDisplayText: String? = null,
    /** 悪手率の値表示（例:「12%(3/25)」）。一致率と同じ分母nを使う。null = 非表示。 */
    val blunderRateDisplayText: String? = null,
)

/** 読み込み結果を画面の状態へ畳む。棋譜が見つからなければnull。 */
fun ReportViewModel.ReportResult.toScreenState(): ReportScreenState? {
    val loadedGame = game ?: return null
    return ReportScreenState(
        game = loadedGame,
        reports = reports,
        flip = flip,
        strengthDisplayText = strengthText,
        positionEvals = positionEvals,
        matchRateDisplayText = matchRateText,
        blunderRateDisplayText = blunderRateText,
    )
}
