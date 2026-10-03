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
import dev.miyado.shogisupplement.engine.EngineInvariants
import dev.miyado.shogisupplement.engine.PvInfo
import dev.miyado.shogisupplement.engine.StudyEngine
import dev.miyado.shogisupplement.notation.JapaneseNotation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val STUDY_LOCAL_ENGINE_POLL_INTERVAL_MS = 2_000L

/**
 * Why not 準備完了まで無期限に待たない理由: 資産が壊れていると準備完了が永久に来ないため、
 * 待ちに上限を設けて失敗として確定させる。
 */
private const val STUDY_LOCAL_ENGINE_PREPARE_TIMEOUT_MS = 30_000L

/**
 * 検討木は分岐元ごとに [dispose] まで保持し、[endStudy] では破棄しない。
 */
class StudyController(
    private val scope: CoroutineScope,
    private val studyEngineFactory: suspend () -> StudyEngine,
    private val evalDisplayProvider: () -> String,
    private val localEngineLikelyAvailable: () -> Boolean = { true },
    private val onTreeChanged: () -> Unit = {},
) {

    private val _studyState = MutableStateFlow<StudyState?>(null)
    val studyState: StateFlow<StudyState?> = _studyState.asStateFlow()

    private var saveFailed = false
    private var studyEngine: StudyEngine? = null

    private var studyBoard: ShogiBoard? = null
    private var protectedMainline: List<String>? = null

    /**
     * Why not blunderId で識別しない理由: 検討は任意の手から開始できるため。
     * 同一 SFEN は対局内で ply が一意なので、局面をキーにする。
     */
    private val treesByOrigin = mutableMapOf<String, StudyTree>()

    private var nextNodeIdCounter = Int.MAX_VALUE.toLong() + 1L
    private fun nextNodeId(): Long = nextNodeIdCounter++

    /** 自動発火と手動再試行をまたぐ単一実行ガード。 */
    private var studyEvalRunning = false

    /**
     * 局面変更時にキャンセルする。再判定対象の局面ごとに同時に生きるループは1本だけ。
     */
    private var pollJob: Job? = null

    /** 再開時も既存の木を再利用するが、表示局面は常にルートから始める。 */
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
        initialTree: StudyTree? = null,
        protectedMoves: List<String>? = null,
    ) {
        val board = runCatching { ShogiBoard.fromSfen(baseSfen) }.getOrNull() ?: return
        studyBoard = board
        protectedMainline = protectedMoves
        if (initialTree != null) {
            treesByOrigin[baseSfen] = initialTree
            fun maxId(nodes: List<StudyNode>): Long = nodes.maxOfOrNull { maxOf(it.id, maxId(it.children)) } ?: 0L
            nextNodeIdCounter = maxOf(nextNodeIdCounter, maxId(initialTree.rootChildren) + 1L)
        } else {
            treesByOrigin.getOrPut(baseSfen) { StudyTree() }
        }
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
        ).copy(
            saveFailed = saveFailed,
            evalState = treesByOrigin.getValue(baseSfen).rootEvalState,
            displayLine = treesByOrigin.getValue(baseSfen).continuation(emptyList()),
        )
        val initialState = requireNotNull(_studyState.value)
        val display = initialState.displayLine
        _studyState.value = initialState.copy(branchFlags = treesByOrigin.getValue(baseSfen).branchFlags(display),
            chipEvalStates = treesByOrigin.getValue(baseSfen).evalStatesAlong(display))
    }

    fun currentTree(): StudyTree? = _studyState.value?.let { treesByOrigin[it.baseSfen] }
    fun treeAtOrigin(baseSfen: String): StudyTree? = treesByOrigin[baseSfen]

    fun setSaveFailed(failed: Boolean) {
        saveFailed = failed
        _studyState.update { it?.copy(saveFailed = failed) }
    }

    fun deleteBranchForPosition(baseSfen: String, moves: List<String>, nodeId: Long? = null): Boolean {
        val current = _studyState.value ?: return false
        if (nodeId != null && current.nodeId != nodeId) return false
        if (current.baseSfen != baseSfen || current.moves != moves || !current.canDeleteBranch) return false
        val tree = treesByOrigin[baseSfen] ?: return false
        treesByOrigin[baseSfen] = tree.withoutBranch(moves)
        val parent = moves.dropLast(1)
        applyMoves(current.copy(displayLine = parent), parent)
        onTreeChanged()
        return true
    }

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
                        _studyState.value = s.copy(showTurnHint = true)
                    }
                    else -> {
                        if (s.showTurnHint) _studyState.value = s.copy(showTurnHint = false)
                    }
                }
            }
        }
    }

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

    fun onChipTapped(depth: Int) {
        val s = _studyState.value ?: return
        if (depth !in 0..s.displayLine.size) return
        navigateToDepth(depth)
    }

    fun onBranchChipTapped(depth: Int) {
        val s = _studyState.value ?: return
        if (depth !in s.displayLine.indices) return
        if (s.branchFlags.getOrNull(depth) != true) return
        val tree = treesByOrigin[s.baseSfen] ?: return
        val siblings = tree.siblingsAtDepth(s.displayLine, depth)
        val options = siblings.map { node ->
            StudyBranchOption(
                moveUsi = node.moveUsi,
                evalState = node.evalState,
                isCurrent = node.id == tree.nodesAtPath(tree.pathForMoves(s.displayLine.take(depth + 1))).lastOrNull()?.id,
                nodeId = node.id,
            )
        }
        _studyState.value = s.copy(openBranchPopupDepth = depth, branchPopupOptions = options)
    }

    /** 候補手の選択。表示局面からその手を指す。 */
    fun onCandidateSelected(moveUsi: String) {
        val board = studyBoard ?: return
        val move = board.legalMoves().firstOrNull { it.toUsiString() == moveUsi } ?: return
        executeStudyMove(move)
    }

    fun onBranchPopupDismiss() {
        _studyState.update { it?.copy(openBranchPopupDepth = null, branchPopupOptions = emptyList()) }
    }

    /**
     * Why not 選択した兄弟より先へ自動移動しない理由: ポップの評価は選択した1手だけで、
     * 表示局面と一致させるため。
     */
    fun onBranchOptionSelected(depth: Int, moveUsi: String) {
        val s = _studyState.value ?: return
        if (depth !in s.displayLine.indices) return
        applyMoves(s, s.displayLine.take(depth) + moveUsi)
    }

    fun navigateToMoves(moves: List<String>) {
        val state = _studyState.value ?: return
        var tree = treesByOrigin[state.baseSfen] ?: return
        var children = tree.rootChildren
        for ((depth, move) in moves.withIndex()) {
            val node = children.firstOrNull { it.moveUsi == move } ?: return
            tree = tree.selectChild(moves.take(depth), node.id)
            children = node.children
        }
        treesByOrigin[state.baseSfen] = tree
        applyMoves(state.copy(displayLine = emptyList()), moves)
    }

    fun onBranchNodeSelected(depth: Int, nodeId: Long) {
        if (depth < 0) return
        val state = _studyState.value ?: return
        if (depth !in state.displayLine.indices) return
        val tree = treesByOrigin[state.baseSfen] ?: return
        val parent = state.displayLine.take(depth)
        val node = tree.siblingsAtDepth(state.displayLine, depth).firstOrNull { it.id == nodeId } ?: return
        treesByOrigin[state.baseSfen] = tree.selectChild(parent, node.id)
        applyMoves(state.copy(displayLine = emptyList()), parent + node.moveUsi)
    }

    fun endStudy() {
        pollJob?.cancel()
        pollJob = null
        studyEngine?.quit()
        studyEngine = null
        studyBoard = null
        studyEvalRunning = false
        _studyState.value = null
    }

    fun dispose() {
        pollJob?.cancel()
        pollJob = null
        studyEngine?.quit()
        studyEngine = null
        treesByOrigin.clear()
        saveFailed = false
    }

    /** Why not 見込み判定を待たない理由: 明示再試行は engineFactory のフォールバックを許可する。 */
    fun analyzeCurrentPosition() {
        val s = _studyState.value ?: return
        pollJob?.cancel()
        pollJob = null
        startAnalysis(s.baseSfen, s.moves, s.flip)
    }

    /** 検討タブ表示時の初回評価。ローカルエンジンの準備待ちとクォータ保護を維持する。 */
    fun autoAnalyzeCurrentPosition() {
        maybeAutoAnalyze()
    }

    private fun maybeAutoAnalyze() {
        pollJob?.cancel()
        pollJob = null
        val s = _studyState.value ?: return
        if (s.evalState != StudyEvalState.None) return
        if (!localEngineLikelyAvailable()) {
            _studyState.update { it?.copy(evalState = StudyEvalState.Preparing) }
            val baseSfen = s.baseSfen
            val moves = s.moves
            val flip = s.flip
            pollJob = scope.launch {
                var waitedMs = 0L
                while (true) {
                    delay(STUDY_LOCAL_ENGINE_POLL_INTERVAL_MS)
                    waitedMs += STUDY_LOCAL_ENGINE_POLL_INTERVAL_MS
                    val cur = _studyState.value
                    // 局面変更または明示再試行後のループは結果を反映しない。
                    if (cur == null || cur.baseSfen != baseSfen || cur.moves != moves || cur.nodeId != s.nodeId) return@launch
                    if (cur.evalState != StudyEvalState.Preparing) return@launch
                    // Why not 判定を1回に減らさない理由: iOS実装（WasmStudyHost）では
                    // この呼び出し自体がウォームアップの起点を兼ねる。
                    if (localEngineLikelyAvailable()) {
                        startAnalysis(baseSfen, moves, flip)
                        return@launch
                    }
                    if (waitedMs >= STUDY_LOCAL_ENGINE_PREPARE_TIMEOUT_MS) {
                        _studyState.update { it?.copy(evalState = StudyEvalState.Error) }
                        return@launch
                    }
                }
            }
            return
        }
        startAnalysis(s.baseSfen, s.moves, s.flip)
    }

    /**
     * 単一実行中の局面変更は完了後に未評価なら再試行する。
     * 完了結果は元局面へ常にキャッシュし、表示状態は現在局面が一致するときだけ更新する。
     */
    private fun startAnalysis(baseSfen: String, moves: List<String>, flip: Boolean) {
        val selected = treesByOrigin[baseSfen]?.selectedChildren.orEmpty()
        val analyzedNodeId = _studyState.value?.nodeId
        if (studyEvalRunning) return
        studyEvalRunning = true
        _studyState.update { it?.copy(evalState = StudyEvalState.Loading) }

        scope.launch {
            val evalResult = try {
                val engine = studyEngine ?: studyEngineFactory().also { studyEngine = it }
                val pvs = engine.analyzeSfen(
                    baseSfen,
                    moves,
                    // 対話的解析も保存済み解析と同じ基準を使う。
                    nodes = EngineInvariants.NODES,
                    multiPv = Engine.STUDY_MULTI_PV,
                )
                if (pvs.isEmpty()) terminalEvalLabel(baseSfen, moves, flip)
                else studyEvalLabel(baseSfen, moves, pvs, flip)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                StudyEvalState.Error
            }
            studyEvalRunning = false

            val currentTree = treesByOrigin[baseSfen] ?: StudyTree()
            val target = currentTree.copy(selectedChildren = selected)
            val stillExists = target.nodesAtPath(target.pathForMoves(moves)).lastOrNull()?.id ?: 0L
            val tree = if (stillExists == analyzedNodeId) target.withEvalState(moves, evalResult)
                .copy(selectedChildren = currentTree.selectedChildren) else currentTree
            treesByOrigin[baseSfen] = tree

            val latest = _studyState.value
            when {
                latest == null -> Unit
                latest.baseSfen == baseSfen && latest.moves == moves && latest.nodeId == analyzedNodeId -> {
                    _studyState.update {
                        it?.copy(evalState = evalResult, chipEvalStates = tree.evalStatesAlong(latest.displayLine))
                    }
                }
                latest.baseSfen == baseSfen -> {
                    _studyState.update { it?.copy(chipEvalStates = tree.evalStatesAlong(latest.displayLine)) }
                }
            }
            maybeAutoAnalyze()
        }
    }

    private fun executeStudyMove(move: ShogiMove) {
        val s = _studyState.value ?: return
        applyMoves(s, s.moves + move.toUsiString())
    }

    private fun applyMoves(s: StudyState, newMoves: List<String>) {
        val addedMove = if (newMoves.size == s.moves.size + 1 && newMoves.dropLast(1) == s.moves) {
            newMoves.last()
        } else {
            null
        }
        var tree = treesByOrigin[s.baseSfen] ?: StudyTree()
        val previousTree = tree
        if (addedMove != null) {
            tree = tree.withMovePlayed(s.moves, addedMove, newId = nextNodeId())
            treesByOrigin[s.baseSfen] = tree
        }
        studyBoard = runCatching { ShogiBoard.fromSfen(s.baseSfen) }.getOrNull()?.also { b ->
            newMoves.forEach { m -> runCatching { b.push(ShogiMove.fromUsi(m)) } }
        }
        val newDisplayLine = if (
            newMoves.size <= s.displayLine.size && newMoves == s.displayLine.take(newMoves.size)
        ) {
            s.displayLine
        } else {
            tree.continuation(newMoves)
        }
        _studyState.value = s.copy(
            moves = newMoves,
            displayLine = newDisplayLine,
            chipEvalStates = tree.evalStatesAlong(newDisplayLine),
            branchFlags = tree.branchFlags(newDisplayLine),
            openBranchPopupDepth = null,
            branchPopupOptions = emptyList(),
            selectedFrom = null,
            selectedDropType = null,
            legalDestinations = emptySet(),
            showPromoteDialog = false,
            pendingPromoteMove = null,
            evalState = tree.evalStateAt(newMoves),
            nodeId = tree.nodesAtPath(tree.pathForMoves(newMoves)).lastOrNull()?.id ?: 0L,
            canDeleteBranch = newMoves.isNotEmpty() && protectedMainline != null && (protectedMainline?.take(newMoves.size) != newMoves ||
                tree.pathForMoves(newMoves).any { it != 0 }),
            showTurnHint = false,
        )
        if (tree.toKifu(emptyMap()) != previousTree.toKifu(emptyMap())) onTreeChanged()
        maybeAutoAnalyze()
    }

    private fun navigateToDepth(depth: Int) {
        val s = _studyState.value ?: return
        applyMoves(s, s.displayLine.take(depth))
    }

    /**
     * 読み筋なしで合法手もない局面は手番側の詰みとして扱う。
     * 合法手が残る場合は解析異常として Error にする。
     */
    private fun terminalEvalLabel(baseSfen: String, moves: List<String>, userIsGote: Boolean): StudyEvalState {
        val board = boardAt(baseSfen, moves) ?: return StudyEvalState.Error
        if (board.legalMoves().isNotEmpty()) return StudyEvalState.Error
        val label = PositionEvalDisplay.format(
            scoreCp = null,
            mateIn = 0,
            userIsGote = userIsGote,
            evalDisplay = evalDisplayProvider(),
            ply = if (board.turn == Side.BLACK) 0 else 1,
        ) ?: return StudyEvalState.Error
        return StudyEvalState.Value(label)
    }

    private fun boardAt(baseSfen: String, moves: List<String>): ShogiBoard? {
        val board = runCatching { ShogiBoard.fromSfen(baseSfen) }.getOrNull() ?: return null
        moves.forEach { m -> runCatching { board.push(ShogiMove.fromUsi(m)) } }
        return board
    }

    private fun studyEvalLabel(
        baseSfen: String,
        moves: List<String>,
        pvs: List<PvInfo>,
        userIsGote: Boolean,
    ): StudyEvalState {
        val board = boardAt(baseSfen, moves) ?: return StudyEvalState.Error
        val moverIsSente = board.turn == Side.BLACK

        // Why not MultiPV番号で並べる: 固定ノードで探索を打ち切るため、最後の反復で
        // 全本数が出そろわず、番号ごとに深さの違う評価が混ざる。評価値で並べ直す。
        val ranked = pvs.sortedByDescending { BlunderJudge.toCp(it.score) }
        val best = ranked.firstOrNull() ?: return StudyEvalState.Error
        val moverCp = BlunderJudge.toCp(best.score)
        val senteCp = if (moverIsSente) moverCp else -moverCp
        val userCp = if (userIsGote) -senteCp else senteCp

        val label = evalLabelOf(best.score, moverIsSente, userIsGote) ?: return StudyEvalState.None
        val candidates = ranked.mapNotNull { pv ->
            val usi = pv.pv.firstOrNull() ?: return@mapNotNull null
            val moveText = runCatching { JapaneseNotation.format(usi, board) }.getOrNull() ?: return@mapNotNull null
            val candidateLabel = evalLabelOf(pv.score, moverIsSente, userIsGote) ?: return@mapNotNull null
            StudyCandidate(moveUsi = usi, moveText = moveText, label = candidateLabel)
        }
        return StudyEvalState.Value(label, userCp = userCp, candidates = candidates)
    }

    /** 手番側視点の [score] を、表示設定に従った先手/自分視点のラベルへ直す。 */
    private fun evalLabelOf(
        score: Score,
        moverIsSente: Boolean,
        userIsGote: Boolean,
    ): PositionEvalDisplay.EvalLabel? {
        val syntheticPly = if (moverIsSente) 0 else 1
        return when (score) {
            is Score.Cp -> {
                val moverCp = BlunderJudge.toCp(score)
                PositionEvalDisplay.format(
                    scoreCp = if (moverIsSente) moverCp else -moverCp,
                    mateIn = null,
                    userIsGote = userIsGote,
                    evalDisplay = evalDisplayProvider(),
                    ply = syntheticPly,
                )
            }
            is Score.Mate -> PositionEvalDisplay.format(
                scoreCp = null,
                mateIn = if (moverIsSente) score.plies else -score.plies,
                userIsGote = userIsGote,
                evalDisplay = evalDisplayProvider(),
                ply = syntheticPly,
            )
        }
    }
}
