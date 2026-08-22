package dev.miyado.shogisupplement

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
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
    val analyzeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.analyzeStoredGame(state.game)
    }
    val analyze: () -> Unit = {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            analyzeLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            vm.analyzeStoredGame(state.game)
        }
    }
    ReportScreen(
        game = state.game,
        reports = state.reports,
        flip = state.flip,
        strengthDisplayText = state.strengthDisplayText,
        evalDisplay = state.evalDisplay,
        positionEvals = state.positionEvals,
        matchRateDisplayText = state.matchRateDisplayText,
        blunderRateDisplayText = state.blunderRateDisplayText,
        analysisPending = state.game.analysisStatus == GameAnalysisStatus.PENDING,
        onAnalyze = analyze,
        onDeleteGame = { vm.deleteGame(state.game.id) },
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
        onStudyChipTapped = { depth -> vm.onStudyChipTapped(depth) },
        onStudyBranchChipTapped = { depth -> vm.onStudyBranchChipTapped(depth) },
        onStudyBranchPopupDismiss = { vm.onStudyBranchPopupDismiss() },
        onStudyBranchOptionSelected = { depth, moveUsi -> vm.onStudyBranchOptionSelected(depth, moveUsi) },
        onStudyAnalyze = { vm.onStudyAnalyze() },
        onCopyKif = { kifText ->
            val clip = ClipData.newPlainText("棋譜", kifText)
            context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
        },
    )
}
