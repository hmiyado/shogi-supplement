package dev.miyado.shogisupplement.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.CompositionLocalProvider
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
import dev.miyado.shogisupplement.auth.AuthRepository
import dev.miyado.shogisupplement.crypto.IosTransferSecretStore
import dev.miyado.shogisupplement.db.BlunderRecord
import dev.miyado.shogisupplement.db.DatabaseFactory
import dev.miyado.shogisupplement.db.GameRecord
import dev.miyado.shogisupplement.db.GameAnalysisStatus
import dev.miyado.shogisupplement.db.GameRepository
import dev.miyado.shogisupplement.db.SettingsRepository
import dev.miyado.shogisupplement.pipeline.InProgressAnalysisRegistry
import dev.miyado.shogisupplement.policy.currentBuildNumber
import dev.miyado.shogisupplement.policy.resolvePolicyPlatform
import dev.miyado.shogisupplement.supabase.SupabaseServices
import dev.miyado.shogisupplement.kifu.KifImportController
import dev.miyado.shogisupplement.kifu.KifOrigin
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.transfer.RemoteTransferRestoreService
import dev.miyado.shogisupplement.ui.account.AccountScreen
import dev.miyado.shogisupplement.ui.account.AccountViewModel
import dev.miyado.shogisupplement.ui.common.ShogiThinTopBar
import dev.miyado.shogisupplement.ui.consent.ConsentScreen
import dev.miyado.shogisupplement.ui.debug.DebugScreen
import dev.miyado.shogisupplement.ui.drill.DrillQuestionContent
import dev.miyado.shogisupplement.ui.drill.DrillResultContent
import dev.miyado.shogisupplement.ui.drill.DrillUiState
import dev.miyado.shogisupplement.ui.forceupdate.ForceUpdateScreen
import dev.miyado.shogisupplement.ui.gamelist.GameListScreen
import dev.miyado.shogisupplement.ui.generated.resources.Res
import dev.miyado.shogisupplement.ui.home.HomeScreen
import dev.miyado.shogisupplement.ui.license.LicenseInfoScreen
import dev.miyado.shogisupplement.ui.manual.ManualKifuScreen
import dev.miyado.shogisupplement.ui.report.StudyOrigin
import dev.miyado.shogisupplement.ui.report.AnalyzingReportScreen
import dev.miyado.shogisupplement.ui.report.ReportScreen
import dev.miyado.shogisupplement.ui.report.ReportScreenState
import dev.miyado.shogisupplement.ui.report.toScreenState
import dev.miyado.shogisupplement.ui.restore.GameRestoreScreen
import dev.miyado.shogisupplement.ui.restore.GameRestoreViewModel
import dev.miyado.shogisupplement.ui.settings.RatingSettingsDialog
import dev.miyado.shogisupplement.ui.settings.SettingsScreen
import dev.miyado.shogisupplement.ui.strength.EstimatedStrengthDetailScreen
import dev.miyado.shogisupplement.ui.strength.StrengthDetailData
import dev.miyado.shogisupplement.ui.strength.StrengthDetailViewModel
import dev.miyado.shogisupplement.ui.common.LocalBoardBaseHeight
import dev.miyado.shogisupplement.ui.common.LocalScaffoldContentInsets
import dev.miyado.shogisupplement.ui.theme.ShogiTheme
import dev.miyado.shogisupplement.ui.transfercode.TransferCodeInputDialog
import dev.miyado.shogisupplement.ui.transfercode.TransferCodeInputUiState
import dev.miyado.shogisupplement.ui.transfercode.TransferCodeInputViewModel
import dev.miyado.shogisupplement.ui.transfercode.TransferCodeScreen
import dev.miyado.shogisupplement.upload.DeleteGameOutcome
import dev.miyado.shogisupplement.upload.resultMessage
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform
import platform.Foundation.NSBundle
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIPasteboard
import platform.UIKit.UIViewController

