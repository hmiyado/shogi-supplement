package dev.miyado.shogisupplement.ui.report

import dev.miyado.shogisupplement.blunder.PositionEvalDisplay
import dev.miyado.shogisupplement.board.PieceType
import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.board.ShogiMove
import dev.miyado.shogisupplement.board.ShogiSquare


/**
 * 検討モードの局面評価状態。
 *
 * Error は表示上「（—）」の固定プレースホルダーである（詳細メッセージは表示しない）。
 * message フィールドは持たない（PvExtState.Error と同様）。
 */
sealed class StudyEvalState {
    object None : StudyEvalState()

    /** ローカルエンジンを自動発火できないため、復旧を待っている状態。 */
    object Preparing : StudyEvalState()
    object Loading : StudyEvalState()

    /**
     * @param label 表示用ラベル。
     * @param userCp 自分視点のcp。差は非線形な勝率軸でなくcp軸で計算する。
     * @param bestMoveText PV先頭手の棋譜表記。PVが空または整形失敗ならnull。
     */
    data class Value(
        val label: PositionEvalDisplay.EvalLabel,
        val userCp: Int? = null,
        val bestMoveText: String? = null,
    ) : StudyEvalState()
    object Error : StudyEvalState()
}

/**
 * 検討開始時に固定する分岐元情報。
 * @param label 表示用ラベル。
 * @param userCp 自分視点のcp。不明ならnullとして差を表示しない。
 */
data class StudyOrigin(val label: String, val userCp: Int?)

data class StudyBranchOption(
    val moveUsi: String,
    val evalState: StudyEvalState,
    val isCurrent: Boolean,
)

/** 検討モードの状態。 @param baseSfen 開始局面。 @param moves 現在局面までのUSI手列。 @param displayLine 表示用のUSI手列。 @param chipEvalStates チップ用評価結果。 @param origin 分岐元情報。 @param branchFlags 兄弟変化の有無。 @param openBranchPopupDepth 開いたポップアップの深さ。 @param branchPopupOptions 兄弟変化一覧。 @param originIsBestPv 開始タブ。 @param originPlyIndex 開始手数。 @param originSelectedIdx 開始時の悪手インデックス。 @param originAbsolutePly 本譜上の開始手数。 @param flip 盤面の反転状態。 */
data class StudyState(
    val baseSfen: String,
    val moves: List<String> = emptyList(),
    val displayLine: List<String> = emptyList(),
    val chipEvalStates: List<StudyEvalState> = emptyList(),
    val origin: StudyOrigin,
    val branchFlags: List<Boolean> = emptyList(),
    val openBranchPopupDepth: Int? = null,
    val branchPopupOptions: List<StudyBranchOption> = emptyList(),
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
    val showTurnHint: Boolean = false,
)

/**
 * タップ位置から初期StudyStateを構築する。持ち駒指定時は盤上の判定を行わない。
 * 手番側の駒なら選択状態で開始し、それ以外は未選択で開始する。
 * テストからも呼べる公開の純粋ロジックとして定義する。
 */
fun buildInitialStudyState(
    baseSfen: String,
    flip: Boolean,
    originIsBestPv: Boolean,
    originPlyIndex: Int,
    originSelectedIdx: Int?,
    originAbsolutePly: Int,
    origin: StudyOrigin,
    tappedSquare: ShogiSquare?,
    board: ShogiBoard,
    tappedHandPieceType: PieceType? = null,
): StudyState {
    if (tappedHandPieceType != null) {
        return StudyState(
            baseSfen = baseSfen,
            origin = origin,
            originIsBestPv = originIsBestPv,
            originPlyIndex = originPlyIndex,
            originSelectedIdx = originSelectedIdx,
            originAbsolutePly = originAbsolutePly,
            flip = flip,
            selectedDropType = tappedHandPieceType,
            legalDestinations = board.legalDropSquares(tappedHandPieceType).toSet(),
        )
    }
    val piece = tappedSquare?.let { board.pieceAt(it) }
    val selectable = piece != null && piece.side == board.turn
    return StudyState(
        baseSfen = baseSfen,
        origin = origin,
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
        // 手番外の駒では未選択で開始し、手番ヒントを表示する。
        showTurnHint = piece != null && piece.side != board.turn,
    )
}

internal enum class ViewerMode { MAINLINE, BEST_PV }

/** レポート画面下部の表示モード。グラフと悪手一覧は領域確保のため排他表示する。 */
internal enum class ReportBodyMode { SUMMARY, LIST }
