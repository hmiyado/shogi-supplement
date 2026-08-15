package dev.miyado.shogisupplement.webApp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.miyado.shogisupplement.board.PieceType
import dev.miyado.shogisupplement.board.ShogiSquare
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.common.UserSideDialog
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
    fun onStudyBranchOptionSelected(depth: Int, moveUsi: String)
    fun onStudyAnalyze()
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
    studyActions: WebStudyActions? = null,
) {
    ShogiTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            // 画面はスマホアプリのレイアウトをそのまま流用しており広幅では
            // 破綻するため、コンテンツ幅をモバイル相当に固定して中央寄せする。
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Box(modifier = Modifier.widthIn(max = 430.dp).fillMaxHeight()) {
                    AppContent(
                        state = state,
                        onBack = onBack,
                        onKifTextChange = onKifTextChange,
                        onStart = onStart,
                        onCancel = onCancel,
                        onConfirmSide = onConfirmSide,
                        onCancelSideSelection = onCancelSideSelection,
                        studyActions = studyActions,
                    )
                }
            }
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
    studyActions: WebStudyActions?,
) {
    val report = state.report
    if (report != null) {
        val studyState = studyActions?.studyState?.collectAsState()?.value
        ReportScreen(
            game = report.game,
            reports = report.reports,
            flip = report.game.userSide == "gote",
            strengthDisplayText = report.strengthText,
            evalDisplay = "cp",
            positionEvals = report.positionEvals,
            matchRateDisplayText = report.matchRateText,
            blunderRateDisplayText = report.blunderRateText,
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
            onStudyAnalyze = { studyActions?.onStudyAnalyze() },
            // Why not 読み筋延長を有効にしない理由: Web版のWorkerは任意局面からのPV延長を
            // 実行する経路を持たないため。
            pvExtensionEnabled = false,
        )
    } else {
        KentoInputScreen(
            state = state,
            onBack = onBack,
            onKifTextChange = onKifTextChange,
            onStart = onStart,
            onCancel = onCancel,
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
) {
    // 低い viewport でもカード全体（解析開始ボタンまで）へ届くようにする。
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
    ) {
        KentoTopBar(onBack = onBack)
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
