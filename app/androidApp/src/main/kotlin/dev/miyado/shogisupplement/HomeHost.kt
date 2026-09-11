package dev.miyado.shogisupplement

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.miyado.shogisupplement.ui.LegalLinks
import dev.miyado.shogisupplement.ui.MainUiState
import dev.miyado.shogisupplement.ui.MainViewModel
import dev.miyado.shogisupplement.ui.home.HomeScreen

/** ホーム画面への MainViewModel 配線。 */
@Composable
fun HomeHost(
    vm: MainViewModel,
    state: MainUiState.Home,
    onOpenKif: () -> Unit,
) {
    val context = LocalContext.current
    HomeScreen(
        pastGames = state.pastGames,
        isLoggedIn = state.isLoggedIn,
        strengthCard = state.strengthCard,
        todaysDrillHint = state.todaysDrillHint,
        drillRecordCard = state.drillRecordCard,
        analyzingSessions = state.analyzingSessions,
        onOpenKif = onOpenKif,
        onGameClick = { game -> vm.showReport(game.id) },
        onAnalyzingClick = { session -> vm.resumeAnalyzing(session.id) },
        onStartDrill = { vm.startDrill() },
        onOpenSettings = { vm.openSettings() },
        onViewAllGames = { vm.openGameList() },
        onOpenStrengthHelp = {
            // 推定棋力の説明はWebヘルプの該当節へ直接遷移（アプリ内ヘルプ画面は廃止しWebに統一）
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(LegalLinks.HELP_WEB_STRENGTH_URL)),
            )
        },
        onOpenStrengthDetail = { vm.openStrengthDetail() },
        onOpenDrillRecordDetail = { vm.openDrillRecordDetail() },
    )
}
