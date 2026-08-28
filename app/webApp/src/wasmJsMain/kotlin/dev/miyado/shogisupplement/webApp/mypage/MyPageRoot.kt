package dev.miyado.shogisupplement.webApp.mypage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import dev.miyado.shogisupplement.ui.theme.ShogiTheme

@Composable
fun MyPageRoot() {
    val scope = rememberCoroutineScope()
    val viewModel = remember { MyPageViewModel(scope) }
    ShogiTheme {
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
