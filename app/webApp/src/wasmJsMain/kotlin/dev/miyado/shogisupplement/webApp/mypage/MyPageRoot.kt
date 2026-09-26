package dev.miyado.shogisupplement.webApp.mypage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.miyado.shogisupplement.webApp.App
import dev.miyado.shogisupplement.webApp.KentoViewModel
import dev.miyado.shogisupplement.webApp.report.WebReportData
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import dev.miyado.shogisupplement.text.AppStrings
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import dev.miyado.shogisupplement.ui.theme.ShogiTheme
import dev.miyado.shogisupplement.webApp.WebAppSurface

@Composable
fun MyPageRoot() {
    val scope = rememberCoroutineScope()
    val viewModel = remember { MyPageViewModel(scope) }
    val detail = (viewModel.state as? MyPageUiState.GameDetailView)?.detail
    if (detail != null) {
        key(detail.game.kifText, detail.game.studyKif) {
            RestoredStudyReport(
                WebReportData(detail.game, detail.reports, emptyList(), null, null, null),
                onBack = viewModel::backToGameList,
            )
        }
        return
    }
    ShogiTheme {
        WebAppSurface {
            MyPageScreen(
                state = viewModel.state,
                transferCodeInputState = viewModel.transferCodeInputViewModel.uiState.collectAsState().value,
                onSubmitTransferCode = viewModel.transferCodeInputViewModel::submit,
                onConfirmTransferCode = viewModel.transferCodeInputViewModel::confirmRestore,
                onCancelTransferCodeConfirmation = viewModel.transferCodeInputViewModel::cancelConfirmation,
                onDismissTransferCodeError = viewModel.transferCodeInputViewModel::dismissError,
                onGameClick = viewModel::openGame,
                onBackFromDetail = viewModel::backToGameList,
                onLogout = viewModel::logout,
                onCopyKif = ::copyTextToClipboard,
                onBackToList = viewModel::backToGameList,
            )
        }
    }
}

@Composable
private fun RestoredStudyReport(report: WebReportData, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val model = remember { KentoViewModel(scope) }
    var ready by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(report) { ready = model.openRestoredReport(report) }
    DisposableEffect(model) { onDispose { model.dispose() } }
    if (ready == true) {
        App(
            state = model.state,
            onBack = { model.leaveReport(onBack) },
            onKifTextChange = model::setKifText,
            onStart = model::startAnalysis,
            onCancel = model::cancelAnalysis,
            onConfirmSide = model::confirmUserSide,
            onCancelSideSelection = model::cancelSideSelection,
            onReanalyze = model::reanalyze,
            onCopyKif = ::copyTextToClipboard,
            onDiscardStudy = model::discardStudyAndContinue,
            onCancelDiscardStudy = model::cancelDiscardStudy,
            studyActions = model,
        )
    } else {
        ShogiTheme {
            WebAppSurface {
                Column(Modifier.padding(16.dp)) {
                    if (ready == null) CircularProgressIndicator()
                    else Text(AppStrings.STUDY_OPEN_FAILED)
                    TextButton(onClick = onBack) { Text(AppStrings.MYPAGE_BACK_TO_LIST_BUTTON) }
                }
            }
        }
    }
}
