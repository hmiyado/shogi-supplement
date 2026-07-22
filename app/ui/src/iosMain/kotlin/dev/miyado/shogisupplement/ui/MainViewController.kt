package dev.miyado.shogisupplement.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Checkbox
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import com.mikepenz.aboutlibraries.Libs
import dev.miyado.shogisupplement.db.BlunderRecord
import dev.miyado.shogisupplement.db.DatabaseFactory
import dev.miyado.shogisupplement.db.GameRecord
import dev.miyado.shogisupplement.db.GameRepository
import dev.miyado.shogisupplement.db.SettingsRepository
import dev.miyado.shogisupplement.supabase.SupabaseServices
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.account.AccountScreen
import dev.miyado.shogisupplement.ui.account.AccountViewModel
import dev.miyado.shogisupplement.ui.drill.DrillQuestionContent
import dev.miyado.shogisupplement.ui.drill.DrillResultContent
import dev.miyado.shogisupplement.ui.drill.DrillUiState
import dev.miyado.shogisupplement.ui.gamelist.GameListScreen
import dev.miyado.shogisupplement.ui.generated.resources.Res
import dev.miyado.shogisupplement.ui.home.HomeScreen
import dev.miyado.shogisupplement.ui.license.LicenseInfoScreen
import dev.miyado.shogisupplement.ui.report.ReportScreen
import dev.miyado.shogisupplement.ui.settings.RatingSettingsDialog
import dev.miyado.shogisupplement.ui.settings.SettingsScreen
import dev.miyado.shogisupplement.ui.theme.ShogiTheme
import dev.miyado.shogisupplement.upload.UploadResult
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSBundle
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIPasteboard
import platform.UIKit.UIViewController

/**
 * iOS 側から呼び出す ComposeUIViewController のエントリ。
 *
 * androidApp と同じ協力オブジェクト群（[HomeViewModel]・[ReportViewModel]・[StudyController]）を
 * [IosMainController] 経由で使用する。レポート画面は検討モード（駒タップ→着手→エンジン評価）・
 * 読み筋延長（▶+）が動作する。設定画面（[SettingsScreen]）も接続している。
 * 「棋譜を追加する」はファイル/クリップボードの選択ダイアログ経由（[KifSourceDialog]）。
 * ファイルは Swift 側 UIDocumentPickerViewController（KifFilePickerCoordinator.swift）→
 * [IosFileImportBridge] → [IosMainController.handleFileImport] という経路。
 * OSSライセンス画面（[LicenseInfoScreen]）は設定→ライセンスから遷移する。
 * アカウント（データ提供＝匿名認証＋アップロード）は設定→アカウントから遷移する
 * （Supabase設定が無いビルドでは行ごと非表示）。棋力設定はiOS未移植のため非表示。
 * ヘルプはWebヘルプ（Safari）へ接続する。
 *
 * 画面遷移:
 *   ホーム（実データ: games一覧＋推定棋力カード＋今日の1問）
 *     → 棋譜タップ → レポート（実データ・検討モード・読み筋延長が動作）→ 戻る
 *     → 「棋譜を追加する」タップ → ファイル/クリップボード選択ダイアログ
 *       → （ファイル）DocumentPicker → KIF検証 → 先後選択ダイアログ
 *       → （クリップボード）KIF検証 → 先後選択ダイアログ
 *       → 解析中（進捗%・固定行=No-jitter）→ 完了でホームへ戻り再読込
 *     → 今日の1問タップ → ドリル（DrillViewModel駆動）→ 戻る
 *     → ⚙タップ → 設定（テーマ／形勢表示単位／先後確認省略の永続化／ライセンス）→ 戻る
 */