/** Supabase設定時、未同意なら他ルートを遮断し、同意処理の完了後だけホームへ進める。 */
@OptIn(ExperimentalNativeApi::class)
fun MainViewController(): UIViewController = ComposeUIViewController {
    val gameRepository = remember { DatabaseFactory.gameRepository() }
    val drillRepository = remember { DatabaseFactory.drillRepository() }
    val settingsRepository = remember { DatabaseFactory.settingsRepository() }
    // Supabase設定が供給されているときだけアカウント導線と自動アップロードを有効化する
    // （未設定ビルドでは設定画面の行ごと非表示 = graceful degradation）。
    val supabaseServices = remember {
        IosSupabaseConfig.load()?.let { config ->
            SupabaseServices(
                config.url,
                config.key,
                gameRepository,
                drillRepository,
                settingsRepository,
                IosTransferSecretStore(),
                // framework実体との不一致を避けるため、Swift側ではなくKotlinバイナリのDebug判定を使う。
                platform = resolvePolicyPlatform("ios", Platform.isDebugBinary),
            )
        }
    }
    // サーバー解析のベースURL。Supabase設定とは独立に判定する（IosSupabaseConfig参照）。
    // 未設定ならnullのままIosMainControllerが端末解析へフォールバックする。
    val analysisBaseUrl = remember { IosSupabaseConfig.loadAnalysisBaseUrl() }
    val controller = remember {
        IosMainController(
            gameRepository,
            drillRepository,
            settingsRepository,
            supabaseServices?.uploadOrchestrator,
            authRepository = supabaseServices?.authRepository,
            analysisBaseUrl = analysisBaseUrl,
            forceUpdatePolicyChecker = supabaseServices?.forceUpdatePolicyChecker,
        )
    }
    // 同意オンボーディング（iOS専用・初回起動必須）: Supabase設定が供給されているビルドで
    // かつ consent_accepted_at 未保存のときだけ表示する（未設定ビルド=開発用は従来どおり
    // スキップして直接ホームへ進む。graceful degradation）。
    // 一度同意すれば以後のアプリ再起動でも再表示しない（フラグはDB永続化のため）。
    var showConsent by remember {
        mutableStateOf(
            supabaseServices != null &&
                settingsRepository.getConsentAcceptedAt() == null &&
                !settingsRepository.isAccountDeclined(),
        )
    }

    val themeMode by controller.themeMode.collectAsState()
    // 強制アップデート判定は同意オンボーディングより前段でゲートする（同意もできない
    // ビルドで同意画面を出しても無意味なため）。checker未配線（Supabase未設定ビルド）
    // またはチェック未完了（起動直後）はnullで、通常どおり以降の分岐に進む。
    val forceUpdateDecision by controller.forceUpdateDecision.collectAsState()
    ShogiTheme(themeMode = themeMode) {
        Surface(modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing),
            ) {
                // 盤の基準はここで決める。safe areaを引く前のウィンドウ高さを使うと、
                // iOSだけ盤がその分だけ大きくなり画面下が入らなくなる。
                CompositionLocalProvider(
                    LocalBoardBaseHeight provides maxHeight,
                    LocalScaffoldContentInsets provides WindowInsets(0, 0, 0, 0),
                ) {
                    val decision = forceUpdateDecision
                    val services = supabaseServices
                    if (decision != null && decision.blocked) {
                        val versionName = remember {
                            (NSBundle.mainBundle.infoDictionary?.get("CFBundleShortVersionString") as? String) ?: "-"
                        }
                        ForceUpdateScreen(
                            message = decision.message,
                            storeUrl = decision.storeUrl,
                            versionName = versionName,
                            buildNumber = currentBuildNumber(),
                            onOpenStore = { decision.storeUrl?.let { openUrl(it) } },
                        )
                    } else if (showConsent && services != null) {
                        IosConsentScreenHost(
                            services = services,
                            onAccepted = { showConsent = false },
                        )
                    } else {
                        DemoApp(gameRepository, settingsRepository, supabaseServices, controller, analysisBaseUrl)
                    }
                }
            }
        }
    }
}

/** 送信中は二重送信を防ぎ、戻る経路を持たせず同意完了まで他ルートを遮断する。 */
@Composable
private fun IosConsentScreenHost(
    services: SupabaseServices,
    onAccepted: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var isSubmitting by remember { mutableStateOf(false) }

    ConsentScreen(
        isSubmitting = isSubmitting,
        onAccept = { withAccount ->
            if (!isSubmitting) {
                if (!withAccount) {
                    services.consentOrchestrator.declineAccount()
                    onAccepted()
                    return@ConsentScreen
                }
                isSubmitting = true
                scope.launch {
                    services.consentOrchestrator.acceptConsent()
                    isSubmitting = false
                    onAccepted()
                }
            }
        },
        onOpenTerms = { openUrl(IOS_TERMS_URL) },
    )
}

/**
 * iOSデモの画面遷移先。
 */
