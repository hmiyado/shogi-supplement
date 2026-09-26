package dev.miyado.shogisupplement.webApp

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.miyado.shogisupplement.board.PieceType
import dev.miyado.shogisupplement.board.ShogiSquare
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.common.UserSideDialog
import dev.miyado.shogisupplement.ui.common.adaptiveContentWidth
import dev.miyado.shogisupplement.ui.report.ReportScreen
import dev.miyado.shogisupplement.ui.report.StudyOrigin
import dev.miyado.shogisupplement.ui.report.StudyState
import dev.miyado.shogisupplement.ui.theme.ShogiTheme
import kotlinx.coroutines.flow.StateFlow

interface WebStudyActions {
    val studyState: StateFlow<StudyState?>

    fun startStudy(
        baseSfen: String,
        flip: Boolean,
        originIsBestPv: Boolean,
        originPlyIndex: Int,
        originSelectedIdx: Int?,
        originAbsolutePly: Int,
        origin: StudyOrigin,
        tappedSquare: ShogiSquare?,
        tappedHandPieceType: PieceType?,
    )

    fun onStudySquareTapped(sq: ShogiSquare)
    fun onStudyHandPieceTapped(pieceType: PieceType)
    fun onStudyPromoteDecision(promote: Boolean)
    fun studyStepBack()
    fun studyResetToStart()
    fun endStudy()
    fun onStudyChipTapped(depth: Int)
    fun onStudyBranchChipTapped(depth: Int)
    fun onStudyBranchPopupDismiss()
    fun onStudyBranchOptionSelected(depth: Int, nodeId: Long)
    fun onStudyAutoAnalyze()
    fun onStudyAnalyze()

    fun onStudyCandidateSelected(moveUsi: String)
    fun saveStudy(onResult: (Boolean) -> Unit)
    fun savedKifForExport(original: String): String? = null

    fun deleteStudyBranch(baseSfen: String, moves: List<String>, nodeId: Long? = null): Boolean
}

@Composable
fun App(
    state: KentoUiState,
    onBack: () -> Unit,
    onKifTextChange: (String) -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onConfirmSide: (userSide: String?) -> Unit,
    onCancelSideSelection: () -> Unit,
    onReanalyze: () -> Unit = {},
    onResumeSavedReport: () -> Unit = {},
    onShowSavedReports: () -> Unit = {},
    onCloseSavedReports: () -> Unit = {},
    onOpenSavedReport: (dev.miyado.shogisupplement.db.GameRecord) -> Unit = {},
    onDeleteSavedReport: ((dev.miyado.shogisupplement.db.GameRecord, Boolean, (dev.miyado.shogisupplement.upload.DeleteGameOutcome) -> Unit) -> Unit)? = null,
    onCopyKif: (String) -> Unit = {},
    onDiscardStudy: () -> Unit = {},
    onCancelDiscardStudy: () -> Unit = {},
    studyActions: WebStudyActions? = null,
) {
    ShogiTheme {
        WebAppSurface {
            if (state.confirmDiscardStudy) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = onCancelDiscardStudy,
                    title = { Text(AppStrings.STUDY_DISCARD_TITLE) },
                    text = { Text(AppStrings.STUDY_DISCARD_BODY) },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = onDiscardStudy) {
                            Text(AppStrings.MANUAL_KIFU_DISCARD, color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = onCancelDiscardStudy) { Text(AppStrings.CANCEL) }
                    },
                )
            }
            AppContent(
                state = state,
                onBack = onBack,
                onKifTextChange = onKifTextChange,
                onStart = onStart,
                onCancel = onCancel,
                onConfirmSide = onConfirmSide,
                onCancelSideSelection = onCancelSideSelection,
                onReanalyze = onReanalyze,
                onResumeSavedReport = onResumeSavedReport,
                onShowSavedReports = onShowSavedReports,
                onCloseSavedReports = onCloseSavedReports,
                onOpenSavedReport = onOpenSavedReport,
                onDeleteSavedReport = onDeleteSavedReport,
                onCopyKif = onCopyKif,
                studyActions = studyActions,
            )
        }
    }
}

