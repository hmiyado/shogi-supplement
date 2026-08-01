package dev.miyado.shogisupplement.ui.report

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.miyado.shogisupplement.board.PieceType
import dev.miyado.shogisupplement.board.ShogiSquare
import dev.miyado.shogisupplement.ui.common.SfenPosition
import dev.miyado.shogisupplement.ui.common.ShogiBoardView

@Composable
internal fun ReportBoardArea(
    studyState: StudyState?,
    currentSfen: String,
    studyCurrentSfen: String?,
    flip: Boolean,
    lastMoveDest: Pair<Int, Int>?,
    studyLastMoveDest: Pair<Int, Int>?,
    clampedPly: Int,
    maxPly: Int,
    viewerMode: ViewerMode,
    selectedIdx: Int?,
    studyOriginAbsolutePly: Int,
    studyOrigin: StudyOrigin,
    onStartStudy: (
        baseSfen: String,
        flip: Boolean,
        originIsBestPv: Boolean,
        originPlyIndex: Int,
        originSelectedIdx: Int?,
        originAbsolutePly: Int,
        origin: StudyOrigin,
        tappedSquare: ShogiSquare?,
        tappedHandPieceType: PieceType?,
    ) -> Unit,
    onStudySquareTapped: (ShogiSquare) -> Unit,
    onStudyHandPieceTapped: (PieceType) -> Unit,
    onPlyIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    ShogiBoardView(
        sfen = studyCurrentSfen ?: currentSfen,
        flip = flip,
        lastMoveDest = if (studyState != null) studyLastMoveDest else lastMoveDest,
        selectedFrom = studyState?.selectedFrom,
        selectedDropType = studyState?.selectedDropType,
        legalDestinations = studyState?.legalDestinations ?: emptySet(),
        onSquareTapped = { sq ->
            if (studyState != null) {
                onStudySquareTapped(sq)
            } else {
                val piece = SfenPosition.parse(currentSfen).boardPieces[sq.file to sq.rank]
                if (piece != null) {
                    // 開始タップのマスを渡し、手番側の駒なら開始と同時に選択する。
                    onStartStudy(
                        currentSfen,
                        flip,
                        viewerMode == ViewerMode.BEST_PV,
                        clampedPly,
                        selectedIdx,
                        studyOriginAbsolutePly,
                        studyOrigin,
                        sq,
                        null,
                    )
                } else {
                    // 駒のないマス: 列位置（flip考慮）で左右半分を近似。
                    val files = if (flip) (1..9).toList() else (9 downTo 1).toList()
                    val visualColIndex = files.indexOf(sq.file)
                    if (visualColIndex <= 4) {
                        if (clampedPly > 0) onPlyIndexChange(clampedPly - 1)
                    } else {
                        if (clampedPly < maxPly) onPlyIndexChange(clampedPly + 1)
                    }
                }
            }
        },
        onHandPieceTapped = { pt ->
            if (studyState != null) {
                onStudyHandPieceTapped(pt)
            } else {
                // 持ち駒タップ: 盤上駒タップと同じ流儀で検討モードを開始し、
                // タップした持ち駒を打ちの選択状態にする（ShogiBoardView が
                // 手番側の持ち駒にしか配線しないため、常に手番側の駒）。
                onStartStudy(
                    currentSfen,
                    flip,
                    viewerMode == ViewerMode.BEST_PV,
                    clampedPly,
                    selectedIdx,
                    studyOriginAbsolutePly,
                    studyOrigin,
                    null,
                    pt,
                )
            }
        },
        modifier = modifier,
    )
}
