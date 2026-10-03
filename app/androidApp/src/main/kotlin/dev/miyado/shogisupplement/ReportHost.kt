package dev.miyado.shogisupplement

import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import dev.miyado.shogisupplement.db.GameAnalysisStatus
import dev.miyado.shogisupplement.ui.MainUiState
import dev.miyado.shogisupplement.ui.MainViewModel
import dev.miyado.shogisupplement.ui.report.ReportScreen

@Composable
fun ReportHost(vm: MainViewModel, state: MainUiState.ShowReport) {
    BackHandler { vm.loadHome() }
    val pvExtState by vm.pvExtState.collectAsState()
    val studyState by vm.studyState.collectAsState()
    val context = LocalContext.current
    val view = LocalView.current
    val analyze: () -> Unit = { vm.analyzeStoredGame(state.report.game) }
    val repertoireOwner by vm.repertoireOwner.collectAsState()
    ReportScreen(
        positionActions = repertoireOwner?.id?.let { owner ->
            { base, moves -> dev.miyado.shogisupplement.ui.repertoire.RepertoirePositionActions(
                vm.repertoireRepository, owner, vm.repertoireSync, base, moves) }
        },
        game = state.report.game,
        reports = state.report.reports,
        flip = state.report.flip,
        strengthDisplayText = state.report.strengthDisplayText,
        evalDisplay = state.evalDisplay,
        positionEvals = state.report.positionEvals,
        matchRateDisplayText = state.report.matchRateDisplayText,
        blunderRateDisplayText = state.report.blunderRateDisplayText,
        analysisPending = state.report.game.analysisStatus == GameAnalysisStatus.PENDING,
        onAnalyze = analyze,
        onReanalyze = { vm.analyzeStoredGame(state.report.game, forceReanalysis = true) },
        hasUnsavedStudy = vm::hasUnsavedStudy,
        onDeleteGame = { deleteServer, onResult ->
            vm.deleteGame(state.report.game, deleteServer, onResult)
        },
        onUpdatePlayers = { senteName, goteName ->
            vm.updatePlayers(state.report.game.id, senteName, goteName)
        },
        justCompleted = state.justCompleted,
        onBack = { vm.loadHome() },
        pvExtState = pvExtState,
        onExtendBestPv = { blunderId, sfenAtEnd, currentPv ->
            vm.extendBestPv(blunderId, sfenAtEnd, currentPv)
        },
        studyState = studyState,
        onStartStudy = { baseSfen, flip, originIsBestPv, originPlyIndex, originSelectedIdx, originAbsolutePly, origin, tappedSquare, tappedHandPieceType ->
            vm.startStudy(
                baseSfen, flip, originIsBestPv, originPlyIndex,
                originSelectedIdx, originAbsolutePly, origin, tappedSquare, tappedHandPieceType,
            )
        },
        onStudySquareTapped = { sq -> vm.onStudySquareTapped(sq) },
        onStudyHandPieceTapped = { pt -> vm.onStudyHandPieceTapped(pt) },
        onStudyPromoteDecision = { promote -> vm.onStudyPromoteDecision(promote) },
        onStudyStepBack = { vm.studyStepBack() },
        onStudyResetToStart = { vm.studyResetToStart() },
        onStudyEnd = { vm.endStudy() },
        onSaveStudy = vm::saveStudy,
        onDeleteStudyBranch = vm::deleteStudyBranch,
        onStudyChipTapped = { depth -> vm.onStudyChipTapped(depth) },
        onStudyBranchChipTapped = { depth -> vm.onStudyBranchChipTapped(depth) },
        onStudyBranchPopupDismiss = { vm.onStudyBranchPopupDismiss() },
        onStudyBranchOptionSelected = { depth, moveUsi -> vm.onStudyBranchOptionSelected(depth, moveUsi) },
        onStudyAnalyze = { vm.onStudyAnalyze() },
        onStudyAutoAnalyze = { vm.onStudyAutoAnalyze() },
        onStudyCandidateSelected = { moveUsi -> vm.onStudyCandidateSelected(moveUsi) },
        onCopyKif = { kifText ->
            val clip = ClipData.newPlainText("棋譜", vm.savedKifForExport(state.report.game.id) ?: kifText)
            context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
        },
        onShare = { shareScreen(context, view) },
    )
}
