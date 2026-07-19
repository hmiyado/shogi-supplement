package dev.miyado.shogisupplement.ui.report

import dev.miyado.shogisupplement.board.ShogiSquare
import dev.miyado.shogisupplement.db.BlunderRecord
import dev.miyado.shogisupplement.db.GameRepository
import dev.miyado.shogisupplement.db.GameRecord
import dev.miyado.shogisupplement.db.PositionEvalRow
import dev.miyado.shogisupplement.engine.Engine
import dev.miyado.shogisupplement.judge.CoefficientTable
import dev.miyado.shogisupplement.strength.StrengthEstimator
import dev.miyado.shogisupplement.strength.toDisplayString
import dev.miyado.shogisupplement.board.PieceType
import dev.miyado.shogisupplement.ui.common.PvExtState
import dev.miyado.shogisupplement.ui.common.PvExtensionRunner
import dev.miyado.shogisupplement.ui.common.defaultIoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * レポート画面（棋譜ビューア）の表示状態を担う協力オブジェクト。
 *
 * MainViewModel（androidApp）が単一のトップレベル ViewModel としてナビゲーション状態
 * （MainUiState）を保持するアーキテクチャのため、本クラスは androidx ViewModel を
 * 継承した独立コンポーネントにはせず、MainViewModel が保持するプレーンな協力オブジェクトに
 * している（Compose の viewModel() から個別取得すると二重のライフサイクル管理になるため）。
 * [dispose] を呼び出し元（MainViewModel）の onCleared 相当のタイミングで呼ぶこと。
 *
 * @param scope 非同期処理に使うスコープ（呼び出し元の viewModelScope を注入）
 * @param ioDispatcher DB/エンジン処理用ディスパッチャ（テスト時はUnconfinedを注入）
 * @param repository DB操作用リポジトリ
 * @param coefTable 強さ推定用係数表
 * @param engineFactory 読み筋延長・検討評価が必要な場合に呼ばれるエンジン生成関数
 * @param evalDisplayProvider 形勢の表示単位（'cp'/'wp'）を都度取得する関数
 */
class ReportViewModel(
    private val scope: CoroutineScope,
    private val repository: GameRepository,
    private val coefTable: CoefficientTable,
    private val engineFactory: () -> Engine,
    private val evalDisplayProvider: () -> String,
    private val ioDispatcher: CoroutineDispatcher = defaultIoDispatcher,
) {

    /** 検討モード。MainViewModel からはこのインスタンス経由で操作する。 */
    val studyController = StudyController(scope, ioDispatcher, engineFactory, evalDisplayProvider)
    val studyState: StateFlow<StudyState?> get() = studyController.studyState

    /** 読み筋オンデマンド延長の状態 Map（blunderId → PvExtState）。 */
    private val _pvExtState = MutableStateFlow<Map<Long, PvExtState>>(emptyMap())
    val pvExtState: StateFlow<Map<Long, PvExtState>> = _pvExtState.asStateFlow()

    /**
     * 読み筋のオンデマンド延長。
     *
     * @param blunderId      延長対象の blunder_report.id
     * @param sfenAtLineEnd  ライン末尾局面の SFEN
     * @param currentPvStr   現在の best_pv 文字列（null = 未保存）
     * @param onUpdated      延長成功時に (blunderId, 新しいbest_pv文字列) を渡すコールバック。
     *   呼び出し元（MainViewModel）が現在表示中の MainUiState.ShowReport.reports を
     *   更新するために使う（レポート表示状態そのものは MainUiState 側にあるため）。
     */
    fun extendBestPv(
        blunderId: Long,
        sfenAtLineEnd: String,
        currentPvStr: String?,
        onUpdated: (blunderId: Long, newPv: String) -> Unit = { _, _ -> },
    ) {
        if (_pvExtState.value[blunderId] is PvExtState.Loading) return
        _pvExtState.update { it + (blunderId to PvExtState.Loading) }

        scope.launch {
            try {
                val newPv = withContext(ioDispatcher) {
                    PvExtensionRunner.extend(blunderId, sfenAtLineEnd, currentPvStr, repository, engineFactory)
                }
                onUpdated(blunderId, newPv)
                _pvExtState.update { it - blunderId }
            } catch (_: Exception) {
                _pvExtState.update { it + (blunderId to PvExtState.Error) }
            }
        }
    }

    /** レポート画面の表示状態。 */
    data class ReportResult(
        val game: GameRecord?,
        val reports: List<BlunderRecord>,
        val flip: Boolean,
        val strengthText: String?,
        val positionEvals: List<PositionEvalRow>,
    )

    /** 特定のゲームIDのレポート表示状態をDBから読み込む。 */
    suspend fun loadReport(gameId: Long): ReportResult = withContext(ioDispatcher) {
        val games = repository.getAllGames()
        val g = games.firstOrNull { it.id == gameId }
        val r = if (g != null) repository.getReports(gameId) else emptyList()
        val fl = g?.userSide == "gote"
        val st = if (g?.userSide != null) computeSingleGameStrengthText(g, r) else null
        val pe = if (g != null) repository.getPositionEvals(gameId) else emptyList()
        ReportResult(g, r, fl, st, pe)
    }

    /** 1局の強さ指標テキストを計算する。 */
    fun computeSingleGameStrengthText(game: GameRecord, reports: List<BlunderRecord>): String? {
        val side = game.userSide ?: return null
        val userMoves = userMoveCount(game.moveCount, side)
        if (userMoves == 0) return null
        val blunders = reports.count { it.side == side }
        val ratePer1000 = blunders * 1000.0 / userMoves
        val estimate = StrengthEstimator.estimate(ratePer1000, userMoves, coefTable)
        return estimate.toDisplayString()
    }

    /** 総手数から user_side の手数を算出する。先手: ceil(total/2), 後手: floor(total/2)。 */
    private fun userMoveCount(totalMoves: Long, userSide: String): Int {
        val t = totalMoves.toInt()
        return if (userSide == "sente") (t + 1) / 2 else t / 2
    }

    // ─── 検討モード委譲（ReportHost からはこのインスタンス経由で呼ぶ）───────────

    fun startStudy(
        baseSfen: String,
        flip: Boolean,
        originIsBestPv: Boolean,
        originPlyIndex: Int,
        originSelectedIdx: Int?,
        originAbsolutePly: Int,
        tappedSquare: ShogiSquare? = null,
    ) = studyController.startStudy(
        baseSfen, flip, originIsBestPv, originPlyIndex, originSelectedIdx, originAbsolutePly, tappedSquare,
    )

    fun onStudySquareTapped(sq: ShogiSquare) = studyController.onStudySquareTapped(sq)
    fun onStudyHandPieceTapped(pieceType: PieceType) = studyController.onStudyHandPieceTapped(pieceType)
    fun onStudyPromoteDecision(promote: Boolean) = studyController.onStudyPromoteDecision(promote)
    fun studyStepBack() = studyController.studyStepBack()
    fun studyResetToStart() = studyController.studyResetToStart()
    fun endStudy() = studyController.endStudy()

    /** リーク厳禁: 呼び出し元（MainViewModel）の onCleared 相当のタイミングで呼ぶこと。 */
    fun dispose() {
        studyController.dispose()
    }
}
