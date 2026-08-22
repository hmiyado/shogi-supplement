package dev.miyado.shogisupplement

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.viewModels
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.miyado.shogisupplement.ui.DebugScreen
import dev.miyado.shogisupplement.ui.LegalLinks
import dev.miyado.shogisupplement.ui.LicensesScreen
import dev.miyado.shogisupplement.ui.MainUiState
import dev.miyado.shogisupplement.ui.MainViewModel
import dev.miyado.shogisupplement.ui.common.ErrorScreen
import dev.miyado.shogisupplement.ui.gamelist.GameListScreen
import dev.miyado.shogisupplement.ui.manual.ManualKifuScreen
import dev.miyado.shogisupplement.ui.theme.ShogiTheme

/** アプリのエントリポイント。 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }

        setContent {
            val vm: MainViewModel = viewModel()
            val themeMode by vm.themeMode.collectAsState()
            ShogiTheme(themeMode = themeMode) {
                Surface(
                    // 内部識別子をリリースのアクセシビリティツリーに
                    // 載せたくないため、UI自動化向けのtestTag露出はDEBUG限定。
                    modifier = Modifier
                        .fillMaxSize()
                        .let { base ->
                            if (BuildConfig.DEBUG) {
                                base.semantics { testTagsAsResourceId = true }
                            } else {
                                base
                            }
                        },
                    color = MaterialTheme.colorScheme.background,
                ) {
                    // 強制アップデート判定はナビゲーション状態（MainUiState）の外側で
                    // ゲートする。ブロック中は KifImportFlow を含む MainApp を一切
                    // コンポジションに含めない（ブロック中は他の画面を描画しない要件）。
                    ForceUpdateHost {
                        val state by vm.state.collectAsState()
                        MainApp(vm, state)
                    }
                }
            }
        }

        intent?.getLongExtra(EXTRA_GAME_ID, -1L)?.takeIf { it >= 0L }?.let { gameId ->
            val vm: MainViewModel by viewModels()
            vm.handleNotificationIntent(gameId)
        }
    }

    /**
     * 起動直後の初回resumeも含めて呼ばれるため、「起動時＋フォアグラウンド復帰時」の
     * チェック要件をこの1箇所で満たす（[ForceUpdateHost] はここが更新するStateFlowを購読するだけ）。
     */
    override fun onResume() {
        super.onResume()
        (application as ShogiApp).checkForceUpdate()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getLongExtra(EXTRA_GAME_ID, -1L).takeIf { it >= 0L }?.let { gameId ->
            val vm: MainViewModel by viewModels()
            vm.handleNotificationIntent(gameId)
        }
    }

    companion object {
        const val EXTRA_GAME_ID = "game_id"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(vm: MainViewModel, state: MainUiState) {
    var showKifSourceSheet by remember { mutableStateOf(false) }
    var showRatingSettingsDialog by remember { mutableStateOf(false) }
    var showManualKifu by remember { mutableStateOf(false) }

    KifImportFlow(
        vm = vm,
        showKifSourceSheet = showKifSourceSheet,
        onShowKifSourceSheetChange = { showKifSourceSheet = it },
        onStartManualKifu = { showManualKifu = true },
        showRatingSettingsDialog = showRatingSettingsDialog,
        onShowRatingSettingsDialogChange = { showRatingSettingsDialog = it },
        // 保存確定前に画面を閉じると、直後の「自分の側」キャンセル時に入力を復元する手段がなくなる。
        onManualKifuHandled = { showManualKifu = false },
    )

    if (showManualKifu) {
        ManualKifuScreen(
            onClose = { showManualKifu = false },
            onSave = { draft ->
                vm.enqueueManualKif(draft.toKifText())
            },
        )
        return
    }

    when (state) {
        is MainUiState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is MainUiState.Home -> {
            HomeHost(vm, state, onOpenKif = { showKifSourceSheet = true })
        }
        is MainUiState.AnalyzingReport -> {
            AnalyzingReportHost(vm, state)
        }
        is MainUiState.ShowReport -> {
            ReportHost(vm, state)
        }
        is MainUiState.Drill -> {
            BackHandler { vm.loadHome() }
            DrillScreen(onBack = { vm.loadHome() })
        }
        is MainUiState.Account -> {
            AccountHost(vm)
        }
        is MainUiState.Licenses -> {
            BackHandler { vm.openSettings() }
            val context = LocalContext.current
            LicensesScreen(
                onBack = { vm.openSettings() },
                onOpenSourceRepo = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(LegalLinks.SOURCE_REPO_URL)),
                    )
                },
            )
        }
        is MainUiState.Settings -> {
            SettingsHost(vm)
        }
        is MainUiState.StrengthDetail -> {
            StrengthDetailHost(vm, state, onEditAccounts = { showRatingSettingsDialog = true })
        }
        is MainUiState.GameList -> {
            BackHandler { vm.loadHome() }
            GameListScreen(
                games = state.games,
                pendingUploadCount = state.pendingUploadCount,
                isUploading = state.isUploading,
                uploadResult = state.uploadResult,
                onBack = { vm.loadHome() },
                onGameClick = { game -> vm.showReport(game.id) },
                onUpload = { vm.uploadFromGameList() },
                onDeleteGame = { game -> vm.deleteGame(game.id) },
            )
        }
        is MainUiState.Error -> {
            BackHandler { vm.loadHome() }
            ErrorScreen(
                message = state.message,
                pastGames = state.pastGames,
                onRetry = { vm.loadHome() },
                onOpenKif = { showKifSourceSheet = true },
                onGameClick = { game -> vm.showReport(game.id) },
            )
        }
        is MainUiState.Debug -> {
            BackHandler { vm.loadHome() }
            DebugScreen(onBack = { vm.loadHome() })
        }
    }
}
