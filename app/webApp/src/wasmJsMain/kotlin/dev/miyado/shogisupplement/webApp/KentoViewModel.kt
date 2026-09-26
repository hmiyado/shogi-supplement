package dev.miyado.shogisupplement.webApp

import dev.miyado.shogisupplement.navigation.AppDestination
import dev.miyado.shogisupplement.navigation.NavigationEvent
import dev.miyado.shogisupplement.navigation.NavigationMachine

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
import dev.miyado.shogisupplement.ui.report.withSavedMainlineEvaluations
import dev.miyado.shogisupplement.ui.report.StudyTree
import dev.miyado.shogisupplement.ui.report.computeSfenAtStep
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** 係数表の配置場所。docs/copy-kento-assets.sh がandroidApp/assetsの正本からコピーする。 */
private const val COEFFICIENTS_URL = "./kento/coefficients_hao_isolate_v1.json"

/**
 * 「棋譜を検討する」ページの状態遷移と、ブラウザ内の解析結果・検討保存を担う。
 */
class KentoViewModel(private val scope: CoroutineScope) : WebStudyActions {
    var state by mutableStateOf(KentoUiState())
        private set

    private var coefTable: CoefficientTable? = null
    private var analysisJob: Job? = null
    private var analysisSequence = 0L
    private var assetDirUrl: String? = null
    private val reportStore = BrowserReportStore()
    private var inputRevision = 0L
    private var resumeJob: Job? = null
    private var studyDocument: BrowserStudyDocument? = null
    private var savedReports = emptyMap<Long, BrowserReportStore.SavedReport>()
    private var pendingStudyDiscard: (() -> Unit)? = null
    private var leavingAfterConfirmation = false
    private val leaveGuard = installBrowserLeaveGuard({ !leavingAfterConfirmation && hasUnsavedStudy() }, null)

    fun hasUnsavedStudy(): Boolean = studyDocument?.document?.isDirty == true || studyOrigins.any { (sfen, path) ->
        val baseline = studyBaselines[path]
        val current = studyController.treeAtOrigin(sfen)
        baseline != null && current != null && current.toKifu(emptyMap()) != baseline.toKifu(emptyMap())
    }

    private fun confirmBeforeDiscard(action: () -> Unit) {
        if (!hasUnsavedStudy()) action()
        else {
            pendingStudyDiscard = action
            state = state.copy(confirmDiscardStudy = true)
        }
    }

    fun cancelDiscardStudy() {
        pendingStudyDiscard = null
        state = state.copy(confirmDiscardStudy = false)
    }

    fun discardStudyAndContinue() {
        val action = pendingStudyDiscard ?: return
        cancelDiscardStudy()
        action()
    }

    fun deleteSavedReport(game: dev.miyado.shogisupplement.db.GameRecord, deleteServer: Boolean,
        onResult: (dev.miyado.shogisupplement.upload.DeleteGameOutcome) -> Unit) {
        val saved = savedReports[game.id]
        if (saved == null || deleteServer) {
            onResult(dev.miyado.shogisupplement.upload.DeleteGameOutcome.LocalFailed)
            return
        }
        scope.launch {
            val deleted = try { reportStore.delete(saved) }
                catch (e: CancellationException) { throw e } catch (_: Exception) { false }
            if (deleted) {
                savedReports = savedReports.filterValues { it.key != saved.key }
                val available = try { reportStore.loadLast() != null }
                    catch (e: CancellationException) { throw e } catch (_: Exception) { state.savedReportAvailable }
                state = state.copy(savedGames = state.savedGames?.filter { it.kifText != saved.report.game.kifText },
                    savedReportAvailable = available)
            }
            onResult(if (deleted) dev.miyado.shogisupplement.upload.DeleteGameOutcome.Success
                else dev.miyado.shogisupplement.upload.DeleteGameOutcome.LocalFailed)
        }
    }

    fun showSavedReports() {
        val event = NavigationEvent.Open(AppDestination.KENTO_LIBRARY)
        if (NavigationMachine.resolve(state.destination, event) == null) return
        val revision = ++inputRevision
        resumeJob?.cancel()
        resumeJob = scope.launch {
            try {
                val reports = reportStore.list()
                if (revision != inputRevision) return@launch
                savedReports = reports.mapIndexed { index, saved -> -(index + 1).toLong() to saved }.toMap()
                state = state.copy(savedGames = savedReports.map { (id, saved) -> saved.report.game.copy(id = id, uploadedAt = null) })
            } catch (e: CancellationException) { throw e } catch (_: Exception) {
                if (revision == inputRevision) state = state.copy(inputError = AppStrings.KENTO_ERROR_GENERIC)
            }
        }
    }

