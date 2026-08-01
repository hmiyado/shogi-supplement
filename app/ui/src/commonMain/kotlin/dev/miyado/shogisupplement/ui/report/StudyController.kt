package dev.miyado.shogisupplement.ui.report

import dev.miyado.shogisupplement.blunder.BlunderJudge
import dev.miyado.shogisupplement.blunder.PositionEvalDisplay
import dev.miyado.shogisupplement.blunder.Score
import dev.miyado.shogisupplement.board.PieceType
import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.board.ShogiMove
import dev.miyado.shogisupplement.board.ShogiSquare
import dev.miyado.shogisupplement.board.Side
import dev.miyado.shogisupplement.engine.Engine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 検討モードのエンジン解析ノード数。仕様書の指定値。 */
private const val STUDY_ANALYSIS_NODES = 200_000

/**
 * レポート画面の検討モードを担う状態・ロジック。
 *
 * DrillViewModel（judgeWithEngine 注入）・AccountViewModel と同じ「ホストがエンジン生成/
 * 評価値表示単位を注入する」パターンを使う:
 * - [engineFactory]: 検討評価が必要になったとき（「解析」ボタン押下時。オンデマンド）呼ばれ、
 *   以後 [studyEngine] として生かしっぱなしにする。エンジンの起動/破棄ライフサイクルは
 *   呼び出し元＝ホストの責務。
 * - [evalDisplayProvider]: 形勢の表示単位（'cp'/'wp'）を都度取得する。
 *
 * 検討手順は分岐元（baseSfen）ごとに木構造で保持し、「検討終了（endStudy）」では
 * 破棄しない。レポート画面を開いている間は [treesByOrigin] に残り続け、同じ分岐元から
 * 検討を再開すると続きから辿れる（[dispose] で画面ごと破棄されるまで）。
 *
 * ReportViewModel が保持し、[dispose] を自身の onCleared 相当のタイミングで呼ぶ
 * （リーク厳禁: 検討エンジンが生きていれば quit する）。
 *
 * @param scope 状態更新の非同期処理に使うスコープ（呼び出し元の viewModelScope 相当を注入）
 * @param ioDispatcher DB/エンジン処理用ディスパッチャ（テスト時はUnconfinedを注入）
 */
