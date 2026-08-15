package dev.miyado.shogisupplement.webApp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.miyado.shogisupplement.judge.CoefficientTable
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.board.PieceType
import dev.miyado.shogisupplement.board.ShogiSquare
import dev.miyado.shogisupplement.ui.report.StudyController
import dev.miyado.shogisupplement.ui.report.StudyOrigin
import dev.miyado.shogisupplement.ui.report.StudyState
import dev.miyado.shogisupplement.webApp.engine.checkEngineAssetsAvailable
import dev.miyado.shogisupplement.webApp.engine.fetchTextAsset
import dev.miyado.shogisupplement.webApp.engine.runEngineAnalysis
import dev.miyado.shogisupplement.webApp.engine.WorkerStudyEngine
import dev.miyado.shogisupplement.webApp.engine.ASSET_BASE_URL
import dev.miyado.shogisupplement.webApp.js.kentoBridge
import dev.miyado.shogisupplement.webApp.report.ParseOutcome
import dev.miyado.shogisupplement.webApp.report.buildWebReport
import dev.miyado.shogisupplement.webApp.report.parseKifInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** 係数表の配置場所。docs/copy-kento-assets.sh がandroidApp/assetsの正本からコピーする。 */
private const val COEFFICIENTS_URL = "./kento/coefficients_hao_isolate_v1.json"

/**
 * 「棋譜を検討する」ページの状態遷移を担う。DBを持たないため、
 * 解析結果はこのインスタンスの寿命内（=タブを開いている間）だけ保持される。
 */
class KentoViewModel(private val scope: CoroutineScope) : WebStudyActions {
    var state by mutableStateOf(KentoUiState())
        private set

    private var coefTable: CoefficientTable? = null
    private var analysisJob: Job? = null
    private var assetDirUrl: String? = null

    private val studyController = StudyController(
        scope = scope,
        studyEngineFactory = { WorkerStudyEngine(checkNotNull(assetDirUrl)) },
        evalDisplayProvider = { "cp" },
        localEngineLikelyAvailable = { true },
    )
    override val studyState: StateFlow<StudyState?>
        get() = studyController.studyState

    init {
        scope.launch {
            val available = checkEngineAssetsAvailable()
            if (available) assetDirUrl = resolveAssetDirUrl()
            state = state.copy(assetsAvailable = available)
        }
        WebStudyBinding.actions = this
    }

    fun setKifText(text: String) {
        state = state.copy(kifText = text)
    }

    fun goHome() {
        kentoBridge().goHome()
    }

    /** パース成功後、解析を即開始せず「自分の側」ダイアログ表示待ちへ遷移する。 */
    fun startAnalysis() {
        if (state.analyzing) return
        val outcome = parseKifInput(state.kifText)
        when (outcome) {
            is ParseOutcome.Error -> {
                state = state.copy(inputError = outcome.message)
            }
            is ParseOutcome.Ok -> {
                state = state.copy(inputError = null, pendingSideSelection = outcome.input)
            }
        }
    }

    /** 側選択ダイアログのキャンセル（外側タップ等）。入力カードへ戻る。 */
    fun cancelSideSelection() {
        state = state.copy(pendingSideSelection = null)
    }

    /** 側選択ダイアログの確定。userSide は null（Web専用の「指定しない」）も許容する。 */
    fun confirmUserSide(userSide: String?) {
        val input = state.pendingSideSelection ?: return
        state = state.copy(
            pendingSideSelection = null,
            analyzing = true,
            progressDone = 0,
            progressTotal = input.moves.size + 1,
        )
        analysisJob = scope.launch {
            try {
                val coef = coefTable ?: CoefficientTable.fromJson(fetchTextAsset(COEFFICIENTS_URL)).also {
                    coefTable = it
                }
                val evals = runEngineAnalysis(input.baseSfenArg, input.moves) { done, total ->
                    state = state.copy(progressDone = done, progressTotal = total)
                }
                val report = buildWebReport(
                    fileName = AppStrings.KENTO_PASTED_GAME_TITLE,
                    moves = input.moves,
                    headers = input.headers,
                    evals = evals,
                    endReason = input.endReason,
                    winner = input.winner,
                    kifText = input.kifText,
                    coef = coef,
                    userSide = userSide,
                )
                state = state.copy(analyzing = false, report = report)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state = state.copy(
                    analyzing = false,
                    inputError = AppStrings.KENTO_ERROR_GENERIC,
                )
            }
        }
    }

    fun cancelAnalysis() {
        analysisJob?.cancel()
        state = state.copy(analyzing = false)
    }

    override fun startStudy(
        baseSfen: String,
        flip: Boolean,
        originIsBestPv: Boolean,
        originPlyIndex: Int,
        originSelectedIdx: Int?,
        originAbsolutePly: Int,
        origin: StudyOrigin,
        tappedSquare: ShogiSquare?,
        tappedHandPieceType: PieceType?,
    ) = studyController.startStudy(
        baseSfen, flip, originIsBestPv, originPlyIndex, originSelectedIdx, originAbsolutePly,
        origin, tappedSquare, tappedHandPieceType,
    )

    override fun onStudySquareTapped(sq: ShogiSquare) = studyController.onStudySquareTapped(sq)
    override fun onStudyHandPieceTapped(pieceType: PieceType) = studyController.onStudyHandPieceTapped(pieceType)
    override fun onStudyPromoteDecision(promote: Boolean) = studyController.onStudyPromoteDecision(promote)
    override fun studyStepBack() = studyController.studyStepBack()
    override fun studyResetToStart() = studyController.studyResetToStart()
    override fun endStudy() = studyController.endStudy()
    override fun onStudyChipTapped(depth: Int) = studyController.onChipTapped(depth)
    override fun onStudyBranchChipTapped(depth: Int) = studyController.onBranchChipTapped(depth)
    override fun onStudyBranchPopupDismiss() = studyController.onBranchPopupDismiss()
    override fun onStudyBranchOptionSelected(depth: Int, moveUsi: String) = studyController.onBranchOptionSelected(depth, moveUsi)
    override fun onStudyAnalyze() = studyController.analyzeCurrentPosition()

    fun dispose() {
        analysisJob?.cancel()
        studyController.dispose()
        if (WebStudyBinding.actions === this) WebStudyBinding.actions = null
    }

    private suspend fun resolveAssetDirUrl(): String = suspendCancellableCoroutine { cont ->
        kentoBridge().resolveAssetDirUrl(
            ASSET_BASE_URL,
            onOk = { cont.resume(it) },
            onError = { message -> cont.resumeWithException(IllegalStateException(message)) },
        )
    }
}
