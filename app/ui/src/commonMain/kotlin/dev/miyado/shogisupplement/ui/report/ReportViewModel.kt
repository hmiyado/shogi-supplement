package dev.miyado.shogisupplement.ui.report

import dev.miyado.shogisupplement.board.ShogiSquare
import dev.miyado.shogisupplement.db.BlunderRecord
import dev.miyado.shogisupplement.db.EngineMatchRate
import dev.miyado.shogisupplement.db.GameRepository
import dev.miyado.shogisupplement.db.GameRecord
import dev.miyado.shogisupplement.db.PositionEvalRow
import dev.miyado.shogisupplement.engine.BlockingStudyEngine
import dev.miyado.shogisupplement.engine.Engine
import dev.miyado.shogisupplement.strength.StrengthEstimator
import dev.miyado.shogisupplement.strength.toDisplayString
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.board.PieceType
import kotlin.math.roundToInt
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
 * レポート画面（棋譜ビューア）の表示状態。
 *
 * Why not androidx ViewModel: Compose の viewModel() から個別取得するとライフサイクルが
 * 二重になる。所有者が [dispose] を呼ぶ協力オブジェクトにする。
 */
class ReportViewModel(
    private val scope: CoroutineScope,
    private val repository: GameRepository,
    private val engineFactory: () -> Engine,
    private val evalDisplayProvider: () -> String,
    private val ioDispatcher: CoroutineDispatcher = defaultIoDispatcher,
    private val localEngineLikelyAvailable: () -> Boolean = { true },
) {

    /** Why not engineFactoryをそのまま渡す: 同期のエンジンは待てる実行文脈へ隔離する必要がある。 */
    val studyController = StudyController(
        scope,
        { BlockingStudyEngine(engineFactory(), ioDispatcher) },
        evalDisplayProvider,
        localEngineLikelyAvailable,
    )
    val studyState: StateFlow<StudyState?> get() = studyController.studyState

    /** 読み筋オンデマンド延長の状態 Map（blunderId → PvExtState）。 */
    private val _pvExtState = MutableStateFlow<Map<Long, PvExtState>>(emptyMap())
    val pvExtState: StateFlow<Map<Long, PvExtState>> = _pvExtState.asStateFlow()

    /** 読み筋のオンデマンド延長。[currentPvStr] のnullは未保存、[onUpdated] は延長成功時のみ呼ぶ。 */
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
        /** エンジン一致率の値表示（例:「62%(31/50)」）。算出不能（データ不足）なら null。 */
        val matchRateText: String? = null,
        /** 悪手率の値表示（例:「12%(3/25)」）。一致率と同じ分母（n）を使う。算出不能なら null。 */
        val blunderRateText: String? = null,
    )

    /** 特定のゲームIDのレポート表示状態をDBから読み込む。 */
    suspend fun loadReport(gameId: Long): ReportResult = withContext(ioDispatcher) {
        val games = repository.getAllGames()
        val g = games.firstOrNull { it.id == gameId }
        val r = if (g != null) repository.getReports(gameId) else emptyList()
        val fl = g?.userSide == "gote"
        val st = if (g?.userSide != null) computeSingleGameStrengthText(g) else null
        val pe = if (g != null) repository.getPositionEvals(gameId) else emptyList()
        // 悪手率・一致率は同じ分母（n=エンジン評価が使えた自分の手数）を共有するため、
        // 一致率の算出は1回だけ呼んで両方を導出する。
        val mrResult = if (g != null) EngineMatchRate.compute(g.movesUsi, pe, g.userSide) else null
        val mr = mrResult?.let { AppStrings.matchRateValue((it.rate * 100).roundToInt(), it.matched, it.sampleMoves) }
        val br = mrResult?.takeIf { it.sampleMoves > 0 }?.let {
            val pct = (r.size.toDouble() / it.sampleMoves * 100).roundToInt()
            AppStrings.blunderRateValue(pct, r.size, it.sampleMoves)
        }
        ReportResult(g, r, fl, st, pe, mr, br)
    }

    /** Why not 悪手レポート一覧から再計算: v2の6特徴量は再現できず、解析時に計算済みの値を使う。 */
    fun computeSingleGameStrengthText(game: GameRecord): String? {
        val side = game.userSide ?: return null
        val userMoves = userMoveCount(game.moveCount, side)
        if (userMoves == 0) return null
        val estimate = StrengthEstimator.aggregate(listOf(game.rating.toInt()), userMoves)
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
        origin: StudyOrigin,
        tappedSquare: ShogiSquare? = null,
        tappedHandPieceType: PieceType? = null,
    ) = studyController.startStudy(
        baseSfen, flip, originIsBestPv, originPlyIndex, originSelectedIdx, originAbsolutePly, origin,
        tappedSquare, tappedHandPieceType,
    )

    fun onStudySquareTapped(sq: ShogiSquare) = studyController.onStudySquareTapped(sq)
    fun onStudyHandPieceTapped(pieceType: PieceType) = studyController.onStudyHandPieceTapped(pieceType)
    fun onStudyPromoteDecision(promote: Boolean) = studyController.onStudyPromoteDecision(promote)
    fun studyStepBack() = studyController.studyStepBack()
    fun studyResetToStart() = studyController.studyResetToStart()
    fun endStudy() = studyController.endStudy()
    fun onStudyChipTapped(depth: Int) = studyController.onChipTapped(depth)
    fun onStudyBranchChipTapped(depth: Int) = studyController.onBranchChipTapped(depth)
    fun onStudyBranchPopupDismiss() = studyController.onBranchPopupDismiss()
    fun onStudyBranchOptionSelected(depth: Int, moveUsi: String) = studyController.onBranchOptionSelected(depth, moveUsi)
    fun onStudyAnalyze() = studyController.analyzeCurrentPosition()

    /** リーク厳禁: 呼び出し元（MainViewModel）の onCleared 相当のタイミングで呼ぶこと。 */
    fun dispose() {
        studyController.dispose()
    }
}