    fun closeSavedReports() {
        if (NavigationMachine.resolve(state.destination, NavigationEvent.Back) != AppDestination.KENTO_INPUT) return
        inputRevision++
        resumeJob?.cancel()
        state = state.copy(savedGames = null)
    }

    fun openSavedReport(game: dev.miyado.shogisupplement.db.GameRecord) {
        val saved = savedReports[game.id] ?: return
        resumeReport(saved.key)
    }
    private var studyOriginMoves: List<String>? = null
    private var studyBaselines = mutableMapOf<List<String>, StudyTree>()
    private val studyOrigins = mutableMapOf<String, List<String>>()

    private val studySaveMutex = Mutex()

    private val studyController = StudyController(
        scope = scope,
        studyEngineFactory = { WorkerStudyEngine(checkNotNull(assetDirUrl)) },
        evalDisplayProvider = { "cp" },
        localEngineLikelyAvailable = { assetDirUrl != null },
        onTreeChanged = { saveStudy {} },
    )
    override val studyState: StateFlow<StudyState?>
        get() = studyController.studyState

    init {
        scope.launch {
            val available = try { reportStore.loadLast() != null }
                catch (e: CancellationException) { throw e } catch (_: Exception) { false }
            state = state.copy(savedReportAvailable = available)
        }
        scope.launch {
            val available = checkEngineAssetsAvailable()
            if (available) assetDirUrl = resolveAssetDirUrl()
            state = state.copy(assetsAvailable = available)
        }
    }

    fun setKifText(text: String) {
        inputRevision++
        state = state.copy(kifText = text)
    }

    fun goHome() = confirmBeforeDiscard {
        inputRevision++
        resumeJob?.cancel()
        leavingAfterConfirmation = true
        kentoBridge().goHome()
    }

    fun leaveReport(action: () -> Unit) = confirmBeforeDiscard(action)

    /** 復元棋譜の検討はこのブラウザへ保存する。既存のローカル検討は上書きしない。 */
    suspend fun openRestoredReport(report: dev.miyado.shogisupplement.webApp.report.WebReportData): Boolean {
        return try {
            val original = requireNotNull(report.game.kifText)
            val existing = reportStore.version(original)
            if (existing == null) {
                val parser = dev.miyado.shogisupplement.kifu.KifTreeParser()
                val incoming = report.game.studyKif ?: original
                require(parser.parse(incoming).mainLineContent() == parser.parse(original).mainLineContent())
                dev.miyado.shogisupplement.ui.report.StudyDocument(incoming) { _, _ -> false }
                BrowserStudyDocument.migrateLegacy(original, BrowserStudyStore("shogi-supplement-reports"))
                check(reportStore.save(report, null, report.game.studyKif ?: original))
            }
            val saved = requireNotNull(reportStore.load(dev.miyado.shogisupplement.util.sha256Hex(original)))
            val study = BrowserStudyDocument.load(original)
            studyController.endStudy()
            studyController.dispose()
            studyDocument = study
            studyOriginMoves = null
            studyBaselines = mutableMapOf()
            studyOrigins.clear()
            state = state.copy(kifText = original, report = saved, inputError = null, restoredReport = true)
            true
        } catch (e: CancellationException) { throw e } catch (_: Exception) { false }
    }

    fun resumeSavedReport() = resumeReport(null)

    private fun resumeReport(key: String?) {
        if (state.analyzing || resumeJob?.isActive == true) return
        val event = NavigationEvent.Open(AppDestination.REPORT)
        if (NavigationMachine.resolve(state.destination, event) != AppDestination.REPORT) return
        val revision = inputRevision
        resumeJob = scope.launch {
            try {
                val report = (if (key == null) reportStore.loadLast() else reportStore.load(key)) ?: return@launch
                val original = requireNotNull(report.game.kifText)
                val study = BrowserStudyDocument.load(original)
                if (inputRevision != revision || state.analyzing) return@launch
                if (NavigationMachine.resolve(state.destination, event) != AppDestination.REPORT) return@launch
                studyController.endStudy()
                studyController.dispose()
                studyDocument = study
                studyOriginMoves = null
                studyBaselines = mutableMapOf()
                studyOrigins.clear()
                state = state.copy(kifText = original, report = report, inputError = null, pendingSideSelection = null, savedGames = null)
            } catch (e: CancellationException) { throw e } catch (_: Exception) {
                if (inputRevision == revision) state = state.copy(inputError = AppStrings.KENTO_ERROR_GENERIC)
            }
        }
    }

