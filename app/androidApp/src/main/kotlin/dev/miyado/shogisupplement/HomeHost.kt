package dev.miyado.shogisupplement

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.miyado.shogisupplement.ui.LegalLinks
import dev.miyado.shogisupplement.ui.MainUiState
import dev.miyado.shogisupplement.ui.MainViewModel
import dev.miyado.shogisupplement.ui.home.HomeScreen

/**
 * ホーム画面への MainViewModel 配線。titleIcon（Android専用リソース）をホイストして渡す
 * （HomeScreen 自体は :ui commonMain の VM 非依存 Composable）。
 */
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
        onOpenKif = onOpenKif,
        onGameClick = { game -> vm.showReport(game.id) },
        onStartDrill = { vm.startDrill() },
        onOpenSettings = { vm.openSettings() },
        onViewAllGames = { vm.openGameList() },
        onOpenStrengthHelp = {
            // 推定棋力の説明はWebヘルプ（アプリ内ヘルプ画面は廃止しWebに統一）
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(LegalLinks.HELP_WEB_URL)),
            )
        },
        // タイトル左のアプリアイコン（R.drawable.ic_app_title_icon）は Android専用リソースのため、
        // HomeScreen（:ui commonMain）からホイストしている。
        titleIcon = {
            Image(
                painter = painterResource(id = R.drawable.ic_app_title_icon),
                contentDescription = null,
                modifier = Modifier
                    .height(30.dp)
                    .width(24.dp),
            )
        },
    )
}
