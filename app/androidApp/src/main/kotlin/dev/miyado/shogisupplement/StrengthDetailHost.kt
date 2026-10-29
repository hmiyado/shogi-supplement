package dev.miyado.shogisupplement

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import dev.miyado.shogisupplement.ui.MainUiState
import dev.miyado.shogisupplement.ui.MainViewModel
import dev.miyado.shogisupplement.ui.strength.EstimatedStrengthDetailScreen

/** 推定棋力詳細画面への VM 配線。対局サービスの編集は個別のダイアログを持たず、[onEditAccounts] 経由で共有ダイアログを開く。 */
@Composable
fun StrengthDetailHost(
    vm: MainViewModel,
    state: MainUiState.StrengthDetail,
    onEditAccounts: () -> Unit,
) {
    BackHandler { vm.loadHome() }
    val context = LocalContext.current
    val view = LocalView.current
    EstimatedStrengthDetailScreen(
        data = state.data,
        onBack = { vm.loadHome() },
        onEditAccounts = onEditAccounts,
        onShare = { shareScreen(context, view) },
    )
}
