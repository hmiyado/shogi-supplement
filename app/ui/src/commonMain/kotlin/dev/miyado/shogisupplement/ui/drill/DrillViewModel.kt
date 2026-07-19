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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ViewModel は Application/File/UsiEngineProcess への直接依存を持たず、必要最小限の
// 注入インターフェース（GameRepository/DrillRepository/SettingsRepository と
// judgeWithEngine 関数）だけに依存する:
//   - judgeWithEngine: ENGINE_EVAL 判定が必要な場合のみ呼ばれる。エンジンの起動/破棄
//     ライフサイクル（Android版=UsiEngineProcess.create/quit を1回の判定ごとに行う、
//     iOS版=起動済みの UsiEngineInProcess を使い回す）は完全にホスト側の責務とし、
//     ViewModel はその結果（DrillJudge.DrillResult）だけを受け取る。
//   - evalDir・ApplicationInfo・nativeLibraryDir 等 Android専用の解決はすべて
//     judgeWithEngine を組み立てる Android ホスト側（DrillScreenHost.kt）に閉じ込められて
//     おり、ViewModel 自体は evalDir を知らない（注入面を最小化している）。

/**
 * ドリル機能の ViewModel。
 *
 * - DB からドリル候補を取得して出題
 * - 盤面でのタップによる手の入力を処理
 * - エンジンを使って正誤判定（必要な場合のみ、judgeWithEngine 経由）
 * - drill_attempt をDBに保存
 *
 * @param gameRepository 棋譜・悪手レポート操作用リポジトリ（出題局面の対局側判定・読み筋延長）
 * @param drillRepository ドリル出題候補・解答履歴の操作用リポジトリ
 * @param settingsRepository 形勢の表示単位（cp/wp）取得用リポジトリ
 * @param judgeWithEngine エンジン評価が必要な場合のみ呼ばれる判定関数。null ならエンジン不要な
 *   即判定2パターン（最善手一致/実戦悪手一致）のみで、それ以外は不正解扱いになる。
 * @param engineFactory 読み筋のオンデマンド延長（結果画面の「最善」タブ）が必要な場合のみ
 *   呼ばれるエンジン生成関数。null なら延長は常にエラー扱い（ボタン自体は出せるがタップ後に
 *   即エラー状態になる）。ReportViewModel と同じ PvExtensionRunner を使う（エンジンの
 *   起動/破棄ライフサイクルはホスト側の責務。judgeWithEngine と同様の注入方針）。
 * @param ioDispatcher DB/エンジン処理用ディスパッチャ（テスト時はUnconfinedを注入）
 */
class DrillViewModel(
    private val gameRepository: GameRepository,
    private val drillRepository: DrillRepository,
    private val settingsRepository: SettingsRepository,
    private val judgeWithEngine: ((blunder: BlunderRecord, userMoveUsi: String) -> DrillJudge.DrillResult)? = null,
    private val engineFactory: (() -> Engine)? = null,
    private val ioDispatcher: CoroutineDispatcher = defaultIoDispatcher,
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

    /**
     * 盤上のマスをタップしたときの処理。
     * - 駒未選択: 自駒を選択してハイライト
     * - 駒選択中: 合法目的マスなら手を確定、そうでなければ選択解除/変更
     * - 持ち駒選択中: 合法打ちマスなら打つ
     */
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
                    legalToHere.size == 1 -> executeMove(legalToHere.first())
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
                    executeMove(dropMoves.first())
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
        executeMove(actualMove)
    }

    /** 「答えを見る」ボタンが押された。 */
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
        }
    }

    /**
     * 読み筋のオンデマンド延長（結果画面の「最善」タブ末尾から）。
     *
     * 対象は現在表示中の DrillUiState.Result.blunder（出題中に正誤判定された悪手）。
     * best_pv は表示中の blunder に紐づくため、成功時は _state を新しい bestPv を持つ
     * Result に差し替える（KifuLineViewer の bestMoves remember キーが blunder なので、
     * これだけで最善タブの手列が自動的に伸びる）。
     *
     * @param sfenAtLineEnd 最善タブのライン末尾局面の SFEN（KifuLineViewer.onExtendRequested から渡される）
     */
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

    private fun executeMove(move: ShogiMove) {
        val blunder = currentBlunder ?: return
        val userMoveUsi = move.toUsiString()
        val flip = (_state.value as? DrillUiState.Question)?.flip ?: false

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
                    )
                }
            }
            _state.value = DrillUiState.Result(result, blunder, blunder.sfenBefore, flip)
        }
    }

    private fun judgeMove(blunder: BlunderRecord, userMoveUsi: String): DrillJudge.DrillResult {
        // 即判定（エンジン不要な2パターン）
        val instant = DrillJudge.judge(blunder, userMoveUsi, engineAnalyze = null)
        if (instant.reason != DrillJudge.Reason.ENGINE_EVAL) return instant

        // エンジン評価が必要な場合: ホストが注入した judgeWithEngine に委譲する
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
            judgeWithEngine: ((blunder: BlunderRecord, userMoveUsi: String) -> DrillJudge.DrillResult)? = null,
            engineFactory: (() -> Engine)? = null,
            ioDispatcher: CoroutineDispatcher = defaultIoDispatcher,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                DrillViewModel(
                    gameRepository, drillRepository, settingsRepository,
                    judgeWithEngine, engineFactory, ioDispatcher,
                )
            }
        }
    }
}
