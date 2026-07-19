package dev.miyado.shogisupplement

import android.content.Intent
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
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.miyado.shogisupplement.ui.DebugScreen
import dev.miyado.shogisupplement.ui.LicensesScreen
import dev.miyado.shogisupplement.ui.MainUiState
import dev.miyado.shogisupplement.ui.MainViewModel
import dev.miyado.shogisupplement.ui.common.ErrorScreen
import dev.miyado.shogisupplement.ui.gamelist.GameListScreen
import dev.miyado.shogisupplement.ui.theme.ShogiTheme

/**
 * アプリのエントリポイント。setContent とナビゲーション分岐（MainApp の when(state)）のみを持つ。
 *
 * 機能ごとに以下のファイルへ分割している:
 * - KifImportFlow.kt: KIF取込フロー（ファイルピッカー/クリップボード/棋力設定/先後選択ダイアログ）
 * - AnalyzingScreen.kt: 解析中画面
 * - HomeHost.kt / ReportHost.kt / AccountHost.kt / SettingsHost.kt:
 *   各画面への MainViewModel 配線（when(state) 分岐の中身をホスト単位で切り出したもの）
 * - GameListScreen / ErrorScreen / RatingSettingsDialog は VM・Android非依存の
 *   純Composableのため :ui commonMain に置く（ヘルプはWebヘルプ=LegalLinks.HELP_WEB_URL）
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // デバッグ: ロック画面越し表示（スクリーンショット確認用）
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

        // 通知権限は起動時ではなく初回の解析開始時に文脈つきで求める（MainApp内。
        // 拒否されても解析は動く——進捗・完了通知が見えなくなるだけ）

        setContent {
            val vm: MainViewModel = viewModel()
            val themeMode by vm.themeMode.collectAsState()
            ShogiTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val state by vm.state.collectAsState()
                    MainApp(vm, state)
                }
            }
        }

        // 通知タップからのコールドスタート: game_id があればレポートを表示
        intent?.getLongExtra(EXTRA_GAME_ID, -1L)?.takeIf { it >= 0L }?.let { gameId ->
            // setContent の後に呼ぶことで ViewModel が初期化済みになっている
            val vm: MainViewModel by viewModels()
            vm.handleNotificationIntent(gameId)
        }
    }

    /** 通知タップによる再起動（singleTop で Activity が再利用される場合）。 */
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

/**
 * 画面遷移のルート Composable。KIF取込フロー（常時コンポジションに含める）を描画したうえで、
 * MainUiState に応じた画面を出し分ける。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(vm: MainViewModel, state: MainUiState) {
    // 棋譜追加ソース選択シート（ファイル vs クリップボード）。HomeScreen/ErrorScreen の
    // onOpenKif と KifImportFlow の双方から開閉するため、この階層にホイストしている。
    var showKifSourceSheet by remember { mutableStateOf(false) }
    // 棋力設定ダイアログ（強さカードの「変更」タップ or KIFフロー初回）。SettingsHost の
    // 「変更」タップと KifImportFlow の双方から開くため、同じ理由でホイストしている。
    var showRatingSettingsDialog by remember { mutableStateOf(false) }

    KifImportFlow(
        vm = vm,
        showKifSourceSheet = showKifSourceSheet,
        onShowKifSourceSheetChange = { showKifSourceSheet = it },
        showRatingSettingsDialog = showRatingSettingsDialog,
        onShowRatingSettingsDialogChange = { showRatingSettingsDialog = it },
    )

    when (state) {
        is MainUiState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is MainUiState.Home -> {
            HomeHost(vm, state, onOpenKif = { showKifSourceSheet = true })
        }
        is MainUiState.Analyzing -> {
            AnalyzingScreen(done = state.done, total = state.total)
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
            // Settings に統一（Account → Licenses → 戻る でも親の Settings へ）
            BackHandler { vm.openSettings() }
            LicensesScreen(onBack = { vm.openSettings() })
        }
        is MainUiState.Settings -> {
            SettingsHost(vm, onOpenRatingSettings = { showRatingSettingsDialog = true })
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