class StudyController(
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val engineFactory: () -> Engine,
    private val evalDisplayProvider: () -> String,
) {

    /** レポート画面の検討モード状態（null = 検討していない）。 */
    private val _studyState = MutableStateFlow<StudyState?>(null)
    val studyState: StateFlow<StudyState?> = _studyState.asStateFlow()

    /** 検討モード中に生かしっぱなしにするエンジン（初回解析で遅延生成、終了/disposeでquit）。 */
    private var studyEngine: Engine? = null

    /** 検討モードの現局面（合法手計算用）。baseSfen + moves を都度適用して保持する。 */
    private var studyBoard: ShogiBoard? = null

    /**
     * 分岐元（baseSfen）ごとの検討木。レポート画面を開いている間（[dispose] まで）保持する。
     *
     * Why not blunderId等で分岐元を識別しないか: 「検討」はどの手からでも開始できる
     * （悪手の手・そうでない手のどちらのタップからも開始可能）ため、局面そのもの（SFEN）を
     * キーにするのが最も自然。同じ SFEN に複数の分岐元（別tab・別選択）から辿り着くことは
     * 実務上ほぼ無い（対局中の同一局面はplyで一意）ため、キー衝突は問題にならない。
     */
    private val treesByOrigin = mutableMapOf<String, StudyTree>()

    /** 検討木のノードID採番（controller生存中で単調増加。木を跨いだ一意性は不要）。 */
    private var nextNodeIdCounter = 1L
    private fun nextNodeId(): Long = nextNodeIdCounter++

    /** オンデマンド解析の実行中フラグ（多重発火防止。連打しても1回分だけ実行する）。 */
    private var studyEvalRunning = false

    /**
     * 検討モードを開始する（レポートビューアで盤上の駒または持ち駒をタップしたときに呼ぶ）。
     *
     * エンジンはここでは生成しない（起動コストを避けるため、実際に「解析」ボタンを押した
     * タイミングで遅延生成する）。
     * 開始タップのマス（tappedSquare）の駒が手番側なら、開始と同時に選択状態にする。
     * 持ち駒タップから開始する場合は tappedHandPieceType を渡す（打ちの選択状態で開始する。
     * tappedSquare とは排他）。
     *
     * 同じ baseSfen で以前に検討木があれば（同じ画面内で「終了」→再開した場合）再利用する。
     * moves は常に空（木のルート）から始める——「終了」直前にどこまで潜っていたかに関わらず、
     * 再開時はいつも分岐元の局面そのものから見せるのが、動作として一番わかりやすいという判断
     * （木は保持するが、表示位置は毎回リセットする。深い場所へは表示中のチップ列で辿り直せる）。
     */
    fun startStudy(
        baseSfen: String,
        flip: Boolean,
        originIsBestPv: Boolean,
        originPlyIndex: Int,
        originSelectedIdx: Int?,
        originAbsolutePly: Int,
        origin: StudyOrigin,
        tappedSquare: ShogiSquare? = null,
        tappedHandPieceType: PieceType? = null,
    ) {
        val board = runCatching { ShogiBoard.fromSfen(baseSfen) }.getOrNull() ?: return
        studyBoard = board
        treesByOrigin.getOrPut(baseSfen) { StudyTree() }
        _studyState.value = buildInitialStudyState(
            baseSfen = baseSfen,
            flip = flip,
            originIsBestPv = originIsBestPv,
            originPlyIndex = originPlyIndex,
            originSelectedIdx = originSelectedIdx,
            originAbsolutePly = originAbsolutePly,
            origin = origin,
            tappedSquare = tappedSquare,
            board = board,
            tappedHandPieceType = tappedHandPieceType,
        )
    }

    /** 検討モードの盤上マスタップ処理（DrillViewModel.onSquareTapped と同型のロジック）。 */
    fun onStudySquareTapped(sq: ShogiSquare) {
        val s = _studyState.value ?: return
        val board = studyBoard ?: return

        val selectedFrom = s.selectedFrom
        when {
            selectedFrom != null -> {
                val legalToHere = board.legalMovesFrom(selectedFrom).filter { it.to == sq }
                when {
                    legalToHere.isEmpty() -> {
                        val piece = board.pieceAt(sq)
                        if (piece != null && piece.side == board.turn) {
                            val dests = board.legalMovesFrom(sq).map { it.to }.toSet()
                            _studyState.value = s.copy(
                                selectedFrom = sq,
                                selectedDropType = null,
                                legalDestinations = dests,
                                showPromoteDialog = false,
                                pendingPromoteMove = null,
                                showTurnHint = false,
                            )
                        } else {
                            _studyState.value = s.copy(
                                selectedFrom = null,
                                selectedDropType = null,
                                legalDestinations = emptySet(),
                                // 手番でない側の駒をタップしたときは手番ヒントを表示する。
                                showTurnHint = piece != null && piece.side != board.turn,
                            )
                        }
                    }
                    legalToHere.size == 1 -> executeStudyMove(legalToHere.first())
                    else -> {
                        val promote = legalToHere.firstOrNull { it.promote } ?: legalToHere.first()
                        _studyState.value = s.copy(showPromoteDialog = true, pendingPromoteMove = promote)
                    }
                }
            }

            s.selectedDropType != null -> {
                val dropMoves = board.legalMoves().filter {
                    it.dropType == s.selectedDropType && it.to == sq
                }
                if (dropMoves.isNotEmpty()) {
                    executeStudyMove(dropMoves.first())
                } else {
                    val piece = board.pieceAt(sq)
                    _studyState.value = s.copy(
                        selectedDropType = null,
                        legalDestinations = emptySet(),
                        // 手番でない側の駒をタップしたときは手番ヒントを表示する。
                        showTurnHint = piece != null && piece.side != board.turn,
                    )
                }
            }

            else -> {
                val piece = board.pieceAt(sq)
                when {
                    piece != null && piece.side == board.turn -> {
                        val dests = board.legalMovesFrom(sq).map { it.to }.toSet()
                        _studyState.value = s.copy(
                            selectedFrom = sq,
                            selectedDropType = null,
                            legalDestinations = dests,
                            showTurnHint = false,
                        )
                    }
                    piece != null -> {
                        // 手番でない側の駒をタップ→ナビ行に「▲番です/△番です」を一時表示。
                        // 次の正常タップで消える（各正常経路が showTurnHint=false に戻す）。
                        _studyState.value = s.copy(showTurnHint = true)
                    }
                    else -> {
                        // 空マスタップ: 表示中の手番ヒントがあれば消すだけ。
                        if (s.showTurnHint) _studyState.value = s.copy(showTurnHint = false)
                    }
                }
            }
        }
    }

    /** 検討モードの持ち駒タップ処理。 */
    fun onStudyHandPieceTapped(pieceType: PieceType) {
        val s = _studyState.value ?: return
        val board = studyBoard ?: return

        if (s.selectedDropType == pieceType) {
            _studyState.value = s.copy(
                selectedDropType = null,
                selectedFrom = null,
                legalDestinations = emptySet(),
                showTurnHint = false,
            )
        } else {
            val dropSquares = board.legalDropSquares(pieceType).toSet()
            _studyState.value = s.copy(
                selectedDropType = pieceType,
                selectedFrom = null,
                legalDestinations = dropSquares,
                showTurnHint = false,
            )
        }
    }

    /** 検討モードの成り選択ダイアログで「成る/成らない」を決定した。 */
    fun onStudyPromoteDecision(promote: Boolean) {
        val s = _studyState.value ?: return
        val pending = s.pendingPromoteMove ?: return
        val board = studyBoard ?: return

        val actualMove = board.legalMovesFrom(pending.from!!)
            .filter { it.to == pending.to }
            .firstOrNull { it.promote == promote }
            ?: pending.copy(promote = promote)

        _studyState.value = s.copy(showPromoteDialog = false, pendingPromoteMove = null)
        executeStudyMove(actualMove)
    }

    fun studyStepBack() {
        val s = _studyState.value ?: return
        if (s.moves.isEmpty()) return
        navigateToDepth(s.moves.size - 1)
    }

    fun studyResetToStart() {
        val s = _studyState.value ?: return
        if (s.moves.isEmpty()) return
        navigateToDepth(0)
    }

    /**
     * 検討パネルの手順チップタップ: 現在ライン上の depth 手目の局面へ移動する
     * （depth = 0 は検討開始局面。studyStepBack/studyResetToStart はこの特殊形）。
     * 現在ラインの範囲外（未来の分岐先など）は無視する——チップ列は常に現在ラインだけを
     * 表示するため、チップタップは「現在ライン内のシーク」に閉じる。
     */
    fun onChipTapped(depth: Int) {
        val s = _studyState.value ?: return
        if (depth !in 0..s.moves.size) return
        navigateToDepth(depth)
    }

    /**
     * 分岐（下向きチェブロン付き）チップタップ: そのチップの depth に兄弟変化があれば、兄弟変化ポップの中身
     * （[StudyState.branchPopupOptions]）を用意して開く。
     */
    fun onBranchChipTapped(depth: Int) {
        val s = _studyState.value ?: return
        if (depth !in s.moves.indices) return
        if (s.branchFlags.getOrNull(depth) != true) return
        val tree = treesByOrigin[s.baseSfen] ?: return
        val siblings = tree.siblingsAtDepth(s.moves, depth)
        val options = siblings.map { node ->
            StudyBranchOption(
                moveUsi = node.moveUsi,
                evalState = node.evalState,
                isCurrent = node.moveUsi == s.moves[depth],
            )
        }
        _studyState.value = s.copy(openBranchPopupDepth = depth, branchPopupOptions = options)
    }

    /** 兄弟変化ポップを閉じる（選ばずに閉じる場合）。 */
    fun onBranchPopupDismiss() {
        _studyState.update { it?.copy(openBranchPopupDepth = null, branchPopupOptions = emptyList()) }
    }

    /**
     * 兄弟変化ポップで別ラインを選ぶ。選んだ兄弟のノードへ切り替えるだけで、
     * その先（選んだ兄弟からさらに指し進めた手）へは自動で潜らない
     * （ポップに出ている評価はその1手だけのものであり、切替直後の表示局面と
     * ポップの情報を一致させるための単純化。深い変化を見たい場合は改めて
     * チップ列から辿る）。
     */
    fun onBranchOptionSelected(depth: Int, moveUsi: String) {
        val s = _studyState.value ?: return
        if (depth !in s.moves.indices) return
        applyMoves(s, s.moves.take(depth) + moveUsi)
    }

    /** 検討モードを終了する（エンジンをquitし、盤面状態を破棄する）。検討木は破棄しない。 */
    fun endStudy() {
        studyEngine?.quit()
        studyEngine = null
        studyBoard = null
        studyEvalRunning = false
        _studyState.value = null
    }

    /**
     * リーク厳禁: 呼び出し元の onCleared 相当のタイミング、または
     * レポート画面を離れるタイミングで呼ぶこと。検討木もここで初めて破棄する
     * （画面を開いている間は保持するため。endStudy では破棄しない）。
     */
    fun dispose() {
        studyEngine?.quit()
        studyEngine = null
        treesByOrigin.clear()
    }

    /**
     * 現在局面をオンデマンドで解析する（「解析 ▶+」ボタン）。moves が空（分岐元そのもの）
     * のときは何もしない——分岐元の形勢はパネル冒頭に既に表示されているため、
     * 同じ局面を改めて解析する意味がない。
     *
     * 自動発火はせず、明示操作でのみ実行するオンデマンド延長パターン。
     */
    fun analyzeCurrentPosition() {
        val s = _studyState.value ?: return
        if (s.moves.isEmpty()) return
        if (studyEvalRunning) return
        studyEvalRunning = true
        _studyState.update { it?.copy(evalState = StudyEvalState.Loading) }

        scope.launch {
            val cur = _studyState.value
            if (cur == null) {
                studyEvalRunning = false
                return@launch
            }
            val evalResult = withContext(ioDispatcher) {
                runCatching {
                    val engine = studyEngine ?: engineFactory().also { studyEngine = it }
                    val pv1 = engine.analyzeSfen(cur.baseSfen, cur.moves, nodes = STUDY_ANALYSIS_NODES)
                        .firstOrNull() ?: error("PV empty")
                    studyEvalLabel(cur.baseSfen, cur.moves, pv1.score, cur.flip)
                }.getOrElse { StudyEvalState.Error }
            }
            studyEvalRunning = false
            val latest = _studyState.value
            // 解析中にチップ操作等で局面が変わっていたら結果を捨てる（stale防止。
            // オンデマンド解析は都度ボタンで明示的に呼ばれるため、
            // 単純に「今の局面と違えば破棄」で十分）。
            if (latest == null || latest.baseSfen != cur.baseSfen || latest.moves != cur.moves) return@launch
            val tree = treesByOrigin[latest.baseSfen] ?: StudyTree()
            treesByOrigin[latest.baseSfen] = tree.withEvalState(latest.moves, evalResult)
            _studyState.update { it?.copy(evalState = evalResult) }
        }
    }

    private fun executeStudyMove(move: ShogiMove) {
        val s = _studyState.value ?: return
        applyMoves(s, s.moves + move.toUsiString())
    }

    /**
     * moves（検討開始局面からの手列）を現在ラインとして適用する共通処理。
     * 木への反映（新規なら分岐として追加・既存なら再利用）→ 盤面再構築 →
     * branchFlags/evalState を木から再取得、の順で行う（chip移動・分岐切替・新手着手の
     * 3経路すべてがこの手順を共有する）。
     */
    private fun applyMoves(s: StudyState, newMoves: List<String>) {
        val addedMove = if (newMoves.size == s.moves.size + 1 && newMoves.dropLast(1) == s.moves) {
            newMoves.last()
        } else {
            null
        }
        var tree = treesByOrigin[s.baseSfen] ?: StudyTree()
        if (addedMove != null) {
            tree = tree.withMovePlayed(s.moves, addedMove, newId = nextNodeId())
            treesByOrigin[s.baseSfen] = tree
        }
        studyBoard = runCatching { ShogiBoard.fromSfen(s.baseSfen) }.getOrNull()?.also { b ->
            newMoves.forEach { m -> runCatching { b.push(ShogiMove.fromUsi(m)) } }
        }
        _studyState.value = s.copy(
            moves = newMoves,
            branchFlags = tree.branchFlags(newMoves),
            openBranchPopupDepth = null,
            branchPopupOptions = emptyList(),
            selectedFrom = null,
            selectedDropType = null,
            legalDestinations = emptySet(),
            showPromoteDialog = false,
            pendingPromoteMove = null,
            evalState = tree.evalStateAt(newMoves),
            showTurnHint = false,
        )
    }

    private fun navigateToDepth(depth: Int) {
        val s = _studyState.value ?: return
        applyMoves(s, s.moves.take(depth))
    }

    /**
     * エンジンPVのスコア（手番側視点）を先手視点に正規化して表示ラベル + 自分視点cpに変換する。
     *
     * PvInfo.score のドキュメント（dev.miyado.shogisupplement.engine.Engine.kt）:
     * 「手番側視点のスコア」。position_eval・BlunderJudge と同じ規約
     * （AnalysisService の positionEvalRows 生成: 手番がgoteなら反転）に合わせ、
     * 検討中の現局面（baseSfen + moves 適用後）の手番が先手なら符号そのまま、
     * 後手なら反転して先手視点にする。
     *
     * userCp は共通のcpエンコード規約（常にcp軸で保持する）に合わせるため、詰みも含めて
     * 同じ方式でエンコードしたうえでユーザー視点に正規化する。
     */
    private fun studyEvalLabel(baseSfen: String, moves: List<String>, score: Score, userIsGote: Boolean): StudyEvalState {
        val board = runCatching { ShogiBoard.fromSfen(baseSfen) }.getOrNull()
            ?: return StudyEvalState.Error
        moves.forEach { m -> runCatching { board.push(ShogiMove.fromUsi(m)) } }
        val moverIsSente = board.turn == Side.BLACK
        // mate_in=0 の勝敗判定用（parity のみ使うダミーply）。
        val syntheticPly = if (moverIsSente) 0 else 1

        val moverCp = BlunderJudge.toCp(score)
        val senteCp = if (moverIsSente) moverCp else -moverCp
        val userCp = if (userIsGote) -senteCp else senteCp

        val label = when (score) {
            is Score.Cp -> {
                PositionEvalDisplay.format(
                    scoreCp = senteCp,
                    mateIn = null,
                    userIsGote = userIsGote,
                    evalDisplay = evalDisplayProvider(),
                    ply = syntheticPly,
                )
            }
            is Score.Mate -> {
                val senteMate = if (moverIsSente) score.plies else -score.plies
                PositionEvalDisplay.format(
                    scoreCp = null,
                    mateIn = senteMate,
                    userIsGote = userIsGote,
                    evalDisplay = evalDisplayProvider(),
                    ply = syntheticPly,
                )
            }
        }
        return label?.let { StudyEvalState.Value(it, userCp = userCp) } ?: StudyEvalState.None
    }
}
