package dev.miyado.shogisupplement.ui.report

import dev.miyado.shogisupplement.blunder.PositionEvalDisplay
import dev.miyado.shogisupplement.board.PieceType
import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.board.ShogiMove
import dev.miyado.shogisupplement.board.ShogiSquare

/**
 * ReportScreen（レポートビューア）が使う UI 状態型。
 *
 * lifecycle 依存のないプレーンな Kotlin 型。
 *
 * 読み筋オンデマンド延長の状態（PvExtState）は DrillViewModel と共用のため
 * ui.common（PvExtensionRunner.kt）にある。
 */

/**
 * 検討モードの局面評価状態。
 *
 * Error は表示上「（—）」の固定プレースホルダーである（詳細メッセージは表示しない）。
 * message フィールドは持たない（PvExtState.Error と同様）。
 */
sealed class StudyEvalState {
    object None : StudyEvalState()
    object Loading : StudyEvalState()
    data class Value(val label: PositionEvalDisplay.EvalLabel) : StudyEvalState()
    object Error : StudyEvalState()
}

/**
 * レポート画面の検討モード状態。
 *
 * v1は検討手順を保存しない（画面離脱・「終了」で揮発する）。
 *
 * @param baseSfen 検討開始局面の SFEN
 * @param moves 検討開始局面からの手列（USI）。空 = 検討開始局面そのもの
 * @param originIsBestPv 検討開始時に選択していたタブ（最善の変化タブなら true）。終了時の復帰に使う
 * @param originPlyIndex 検討開始時の（元タブ内での）plyIndex。終了時の復帰に使う
 * @param originSelectedIdx 検討開始時に選択していた悪手インデックス。終了時の復帰に使う
 * @param originAbsolutePly 検討開始局面の本譜上の絶対手数（0 = 開始局面）。バナー表示用
 * @param flip 盤の反転（= game.userSide == "gote"）。評価値表示の視点正規化にも使う
 */
data class StudyState(
    val baseSfen: String,
    val moves: List<String> = emptyList(),
    val originIsBestPv: Boolean,
    val originPlyIndex: Int,
    val originSelectedIdx: Int?,
    val originAbsolutePly: Int,
    val flip: Boolean,
    val selectedFrom: ShogiSquare? = null,
    val selectedDropType: PieceType? = null,
    val legalDestinations: Set<ShogiSquare> = emptySet(),
    val showPromoteDialog: Boolean = false,
    val pendingPromoteMove: ShogiMove? = null,
    val evalState: StudyEvalState = StudyEvalState.None,
    /** 手番でない側の駒をタップした直後だけ true（ナビ行末尾に「▲番です/△番です」を表示）。 */
    val showTurnHint: Boolean = false,
)

/**
 * 検討開始時の初期 StudyState を構築する（開始タップのマスも受け取る）。
 *
 * tappedSquare の駒が手番側なら、検討開始と同時にその駒を選択状態にする
 * （selectedFrom + legalDestinations 設定）。手番側でなければ選択なしで開始する。
 *
 * MainViewModel.startStudy から使う純粋ロジック（Robolectric テストから直接呼べるよう分離）。
 * ReportScreenStudyInteractionTest（androidApp側）から別モジュール越しに呼ぶため、
 * visibility は internal ではなく public にしている。
 */
fun buildInitialStudyState(
    baseSfen: String,
    flip: Boolean,
    originIsBestPv: Boolean,
    originPlyIndex: Int,
    originSelectedIdx: Int?,
    originAbsolutePly: Int,
    tappedSquare: ShogiSquare?,
    board: ShogiBoard,
): StudyState {
    val piece = tappedSquare?.let { board.pieceAt(it) }
    val selectable = piece != null && piece.side == board.turn
    return StudyState(
        baseSfen = baseSfen,
        originIsBestPv = originIsBestPv,
        originPlyIndex = originPlyIndex,
        originSelectedIdx = originSelectedIdx,
        originAbsolutePly = originAbsolutePly,
        flip = flip,
        selectedFrom = if (selectable) tappedSquare else null,
        legalDestinations = if (selectable) {
            board.legalMovesFrom(tappedSquare).map { it.to }.toSet()
        } else {
            emptySet()
        },
        // 開始タップが手番でない側の駒だった場合も、選択なし開始＋手番ヒントで理由を伝える。
        showTurnHint = piece != null && piece.side != board.turn,
    )
}

/** 棋譜ビューア モード（ReportScreen 内部の本譜/最善の変化タブ切替）。 */
internal enum class ViewerMode { MAINLINE, BEST_PV }
