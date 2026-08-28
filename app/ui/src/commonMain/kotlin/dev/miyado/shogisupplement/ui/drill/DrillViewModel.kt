package dev.miyado.shogisupplement.ui.drill

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.miyado.shogisupplement.board.PieceType
import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.board.ShogiMove
import dev.miyado.shogisupplement.board.ShogiSquare
import dev.miyado.shogisupplement.db.BlunderRecord
import dev.miyado.shogisupplement.db.DrillRepository
import dev.miyado.shogisupplement.db.GameRepository
import dev.miyado.shogisupplement.db.SettingsRepository
import dev.miyado.shogisupplement.drill.DrillJudge
import dev.miyado.shogisupplement.drill.DrillRotation
import dev.miyado.shogisupplement.engine.Engine
import dev.miyado.shogisupplement.ui.common.PvExtState
import dev.miyado.shogisupplement.ui.common.PvExtensionRunner
import dev.miyado.shogisupplement.ui.common.defaultIoDispatcher
import dev.miyado.shogisupplement.upload.DrillAttemptSync
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ViewModelはリポジトリと判定関数だけに依存し、エンジンのライフサイクルはホストへ委ねる。
// Android固有のevalDirやApplicationInfoは注入関数を組み立てるホストに閉じ込める。

/** ドリルの状態、盤面入力、判定、解答保存を管理する。 @param gameRepository 棋譜と悪手のリポジトリ。 @param drillRepository 出題と履歴のリポジトリ。 @param settingsRepository 表示単位のリポジトリ。 @param judgeWithEngine 二次判定関数。 @param engineFactory PV延長用エンジンfactory。 @param ioDispatcher DBとエンジン処理用dispatcher。 */
class DrillViewModel(
    private val gameRepository: GameRepository,
    private val drillRepository: DrillRepository,
    private val settingsRepository: SettingsRepository,
    private val judgeWithEngine: (suspend (blunder: BlunderRecord, userMoveUsi: String) -> DrillJudge.DrillResult)? = null,
    private val engineFactory: (() -> Engine)? = null,
    private val ioDispatcher: CoroutineDispatcher = defaultIoDispatcher,
    private val drillAttemptSync: DrillAttemptSync? = null,
) : ViewModel() {

    private val _state = MutableStateFlow<DrillUiState>(DrillUiState.Loading)
    val state: StateFlow<DrillUiState> = _state

    /** 形勢の表示単位（"cp" or "wp"）。DB から初期値をロードする。 */
    private val _evalDisplay = MutableStateFlow("cp")
    val evalDisplay: StateFlow<String> = _evalDisplay.asStateFlow()

    /** 読み筋オンデマンド延長の状態 Map（blunderId → PvExtState）。ReportViewModel と同型。 */
    private val _pvExtState = MutableStateFlow<Map<Long, PvExtState>>(emptyMap())
    val pvExtState: StateFlow<Map<Long, PvExtState>> = _pvExtState.asStateFlow()

    /** 現在の出題局面を保持する ShogiBoard（合法手計算用）。 */
    private var currentBoard: ShogiBoard? = null
    private var currentBlunder: BlunderRecord? = null

    init {
        viewModelScope.launch {
            _evalDisplay.value = withContext(ioDispatcher) { settingsRepository.getEvalDisplay() }
        }
        loadNextQuestion()
    }

    /** 次の問題をロードする。周回決定則（解答回数少→◎○順→priority降順）で選択する。 */
    fun loadNextQuestion() {
        _state.value = DrillUiState.Loading
        viewModelScope.launch {
            val (candidates, attemptCounts) = withContext(ioDispatcher) {
                drillRepository.getDrillCandidates() to drillRepository.getDrillAttemptCounts()
            }
            if (candidates.isEmpty()) {
                _state.value = DrillUiState.NoCandidates
            } else {
                val blunder = DrillRotation.selectNext(candidates, attemptCounts)!!
                val attemptCount = attemptCounts[blunder.id] ?: 0
                val flip = withContext(ioDispatcher) {
                    gameRepository.getGameById(blunder.gameId)?.userSide == "gote"
                }
                val board = ShogiBoard.fromSfen(blunder.sfenBefore)
                currentBoard = board
                currentBlunder = blunder
                _state.value = DrillUiState.Question(
                    blunder = blunder,
                    sfenCurrent = blunder.sfenBefore,
                    attemptCount = attemptCount,
                    totalCandidates = candidates.size,
                    flip = flip,
                )
            }
        }
    }

    /** 盤面タップを処理し、合法手なら確定、無効なら選択状態を更新する。 */
    fun onSquareTapped(sq: ShogiSquare) {
        val state = _state.value as? DrillUiState.Question ?: return
        val board = currentBoard ?: return

        when {
            state.selectedFrom != null -> {
                // DrillUiState は別ファイル（DrillState.kt）で宣言されているプロパティのため、
                // この時点で state.selectedFrom が非nullであることが確定しているにも関わらず
                // Kotlin はスマートキャストできない（"Smart cast to 'ShogiSquare' is
                // impossible" コンパイルエラー）。直前の != null 判定で保証済みのため
                // !! で明示する。
                val legalToHere = board.legalMovesFrom(state.selectedFrom!!).filter { it.to == sq }
                when {
                    legalToHere.isEmpty() -> {
                        // 合法手なし: 別の自駒を選択 or 選択解除
                        val piece = board.pieceAt(sq)
                        if (piece != null && piece.side == board.turn) {
                            val dests = board.legalMovesFrom(sq).map { it.to }.toSet()
                            _state.value = state.copy(
                                selectedFrom = sq,
                                selectedDropType = null,
                                legalDestinations = dests,
                                showPromoteDialog = false,
                                pendingPromoteMove = null,
                            )
                        } else {
                            _state.value = state.copy(
                                selectedFrom = null,
                                selectedDropType = null,
                                legalDestinations = emptySet(),
                            )
                        }
                    }
                    legalToHere.size == 1 -> applyMove(legalToHere.first())
                    else -> {
                        // 成り/不成の選択が必要
                        val promote = legalToHere.firstOrNull { it.promote } ?: legalToHere.first()
                        _state.value = state.copy(
                            showPromoteDialog = true,
                            pendingPromoteMove = promote,
                        )
                    }
                }
            }

            state.selectedDropType != null -> {
                val dropMoves = board.legalMoves().filter {
                    it.dropType == state.selectedDropType && it.to == sq
                }
                if (dropMoves.isNotEmpty()) {
                    applyMove(dropMoves.first())
                } else {
                    _state.value = state.copy(
                        selectedDropType = null,
                        legalDestinations = emptySet(),
                    )
                }
            }

            else -> {
                // 何も選択されていない: 自駒を選択
                val piece = board.pieceAt(sq)
                if (piece != null && piece.side == board.turn) {
                    val dests = board.legalMovesFrom(sq).map { it.to }.toSet()
                    _state.value = state.copy(
                        selectedFrom = sq,
                        selectedDropType = null,
                        legalDestinations = dests,
                    )
                }
            }
        }
    }

    /** 持ち駒をタップしたときの処理。 */
    fun onHandPieceTapped(pieceType: PieceType) {
        val state = _state.value as? DrillUiState.Question ?: return
        val board = currentBoard ?: return

        if (state.selectedDropType == pieceType) {
            // 同じ駒を再タップ: 選択解除
            _state.value = state.copy(
                selectedDropType = null,
                selectedFrom = null,
                legalDestinations = emptySet(),
            )
        } else {
            val dropSquares = board.legalDropSquares(pieceType).toSet()
            _state.value = state.copy(
                selectedDropType = pieceType,
                selectedFrom = null,
                legalDestinations = dropSquares,
            )
        }
    }

    /** 成り選択ダイアログで「成る/成らない」を決定した。 */
    fun onPromoteDecision(promote: Boolean) {
        val state = _state.value as? DrillUiState.Question ?: return
        val pending = state.pendingPromoteMove ?: return
        val board = currentBoard ?: return

        // promote フラグを確定させた手を探す
        val actualMove = board.legalMovesFrom(pending.from!!)
            .filter { it.to == pending.to }
            .firstOrNull { it.promote == promote }
            ?: pending.copy(promote = promote)

        _state.value = state.copy(showPromoteDialog = false, pendingPromoteMove = null)
        applyMove(actualMove)
    }

    /** 「正解を見る」ボタンが押された。 */
    fun onSurrender() {
        val blunder = currentBlunder ?: return
        val flip = (_state.value as? DrillUiState.Question)?.flip ?: false
        val surrenderResult = DrillJudge.DrillResult(
            isCorrect = false,
            lossWp = blunder.lossWp,
            userMoveUsi = "[降参]",
            bestMoveUsi = blunder.bestUsi,
            reason = DrillJudge.Reason.MATCH_ACTUAL_BLUNDER,
        )
        viewModelScope.launch {
            withContext(ioDispatcher) {
                drillRepository.saveDrillAttempt(
                    blunderReportId = blunder.id,
                    userMoveUsi = "[降参]",
                    isCorrect = false,
                    lossWp = blunder.lossWp,
                )
            }
            _state.value = DrillUiState.Result(surrenderResult, blunder, blunder.sfenBefore, flip)
            startDrillAttemptUpload()
        }
    }

    /** 盤で1手戻す。読み筋を含め、最後に入力した1手だけを取り消す。 */
    fun undoLastMove() {
        val state = _state.value as? DrillUiState.Question ?: return
        if (state.moves.isEmpty()) return
        val blunder = currentBlunder ?: return
        val newMoves = state.moves.dropLast(1)
        val board = ShogiBoard.fromSfen(blunder.sfenBefore)
        newMoves.forEach { usi -> board.push(ShogiMove.fromUsi(usi)) }
        currentBoard = board
        _state.value = state.copy(
            sfenCurrent = board.toSfen(),
            selectedFrom = null,
            selectedDropType = null,
            legalDestinations = emptySet(),
            moves = newMoves,
        )
    }

    /** 盤への入力を最初からやり直す。出題局面まで巻き戻す。 */
    fun resetMoves() {
        val state = _state.value as? DrillUiState.Question ?: return
        val blunder = currentBlunder ?: return
        val board = ShogiBoard.fromSfen(blunder.sfenBefore)
        currentBoard = board
        _state.value = state.copy(
            sfenCurrent = board.toSfen(),
            selectedFrom = null,
            selectedDropType = null,
            legalDestinations = emptySet(),
            moves = emptyList(),
        )
    }

    /** 「答える」ボタンが押された。先頭の手を予測手として判定し、続きがあれば読み筋として保存する。 */
    fun submitAnswer() {
        val state = _state.value as? DrillUiState.Question ?: return
        val blunder = currentBlunder ?: return
        val userMoveUsi = state.moves.firstOrNull() ?: return
        val readPv = state.moves.drop(1).takeIf { it.isNotEmpty() }?.joinToString(" ")
        val flip = state.flip

        _state.value = DrillUiState.Judging

        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                judgeMove(blunder, userMoveUsi)
            }
            withContext(ioDispatcher) {
                runCatching {
                    drillRepository.saveDrillAttempt(
                        blunderReportId = blunder.id,
                        userMoveUsi = userMoveUsi,
                        isCorrect = result.isCorrect,
                        lossWp = if (result.lossWp.isNaN()) null else result.lossWp,
                        readPv = readPv,
                    )
                }
            }
            _state.value = DrillUiState.Result(result, blunder, blunder.sfenBefore, flip, readPv)
            startDrillAttemptUpload()
        }
    }

    /** 最善タブのPVを延長する。 @param sfenAtLineEnd ライン末尾局面のSFEN。 */
    fun extendBestPv(sfenAtLineEnd: String) {
        val resultState = _state.value as? DrillUiState.Result ?: return
        val blunder = resultState.blunder
        val blunderId = blunder.id
        if (_pvExtState.value[blunderId] is PvExtState.Loading) return
        _pvExtState.update { it + (blunderId to PvExtState.Loading) }

        viewModelScope.launch {
            try {
                val factory = engineFactory ?: error("engine not available")
                val newPv = withContext(ioDispatcher) {
                    PvExtensionRunner.extend(blunderId, sfenAtLineEnd, blunder.bestPv, gameRepository, factory)
                }
                val latest = _state.value
                if (latest is DrillUiState.Result && latest.blunder.id == blunderId) {
                    _state.value = latest.copy(blunder = latest.blunder.copy(bestPv = newPv))
                }
                _pvExtState.update { it - blunderId }
            } catch (_: Exception) {
                _pvExtState.update { it + (blunderId to PvExtState.Error) }
            }
        }
    }

    // ─── 内部ヘルパー ─────────────────────────────────────────────────────────

    /** 盤へ1手積む。予測手・読み筋のいずれも、判定や保存はせずここで盤面とmovesだけを進める。 */
    private fun applyMove(move: ShogiMove) {
        val state = _state.value as? DrillUiState.Question ?: return
        val board = currentBoard ?: return
        board.push(move)
        _state.value = state.copy(
            sfenCurrent = board.toSfen(),
            selectedFrom = null,
            selectedDropType = null,
            legalDestinations = emptySet(),
            moves = state.moves + move.toUsiString(),
        )
    }

    /** 次の一手の成績アップロードを別コルーチンで起動する。Result遷移をブロックしない。 */
    private fun startDrillAttemptUpload() {
        viewModelScope.launch {
            withContext(ioDispatcher) {
                drillAttemptSync?.syncPendingAttempts()
            }
        }
    }

    private suspend fun judgeMove(blunder: BlunderRecord, userMoveUsi: String): DrillJudge.DrillResult {
        // エンジン不要な判定（best_usi一致/実戦悪手一致/一次判定=pv2境界）をまず試す。
        // reason が ENGINE_EVAL のときだけ、一次判定が Ambiguous/Unavailable だったことを意味する
        // （DrillJudge.judge 参照）。
        val instant = DrillJudge.judge(blunder, userMoveUsi, engineAnalyze = null)
        if (instant.reason != DrillJudge.Reason.ENGINE_EVAL) return instant

        // 曖昧領域: ホストが注入した二次判定（judgeWithEngine）に委譲する
        // （エンジンの起動/破棄ライフサイクルはホスト側の責務。クラスKDoc参照）。
        return judgeWithEngine?.invoke(blunder, userMoveUsi) ?: DrillJudge.DrillResult(
            // エンジン起動失敗・未注入: 不正解として返す
            isCorrect = false,
            lossWp = Double.NaN,
            userMoveUsi = userMoveUsi,
            bestMoveUsi = blunder.bestUsi,
            reason = DrillJudge.Reason.ENGINE_EVAL,
        )
    }

    companion object {
        /** ViewModelProvider.Factory を作成する（コンポーザブルからの注入に使用）。 */
        fun factory(
            gameRepository: GameRepository,
            drillRepository: DrillRepository,
            settingsRepository: SettingsRepository,
            judgeWithEngine: (suspend (blunder: BlunderRecord, userMoveUsi: String) -> DrillJudge.DrillResult)? = null,
            engineFactory: (() -> Engine)? = null,
            ioDispatcher: CoroutineDispatcher = defaultIoDispatcher,
            drillAttemptSync: DrillAttemptSync? = null,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                DrillViewModel(
                    gameRepository = gameRepository,
                    drillRepository = drillRepository,
                    settingsRepository = settingsRepository,
                    judgeWithEngine = judgeWithEngine,
                    engineFactory = engineFactory,
                    ioDispatcher = ioDispatcher,
                    drillAttemptSync = drillAttemptSync,
                )
            }
        }
    }
}
