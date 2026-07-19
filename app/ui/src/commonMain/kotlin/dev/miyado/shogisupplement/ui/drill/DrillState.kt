package dev.miyado.shogisupplement.ui.drill

import dev.miyado.shogisupplement.board.PieceType
import dev.miyado.shogisupplement.board.ShogiMove
import dev.miyado.shogisupplement.board.ShogiSquare
import dev.miyado.shogisupplement.db.BlunderRecord
import dev.miyado.shogisupplement.drill.DrillJudge

/**
 * ドリル画面の UI 状態。
 *
 * lifecycle 依存のないプレーンな Kotlin 型。
 */
sealed class DrillUiState {
    /** 候補読み込み中。 */
    object Loading : DrillUiState()

    /** ドリル出題対象がない（◎/○ がゼロ件）。 */
    object NoCandidates : DrillUiState()

    /** 出題中。ユーザーが盤面で手を選ぶ状態。 */
    data class Question(
        val blunder: BlunderRecord,
        /** 現在表示中の SFEN（出題局面）。 */
        val sfenCurrent: String,
        /** 選択中の盤上マス（駒を選んだ場合）。 */
        val selectedFrom: ShogiSquare? = null,
        /** 選択中の持ち駒種（持ち駒を選んだ場合）。 */
        val selectedDropType: PieceType? = null,
        /** ハイライトすべき合法目的マス。 */
        val legalDestinations: Set<ShogiSquare> = emptySet(),
        /** 成り選択ダイアログを表示中かどうか。 */
        val showPromoteDialog: Boolean = false,
        /** 成り選択待ちの手（ダイアログ表示中のみセット）。 */
        val pendingPromoteMove: ShogiMove? = null,
        /** この問題のこれまでの解答回数（周回情報表示用）。 */
        val attemptCount: Int = 0,
        /** 出題候補の総問題数（周回情報表示用）。 */
        val totalCandidates: Int = 0,
        /** 盤を180度反転するか（出題元の局で自分が後手の場合）。 */
        val flip: Boolean = false,
    ) : DrillUiState()

    /** エンジン判定中。 */
    object Judging : DrillUiState()

    /** 判定結果を表示中。 */
    data class Result(
        val drillResult: DrillJudge.DrillResult,
        val blunder: BlunderRecord,
        /** 出題局面の SFEN（結果画面でも盤を表示するため保持する）。 */
        val sfenBefore: String,
        /**
         * 出題時の flip 設定（後手視点フラグ）。 = game.userSide == "gote"。
         * DrillScreen のナビラベル形勢サフィックス（evalSuffixText）計算で userIsGote
         * としても再利用する（MainActivity.kt の BEST_PV 形勢行と同じ定義のため）。
         */
        val flip: Boolean = false,
    ) : DrillUiState()
}