@Composable
private fun AppContent(
    state: KentoUiState,
    onBack: () -> Unit,
    onKifTextChange: (String) -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onConfirmSide: (userSide: String?) -> Unit,
    onCancelSideSelection: () -> Unit,
    onReanalyze: () -> Unit,
    onResumeSavedReport: () -> Unit,
    onShowSavedReports: () -> Unit,
    onCloseSavedReports: () -> Unit,
    onOpenSavedReport: (dev.miyado.shogisupplement.db.GameRecord) -> Unit,
    onDeleteSavedReport: ((dev.miyado.shogisupplement.db.GameRecord, Boolean, (dev.miyado.shogisupplement.upload.DeleteGameOutcome) -> Unit) -> Unit)?,
    onCopyKif: (String) -> Unit,
    studyActions: WebStudyActions?,
) {
    val report = state.report
    val savedGames = state.savedGames
    if (savedGames != null && report == null && !state.analyzing) {
        dev.miyado.shogisupplement.ui.gamelist.GameListScreen(
            games = savedGames,
            canDelete = onDeleteSavedReport != null,
            onBack = onCloseSavedReports,
            onGameClick = onOpenSavedReport,
            onDeleteGame = { game, server, result -> onDeleteSavedReport?.invoke(game, server, result) },
        )
        return
    }
    if (report != null && !state.analyzing) {
        val studyState = studyActions?.studyState?.collectAsState()?.value
        Column(modifier = Modifier.fillMaxSize()) {
            if (state.restoredReport) {
                Text(AppStrings.STUDY_BROWSER_SAVE_NOTICE, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp))
            }
            state.reanalysisError?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp))
            }
            ReportScreen(
            game = report.game,
            reports = report.reports,
            flip = report.game.userSide == "gote",
            strengthDisplayText = report.strengthText,
            evalDisplay = "cp",
            positionEvals = report.positionEvals,
            matchRateDisplayText = report.matchRateText,
            blunderRateDisplayText = report.blunderRateText,
            canEdit = false,
            canDelete = false,
            onReanalyze = onReanalyze,
            onCopyKif = { original -> onCopyKif(studyActions?.savedKifForExport(original) ?: original) },
            onBack = onBack,
            studyState = studyState,
            onStartStudy = { baseSfen, flip, originIsBestPv, originPlyIndex, originSelectedIdx, originAbsolutePly, origin, tappedSquare, tappedHandPieceType ->
                studyActions?.startStudy(
                    baseSfen, flip, originIsBestPv, originPlyIndex, originSelectedIdx,
                    originAbsolutePly, origin, tappedSquare, tappedHandPieceType,
                )
            },
            onStudySquareTapped = { sq -> studyActions?.onStudySquareTapped(sq) },
            onStudyHandPieceTapped = { pieceType -> studyActions?.onStudyHandPieceTapped(pieceType) },
            onStudyPromoteDecision = { promote -> studyActions?.onStudyPromoteDecision(promote) },
            onStudyStepBack = { studyActions?.studyStepBack() },
            onStudyResetToStart = { studyActions?.studyResetToStart() },
            onStudyEnd = { studyActions?.endStudy() },
            onStudyChipTapped = { depth -> studyActions?.onStudyChipTapped(depth) },
            onStudyBranchChipTapped = { depth -> studyActions?.onStudyBranchChipTapped(depth) },
            onStudyBranchPopupDismiss = { studyActions?.onStudyBranchPopupDismiss() },
            onStudyBranchOptionSelected = { depth, moveUsi -> studyActions?.onStudyBranchOptionSelected(depth, moveUsi) },
            onStudyAutoAnalyze = { studyActions?.onStudyAutoAnalyze() },
            onStudyAnalyze = { studyActions?.onStudyAnalyze() },
            onStudyCandidateSelected = { moveUsi -> studyActions?.onStudyCandidateSelected(moveUsi) },
            onSaveStudy = studyActions?.let { actions -> { callback -> actions.saveStudy(callback) } },
            onDeleteStudyBranch = studyActions?.let { actions -> { sfen, moves, nodeId -> actions.deleteStudyBranch(sfen, moves, nodeId) } },
            // Why not 読み筋延長を有効にしない理由: Web版のWorkerは任意局面からのPV延長を
            // 実行する経路を持たないため。
                pvExtensionEnabled = false,
            )
        }
    } else {
        KentoInputScreen(
            state = state,
            onBack = onBack,
            onKifTextChange = onKifTextChange,
            onStart = onStart,
            onCancel = onCancel,
            onResumeSavedReport = onResumeSavedReport,
            onShowSavedReports = onShowSavedReports,
        )
        // ダイアログはPopupのスクリムが背後の操作を塞ぐため、入力カードを隠さず表示したままにする。
        val pending = state.pendingSideSelection
        if (pending != null) {
            UserSideDialog(
                senteName = pending.headers["先手"],
                goteName = pending.headers["後手"],
                savedUserSide = null,
                // Web版は次回省略の対象となるアカウント設定を持たないため常に非表示。
                showSkipOption = false,
                onConfirm = { userSide, _ -> onConfirmSide(userSide) },
                onDismiss = onCancelSideSelection,
            )
        }
    }
}

@Composable
private fun KentoInputScreen(
    state: KentoUiState,
    onBack: () -> Unit,
    onKifTextChange: (String) -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onResumeSavedReport: () -> Unit,
    onShowSavedReports: () -> Unit,
) {
    // 低い viewport でもカード全体（解析開始ボタンまで）へ届くようにする。
    Column(
        modifier = Modifier
            .fillMaxSize()
            .adaptiveContentWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
    ) {
        KentoTopBar(onBack = onBack)
        OutlinedButton(onClick = onShowSavedReports, enabled = !state.analyzing) {
            Text(AppStrings.GAME_LIST_TITLE)
        }
        Box(Modifier.height(48.dp)) {
            OutlinedButton(onClick = onResumeSavedReport, enabled = state.savedReportAvailable && !state.analyzing) {
                Text(AppStrings.KENTO_RESUME_SAVED_REPORT)
            }
        }
        when (state.assetsAvailable) {
            null -> Unit
            false -> Text(
                AppStrings.KENTO_ASSETS_UNAVAILABLE,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            true -> InputCard(
                    kifText = state.kifText,
                onKifTextChange = onKifTextChange,
                    inputError = state.inputError,
                analyzing = state.analyzing,
                progressDone = state.progressDone,
                progressTotal = state.progressTotal,
                onStart = onStart,
                onCancel = onCancel,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
