package dev.miyado.shogisupplement

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.miyado.shogisupplement.db.AppDatabase
import dev.miyado.shogisupplement.ui.LegalLinks
import dev.miyado.shogisupplement.ui.MainViewModel
import dev.miyado.shogisupplement.ui.account.AccountScreen
import dev.miyado.shogisupplement.ui.account.AccountViewModel

/** アカウント画面（親は設定画面）への AccountViewModel 配線。 */
@Composable
fun AccountHost(vm: MainViewModel) {
    // アカウント画面の親は設定画面のため、戻る操作は設定画面へ遷移させる
    BackHandler { vm.openSettings() }
    val context = LocalContext.current
    val app = context.applicationContext as ShogiApp
    val authRepo = remember { app.authRepository }
    val gameRepo = remember { AppDatabase.gameRepository(context) }
    val settingsRepo = remember { AppDatabase.settingsRepository(context) }
    val accountVm: AccountViewModel = viewModel(
        factory = AccountViewModel.factory(authRepo, gameRepo, settingsRepo, app.uploadOrchestrator),
    )
    // AccountScreen（:ui commonMain）は AccountViewModel（Android専用）に直接依存できないため、
    // 状態hoisting＋コールバック方式にしている（ReportScreen 等と同じパターン）。
    val accountState by accountVm.uiState.collectAsState()
    AccountScreen(
        state = accountState,
        onBack = { vm.openSettings() },
        onSignInAnonymously = accountVm::signInAnonymously,
        onSetAutoUpload = accountVm::setAutoUpload,
        onManualUpload = accountVm::manualUpload,
        onDeleteAccount = accountVm::deleteAccount,
        onOpenTerms = {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(LegalLinks.TERMS_URL)),
            )
        },
    )
}
