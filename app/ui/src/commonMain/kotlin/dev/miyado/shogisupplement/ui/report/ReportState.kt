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

    /**
     * ローカルエンジンが使える見込みが無く、自動発火を保留している。[Loading] とは
     * 文言・見た目を変える（バッチ解析の「解析中」・WASM解析中とユーザーが混同しないため）。
     */
    object Preparing : StudyEvalState()
    object Loading : StudyEvalState()

    /**
     * @param label 表示用ラベル（cp/wp・詰みの表記差を吸収済み）
     * @param userCp 自分視点の cp（詰みは ±(30000-|n|) にエンコード）。分岐元との「差」を
     *   計算する分子/分母として使う。表示単位（cp/wp）に関わらず常にcp軸で保持する
     *   （差の計算をcp軸に統一するため。Why not wp軸で差を出す: 勝率は非線形なため
     *   「差」の直感的な意味が薄れる。cp軸の差の方が一貫して解釈しやすい）。
     * @param bestMoveText PVの先頭手の棋譜表記（例:「▲2六歩」）。PVが空・整形失敗なら null
     *   （数値のみ表示する）。
     */
    data class Value(
        val label: PositionEvalDisplay.EvalLabel,
        val userCp: Int? = null,
        val bestMoveText: String? = null,
    ) : StudyEvalState()
    object Error : StudyEvalState()
}

/**
 * 検討の分岐元情報（検討開始時に一度だけ計算し、以後不変）。
 *
 * @param label 表示用ラベル（例:「42手目 ▲３四飛（−320）」。形勢が無ければ「（−320）」部分は無い）
 * @param userCp 自分視点の cp（[StudyEvalState.Value.userCp] と同じ規約）。検討局面との
 *   「差」計算に使う。分岐元の形勢が不明（position_eval未保存等）なら null（差は表示しない）
 */
data class StudyOrigin(val label: String, val userCp: Int?)

data class StudyBranchOption(
    val moveUsi: String,
    val evalState: StudyEvalState,
    /** 現在表示しているラインがこの兄弟を通っているか（「（いま）」表示に使う）。 */
    val isCurrent: Boolean,
)

/**
 * レポート画面の検討モード状態。
 *
 * 検討手順は木構造で保持し、レポート画面を開いている間は「終了」しても
 * 破棄しない（同じ分岐元から検討を再開すると続きから辿れる）。
 * レポート画面を離れたら破棄する（永続化はしない）。
 *
 * @param baseSfen 検討開始局面の SFEN
 * @param moves 検討開始局面からの実際の現在局面までの手列（USI）。空 = 検討開始局面そのもの。
 *   盤面・合法手判定はこの手列を基準にする
 * @param displayLine チップ列に表示する手列（USI）。moves は常にこの先頭部分（prefix）——
 *   1手戻っても displayLine は縮めず、先の手のチップを淡色（ink3）で表示し続ける
 *   （実機確認: 「戻ると先が消えてしまう」との指摘）。指し直しで分岐に入ったとき
 *   （＝moves の続きが displayLine と食い違う、または displayLine の先端を超えて指したとき）は
 *   displayLine を新しい moves に置き換える（旧変化は木構造側には残ったまま）
 * @param chipEvalStates displayLine と同じ長さ。各手を指した後の局面の解析結果
 *   （チップへの評価値併記「△５六飛(-1298)」に使う）
 * @param origin 分岐元の表示情報（検討開始時に固定）
 * @param branchFlags displayLine と同じ長さ。各手が兄弟変化を持つか（チップの下向きチェブロン表示に使う）
 * @param openBranchPopupDepth 分岐チップタップで開いている兄弟変化ポップの深さ（displayLineのインデックス）。
 *   null なら閉じている
 * @param branchPopupOptions openBranchPopupDepth のポップの中身（兄弟変化一覧）
 * @param originIsBestPv 検討開始時に選択していたタブ（最善の変化タブなら true）。終了時の復帰に使う
 * @param originPlyIndex 検討開始時の（元タブ内での）plyIndex。終了時の復帰に使う
 * @param originSelectedIdx 検討開始時に選択していた悪手インデックス。終了時の復帰に使う
 * @param originAbsolutePly 検討開始局面の本譜上の絶対手数（0 = 開始局面）。バナー表示用
 * @param flip 盤の反転（= game.userSide == "gote"）。評価値表示の視点正規化にも使う
 */
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
    /** 手番でない側の駒をタップした直後だけ true（ナビ行末尾に「▲番です/△番です」を表示）。 */
    val showTurnHint: Boolean = false,
)

/**
 * 検討開始時の初期 StudyState を構築する（開始タップのマスまたは持ち駒種別を受け取る）。
 *
 * tappedSquare の駒が手番側なら、検討開始と同時にその駒を選択状態にする
 * （selectedFrom + legalDestinations 設定）。手番側でなければ選択なしで開始する。
 *
 * tappedHandPieceType が渡されたとき（持ち駒タップからの開始）は tappedSquare 側の判定を
 * 行わず、その駒種別を打ちの選択状態にする（ShogiBoardView の持ち駒タップは手番側の
 * 持ち駒にしか配線されないため、盤上駒タップの「手番でない側」判定に相当するものは無い）。
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
        // 開始タップが手番でない側の駒だった場合も、選択なし開始＋手番ヒントで理由を伝える。
        showTurnHint = piece != null && piece.side != board.turn,
    )
}

/** 棋譜ビューア モード（ReportScreen 内部の本譜/最善の変化タブ切替）。 */
internal enum class ViewerMode { MAINLINE, BEST_PV }

/**
 * レポート画面下部の表示モード（ReportScreen 内部）。
 * SUMMARY = グラフ＋悪手サマリー（既定）。LIST = 悪手一覧（既存のカードリスト）。
 * 悪手一覧はグラフ＋サマリーより表示エリアを要するため排他表示にする
 * （常時表示だと一覧の可視領域が狭くなりすぎるため。miyadoさん実機確認での指摘）。
 */
internal enum class ReportBodyMode { SUMMARY, LIST }