fun MainViewController(): UIViewController = ComposeUIViewController {
    val gameRepository = remember { DatabaseFactory.gameRepository() }
    val drillRepository = remember { DatabaseFactory.drillRepository() }
    val settingsRepository = remember { DatabaseFactory.settingsRepository() }
    // Supabase設定が供給されているときだけアカウント導線と自動アップロードを有効化する
    // （未設定ビルドでは設定画面の行ごと非表示 = graceful degradation）。
    val supabaseServices = remember {
        IosSupabaseConfig.load()?.let { config ->
            SupabaseServices(config.url, config.key, gameRepository, settingsRepository)
        }
    }
    val controller = remember {
        IosMainController(gameRepository, drillRepository, settingsRepository, supabaseServices?.uploadOrchestrator)
    }
    val themeMode by controller.themeMode.collectAsState()
    ShogiTheme(themeMode = themeMode) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing),
            ) {
                DemoApp(gameRepository, settingsRepository, supabaseServices, controller)
            }
        }
    }
}

/**
 * iOSデモの画面遷移先。
 */
private sealed class DemoRoute {
    object Home : DemoRoute()
    data class Report(val gameId: Long) : DemoRoute()
    object Drill : DemoRoute()
    object Settings : DemoRoute()
    object Licenses : DemoRoute()
    object Account : DemoRoute()
    object GameList : DemoRoute()
}