    /** パース成功後、解析を即開始せず「自分の側」ダイアログ表示待ちへ遷移する。 */
    fun startAnalysis() {
        if (state.analyzing) return
        inputRevision++
        resumeJob?.cancel()
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
        startAnalysis(input, userSide)
    }

    /** 表示中の棋譜を利用者の明示操作で再解析する。通常表示は既存結果を保持する。 */
    fun reanalyze() = confirmBeforeDiscard { reanalyzeAfterConfirmation() }

    private fun reanalyzeAfterConfirmation() {
        val input = when (val outcome = parseKifInput(state.kifText)) {
            is ParseOutcome.Error -> {
                state = state.copy(inputError = outcome.message)
                return
            }
            is ParseOutcome.Ok -> outcome.input
        }
        startAnalysis(input, state.report?.game?.userSide)
    }

    private fun startAnalysis(input: dev.miyado.shogisupplement.webApp.report.ParsedInput, userSide: String?) {
        if (state.analyzing) return
        val destination = NavigationMachine.next(state.destination, NavigationEvent.AnalysisStarted)
        if (destination != AppDestination.ANALYZING) return
        val requestId = (++analysisSequence).toString()
        analysisJob?.cancel()
        state = state.copy(
            pendingSideSelection = null,
            reanalysisError = null,
            analyzing = destination == AppDestination.ANALYZING,
            analysisRequestId = requestId,
            progressDone = 0,
            progressTotal = input.moves.size + 1,
        )
        analysisJob = scope.launch {
            try {
                val storedVersion = input.kifText?.let { original ->
                    try { reportStore.version(original) }
                    catch (e: CancellationException) { throw e } catch (_: Exception) { null }
                }
                val coef = coefTable ?: CoefficientTable.fromJson(fetchTextAsset(COEFFICIENTS_URL)).also {
                    coefTable = it
                }
                val evals = runEngineAnalysis(input.baseSfenArg, input.moves) { done, total ->
                    if (acceptsAnalysisEvent(requestId)) {
                        state = state.copy(progressDone = done, progressTotal = total)
                    }
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
                if (!acceptsAnalysisEvent(requestId)) return@launch
                val persisted = try { reportStore.save(report, storedVersion) }
                    catch (e: CancellationException) { throw e } catch (_: Exception) { false }
                if (!acceptsAnalysisEvent(requestId)) return@launch
                val savedStudy = try {
                    input.kifText?.let { BrowserStudyDocument.load(it) }
                } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
                if (!acceptsAnalysisEvent(requestId)) return@launch
                val completed = NavigationMachine.next(state.destination, NavigationEvent.AnalysisCompleted)
                if (completed == AppDestination.REPORT) {
                    studyController.endStudy()
                    studyController.dispose()
                    studyDocument = savedStudy
                    studyOriginMoves = null
                    studyBaselines = mutableMapOf()
                    studyOrigins.clear()
                    state = state.copy(analyzing = false, report = report, analysisRequestId = null)
                    if (state.report === report) state = state.copy(
                        savedReportAvailable = state.savedReportAvailable || persisted,
                        reanalysisError = if (persisted) null else AppStrings.STUDY_SAVE_FAILED,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!acceptsAnalysisEvent(requestId)) return@launch
                state = state.copy(
                    analyzing = false,
                    analysisRequestId = null,
                    inputError = AppStrings.KENTO_ERROR_GENERIC,
                    reanalysisError = if (state.report != null) AppStrings.KENTO_ERROR_GENERIC else null,
                )
            }
        }
    }

    fun cancelAnalysis() {
        analysisJob?.cancel()
        state = state.copy(analyzing = false, analysisRequestId = null)
    }

    private fun acceptsAnalysisEvent(requestId: String): Boolean =
        NavigationMachine.acceptsAnalysisEvent(state.destination, state.analysisRequestId, requestId)

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
    ) {
        val report = state.report
        val game = report?.game
        val blunder = originSelectedIdx?.let { report?.reports?.getOrNull(it) }
        val moves = when {
            game == null -> null
            !originIsBestPv && originAbsolutePly in 0..game.movesUsi.size -> game.movesUsi.take(originAbsolutePly)
            originIsBestPv && blunder != null -> {
                val pv = blunder.bestPv?.split(" ")?.filter { it.isNotBlank() }.orEmpty()
                if (originPlyIndex !in 0..pv.size) null
                else game.movesUsi.take((blunder.ply.toInt() - 1).coerceAtLeast(0)) + pv.take(originPlyIndex)
            }
            else -> null
        }
        val path = moves?.takeIf { runCatching { computeSfenAtStep(null, it, it.size) == baseSfen }.getOrDefault(false) }
        studyOriginMoves = path
        val cached = if (path != null && studyOrigins[baseSfen] == path) studyController.treeAtOrigin(baseSfen) else null
        val initial = (cached ?: path?.let { studyDocument?.document?.tree?.subtree(it) } ?: StudyTree()).let { tree ->
            if (path != null && game != null) tree.withSavedMainlineEvaluations(
                game.movesUsi, path, report.positionEvals, flip, "cp",
            ) else tree
        }
        if (path != null) {
            if (cached == null) studyBaselines[path] = initial
            studyOrigins[baseSfen] = path
        }
        studyController.startStudy(
            baseSfen, flip, originIsBestPv, originPlyIndex, originSelectedIdx, originAbsolutePly,
            origin, tappedSquare, tappedHandPieceType, initialTree = initial,
            protectedMoves = if (path == null || game == null) null
                else if (game.movesUsi.take(path.size) == path) game.movesUsi.drop(path.size) else emptyList(),
        )
    }

    override fun savedKifForExport(original: String): String? =
        studyDocument?.document?.savedKif?.takeIf { state.report?.game?.kifText == original }

    override fun saveStudy(onResult: (Boolean) -> Unit) {
        val session = studyDocument
        val path = studyOriginMoves
        val tree = studyController.currentTree()
        if (session == null || path == null || tree == null) { onResult(false); return }
        val edits = studyOrigins.mapNotNull { (sfen, origin) ->
            studyController.treeAtOrigin(sfen)?.let {
                dev.miyado.shogisupplement.ui.report.StudyDocument.Edit(origin, it, studyBaselines[origin])
            }
        }
        val baselines = studyBaselines
        scope.launch {
            studySaveMutex.withLock {
                val saved = try {
                    val rebased = edits.map { it.copy(baseline = baselines[it.originMoves]) }
                    session.document.updateAll(rebased)
                    session.save()
                } catch (e: CancellationException) { throw e } catch (_: Exception) { false }
                if (saved) edits.forEach { baselines[it.originMoves] = it.tree }
                if (studyDocument === session) {
                    studyController.setSaveFailed(!saved)
                }
                onResult(saved)
            }
        }
    }


    override fun deleteStudyBranch(baseSfen: String, moves: List<String>, nodeId: Long?): Boolean =
        studyController.deleteBranchForPosition(baseSfen, moves, nodeId)

    override fun onStudySquareTapped(sq: ShogiSquare) = studyController.onStudySquareTapped(sq)
    override fun onStudyHandPieceTapped(pieceType: PieceType) = studyController.onStudyHandPieceTapped(pieceType)
    override fun onStudyPromoteDecision(promote: Boolean) = studyController.onStudyPromoteDecision(promote)
    override fun studyStepBack() = studyController.studyStepBack()
    override fun studyResetToStart() = studyController.studyResetToStart()
    override fun endStudy() = studyController.endStudy()
    override fun onStudyChipTapped(depth: Int) = studyController.onChipTapped(depth)
    override fun onStudyBranchChipTapped(depth: Int) = studyController.onBranchChipTapped(depth)
    override fun onStudyBranchPopupDismiss() = studyController.onBranchPopupDismiss()
    override fun onStudyBranchOptionSelected(depth: Int, nodeId: Long) = studyController.onBranchNodeSelected(depth, nodeId)
    override fun onStudyAutoAnalyze() = studyController.autoAnalyzeCurrentPosition()
    override fun onStudyAnalyze() = studyController.analyzeCurrentPosition()

    override fun onStudyCandidateSelected(moveUsi: String) = studyController.onCandidateSelected(moveUsi)

    fun dispose() {
        leaveGuard.dispose()
        inputRevision++
        resumeJob?.cancel()
        analysisJob?.cancel()
        state = state.copy(analyzing = false, analysisRequestId = null)
        studyController.dispose()
    }

    private suspend fun resolveAssetDirUrl(): String = suspendCancellableCoroutine { cont ->
        kentoBridge().resolveAssetDirUrl(
            ASSET_BASE_URL,
            onOk = { cont.resume(it) },
            onError = { message -> cont.resumeWithException(IllegalStateException(message)) },
        )
    }
}
