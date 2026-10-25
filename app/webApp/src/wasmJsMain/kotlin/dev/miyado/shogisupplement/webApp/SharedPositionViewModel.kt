package dev.miyado.shogisupplement.webApp

import dev.miyado.shogisupplement.board.PieceType
import dev.miyado.shogisupplement.gameshare.GameShareSnapshot
import dev.miyado.shogisupplement.board.ShogiSquare
import dev.miyado.shogisupplement.ui.report.SharedPosition
import dev.miyado.shogisupplement.ui.report.StudyController
import dev.miyado.shogisupplement.ui.report.StudyEvalState
import dev.miyado.shogisupplement.ui.report.StudyOrigin
import dev.miyado.shogisupplement.ui.report.StudyState
import dev.miyado.shogisupplement.ui.report.StudyTree
import dev.miyado.shogisupplement.ui.report.initialStudyTree
import dev.miyado.shogisupplement.ui.report.hasSameMovesAs
import dev.miyado.shogisupplement.ui.report.selectedPath
import dev.miyado.shogisupplement.webApp.engine.WorkerStudyEngine
import dev.miyado.shogisupplement.webApp.engine.checkEngineAssetsAvailable
import dev.miyado.shogisupplement.webApp.js.kentoBridge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val ASSET_RETRY_DELAY_MS = 2_000L

class SharedPositionViewModel private constructor(
    private val scope: CoroutineScope,
    initialSfen: String,
    flip: Boolean,
    originAbsolutePly: Int,
    private val initialTree: StudyTree?,
    selectedPath: List<String>,
    private val protectedMainline: List<String>?,
    originLabel: String,
    private val sharedGame: Boolean,
) : SharedPositionStudyActions {
    constructor(
        scope: CoroutineScope,
        position: SharedPosition,
        originLabel: String,
    ) : this(
        scope = scope,
        initialSfen = position.sfen,
        flip = position.flip,
        originAbsolutePly = (position.moveNumber - 1).coerceAtLeast(0),
        initialTree = null,
        selectedPath = emptyList(),
        protectedMainline = null,
        originLabel = originLabel,
        sharedGame = false,
    )

    constructor(
        scope: CoroutineScope,
        game: GameShareSnapshot,
        originLabel: String,
    ) : this(
        scope = scope,
        initialSfen = game.initialSfen,
        flip = game.flip,
        originAbsolutePly = 0,
        initialTree = game.initialStudyTree(),
        selectedPath = game.selectedPath(),
        protectedMainline = game.mainline,
        originLabel = originLabel,
        sharedGame = true,
    )
    private var assetDirUrl: String? = null
    private var leavingAfterConfirmation = false
    private val leaveGuard = installBrowserLeaveGuard({ !leavingAfterConfirmation && hasUnsavedVariation() }, null)
    private val studyController = StudyController(
        scope = scope,
        studyEngineFactory = { WorkerStudyEngine(checkNotNull(assetDirUrl)) },
        evalDisplayProvider = { "cp" },
        localEngineLikelyAvailable = { assetDirUrl != null },
    )
    val studyState: StateFlow<StudyState?> = studyController.studyState

    init {
        studyController.startStudy(
            baseSfen = initialSfen,
            flip = flip,
            originIsBestPv = false,
            originPlyIndex = 0,
            originSelectedIdx = null,
            originAbsolutePly = originAbsolutePly,
            origin = StudyOrigin(originLabel, null),
            initialTree = initialTree,
            protectedMoves = protectedMainline,
        )
        if (selectedPath.isNotEmpty()) studyController.navigateToMoves(selectedPath)
        studyController.autoAnalyzeCurrentPosition()
        scope.launch {
            while (true) {
                val available = try { checkEngineAssetsAvailable() }
                catch (e: CancellationException) { throw e } catch (_: Exception) { false }
                if (available) {
                    val resolved = try { resolveAssetDirUrl() }
                    catch (e: CancellationException) { throw e } catch (_: Exception) { null }
                    if (resolved != null) {
                        assetDirUrl = resolved
                        retryAnalysisAfterAssetsReady()
                        break
                    }
                }
                delay(ASSET_RETRY_DELAY_MS)
            }
        }
    }

    private suspend fun resolveAssetDirUrl(): String = suspendCancellableCoroutine { cont ->
        kentoBridge().resolveAssetDirUrl(
            dev.miyado.shogisupplement.webApp.engine.ASSET_BASE_URL,
            onOk = { if (cont.isActive) cont.resume(it) },
            onError = { message -> if (cont.isActive) cont.resumeWithException(IllegalStateException(message)) },
        )
    }

    private fun retryAnalysisAfterAssetsReady() {
        when (studyController.studyState.value?.evalState) {
            StudyEvalState.Preparing, StudyEvalState.Error -> studyController.analyzeCurrentPosition()
            else -> Unit
        }
    }

    override fun hasUnsavedVariation(): Boolean =
        if (sharedGame) {
            val tree = studyController.currentTree()
            tree != null && initialTree != null && !tree.hasSameMovesAs(initialTree)
        } else {
            studyController.currentTree()?.rootChildren?.isNotEmpty() == true
        }

    override fun confirmLeave() {
        leavingAfterConfirmation = true
    }

    fun onSelectGamePly(ply: Int) {
        val mainline = protectedMainline ?: return
        studyController.navigateToMoves(mainline.take(ply.coerceIn(0, mainline.size)))
    }

    override fun onStudySquareTapped(sq: ShogiSquare) = studyController.onStudySquareTapped(sq)
    override fun onStudyHandPieceTapped(pieceType: PieceType) = studyController.onStudyHandPieceTapped(pieceType)
    override fun onStudyStepBack() = studyController.studyStepBack()
    override fun onStudyChipTapped(depth: Int) = studyController.onChipTapped(depth)
    override fun onStudyBranchChipTapped(depth: Int) = studyController.onBranchChipTapped(depth)
    override fun onStudyBranchPopupDismiss() = studyController.onBranchPopupDismiss()
    override fun onStudyBranchOptionSelected(depth: Int, nodeId: Long) = studyController.onBranchNodeSelected(depth, nodeId)
    override fun onStudyAnalyze() = studyController.analyzeCurrentPosition()
    override fun onStudyCandidateSelected(moveUsi: String) = studyController.onCandidateSelected(moveUsi)
    override fun onStudyPromoteDecision(promote: Boolean) = studyController.onStudyPromoteDecision(promote)

    fun dispose() {
        leaveGuard.dispose()
        studyController.dispose()
    }
}