@Composable
private fun DemoApp(
    gameRepository: GameRepository,
    settingsRepository: SettingsRepository,
    supabaseServices: SupabaseServices?,
    controller: IosMainController,
) {
    var route by remember { mutableStateOf<DemoRoute>(DemoRoute.Home) }
    // 「棋譜を追加する」タップで最初に出す、ファイル/クリップボードの選択ダイアログ。
    var showKifSourceDialog by remember { mutableStateOf(false) }

    // リーク厳禁: 画面（ComposeUIViewController）が破棄されるタイミングで検討エンジンを解放する。
    DisposableEffect(controller) {
        onDispose { controller.dispose() }
    }

    // Swift側 UIDocumentPickerViewController の選択結果を受け取り、
    // KIF取込フロー（IosMainController.handleFileImport）へ渡す。
    LaunchedEffect(controller) {
        IosFileImportBridge.result.collect { picked ->
            controller.handleFileImport(picked.fileName, picked.text)
        }
    }

    val homeData by controller.homeData.collectAsState()
    val importState by controller.importState.collectAsState()

    // 解析完了 → 解析した棋譜のレポート画面へ遷移（androidApp と同じ挙動）。
    val completedGameId by controller.completedGameId.collectAsState()
    LaunchedEffect(completedGameId) {
        completedGameId?.let { gameId ->
            route = DemoRoute.Report(gameId)
            controller.consumeCompletedGameId()
        }
    }

    // 取込フローが完了(Idle)に戻ったタイミングでホームへ復帰する
    // （Analyzing/SideConfirm/Error 中は現在のルートを維持し、ダイアログ/進捗画面を重ねて出す）。

    when (val state = importState) {
        is IosMainController.ImportState.RatingSetup -> {
            // アカウント名未設定の初回取込: 先に棋力設定（androidApp と同じ導線）。
            // キャンセルは取込フローごと中止する。
            RatingSettingsDialog(
                savedService = controller.getRatingSettings().service,
                savedRatingRaw = controller.getRatingSettings().ratingRaw,
                savedRatingRule = controller.getRatingSettings().ratingRule,
                savedServiceAccounts = controller.getAllServiceAccounts(),
                savedServiceRanks = controller.getAllServiceRanks(),
                onConfirm = { service, ratingRaw, ratingRule, serviceAccountsNew, ranks ->
                    controller.completeRatingSetup(service, ratingRaw, ratingRule, serviceAccountsNew, ranks)
                },
                onDismiss = { controller.dismissImport() },
            )
        }
        is IosMainController.ImportState.SideConfirm -> {
            UserSideSimpleDialog(
                senteName = state.senteName,
                goteName = state.goteName,
                suggestedSide = state.suggestedSide,
                showSkipOption = state.suggestedByAccount,
                onConfirm = { side, skipNext ->
                    if (state.suggestedByAccount) controller.saveSkipSideConfirm(skipNext)
                    controller.confirmSideAndAnalyze(side)
                },
                onDismiss = { controller.dismissImport() },
            )
        }
        is IosMainController.ImportState.Error -> {
            AlertDialog(
                onDismissRequest = { controller.dismissImport() },
                title = {
                    Text(
                        if (state.fromFile) AppStrings.KIF_SOURCE_FILE else AppStrings.KIF_SOURCE_CLIPBOARD,
                    )
                },
                text = { Text(state.message) },
                confirmButton = {
                    TextButton(onClick = { controller.dismissImport() }) {
                        Text(AppStrings.CANCEL)
                    }
                },
            )
        }
        else -> {}
    }

    // 「棋譜を追加する」タップ後、ファイル/クリップボードの選択ダイアログ。
    if (showKifSourceDialog) {
        KifSourceDialog(
            onPickFile = {
                showKifSourceDialog = false
                IosFileImportBridge.requestOpenFilePicker()
            },
            onPickClipboard = {
                showKifSourceDialog = false
                controller.handleClipboardImport()
            },
            onDismiss = { showKifSourceDialog = false },
        )
    }

    val analyzingState = importState as? IosMainController.ImportState.Analyzing

    if (analyzingState != null) {
        IosAnalyzingScreen(done = analyzingState.done, total = analyzingState.total)
        return
    }

    when (val r = route) {
        DemoRoute.Home -> {
            val data = homeData
            if (data == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                HomeScreen(
                    pastGames = data.games,
                    strengthCard = data.strengthCard,
                    todaysDrillHint = data.todaysDrillHint,
                    onOpenKif = { showKifSourceDialog = true },
                    onGameClick = { game -> route = DemoRoute.Report(game.id) },
                    onStartDrill = { route = DemoRoute.Drill },
                    onOpenSettings = { route = DemoRoute.Settings },
                    onViewAllGames = { route = DemoRoute.GameList },
                    onOpenStrengthHelp = { openUrl(IOS_HELP_STRENGTH_URL) },
                )
            }
        }
        is DemoRoute.Report -> {
            IosReportScreenHost(
                gameId = r.gameId,
                controller = controller,
                onBack = { route = DemoRoute.Home },
            )
        }
        DemoRoute.Drill -> {
            IosDrillScreen(onBack = {
                route = DemoRoute.Home
                controller.reloadHome()
            })
        }
        DemoRoute.Settings -> {
            IosSettingsScreenHost(
                controller = controller,
                onBack = { route = DemoRoute.Home },
                onOpenLicenses = { route = DemoRoute.Licenses },
                onOpenAccount = if (supabaseServices != null) {
                    { route = DemoRoute.Account }
                } else {
                    null
                },
            )
        }
        DemoRoute.Licenses -> {
            val libraries = remember { loadBundledLibraries() }
            LicenseInfoScreen(
                libraries = libraries,
                onBack = { route = DemoRoute.Settings },
                onOpenSourceRepo = { openUrl(IOS_SOURCE_REPO_URL) },
            )
        }
        DemoRoute.Account -> {
            val services = supabaseServices
            if (services == null) {
                // 設定なしでこのルートには到達しない（導線自体が非表示）が、念のため戻す。
                route = DemoRoute.Settings
            } else {
                IosAccountScreenHost(
                    services = services,
                    gameRepository = gameRepository,
                    settingsRepository = settingsRepository,
                    onBack = { route = DemoRoute.Settings },
                )
            }
        }
        DemoRoute.GameList -> {
            IosGameListScreenHost(
                repository = gameRepository,
                services = supabaseServices,
                onBack = { route = DemoRoute.Home },
                onGameClick = { game -> route = DemoRoute.Report(game.id) },
            )
        }
    }
}

