package dev.miyado.shogisupplement

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import dev.miyado.shogisupplement.ui.MainUiState
import dev.miyado.shogisupplement.ui.MainViewModel
import dev.miyado.shogisupplement.ui.report.AnalyzingReportScreen

/**
 * 解析中レポート画面への MainViewModel 配線。
 * バック操作はホーム画面へ戻す（解析自体はフォアグラウンドサービス側で継続する）。
 */
@Composable
fun AnalyzingReportHost(vm: MainViewModel, state: MainUiState.AnalyzingReport) {
    BackHandler { vm.loadHome() }
    AnalyzingReportScreen(
        titleHint = state.titleHint,
        moves = state.moves,
        userSide = state.userSide,
        progressive = state.progressive,
        onBack = { vm.loadHome() },
    )
}