private sealed class DemoRoute {
    object Home : DemoRoute()
    data class Report(val gameId: Long, val justCompleted: Boolean = false) : DemoRoute()
    object Drill : DemoRoute()
    object Settings : DemoRoute()
    object Licenses : DemoRoute()
    object Account : DemoRoute()
    object TransferCode : DemoRoute()
    object GameList : DemoRoute()
    object ManualKifu : DemoRoute()
    object Debug : DemoRoute()
    /** 推定棋力詳細画面（ホーム画面の推定棋力カードタップで遷移）。 */
    data class StrengthDetail(val data: StrengthDetailData) : DemoRoute()

    /** 引き継ぎコード復元成功後の遷移先（サーバー上の自分の棋譜をダウンロード復元する画面）。 */
    object GameRestore : DemoRoute()
}

@OptIn(ExperimentalNativeApi::class)
@Composable
private fun DemoApp(
    gameRepository: GameRepository,
    settingsRepository: SettingsRepository,
    supabaseServices: SupabaseServices?,
    controller: IosMainController,
    analysisBaseUrl: String? = null,
) {
    var route by remember { mutableStateOf<DemoRoute>(DemoRoute.Home) }
    // 「棋譜を追加する」タップで最初に出す、ファイル/クリップボードの選択ダイアログ。
    var showKifSourceDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val strengthDetailViewModel = remember { StrengthDetailViewModel(gameRepository, settingsRepository) }

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

    // Why not 同意ゲート通過後の条件を明示的に確認する: DemoAppはshowConsent=falseの時だけ
    // 組み立てられるため、ここに置くだけで自然に満たせる。
    LaunchedEffect(controller) {
        controller.resumeIfPending()
    }

    val homeData by controller.homeData.collectAsState()
    val importState by controller.importState.collectAsState()
    val importStep by controller.kifImport.step.collectAsState()
    // ホームの解析中カード用。importStateとは別軸（dismissでImportStateが消えても
    // レジストリ側は生き続ける）ため、ここは直接購読する。
    val analyzingSessions by InProgressAnalysisRegistry.shared.sessions.collectAsState()

    // 解析完了 → 解析した棋譜のレポート画面へ遷移（androidApp と同じ挙動）。
    val completedAnalysis by controller.completedAnalysis.collectAsState()
    LaunchedEffect(completedAnalysis) {
        completedAnalysis?.let { completed ->
            route = DemoRoute.Report(completed.gameId, justCompleted = completed.justCompleted)
            controller.consumeCompletedAnalysis()
        }
    }

    // 取込フローが完了(Idle)に戻ったタイミングでホームへ復帰する
    // （Analyzing/SideConfirm/Error 中は現在のルートを維持し、ダイアログ/進捗画面を重ねて出す）。

    when (val step = importStep) {
        is KifImportController.Step.RatingSetup -> {
            // アカウント名未設定の初回取込: 先に棋力設定。キャンセルは取込フローごと中止する。
            RatingSettingsDialog(
                savedService = controller.getRatingSettings().service,
                savedRatingRaw = controller.getRatingSettings().ratingRaw,
                savedRatingRule = controller.getRatingSettings().ratingRule,
                savedServiceAccounts = controller.getAllServiceAccounts(),
                savedServiceRanks = controller.getAllServiceRanks(),
                onConfirm = { service, ratingRaw, ratingRule, serviceAccountsNew, ranks ->
                    controller.kifImport.completeRatingSetup(
                        service,
                        ratingRaw,
                        ratingRule,
                        serviceAccountsNew,
                        ranks,
                    )
                },
                onDismiss = { controller.kifImport.dismiss() },
            )
        }
        is KifImportController.Step.SideConfirm -> {
            UserSideSimpleDialog(
                senteName = step.kif.senteName,
                goteName = step.kif.goteName,
                suggestedSide = step.suggestion.side,
                showSkipOption = step.suggestion.matchedByAccount,
                onConfirm = { side, skipNext -> controller.kifImport.confirmSide(side, skipNext) },
                onDismiss = { controller.kifImport.dismiss() },
            )
        }
        is KifImportController.Step.AccountCreationConfirm -> {
            AlertDialog(
                onDismissRequest = { controller.kifImport.dismiss() },
                title = { Text(AppStrings.IMPORT_ACCOUNT_NOTICE_TITLE) },
                text = { Text(AppStrings.IMPORT_ACCOUNT_NOTICE_BODY) },
                confirmButton = {
                    TextButton(onClick = { controller.kifImport.confirmAccountCreation() }) {
                        Text(AppStrings.IMPORT_ACCOUNT_NOTICE_CONTINUE)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { controller.kifImport.declineAccount() }) {
                        Text(AppStrings.IMPORT_ACCOUNT_NOTICE_DECLINE)
                    }
                },
            )
        }
        is KifImportController.Step.Failed -> {
            ImportErrorDialog(
                title = if (step.origin == KifOrigin.FILE) AppStrings.KIF_SOURCE_FILE else AppStrings.KIF_SOURCE_CLIPBOARD,
                message = step.message,
                onDismiss = { controller.kifImport.dismiss() },
            )
        }
        else -> {}
    }

    (importState as? IosMainController.ImportState.Error)?.let { error ->
        ImportErrorDialog(
            title = AppStrings.KIF_SOURCE_CLIPBOARD,
            message = error.message,
            onDismiss = { controller.dismissAnalysisError() },
        )
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
            onPickManual = {
                showKifSourceDialog = false
                route = DemoRoute.ManualKifu
            },
            onDismiss = { showKifSourceDialog = false },
        )
    }

    val analyzingState = importState as? IosMainController.ImportState.Analyzing

    if (analyzingState != null) {
        AnalyzingReportScreen(
            titleHint = analyzingState.fileName,
            moves = analyzingState.moves,
            userSide = analyzingState.userSide,
            progressive = analyzingState.progressive,
            // 手動棋譜入力から解析中に入った場合、routeがDemoRoute.ManualKifuのまま
            // 更新されていないため、ここで明示的にHomeへ戻す（route=Homeからの入場では無害）。
            onBack = {
                controller.leaveAnalyzingView()
                route = DemoRoute.Home
            },
        )
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
                    drillRecordCard = data.drillRecordCard,
                    analyzingSessions = analyzingSessions.values.toList(),
                    onOpenKif = { showKifSourceDialog = true },
                    onGameClick = { game -> route = DemoRoute.Report(game.id) },
                    onAnalyzingClick = { session -> controller.resumeAnalyzing(session.id) },
                    onStartDrill = { route = DemoRoute.Drill },
                    onOpenSettings = { route = DemoRoute.Settings },
                    onViewAllGames = { route = DemoRoute.GameList },
                    onOpenStrengthHelp = { openUrl(IOS_HELP_STRENGTH_URL) },
                    onOpenStrengthDetail = {
                        scope.launch {
                            strengthDetailViewModel.loadStrengthDetail()?.let { route = DemoRoute.StrengthDetail(it) }
                        }
                    },
                )
            }
        }
        DemoRoute.ManualKifu -> {
            // route切替をsave直後ではなく保存確定後（Analyzing/Reportへの遷移）に遅らせる
            // （直後に切り替えると、「自分の側」キャンセル時に入力を復元する手段がなくなるため）。
            ManualKifuScreen(
                onClose = { route = DemoRoute.Home },
                onSave = { draft ->
                    controller.beginManualImport(draft.toKifText())
                },
            )
        }
        is DemoRoute.StrengthDetail -> {
            // 対局サービスの編集ダイアログはこの画面専用（Settings画面の棋力入力は廃止済み）。
            var showEditDialog by remember { mutableStateOf(false) }
            if (showEditDialog) {
                RatingSettingsDialog(
                    savedService = controller.getRatingSettings().service,
                    savedRatingRaw = controller.getRatingSettings().ratingRaw,
                    savedRatingRule = controller.getRatingSettings().ratingRule,
                    savedServiceAccounts = controller.getAllServiceAccounts(),
                    savedServiceRanks = controller.getAllServiceRanks(),
                    onConfirm = { service, ratingRaw, ratingRule, serviceAccountsNew, ranks ->
                        controller.saveRatingSettings(service, ratingRaw, ratingRule, serviceAccountsNew, ranks)
                        showEditDialog = false
                        scope.launch {
                            strengthDetailViewModel.loadStrengthDetail()?.let { route = DemoRoute.StrengthDetail(it) }
                        }
                    },
                    onDismiss = { showEditDialog = false },
                )
            }
            EstimatedStrengthDetailScreen(
                data = r.data,
                onBack = { route = DemoRoute.Home },
                onEditAccounts = { showEditDialog = true },
            )
        }
        is DemoRoute.Report -> {
            IosReportScreenHost(
                gameId = r.gameId,
                justCompleted = r.justCompleted,
                controller = controller,
                onBack = { route = DemoRoute.Home },
            )
        }
        DemoRoute.Drill -> {
            IosDrillScreen(
                authRepository = supabaseServices?.authRepository,
                analysisBaseUrl = analysisBaseUrl,
                services = supabaseServices,
                onBack = {
                    route = DemoRoute.Home
                    controller.reloadHome()
                },
            )
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
                onOpenTransferCode = if (supabaseServices != null) {
                    { route = DemoRoute.TransferCode }
                } else {
                    null
                },
                // Kotlin/NativeにBuildConfig相当が無いため、DEBUGビルド判定はPlatform.isDebugBinaryで行う。
                onOpenDebug = if (Platform.isDebugBinary) {
                    { route = DemoRoute.Debug }
                } else {
                    null
                },
                services = supabaseServices,
                analysisBaseUrl = analysisBaseUrl,
                onRestoreSuccess = { route = DemoRoute.GameRestore },
            )
        }
        DemoRoute.Debug -> {
            IosDebugScreenHost(
                onBack = { route = DemoRoute.Settings },
                gameRepository = gameRepository,
                services = supabaseServices,
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
        DemoRoute.TransferCode -> {
            val services = supabaseServices
            if (services == null) {
                // 設定なしでこのルートには到達しない（導線自体が非表示）が、念のため戻す。
                route = DemoRoute.Settings
            } else {
                IosTransferCodeScreenHost(
                    services = services,
                    onBack = { route = DemoRoute.Settings },
                )
            }
        }
        DemoRoute.GameList -> {
            IosGameListScreenHost(
                repository = gameRepository,
                services = supabaseServices,
                controller = controller,
                onBack = { route = DemoRoute.Home },
                onGameClick = { game -> route = DemoRoute.Report(game.id) },
            )
        }
        DemoRoute.GameRestore -> {
            val services = supabaseServices
            if (services == null) {
                // 設定なしでこのルートには到達しない（引き継ぎコード自体がSupabase設定必須）が、
                // 念のため戻す。
                route = DemoRoute.Home
            } else {
                IosGameRestoreScreenHost(
                    services = services,
                    controller = controller,
                    onFinish = {
                        route = DemoRoute.Home
                        controller.reloadHome()
                    },
                )
            }
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
    controller: IosMainController,
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
        onDeleteGame = { game, deleteServer, onResult ->
            scope.launch {
                val outcome = controller.deleteGame(game, deleteServer)
                if (outcome == DeleteGameOutcome.Success) {
                    // ホーム側の再読込完了を待ってからonResultを返す（連続削除時、投げっぱなしだと
                    // 完了順序が保証されず、削除済みの棋譜がホームに残って見えることがある）。
                    games = repository.getAllGames()
                    pendingUploadCount = if (isLoggedIn) repository.getNotUploadedGames().size else 0
                    controller.reloadHomeAndWait()
                }
                onResult(outcome)
            }
        },
        onUpload = {
            val orchestrator = services?.uploadOrchestrator
            if (orchestrator != null && !isUploading) {
                isUploading = true
                uploadResult = null
                scope.launch {
                    val result = orchestrator.uploadAll()
                    games = repository.getAllGames()
                    pendingUploadCount = repository.getNotUploadedGames().size
                    isUploading = false
                    uploadResult = result.resultMessage()
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
    onPickManual: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(AppStrings.KIF_SOURCE_TITLE) },
        text = {
            Column {
                KifSourceOptionRow(AppStrings.KIF_SOURCE_FILE, onPickFile)
                KifSourceOptionRow(AppStrings.KIF_SOURCE_CLIPBOARD, onPickClipboard)
                KifSourceOptionRow(AppStrings.KIF_SOURCE_MANUAL, onPickManual)
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

/** アカウント名一致時だけ、次回から先後確認を省略できる。 */
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

@Composable
private fun IosReportScreenHost(
    gameId: Long,
    controller: IosMainController,
    onBack: () -> Unit,
    justCompleted: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    var report by remember(gameId) { mutableStateOf<ReportScreenState?>(null) }
    var loaded by remember(gameId) { mutableStateOf(false) }

    LaunchedEffect(gameId) {
        report = controller.loadReport(gameId).toScreenState()
        loaded = true
    }

    val current = report
    val g = current?.game
    if (!loaded || current == null || g == null) {
        // 削除直後の一覧再訪等でgameIdがDBに存在しない場合、g==nullのまま固定されスピナーが
        // 永久に残るためここで離脱する。
        if (loaded && g == null) {
            LaunchedEffect(gameId) { onBack() }
        }
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
        reports = current.reports,
        flip = current.flip,
        strengthDisplayText = current.strengthDisplayText,
        evalDisplay = evalDisplay,
        positionEvals = current.positionEvals,
        matchRateDisplayText = current.matchRateDisplayText,
        blunderRateDisplayText = current.blunderRateDisplayText,
        analysisPending = g.analysisStatus == GameAnalysisStatus.PENDING,
        onAnalyze = { controller.analyzeStoredGame(g) },
        onDeleteGame = { deleteServer, onResult ->
            scope.launch {
                val outcome = controller.deleteGame(g, deleteServer)
                onResult(outcome)
                if (outcome == DeleteGameOutcome.Success) {
                    controller.reloadHome()
                    onBack()
                }
            }
        },
        onUpdatePlayers = { senteName, goteName ->
            scope.launch {
                controller.updateGamePlayers(g.id, senteName, goteName)
                // 反映時点の状態から作る。合成時の値を握ると、別の更新と重なって巻き戻る。
                report = report?.let { it.copy(game = it.game.copy(senteName = senteName, goteName = goteName)) }
                controller.reloadHome()
            }
        },
        justCompleted = justCompleted,
        onBack = onBack,
        pvExtState = pvExtState,
        // iOSは読み筋延長のUI導線を非表示にする（決定済み・機能自体は消さない。Androidは不変）。
        pvExtensionEnabled = false,
        onExtendBestPv = { blunderId, sfenAtEnd, currentPv ->
            controller.extendBestPv(blunderId, sfenAtEnd, currentPv) { id, newPv ->
                report = report?.let {
                    it.copy(reports = it.reports.map { r -> if (r.id == id) r.copy(bestPv = newPv) else r })
                }
            }
        },
        studyState = studyState,
        onStartStudy = { baseSfen, sFlip, originIsBestPv, originPlyIndex, originSelectedIdx, originAbsolutePly, origin, tappedSquare, tappedHandPieceType ->
            controller.startStudy(
                baseSfen, sFlip, originIsBestPv, originPlyIndex,
                originSelectedIdx, originAbsolutePly, origin, tappedSquare, tappedHandPieceType,
            )
        },
        onStudySquareTapped = { sq -> controller.onStudySquareTapped(sq) },
        onStudyHandPieceTapped = { pt -> controller.onStudyHandPieceTapped(pt) },
        onStudyPromoteDecision = { promote -> controller.onStudyPromoteDecision(promote) },
        onStudyStepBack = { controller.studyStepBack() },
        onStudyResetToStart = { controller.studyResetToStart() },
        onStudyEnd = { controller.endStudy() },
        onStudyChipTapped = { depth -> controller.onStudyChipTapped(depth) },
        onStudyBranchChipTapped = { depth -> controller.onStudyBranchChipTapped(depth) },
        onStudyBranchPopupDismiss = { controller.onStudyBranchPopupDismiss() },
        onStudyBranchOptionSelected = { depth, moveUsi -> controller.onStudyBranchOptionSelected(depth, moveUsi) },
        onStudyAnalyze = { controller.onStudyAnalyze() },
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
private fun IosDrillScreen(
    onBack: () -> Unit,
    authRepository: AuthRepository? = null,
    analysisBaseUrl: String? = null,
    services: SupabaseServices? = null,
) {
    val vm = remember(authRepository, analysisBaseUrl, services) {
        DrillDemoFactory.create(
            authRepository = authRepository,
            analysisBaseUrl = analysisBaseUrl,
            drillAttemptSync = services?.uploadOrchestrator,
        )
    }
    val state by vm.state.collectAsState()
    val evalDisplay by vm.evalDisplay.collectAsState()
    val pvExtState by vm.pvExtState.collectAsState()

    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ShogiThinTopBar(title = AppStrings.DRILL_TITLE, onBack = onBack)
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
                        onUndoMove = vm::undoLastMove,
                        onResetMoves = vm::resetMoves,
                        onSubmitAnswer = vm::submitAnswer,
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
                        readPv = s.readPv,
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

/** オプションの遷移コールバックがnullなら対応する設定行を表示しない。 */
@Composable
private fun IosSettingsScreenHost(
    controller: IosMainController,
    onBack: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenAccount: (() -> Unit)?,
    onOpenTransferCode: (() -> Unit)? = null,
    onOpenDebug: (() -> Unit)? = null,
    /** 引き継ぎコード入力ダイアログの配線用。null（Supabase未設定ビルド）なら行ごと非表示。 */
    services: SupabaseServices? = null,
    analysisBaseUrl: String? = null,
    /** 引き継ぎコード復元成功後の遷移先（棋譜ダウンロード復元画面）。 */
    onRestoreSuccess: () -> Unit = {},
) {
    val themeMode by controller.themeMode.collectAsState()
    val evalDisplay by controller.evalDisplay.collectAsState()
    val skipSideConfirm by controller.skipSideConfirm.collectAsState()
    var showTransferCodeInput by remember { mutableStateOf(false) }
    val versionName = remember {
        (NSBundle.mainBundle.infoDictionary?.get("CFBundleShortVersionString") as? String) ?: "-"
    }

    if (showTransferCodeInput && services != null && analysisBaseUrl != null) {
        IosTransferCodeInputHost(
            services = services,
            settingsRepository = controller.settings,
            analysisBaseUrl = analysisBaseUrl,
            onDismiss = { showTransferCodeInput = false },
            onRestoreSuccess = {
                showTransferCodeInput = false
                onRestoreSuccess()
            },
        )
    }

    val accountDeclined = controller.isAccountDeclined()

    SettingsScreen(
        versionName = versionName,
        themeMode = themeMode,
        evalDisplay = evalDisplay,
        onBack = onBack,
        onOpenAccount = onOpenAccount,
        onOpenTransferCode = onOpenTransferCode,
        onOpenTransferCodeInput = if (services != null && analysisBaseUrl != null) {
            { showTransferCodeInput = true }
        } else {
            null
        },
        onCreateAccount = if (accountDeclined) onOpenAccount else null,
        onThemeChange = { mode -> controller.saveThemeMode(mode) },
        onEvalDisplayChange = { mode -> controller.saveEvalDisplay(mode) },
        skipSideConfirm = skipSideConfirm,
        onSkipSideConfirmChange = { skip -> controller.saveSkipSideConfirm(skip) },
        onOpenHelp = { openUrl(IOS_HELP_URL) },
        onOpenFeedback = { openUrl(IOS_FEEDBACK_URL) },
        onOpenTerms = { openUrl(IOS_TERMS_URL) },
        onOpenReleaseNotes = { openUrl(IOS_RELEASE_NOTES_URL) },
        onOpenLicenses = onOpenLicenses,
        onOpenDebug = onOpenDebug,
    )
}

/** 保存・クリア後は永続値から再計算し、楽観的な状態を表示しない。 */
@Composable
private fun IosDebugScreenHost(
    onBack: () -> Unit,
    gameRepository: GameRepository,
    services: SupabaseServices?,
) {
    val scope = rememberCoroutineScope()
    var effectiveInfo by remember { mutableStateOf(WasmSiteOverrideStore.effectiveInfo()) }
    val savedInitial = remember { WasmSiteOverrideStore.savedValue() ?: "" }

    DebugScreen(
        onBack = onBack,
        siteBaseUrlInputInitial = savedInitial,
        effectiveSiteBaseUrl = effectiveInfo.url,
        effectiveSiteBaseUrlSource = when (effectiveInfo.source) {
            WasmSiteOverrideStore.Source.ENVIRONMENT -> AppStrings.DEBUG_WASM_SITE_SOURCE_ENV
            WasmSiteOverrideStore.Source.SAVED -> AppStrings.DEBUG_WASM_SITE_SOURCE_SAVED
            WasmSiteOverrideStore.Source.PRODUCTION -> AppStrings.DEBUG_WASM_SITE_SOURCE_PRODUCTION
        },
        onSave = { input ->
            val ok = WasmSiteOverrideStore.save(input)
            if (ok) effectiveInfo = WasmSiteOverrideStore.effectiveInfo()
            ok
        },
        onClear = {
            WasmSiteOverrideStore.clear()
            effectiveInfo = WasmSiteOverrideStore.effectiveInfo()
        },
        // 解析エンジンのキャッシュは消さない: 初期状態の確認を繰り返すたびに
        // 数十MBの再取得を強いるため。
        onWipeLocalData = {
            scope.launch {
                services?.authRepository?.signOut()
                services?.transferSecretStore?.clear()
                gameRepository.deleteAllLocalData()
            }
        },
    )
}

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

/** コードは画面ごとに非同期導出するが、KeychainにSがあれば再生成しない。 */
@Composable
private fun IosTransferCodeScreenHost(
    services: SupabaseServices,
    onBack: () -> Unit,
) {
    var code by remember { mutableStateOf<String?>(null) }
    var regenerateError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(services) {
        code = services.getOrCreateTransferCode()
    }
    TransferCodeScreen(
        code = code,
        onBack = onBack,
        onCopy = { text -> UIPasteboard.generalPasteboard.string = text },
        onRegenerate = {
            scope.launch {
                // 表示を先に消す: 差し替え中に古いコードを書き写させない。
                code = null
                regenerateError = null
                val result = services.transferSecretRegistrar.rotate()
                if (result.isFailure) {
                    regenerateError = AppStrings.TRANSFER_CODE_REGENERATE_FAILED
                }
                code = services.getOrCreateTransferCode()
            }
        },
        regenerateError = regenerateError,
    )
}

/** 成功見出しの重複を避けるため、復元成功時は確認を挟まず復元画面へ遷移する。 */
@OptIn(ExperimentalNativeApi::class)
@Composable
private fun IosTransferCodeInputHost(
    services: SupabaseServices,
    settingsRepository: SettingsRepository,
    analysisBaseUrl: String,
    onDismiss: () -> Unit,
    onRestoreSuccess: () -> Unit,
) {
    val vm = remember(services, analysisBaseUrl) {
        TransferCodeInputViewModel(
            authRepository = services.authRepository,
            transferRestoreService = RemoteTransferRestoreService(
                baseUrl = analysisBaseUrl,
                authRepository = services.authRepository,
                transferSecretStore = IosTransferSecretStore(),
                settingsRepository = settingsRepository,
                platform = resolvePolicyPlatform("ios", Platform.isDebugBinary),
                appCheckTokenProvider = AppCheckTokenBridge::getToken,
            ),
        )
    }
    val state by vm.uiState.collectAsState()

    LaunchedEffect(state) {
        if (state is TransferCodeInputUiState.Success) {
            onRestoreSuccess()
        }
    }

    TransferCodeInputDialog(
        state = state,
        onSubmit = vm::submit,
        onConfirm = vm::confirmRestore,
        onCancelConfirmation = vm::cancelConfirmation,
        onDismiss = {
            vm.dismissError()
            onDismiss()
        },
    )
}

@Composable
private fun IosGameRestoreScreenHost(
    services: SupabaseServices,
    controller: IosMainController,
    onFinish: () -> Unit,
) {
    val vm = remember(services) {
        GameRestoreViewModel(
            gameDownloadService = services.gameDownloadService,
            importGame = controller::importDownloadedGame,
        )
    }
    val state by vm.uiState.collectAsState()
    GameRestoreScreen(
        state = state,
        onStart = vm::startDownload,
        onRetry = vm::retry,
        onAnalyze = {
            controller.analyzePendingGames()
            onFinish()
        },
        onFinish = onFinish,
    )
}

// androidApp の LegalLinks（androidApp モジュール内・:ui からは参照不可）と同じ URL 値。
// LegalLinks.kt 自体は編集禁止対象ではないが、:ui iosMain から androidApp モジュールへは
// 依存できないため、値のみをここに複製する。
private const val IOS_TERMS_URL = "https://shogi-supplement.miyado.dev/terms.html"
private const val IOS_FEEDBACK_URL = "https://x.com/shogisupplement"
private const val IOS_HELP_URL = "https://shogi-supplement.miyado.dev/help.html"
private const val IOS_HELP_STRENGTH_URL = "$IOS_HELP_URL#strength"
private const val IOS_RELEASE_NOTES_URL = "https://shogi-supplement.miyado.dev/release-notes.html"
// LicenseInfoScreen の表示テキスト（AppStrings.LICENSE_SOURCE_URL）と同じ値。
// 実際に開くURLはプラットフォーム側のこの定数を使う（TERMS/FEEDBACKと同じ複製パターン）。
private const val IOS_SOURCE_REPO_URL = "https://github.com/hmiyado/shogi-supplement"

private fun openUrl(url: String) {
    val nsUrl = NSURL.URLWithString(url) ?: return
    UIApplication.sharedApplication.openURL(nsUrl, options = emptyMap<Any?, Any>(), completionHandler = null)
}

/** iOSではContextがないため同梱resourceを同期読込し、失敗時は画面を壊さずnullにする。 */
private fun loadBundledLibraries(): Libs? = runCatching {
    val json = runBlocking { Res.readBytes("files/aboutlibraries.json") }.decodeToString()
    Libs.Builder().withJson(json).build()
}.getOrNull()

@Composable
private fun ImportErrorDialog(title: String, message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(AppStrings.CANCEL) }
        },
    )
}