/**
 * 棋譜一覧画面。androidApp の MainUiState.GameList と同じ配線:
 * 未アップロード件数はログイン中のみ表示し、一括アップロード導線を持つ。
 */
@Composable
private fun IosGameListScreenHost(
    repository: GameRepository,
    services: SupabaseServices?,
    onBack: () -> Unit,
    onGameClick: (GameRecord) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var games by remember { mutableStateOf(repository.getAllGames()) }
    val isLoggedIn = services?.authRepository?.currentUser?.value != null
    var pendingUploadCount by remember {
        mutableStateOf(if (isLoggedIn) repository.getNotUploadedGames().size else 0)
    }
    var isUploading by remember { mutableStateOf(false) }
    var uploadResult by remember { mutableStateOf<String?>(null) }

    GameListScreen(
        games = games,
        pendingUploadCount = pendingUploadCount,
        isUploading = isUploading,
        uploadResult = uploadResult,
        onBack = onBack,
        onGameClick = onGameClick,
        onUpload = {
            val orchestrator = services?.uploadOrchestrator
            if (orchestrator != null && !isUploading) {
                isUploading = true
                uploadResult = null
                scope.launch {
                    val results = orchestrator.uploadAll()
                    val success = results.values.count {
                        it is UploadResult.Success || it is UploadResult.Duplicate
                    }
                    val failed = results.values.count { it is UploadResult.Failure }
                    games = repository.getAllGames()
                    pendingUploadCount = repository.getNotUploadedGames().size
                    isUploading = false
                    uploadResult = AppStrings.accountUploadResult(success, failed)
                }
            }
        },
    )
}

/**
 * 「棋譜を追加する」タップ直後の取込元選択ダイアログ。
 * Android の KifImportFlow.kt（ModalBottomSheet）の iOS 簡易版。シートではなく
 * シンプルな2択ダイアログにしている。
 */
@Composable
private fun KifSourceDialog(
    onPickFile: () -> Unit,
    onPickClipboard: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(AppStrings.KIF_SOURCE_TITLE) },
        text = {
            Column {
                KifSourceOptionRow(AppStrings.KIF_SOURCE_FILE, onPickFile)
                KifSourceOptionRow(AppStrings.KIF_SOURCE_CLIPBOARD, onPickClipboard)
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(AppStrings.CANCEL)
            }
        },
    )
}

@Composable
private fun KifSourceOptionRow(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    )
}

/**
 * 「棋譜を追加する」タップ後の先後選択ダイアログ。
 * androidApp の UserSideDialog（KifImportFlow.kt）と同じ挙動:
 * 推定側（アカウント名一致 or 前回選択）を初期選択にし、アカウント名一致のときだけ
 * 「次回から省略」チェックボックスを表示する。
 */
@Composable
private fun UserSideSimpleDialog(
    senteName: String?,
    goteName: String?,
    suggestedSide: String?,
    showSkipOption: Boolean,
    onConfirm: (userSide: String, skipNext: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var userSide by remember { mutableStateOf(suggestedSide) }
    var skipNext by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(AppStrings.SIDE_DIALOG_TITLE) },
        text = {
            Column {
                if (senteName != null || goteName != null) {
                    Text(
                        AppStrings.playersLine(senteName, goteName),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                UserSideOptionRow(
                    selected = userSide == "sente",
                    label = AppStrings.sideSente(senteName),
                    onClick = { userSide = "sente" },
                )
                UserSideOptionRow(
                    selected = userSide == "gote",
                    label = AppStrings.sideGote(goteName),
                    onClick = { userSide = "gote" },
                )
                if (showSkipOption) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { skipNext = !skipNext },
                    ) {
                        Checkbox(checked = skipNext, onCheckedChange = { skipNext = it })
                        Text(
                            AppStrings.SKIP_SIDE_CONFIRM_CHECKBOX,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { userSide?.let { onConfirm(it, skipNext) } },
                enabled = userSide != null,
            ) {
                Text(AppStrings.START_ANALYSIS)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(AppStrings.CANCEL)
            }
        },
    )
}

@Composable
private fun UserSideOptionRow(selected: Boolean, label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}

/**
 * 解析中の進捗表示。androidApp の AnalyzingScreen（MainActivity.kt）と同じ構成
 * （DESIGN.md No-jitter原則: 固定高さ・排他的な内容切替。進捗行の位置は準備中/進捗中で変わらない）。
 */
@Composable
private fun IosAnalyzingScreen(done: Int, total: Int) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            if (total > 0) {
                Text(AppStrings.analyzingProgress(done, total))
            } else {
                Text(AppStrings.ANALYZING_PREPARING)
            }
        }
    }
}

