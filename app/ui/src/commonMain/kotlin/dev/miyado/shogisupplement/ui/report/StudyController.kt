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
 * - [engineFactory]: 検討評価が必要になったとき（初手のみ）呼ばれ、以後 [studyEngine] として
 *   生かしっぱなしにする。エンジンの起動/破棄ライフサイクルは呼び出し元＝ホストの責務。
 * - [evalDisplayProvider]: 形勢の表示単位（'cp'/'wp'）を都度取得する。
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

    /** 検討モード中に生かしっぱなしにするエンジン（初手で遅延生成、終了/disposeでquit）。 */
    private var studyEngine: Engine? = null

    /** 検討モードの現局面（合法手計算用）。baseSfen + moves を都度適用して保持する。 */
    private var studyBoard: ShogiBoard? = null

    /** 検討評価ジョブの直列化フラグ（解析中に次の手が指されたら dirty を立てて完了後に再実行）。 */
    private var studyEvalRunning = false
    private var studyEvalDirty = false

    /**
     * 検討モードを開始する（レポートビューアで盤上の駒をタップしたときに呼ぶ）。
     *
     * エンジンはここでは生成しない（起動コストを避けるため、実際に手を指す初手のタイミングで
     * 遅延生成する）。
     * 開始タップのマス（tappedSquare）の駒が手番側なら、開始と同時に選択状態にする。
     */
    fun startStudy(
        baseSfen: String,
        flip: Boolean,
        originIsBestPv: Boolean,
        originPlyIndex: Int,
        originSelectedIdx: Int?,
        originAbsolutePly: Int,
        tappedSquare: ShogiSquare? = null,
    ) {
        val board = runCatching { ShogiBoard.fromSfen(baseSfen) }.getOrNull() ?: return
        studyBoard = board
        _studyState.value = buildInitialStudyState(
            baseSfen = baseSfen,
            flip = flip,
            originIsBestPv = originIsBestPv,
            originPlyIndex = originPlyIndex,
            originSelectedIdx = originSelectedIdx,
            originAbsolutePly = originAbsolutePly,
            tappedSquare = tappedSquare,
            board = board,
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
                        // 手番でない側の駒をタップ→ナビ行末尾に「▲番です/△番です」。
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

    /** 検討の1手戻し（解析はしない。「1手指すたび」だけがトリガー）。 */
    fun studyStepBack() {
        val s = _studyState.value ?: return
        if (s.moves.isEmpty()) return
        val newMoves = s.moves.dropLast(1)
        studyBoard = runCatching { ShogiBoard.fromSfen(s.baseSfen) }.getOrNull()?.also { b ->
            newMoves.forEach { m -> runCatching { b.push(ShogiMove.fromUsi(m)) } }
        }
        _studyState.value = s.copy(
            moves = newMoves,
            selectedFrom = null,
            selectedDropType = null,
            legalDestinations = emptySet(),
            showPromoteDialog = false,
            pendingPromoteMove = null,
            evalState = StudyEvalState.None,
            showTurnHint = false,
        )
    }

    /** 検討開始局面へ戻す（解析はしない）。 */
    fun studyResetToStart() {
        val s = _studyState.value ?: return
        if (s.moves.isEmpty()) return
        studyBoard = runCatching { ShogiBoard.fromSfen(s.baseSfen) }.getOrNull()
        _studyState.value = s.copy(
            moves = emptyList(),
            selectedFrom = null,
            selectedDropType = null,
            legalDestinations = emptySet(),
            showPromoteDialog = false,
            pendingPromoteMove = null,
            evalState = StudyEvalState.None,
            showTurnHint = false,
        )
    }

    /** 検討モードを終了する（エンジンをquitし、状態を破棄する）。手順は保存しない（v1）。 */
    fun endStudy() {
        studyEngine?.quit()
        studyEngine = null
        studyBoard = null
        studyEvalRunning = false
        studyEvalDirty = false
        _studyState.value = null
    }

    /** リーク厳禁: 呼び出し元（ReportViewModel）の onCleared 相当のタイミングで呼ぶこと。 */
    fun dispose() {
        studyEngine?.quit()
        studyEngine = null
    }

    private fun executeStudyMove(move: ShogiMove) {
        val s = _studyState.value ?: return
        val board = studyBoard ?: return
        board.push(move)
        _studyState.value = s.copy(
            moves = s.moves + move.toUsiString(),
            selectedFrom = null,
            selectedDropType = null,
            legalDestinations = emptySet(),
            showPromoteDialog = false,
            pendingPromoteMove = null,
            showTurnHint = false,
        )
        triggerStudyEval()
    }

    /**
     * 検討評価を非同期でトリガーする。
     *
     * 直列・最新優先: 既に解析実行中なら dirty フラグだけ立てて戻る。
     * 実行中の解析が完了した時点で dirty なら、その時点の最新局面で再実行する
     * （エンジンへの多重発話を避けつつ、最新局面のみを評価する）。
     */
    private fun triggerStudyEval() {
        if (studyEvalRunning) {
            studyEvalDirty = true
            return
        }
        runStudyEvalOnce()
    }

    private fun runStudyEvalOnce() {
        val started = _studyState.value ?: return
        studyEvalRunning = true
        studyEvalDirty = false
        _studyState.update { it?.copy(evalState = StudyEvalState.Loading) }

        scope.launch {
            // 実行直前の最新状態で評価する（trigger 時点ではなく launch 時点の最新値）。
            val cur = _studyState.value ?: started
            val evalResult = withContext(ioDispatcher) {
                runCatching {
                    val engine = studyEngine ?: engineFactory().also { studyEngine = it }
                    val pv1 = engine.analyzeSfen(cur.baseSfen, cur.moves, nodes = STUDY_ANALYSIS_NODES)
                        .firstOrNull() ?: error("PV empty")
                    studyEvalLabel(cur.baseSfen, cur.moves, pv1.score, cur.flip)
                }.getOrElse { StudyEvalState.Error }
            }
            _studyState.update { it?.copy(evalState = evalResult) }
            studyEvalRunning = false
            if (studyEvalDirty && _studyState.value != null) {
                runStudyEvalOnce()
            }
        }
    }

    /**
     * エンジンPVのスコア（手番側視点）を先手視点に正規化して表示ラベルに変換する。
     *
     * PvInfo.score のドキュメント（dev.miyado.shogisupplement.engine.Engine.kt）:
     * 「手番側視点のスコア」。position_eval・BlunderJudge と同じ規約
     * （AnalysisService の positionEvalRows 生成: 手番がgoteなら反転）に合わせ、
     * 検討中の現局面（baseSfen + moves 適用後）の手番が先手なら符号そのまま、
     * 後手なら反転して先手視点にする。
     */
    private fun studyEvalLabel(baseSfen: String, moves: List<String>, score: Score, userIsGote: Boolean): StudyEvalState {
        val board = runCatching { ShogiBoard.fromSfen(baseSfen) }.getOrNull()
            ?: return StudyEvalState.Error
        moves.forEach { m -> runCatching { board.push(ShogiMove.fromUsi(m)) } }
        val moverIsSente = board.turn == Side.BLACK
        // mate_in=0 の勝敗判定用（parity のみ使うダミーply）。
        val syntheticPly = if (moverIsSente) 0 else 1
        val label = when (score) {
            is Score.Cp -> {
                val cp = BlunderJudge.toCp(score)
                val senteCp = if (moverIsSente) cp else -cp
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
        return label?.let { StudyEvalState.Value(it) } ?: StudyEvalState.None
    }
}
