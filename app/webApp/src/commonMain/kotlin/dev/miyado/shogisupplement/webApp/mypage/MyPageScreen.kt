package dev.miyado.shogisupplement.webApp.mypage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.miyado.shogisupplement.db.GameRecord
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.gamelist.GameListScreen
import dev.miyado.shogisupplement.ui.report.ReportScreen
import dev.miyado.shogisupplement.ui.transfercode.TransferCodeInputDialog
import dev.miyado.shogisupplement.ui.transfercode.TransferCodeInputUiState

@Composable
fun MyPageScreen(
    state: MyPageUiState,
    transferCodeInputState: TransferCodeInputUiState,
    onSubmitTransferCode: (String) -> Unit,
    onConfirmTransferCode: () -> Unit,
    onCancelTransferCodeConfirmation: () -> Unit,
    onDismissTransferCodeError: () -> Unit,
    onGameClick: (GameRecord) -> Unit,
    onBackFromDetail: () -> Unit,
    onLogout: () -> Unit,
    onCopyKif: (String) -> Unit,
    onBackToList: () -> Unit,
) {
    var showLoginDialog by remember { mutableStateOf(false) }

    LaunchedEffect(transferCodeInputState) {
        if (transferCodeInputState is TransferCodeInputUiState.Success) showLoginDialog = false
    }

    when (state) {
        is MyPageUiState.GameList -> GameListScreen(
            games = state.games,
            canDelete = false,
            // マイページはトップレベルのページで戻り先が無いため、矢印自体を出さない。
            onBack = null,
            onGameClick = onGameClick,
            topBarActions = { TextButton(onClick = onLogout) { Text(AppStrings.MYPAGE_LOGOUT_BUTTON) } },
        )
        MyPageUiState.LoggedOut -> LoginPrompt(onOpenLogin = { showLoginDialog = true })
        MyPageUiState.LoadingGames, MyPageUiState.LoadingDetail -> LoadingView()
        is MyPageUiState.GameDetailView -> ReportScreen(
            game = state.detail.game,
            reports = state.detail.reports,
            flip = state.detail.game.userSide == "gote",
            canDelete = false,
            canEdit = false,
            onBack = onBackFromDetail,
            onCopyKif = onCopyKif,
        )
        is MyPageUiState.Error -> ErrorView(
            message = state.message,
            onLogout = onLogout,
            onBackToList = if (state.previousGames != null) onBackToList else null,
        )
    }

    if (showLoginDialog) {
        TransferCodeInputDialog(
            state = transferCodeInputState,
            onSubmit = onSubmitTransferCode,
            onConfirm = onConfirmTransferCode,
            onCancelConfirmation = onCancelTransferCodeConfirmation,
            onDismiss = {
                onDismissTransferCodeError()
                showLoginDialog = false
            },
        )
    }
}

@Composable
private fun LoginPrompt(onOpenLogin: () -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Text(AppStrings.MYPAGE_LOGIN_DESCRIPTION, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onOpenLogin) {
                Text(AppStrings.MYPAGE_LOGIN_BUTTON)
            }
        }
    }
}

@Composable
private fun LoadingView() {
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        ) {
            CircularProgressIndicator(modifier = Modifier.padding(24.dp))
        }
    }
}

/** ログアウトを常に用意する: リロード以外に復帰手段が無いと、共有ブラウザでセッションを消せなくなる。 */
@Composable
private fun ErrorView(message: String, onLogout: () -> Unit, onBackToList: (() -> Unit)?) {
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(message, color = MaterialTheme.colorScheme.error)
            if (onBackToList != null) {
                Button(onClick = onBackToList) {
                    Text(AppStrings.MYPAGE_BACK_TO_LIST_BUTTON)
                }
            }
            Button(onClick = onLogout) {
                Text(AppStrings.MYPAGE_LOGOUT_BUTTON)
            }
        }
    }
}