/**
 * レポート画面（実データ）。gameId から [IosMainController.loadReport]（内部で
 * [ReportViewModel] 経由）で対局＋悪手を読み込む。検討モード・読み筋延長は
 * controller が保持する ReportViewModel/StudyController を実際に駆動する
 * （androidApp の ReportHost.kt と同型の配線）。
 */
@Composable
private fun IosReportScreenHost(
    gameId: Long,
    controller: IosMainController,
    onBack: () -> Unit,
) {
    var game by remember(gameId) { mutableStateOf<GameRecord?>(null) }
    var reports by remember(gameId) { mutableStateOf<List<BlunderRecord>>(emptyList()) }
    var flip by remember(gameId) { mutableStateOf(false) }
    var strengthText by remember(gameId) { mutableStateOf<String?>(null) }
    var positionEvals by remember(gameId) { mutableStateOf<List<dev.miyado.shogisupplement.db.PositionEvalRow>>(emptyList()) }
    var loaded by remember(gameId) { mutableStateOf(false) }

    LaunchedEffect(gameId) {
        val result = controller.loadReport(gameId)
        game = result.game
        reports = result.reports
        flip = result.flip
        strengthText = result.strengthText
        positionEvals = result.positionEvals
        loaded = true
    }

    val g = game
    if (!loaded || g == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val evalDisplay by controller.evalDisplay.collectAsState()
    val pvExtState by controller.pvExtState.collectAsState()
    val studyState by controller.studyState.collectAsState()

    ReportScreen(
        game = g,
        reports = reports,
        flip = flip,
        strengthDisplayText = strengthText,
        evalDisplay = evalDisplay,
        positionEvals = positionEvals,
        onBack = onBack,
        pvExtState = pvExtState,
        onExtendBestPv = { blunderId, sfenAtEnd, currentPv ->
            controller.extendBestPv(blunderId, sfenAtEnd, currentPv) { id, newPv ->
                reports = reports.map { r -> if (r.id == id) r.copy(bestPv = newPv) else r }
            }
        },
        studyState = studyState,
        onStartStudy = { baseSfen, sFlip, originIsBestPv, originPlyIndex, originSelectedIdx, originAbsolutePly, tappedSquare ->
            controller.startStudy(
                baseSfen, sFlip, originIsBestPv, originPlyIndex,
                originSelectedIdx, originAbsolutePly, tappedSquare,
            )
        },
        onStudySquareTapped = { sq -> controller.onStudySquareTapped(sq) },
        onStudyHandPieceTapped = { pt -> controller.onStudyHandPieceTapped(pt) },
        onStudyPromoteDecision = { promote -> controller.onStudyPromoteDecision(promote) },
        onStudyStepBack = { controller.studyStepBack() },
        onStudyResetToStart = { controller.studyResetToStart() },
        onStudyEnd = { controller.endStudy() },
        // KIFコピー（トップバー⧉アイコン）。iOSはクリップボードへ直接書き込む
        // （Android版 ReportHost.kt の ClipboardManager 相当・snackbar表示は ReportScreen 側）。
        onCopyKif = { kifText -> UIPasteboard.generalPasteboard.string = kifText },
    )
}

/**
 * ドリル画面。androidApp の DrillScreenHost.DrillScreen と同じ構成で、
 * KMP版 DrillViewModel（:ui commonMain・実DB・実エンジン）を駆動する。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IosDrillScreen(onBack: () -> Unit) {
    val vm = remember { DrillDemoFactory.create() }
    val state by vm.state.collectAsState()
    val evalDisplay by vm.evalDisplay.collectAsState()
    val pvExtState by vm.pvExtState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(AppStrings.DRILL_TITLE) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = AppStrings.BACK,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                is DrillUiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is DrillUiState.NoCandidates -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(AppStrings.DRILL_EMPTY_TITLE, style = MaterialTheme.typography.titleMedium)
                    }
                }
                is DrillUiState.Question -> {
                    DrillQuestionContent(
                        state = s,
                        onSquareTapped = vm::onSquareTapped,
                        onHandPieceTapped = vm::onHandPieceTapped,
                        onPromoteDecision = vm::onPromoteDecision,
                        onSurrender = vm::onSurrender,
                    )
                }
                is DrillUiState.Judging -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is DrillUiState.Result -> {
                    DrillResultContent(
                        result = s.drillResult,
                        blunder = s.blunder,
                        sfenBefore = s.sfenBefore,
                        flip = s.flip,
                        evalDisplay = evalDisplay,
                        pvExtState = pvExtState,
                        onExtendBestPv = vm::extendBestPv,
                        onNext = vm::loadNextQuestion,
                        onBack = onBack,
                    )
                }
            }
        }
    }
}

/**
 * 設定画面。SettingsScreen（:ui commonMain）を iOS の
 * [IosMainController] に接続する。Android専用の項目（通知等）は現時点の SettingsScreen には
 * 存在しないため非表示判断は不要。
 *
 * アカウント（データ提供）は Supabase 設定が供給されているビルドでのみ表示する
 * （[onOpenAccount] が null のときは [SettingsScreen] 側で行自体を非表示）。
 * 棋力設定は [RatingSettingsDialog]（:ui commonMain・Androidと共通実装）を開く。
 * ヘルプはWebヘルプ（Safari）へ、OSSライセンスは [LicenseInfoScreen] へ、それぞれ接続する。
 */
@Composable
private fun IosSettingsScreenHost(
    controller: IosMainController,
    onBack: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenAccount: (() -> Unit)?,
) {
    val themeMode by controller.themeMode.collectAsState()
    val evalDisplay by controller.evalDisplay.collectAsState()
    val skipSideConfirm by controller.skipSideConfirm.collectAsState()
    var showRatingSettings by remember { mutableStateOf(false) }
    val versionName = remember {
        (NSBundle.mainBundle.infoDictionary?.get("CFBundleShortVersionString") as? String) ?: "-"
    }

    if (showRatingSettings) {
        RatingSettingsDialog(
            savedService = controller.getRatingSettings().service,
            savedRatingRaw = controller.getRatingSettings().ratingRaw,
            savedRatingRule = controller.getRatingSettings().ratingRule,
            savedServiceAccounts = controller.getAllServiceAccounts(),
            savedServiceRanks = controller.getAllServiceRanks(),
            onConfirm = { service, ratingRaw, ratingRule, serviceAccountsNew, ranks ->
                controller.saveRatingSettings(service, ratingRaw, ratingRule, serviceAccountsNew, ranks)
                showRatingSettings = false
            },
            onDismiss = { showRatingSettings = false },
        )
    }

    SettingsScreen(
        versionName = versionName,
        themeMode = themeMode,
        evalDisplay = evalDisplay,
        onBack = onBack,
        onOpenRatingSettings = { showRatingSettings = true },
        onOpenAccount = onOpenAccount,
        onThemeChange = { mode -> controller.saveThemeMode(mode) },
        onEvalDisplayChange = { mode -> controller.saveEvalDisplay(mode) },
        skipSideConfirm = skipSideConfirm,
        onSkipSideConfirmChange = { skip -> controller.saveSkipSideConfirm(skip) },
        onOpenHelp = { openUrl(IOS_HELP_URL) },
        onOpenFeedback = { openUrl(IOS_FEEDBACK_URL) },
        onOpenTerms = { openUrl(IOS_TERMS_URL) },
        onOpenLicenses = onOpenLicenses,
        // onOpenDebug: iOSにDEBUGビルド用デバッグ画面がまだ無いため常に null（非表示）。
        onOpenDebug = null,
    )
}

/**
 * アカウント画面（データ提供）。androidApp の AccountHost と同じ配線を
 * iOS向けに行う（AccountViewModel は commonMain・状態hoisting＋コールバック方式）。
 */
@Composable
private fun IosAccountScreenHost(
    services: SupabaseServices,
    gameRepository: GameRepository,
    settingsRepository: SettingsRepository,
    onBack: () -> Unit,
) {
    val vm = remember {
        AccountViewModel(
            authRepository = services.authRepository,
            gameRepository = gameRepository,
            settingsRepository = settingsRepository,
            uploadOrchestrator = services.uploadOrchestrator,
        )
    }
    val state by vm.uiState.collectAsState()
    AccountScreen(
        state = state,
        onBack = onBack,
        onSignInAnonymously = vm::signInAnonymously,
        onSetAutoUpload = vm::setAutoUpload,
        onManualUpload = vm::manualUpload,
        onDeleteAccount = vm::deleteAccount,
        onOpenTerms = { openUrl(IOS_TERMS_URL) },
    )
}

// androidApp の LegalLinks（androidApp モジュール内・:ui からは参照不可）と同じ URL 値。
// LegalLinks.kt 自体は編集禁止対象ではないが、:ui iosMain から androidApp モジュールへは
// 依存できないため、値のみをここに複製する。
private const val IOS_TERMS_URL = "https://shogi-supplement.miyado.dev/terms.html"
private const val IOS_FEEDBACK_URL = "https://x.com/shogisupplement"
private const val IOS_HELP_URL = "https://shogi-supplement.miyado.dev/help.html"
private const val IOS_HELP_STRENGTH_URL = "$IOS_HELP_URL#strength"
// LicenseInfoScreen の表示テキスト（AppStrings.LICENSE_SOURCE_URL）と同じ値。
// 実際に開くURLはプラットフォーム側のこの定数を使う（TERMS/FEEDBACKと同じ複製パターン）。
private const val IOS_SOURCE_REPO_URL = "https://github.com/hmiyado/shogi-supplement"

private fun openUrl(url: String) {
    val nsUrl = NSURL.URLWithString(url) ?: return
    UIApplication.sharedApplication.openURL(nsUrl, options = emptyMap<Any?, Any>(), completionHandler = null)
}

/**
 * 依存OSS一覧（AboutLibraries）を compose resources から読み込む。
 * Android の `res/raw/aboutlibraries.json`（`./gradlew :androidApp:exportLibraryDefinitions` で
 * 生成）と同じ内容を ui/src/commonMain/composeResources/files/ に複製したものを読む
 * （iOS には Android の Context 経由読み込みに相当する手段がないため）。
 * フォント読み込み（ui/theme/Type.ios.kt）と同じ runBlocking パターンで同期化する。
 * 読み込みに失敗しても一覧が空になるだけでヘッダ・リポジトリリンクは表示できるため、
 * 画面全体をクラッシュさせず null にフォールバックする。
 */
private fun loadBundledLibraries(): Libs? = runCatching {
    val json = runBlocking { Res.readBytes("files/aboutlibraries.json") }.decodeToString()
    Libs.Builder().withJson(json).build()
}.getOrNull()
