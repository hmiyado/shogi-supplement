package dev.miyado.shogisupplement.ui

import dev.miyado.shogisupplement.auth.AuthRepository
import dev.miyado.shogisupplement.board.PieceType
import dev.miyado.shogisupplement.board.ShogiSquare
import dev.miyado.shogisupplement.db.DrillRepository
import dev.miyado.shogisupplement.db.GameRecord
import dev.miyado.shogisupplement.db.GameRepository
import dev.miyado.shogisupplement.db.RatingSettings
import dev.miyado.shogisupplement.crash.NoopCrashReporter
import dev.miyado.shogisupplement.db.SettingsRepository
import dev.miyado.shogisupplement.download.GameImportOutcome
import dev.miyado.shogisupplement.download.ReconstructedGame
import dev.miyado.shogisupplement.engine.AnalysisOrchestrator
import dev.miyado.shogisupplement.engine.AnalysisRunner
import dev.miyado.shogisupplement.engine.AuthRetryingAnalyzer
import dev.miyado.shogisupplement.engine.Engine
import dev.miyado.shogisupplement.engine.GameAnalyzer
import dev.miyado.shogisupplement.engine.IosCoefficients
import dev.miyado.shogisupplement.engine.IosEngineHost
import dev.miyado.shogisupplement.engine.IsolatedEngine
import dev.miyado.shogisupplement.engine.FailoverAnalyzer
import dev.miyado.shogisupplement.engine.FailoverEngine
import dev.miyado.shogisupplement.engine.RemoteAnalysisRunner
import dev.miyado.shogisupplement.engine.WasmAnalysisRunner
import dev.miyado.shogisupplement.engine.WasmStudyBridge
import dev.miyado.shogisupplement.engine.WasmStudyEngine
import dev.miyado.shogisupplement.kifu.KifParser
import dev.miyado.shogisupplement.db.saveRatingSettingsBundle
import dev.miyado.shogisupplement.kifu.GameImportFlow
import dev.miyado.shogisupplement.kifu.KifImportController
import dev.miyado.shogisupplement.kifu.KifImportRequest
import dev.miyado.shogisupplement.kifu.GameImporter
import dev.miyado.shogisupplement.pipeline.InProgressAnalysisRegistry
import dev.miyado.shogisupplement.pipeline.ProgressiveReportState
import dev.miyado.shogisupplement.policy.ForceUpdateJudge
import dev.miyado.shogisupplement.policy.ForceUpdatePolicyChecker
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.common.AppSettingsController
import dev.miyado.shogisupplement.ui.common.PvExtState
import dev.miyado.shogisupplement.ui.common.defaultIoDispatcher
import dev.miyado.shogisupplement.ui.home.HomeViewModel
import dev.miyado.shogisupplement.ui.report.ReportViewModel
import dev.miyado.shogisupplement.ui.report.StudyOrigin
import dev.miyado.shogisupplement.ui.report.StudyState
import dev.miyado.shogisupplement.upload.DeleteGameOutcome
import dev.miyado.shogisupplement.upload.GameDeleter
import dev.miyado.shogisupplement.upload.UploadOrchestrator
import dev.miyado.shogisupplement.util.currentEpochSeconds
import dev.miyado.shogisupplement.util.sha256Hex
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.isActive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationWillEnterForegroundNotification
import platform.UIKit.UIPasteboard

/**
 * iOSはプロセス内エンジンを一度しか起動できないため、`quit()`をno-opにしたラッパーで
 * ドリル・取込・検討・読み筋延長の常駐エンジンを共有する。
 * バックグラウンド遷移でストリームが失われてもサーバー解析はmoves_hashで冪等に完走するため、
 * [PendingAnalysisStore]の申告情報から再実行して復旧する。
 */
class IosMainController(
    private val gameRepository: GameRepository,
    private val drillRepository: DrillRepository,
    private val settingsRepository: SettingsRepository,
    /** null = Supabase未設定ビルド（自動アップロードなし）。 */
    private val uploadOrchestrator: UploadOrchestrator? = null,
    /** null = Supabase未設定ビルド（サーバー解析なし・匿名サインイン保証もしない）。 */
    private val authRepository: AuthRepository? = null,
    /** null = ANALYSIS_BASE_URL未設定（端末解析にフォールバックする。graceful degradation）。 */
    private val analysisBaseUrl: String? = null,
    /**
     * null = Supabase未設定ビルド（強制アップデートチェックをスキップする。
     * 同意オンボーディングと同じgraceful degradation）。
     */
    private val forceUpdatePolicyChecker: ForceUpdatePolicyChecker? = null,
) {

    /** 保存後の状態。保存までの手順は [kifImport] が持つ。 */
    sealed class ImportState {
        object Idle : ImportState()

        /**
         * 解析中。手順（moves）はKIF原文パース直後に確定するため解析開始と同時に持ち、
         * [progressive] を解析結果到着のたびに畳み込んでいく。
         */
        data class Analyzing(
            val fileName: String,
            val moves: List<String>,
            val userSide: String?,
            val progressive: ProgressiveReportState,
        ) : ImportState()

        /** 保存または解析の失敗。 */
        data class Error(val message: String) : ImportState()
    }

    private val scope = CoroutineScope(SupervisorJob() + defaultIoDispatcher)
    private val coefTable = IosCoefficients.getInstance()

    /**
     * 全局面のストリーム完了前に切れないよう、Cloud Runより長いタイムアウトで使い回す。
     */
    private val analysisHttpClient: HttpClient? = analysisBaseUrl?.let {
        HttpClient(Darwin) {
            install(HttpTimeout) {
                requestTimeoutMillis = SERVER_ANALYSIS_REQUEST_TIMEOUT_MS
                socketTimeoutMillis = SERVER_ANALYSIS_SOCKET_TIMEOUT_MS
            }
        }
    }

    /**
     * 検討パネルにはキャンセル手段がないため、単発局面解析は短いタイムアウトへ分離する。
     */
    private val studyAnalysisHttpClient: HttpClient? = analysisBaseUrl?.let {
        HttpClient(Darwin) {
            install(HttpTimeout) {
                requestTimeoutMillis = STUDY_ANALYSIS_REQUEST_TIMEOUT_MS
                socketTimeoutMillis = STUDY_ANALYSIS_SOCKET_TIMEOUT_MS
            }
        }
    }

    /** ホーム画面（games一覧・推定棋力カード・今日の1問）のロードを担う協力オブジェクト。 */
    private val homeViewModel = HomeViewModel(
        gameRepository = gameRepository,
        drillRepository = drillRepository,
        settingsRepository = settingsRepository,
    )

    private val appSettings = AppSettingsController(settingsRepository, scope)

    private val gameDeleter = GameDeleter(gameRepository, uploadOrchestrator)

    /** 形勢の表示単位（'cp'/'wp'）。 */
    val evalDisplay: StateFlow<String> = appSettings.evalDisplay

    /** レポート表示状態・読み筋延長・検討モードを担う協力オブジェクト（androidApp と同型）。 */
    val reportViewModel: ReportViewModel = ReportViewModel(
        scope = scope,
        repository = gameRepository,
        engineFactory = studyEngineFactory(),
        evalDisplayProvider = { appSettings.evalDisplay.value },
        localEngineLikelyAvailable = studyLocalEngineLikelyAvailable(),
    )

    /**
     * エンジン入り版は常駐エンジンを共有する。engineless版は認証とURLがある場合だけ
     * [RemoteStudyEngine]を返し、設定漏れでは取込解析と同じく例外を投げる。
     */
    private fun studyEngineFactory(): () -> Engine {
        val auth = authRepository
        val baseUrl = analysisBaseUrl
        val runnerHttpClient = studyAnalysisHttpClient
        if (IosEngineHost.ENGINE_LINKED || auth == null || baseUrl == null || runnerHttpClient == null) {
            return IosEngineHost.studyEngineFactory()
        }
        val runner = RemoteAnalysisRunner(
            baseUrl = baseUrl,
            accessTokenProvider = { checkNotNull(auth.accessToken()) { "アクセストークンが取得できない" } },
            platform = "ios",
            httpClient = runnerHttpClient,
            appCheckTokenProvider = AppCheckTokenBridge::getToken,
        )
        val remoteEngine = RemoteStudyEngine { sfen, moves ->
            // サーバー解析はJWT必須のため、未ログインならここで匿名サインインする
            // （通常は取込解析時に済んでいるはずで、ここに来るのは保険）。
            if (auth.currentUser.value == null) {
                auth.signInAnonymously()
            }
            runner.analyzePosition(sfen, moves)
        }
        // ローカルWASM優先・不可時（WASMバイナリ未準備・ホスト起動失敗）はサーバーへ
        // （FailoverEngine KDoc参照。WasmStudyEngineはfail-fastで即座に例外を投げるため
        // ダウンロード中等で数十秒待たせてから切り替わることはない）。
        return { FailoverEngine(primary = WasmStudyEngine(), secondary = remoteEngine) }
    }

    /**
     * WASM＋サーバー構成では、未準備時の自動フォールバックによるクォータ消費を防ぐため
     * [WasmStudyBridge.localReadyProvider]がfalseなら着手解析を自動発火しない。
     */
    private fun studyLocalEngineLikelyAvailable(): () -> Boolean {
        val auth = authRepository
        val baseUrl = analysisBaseUrl
        val runnerHttpClient = studyAnalysisHttpClient
        if (IosEngineHost.ENGINE_LINKED || auth == null || baseUrl == null || runnerHttpClient == null) {
            return { true }
        }
        return { WasmStudyBridge.localReadyProvider?.invoke() ?: false }
    }

    /** 読み筋オンデマンド延長の状態 Map（blunderId → PvExtState）。ReportViewModel へ委譲。 */
    val pvExtState: StateFlow<Map<Long, PvExtState>> get() = reportViewModel.pvExtState

    /** レポート画面の検討モード状態（null = 検討していない）。ReportViewModel へ委譲。 */
    val studyState: StateFlow<StudyState?> get() = reportViewModel.studyState

    private val _homeData = MutableStateFlow<HomeViewModel.HomeResult?>(null)
    val homeData: StateFlow<HomeViewModel.HomeResult?> = _homeData.asStateFlow()

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    /** KIFを受け取ってから保存を依頼するまでの手順。 */
    val kifImport = KifImportController(
        settingsRepository = settingsRepository,
        scope = scope,
        // 未ログインのまま解析へ進むと匿名アカウントが新規に作られる。
        analysisWouldCreateAccount = {
            authRepository != null && analysisBaseUrl != null && authRepository.currentUser.value == null
        },
        dateTimeLabel = { currentDateTimeLabel() },
        onImport = { request -> importConfirmedKif(request) },
    )

    /** 現在進行中の解析コルーチン（再開時に前回分をキャンセルするため保持する）。 */
    private var currentAnalysisJob: Job? = null

    /** 直近の進捗更新のエポック秒。 */
    private var lastProgressAtEpochSeconds: Long? = null

    /**
     * 解析完了イベント。UI側（MainViewController）がレポート画面への遷移に使い、
     * 遷移したら [consumeCompletedAnalysis] で消費する（androidApp の
     * onAnalysisCompleted → showReport と同じ「完了したらレポートへ」の挙動）。
     * ImportState に Completed を足さない理由: 遷移は一度きりのイベントで、
     * 状態として残すと再コンポーズのたびに遷移が再発火する。
     */
    data class CompletedAnalysis(
        val gameId: Long,
        /** falseは重複KIFの再取込（何も解析していない）。 */
        val justCompleted: Boolean,
    )

    private val _completedAnalysis = MutableStateFlow<CompletedAnalysis?>(null)
    val completedAnalysis: StateFlow<CompletedAnalysis?> = _completedAnalysis.asStateFlow()

    fun consumeCompletedAnalysis() {
        _completedAnalysis.value = null
    }

    /** テーマモード（'system'/'light'/'dark'）。 */
    val themeMode: StateFlow<String> = appSettings.themeMode

    val skipSideConfirm: StateFlow<Boolean> = appSettings.skipSideConfirm

    /**
     * 強制アップデート判定。null = 未チェック（[MainViewController] 側はこの間、
     * 通常の同意/ホーム分岐を出す＝取得中に誤ってブロック画面を出さない）。
     * checker が null（Supabase未設定ビルド）のときは常にnullのまま。
     */
    private val _forceUpdateDecision = MutableStateFlow<ForceUpdateJudge.Decision?>(null)
    val forceUpdateDecision: StateFlow<ForceUpdateJudge.Decision?> = _forceUpdateDecision.asStateFlow()

    init {
        reloadHome()
        // 起動時チェック。フォアグラウンド復帰時は onWillEnterForeground 内で再チェックする。
        checkForceUpdate()
        // Why not MainViewController側で登録: IosMainControllerはプロセス生存期間中1個しか
        // rememberされないため、ここに置くだけで観察者の二重登録が起きない。
        NSNotificationCenter.defaultCenter.addObserverForName(
            UIApplicationWillEnterForegroundNotification,
            null,
            NSOperationQueue.mainQueue,
        ) { onWillEnterForeground() }
    }

    /**
     * 強制アップデート判定を実行し [forceUpdateDecision] を更新する。
     * checker自体が取得失敗→キャッシュ→fail-openの調停を担うため、ここでは結果を
     * そのまま反映するだけでよい（[ForceUpdatePolicyChecker] のKDoc参照）。
     */
    private fun checkForceUpdate() {
        val checker = forceUpdatePolicyChecker ?: return
        scope.launch { _forceUpdateDecision.value = checker.check() }
    }

    fun reloadHome() {
        scope.launch { reloadHomeAndWait() }
    }

    /** 完了を待ってから返す（reloadHomeは投げっぱなしのため、完了順序を保証したい場面では使えない）。 */
    suspend fun reloadHomeAndWait() {
        _homeData.value = homeViewModel.loadHomeData()
    }

    /**
     * 起動時の自動再開。pendingがありローカルDB未保存なら解析を再実行する。
     * 既にDB保存済み（pending削除だけ失敗したケース）なら、二重解析を避けpendingを削除するだけに留める。
     */
    fun resumeIfPending() {
        scope.launch {
            val pending = PendingAnalysisStore.load() ?: return@launch
            if (gameRepository.getByHash(sha256Hex(pending.kifText)) != null) {
                PendingAnalysisStore.clear()
                return@launch
            }
            launchAnalysis(pending)
        }
    }

    /**
     * フォアグラウンド復帰時の再問い合わせ。無進捗時間が閾値を超えた場合のみ再実行する。
     * Why not 無条件に再実行: 短時間のバックグラウンドはストリームが生きていることが多く、
     * 再送すると二重POSTになるため。
     */
    private fun onWillEnterForeground() {
        // 強制アップデート判定は解析再開の無進捗しきい値とは無関係の独立した関心事のため、
        // 常に（無条件で）再チェックする。
        checkForceUpdate()
        if (!shouldResumeAfterForeground(_importState.value, lastProgressAtEpochSeconds, currentEpochSeconds())) {
            return
        }
        val pending = PendingAnalysisStore.load() ?: return
        launchAnalysis(pending)
    }

    /** 特定のゲームIDのレポート表示状態をDBから読み込む（ReportViewModel へ委譲）。 */
    suspend fun loadReport(gameId: Long): ReportViewModel.ReportResult = reportViewModel.loadReport(gameId)

    suspend fun deleteGame(game: GameRecord, deleteServer: Boolean): DeleteGameOutcome =
        withContext(defaultIoDispatcher) { gameDeleter.delete(game, deleteServer) }

    suspend fun updateGamePlayers(gameId: Long, senteName: String?, goteName: String?) =
        withContext(defaultIoDispatcher) { gameRepository.updateGamePlayers(gameId, senteName, goteName) }

    fun saveThemeMode(mode: String) = appSettings.saveThemeMode(mode)

    fun saveEvalDisplay(mode: String) = appSettings.saveEvalDisplay(mode)

    fun saveSkipSideConfirm(skip: Boolean) = appSettings.saveSkipSideConfirm(skip)

    /**
     * UIPasteboardの同期読みはIPCやiCloud取得でブロックし得るため、[scope]で読む。
     */
    fun handleClipboardImport() {
        scope.launch {
            val text = UIPasteboard.generalPasteboard.string
            handlePastedText(text)
        }
    }

    fun handlePastedText(text: String?) = kifImport.beginFromClipboard(text)

    fun beginManualImport(kifText: String, fileName: String = "manual.kif") =
        kifImport.beginManual(kifText, fileName)

    /** 引き継ぎ復元の成功時に、作らない判断を戻すために渡す。 */
    val settings: SettingsRepository get() = settingsRepository

    /** アカウントを作らないと決めた端末か。設定に作成の導線を出すかが分かれる。 */
    fun isAccountDeclined(): Boolean = settingsRepository.isAccountDeclined()

    /**
     * 断ったあとに気が変わったときの受け皿。次の解析からサーバーを使う。
     * アカウント自体は解析時に作られるため、ここでは判断だけを戻す。
     */
    fun undoAccountDecline() {
        settingsRepository.saveAccountDeclined(false)
    }

    /** 棋力設定の一括保存（設定画面から。取込フロー中は [kifImport] が保存する）。 */
    fun saveRatingSettings(
        service: String?,
        ratingRaw: Int?,
        ratingRule: String?,
        serviceAccounts: Map<String, String>,
        serviceRanks: Map<String, Map<String, Int>>,
    ) = settingsRepository.saveRatingSettingsBundle(service, ratingRaw, ratingRule, serviceAccounts, serviceRanks)

    /** 棋力設定ダイアログの初期値（設定画面・取込フロー共用）。 */
    fun getRatingSettings(): RatingSettings = settingsRepository.getRatingSettings()

    fun getAllServiceAccounts(): Map<String, String> = settingsRepository.getAllServiceAccounts()

    fun getAllServiceRanks(): Map<String, Map<String, Int>> = settingsRepository.getAllServiceRanks()

    /**
     * レジストリの現在スナップショットからImportState.Analyzingへ入る（0からやり直さない）。
     * 完了・失敗との競合でスナップショットが消えていれば何もしない。
     */
    fun resumeAnalyzing(id: String) {
        val session = InProgressAnalysisRegistry.shared.snapshot(id) ?: return
        _importState.value = ImportState.Analyzing(
            fileName = session.fileName,
            moves = session.progressive.moves,
            userSide = session.userSide,
            progressive = session.progressive,
        )
    }

    /** 保存・解析の失敗ダイアログを閉じる。中断した解析の再開情報も破棄する。 */
    fun dismissAnalysisError() {
        PendingAnalysisStore.clear()
        _importState.value = ImportState.Idle
    }

    /** 解析中の画面から離れる。解析自体は続くため再開情報は残す。 */
    fun leaveAnalyzingView() {
        _importState.value = ImportState.Idle
    }

    /**
     * @param text nullはUTF-8/Shift_JISのデコード失敗で、空・不正と同じエラーにする。
     */
    fun handleFileImport(fileName: String, text: String?) = kifImport.beginFromFile(fileName, text)

    /** 新規なら解析を即開始し、既存棋譜と同一ハッシュなら再解析しない。 */
    private suspend fun importConfirmedKif(request: KifImportRequest) {
        val next = GameImportFlow(gameRepository).import(
            kifContent = request.kifText,
            fileName = request.fileName,
            userSide = request.userSide,
            ratingService = request.ratingService,
            ratingRaw = request.ratingRaw,
            ratingRule = request.ratingRule,
        )
        when (next) {
            is GameImportFlow.Next.Analyze -> analyzeStoredGame(next.game)
            is GameImportFlow.Next.OpenReport -> {
                _importState.value = ImportState.Idle
                reloadHome()
                _completedAnalysis.value = CompletedAnalysis(next.gameId, justCompleted = false)
            }
            is GameImportFlow.Next.Failed -> _importState.value = ImportState.Error(next.message)
        }
    }

    fun analyzeStoredGame(game: dev.miyado.shogisupplement.db.GameRecord) {
        val pending = game.toPendingAnalysis() ?: return
        PendingAnalysisStore.save(pending)
        launchAnalysis(pending)
    }

    /** 3経路（通常取込・フォアグラウンド復帰・起動時再開）が共通で通る。既存ジョブは先にキャンセルするため二重に走らない。 */
    private fun launchAnalysis(pending: PendingAnalysis) {
        currentAnalysisJob?.cancel()
        lastProgressAtEpochSeconds = currentEpochSeconds()
        val moves = runCatching { KifParser().parse(pending.kifText).moves }.getOrElse { emptyList() }
        // idは保存時のcontent_hashと同一。
        // レジストリはIosMainController（プロセス生存期間のシングルトン）だけが書く
        // ——画面を離れてimportStateが畳まれてもレジストリ側は生き続ける。
        val id = pending.contentHash ?: sha256Hex(pending.kifText)
        InProgressAnalysisRegistry.shared.start(id, pending.fileName, moves, pending.userSide)
        _importState.value = ImportState.Analyzing(
            fileName = pending.fileName,
            moves = moves,
            userSide = pending.userSide,
            progressive = ProgressiveReportState.initial(moves),
        )

        currentAnalysisJob = scope.launch {
            val outcome = runAnalysis(pending, id)
            // 再開でキャンセルされた旧ジョブが結果を持ち帰っても状態を触らせない
            // （新ジョブの表示をキャンセル起因のエラーで上書きさせないため）
            if (!isActive) return@launch
            // 解析中レポート画面を実際に見ているときだけ完了・失敗を画面へ反映する。
            // ホーム等の他画面にいる間に裏で完了しても、その画面から強制的に連れ去らない
            // （画面はレジストリの購読者に過ぎず、遷移は「見ている」ときの一度きりの体験でよい）。
            val wasWatching = _importState.value is ImportState.Analyzing
            InProgressAnalysisRegistry.shared.finish(id)
            when (outcome) {
                is AnalysisOrchestrator.Outcome.Completed -> {
                    // 自動アップロード設定ON＋ログイン中のときだけ実行される
                    // （androidApp の AnalysisService と同じ配線・失敗はサイレント）。
                    uploadOrchestrator?.maybeAutoUpload(outcome.gameId)
                    reloadHome()
                    PendingAnalysisStore.clear()
                    if (wasWatching) {
                        _importState.value = ImportState.Idle
                        _completedAnalysis.value = CompletedAnalysis(
                            gameId = outcome.gameId,
                            justCompleted = !outcome.alreadyExisted,
                        )
                    }
                }
                is AnalysisOrchestrator.Outcome.Failed -> {
                    // Why not pendingをここで消す: 失敗ダイアログを閉じるまで「再開すべき解析」
                    // として残しておくことで、切断が実は継続中でも後で再問い合わせできる。
                    if (wasWatching) {
                        _importState.value = ImportState.Error(outcome.message)
                    } else {
                        // 失敗の通知自体は画面が無いため出さない（Androidの通知に相当するものが
                        // iOS側に無い＝既存挙動のまま）。ホームの解析中カードを消すためだけに
                        // リロードする（新規UIは作らない）。
                        reloadHome()
                    }
                }
            }
        }
    }

    /** analyzer構築〜orchestrator実行のみを担う（状態遷移は呼び出し元 [launchAnalysis] の責務）。 */
    private suspend fun runAnalysis(pending: PendingAnalysis, id: String): AnalysisOrchestrator.Outcome {
        analyzerConfigurationError()?.let { return it }

        val auth = authRepository
        val baseUrl = analysisBaseUrl
        // サーバー解析はJWTでユーザーを識別するため、未ログインならここで匿名サインインする。
        // signInAnonymously の自動呼び出しはここ（明示的なサーバー解析経路）に限定し、
        // 既存アカウントがある場合は currentUser が非null のため再発行されない。
        if (serverAnalysisAvailable() && auth != null && baseUrl != null && auth.currentUser.value == null) {
            val signInResult = auth.signInAnonymously()
            if (signInResult.isFailure) {
                return AnalysisOrchestrator.Outcome.Failed(AppStrings.AUTH_ERROR_ANON_SIGN_IN_GENERIC)
            }
        }

        val orchestrator = AnalysisOrchestrator(
            repository = gameRepository,
            coefTable = coefTable,
            analyzer = buildAnalyzer(),
        )
        return orchestrator.analyzeAndSave(
            kifContent = pending.kifText,
            fileName = pending.fileName,
            userSide = pending.userSide,
            ratingService = pending.ratingService,
            ratingRaw = pending.ratingRaw,
            ratingRule = pending.ratingRule,
            contentHash = pending.contentHash,
            sourcePlaceOverride = pending.sourcePlaceOverride,
            // NDJSONのprogress行は無進捗判定だけを更新し、局面状態とは分離する。
            onProgress = { _, _ -> lastProgressAtEpochSeconds = currentEpochSeconds() },
            onPositionResult = { ply, pvs ->
                InProgressAnalysisRegistry.shared.updatePosition(id, ply, pvs)
                val current = _importState.value
                if (current is ImportState.Analyzing) {
                    _importState.value = current.copy(progressive = current.progressive.withPosition(ply, pvs))
                }
            },
        )
    }

    /**
     * 棋譜ダウンロード復元（[dev.miyado.shogisupplement.ui.restore.GameRestoreViewModel]）の
     * 1局ぶんの取込コールバック。ここではDB保存だけを行い、解析は全局の復元後に
     * [analyzePendingGames] から順番に開始する。
     */
    suspend fun importDownloadedGame(game: ReconstructedGame): GameImportOutcome {
        val outcome = GameImporter(gameRepository).importGame(
            kifContent = game.kifText,
            fileName = game.fileName,
            userSide = game.userSide,
            ratingService = game.ratingService,
            ratingRaw = game.ratingRaw,
            ratingRule = game.ratingRule,
            contentHash = game.contentHash,
            sourcePlaceOverride = game.sourcePlaceOverride,
        )
        return when (outcome) {
            is GameImporter.Outcome.Imported -> GameImportOutcome(success = true, gameId = outcome.gameId)
            is GameImporter.Outcome.Failed -> GameImportOutcome(success = false)
        }
    }

    fun analyzePendingGames() {
        if (currentAnalysisJob?.isActive == true) return
        currentAnalysisJob = scope.launch {
            for (game in gameRepository.getPendingGames()) {
                val pending = game.toPendingAnalysis() ?: continue
                val id = game.contentHash
                InProgressAnalysisRegistry.shared.start(id, game.fileName, game.movesUsi, game.userSide)
                val outcome = try {
                    runAnalysis(pending, id)
                } finally {
                    InProgressAnalysisRegistry.shared.finish(id)
                }
                if (outcome is AnalysisOrchestrator.Outcome.Completed) {
                    uploadOrchestrator?.maybeAutoUpload(outcome.gameId)
                }
            }
            reloadHome()
        }
    }

    private fun dev.miyado.shogisupplement.db.GameRecord.toPendingAnalysis(): PendingAnalysis? {
        val text = kifText ?: return null
        return PendingAnalysis(
            kifText = text,
            userSide = userSide,
            fileName = fileName,
            ratingService = ratingService,
            ratingRaw = ratingRaw,
            ratingRule = ratingRule,
            contentHash = contentHash,
            sourcePlaceOverride = sourcePlace,
            createdAtEpochSeconds = currentEpochSeconds(),
        )
    }

    /**
     * Why not アカウントを作らない端末にも返す: その場合は端末内WASMで解析するため、
     * サーバーURLの有無は結果に影響しない。
     */
    private fun analyzerConfigurationError(): AnalysisOrchestrator.Outcome.Failed? =
        if (!IosEngineHost.ENGINE_LINKED && analysisBaseUrl == null &&
            !settingsRepository.isAccountDeclined()
        ) {
            AnalysisOrchestrator.Outcome.Failed(AppStrings.ANALYSIS_SERVER_NOT_CONFIGURED)
        } else {
            null
        }

    /** アカウントを作らないと決めた端末はサーバーへ出さない（解析も送信も端末内で完結する）。 */
    private fun serverAnalysisAvailable(): Boolean =
        authRepository != null && analysisBaseUrl != null && !settingsRepository.isAccountDeclined()

    private fun buildAnalyzer(): GameAnalyzer {
        val auth = authRepository
        val baseUrl = analysisBaseUrl
        // アカウントを作らない端末はサーバーを使えないため、エンジン非同梱ビルドでも
        // 動く端末内WASMを単独で使う（else側はネイティブエンジンを前提にしている）。
        if (!serverAnalysisAvailable() && !IosEngineHost.ENGINE_LINKED) {
            return WasmAnalysisRunner()
        }
        return if (serverAnalysisAvailable() && auth != null && baseUrl != null) {
            // 429・障害・接続断では同条件のWASMで最初から再解析する。426では切り替えない。
            FailoverAnalyzer(
                delegate = AuthRetryingAnalyzer(
                    delegate = RemoteAnalysisRunner(
                        baseUrl = baseUrl,
                        accessTokenProvider = {
                            checkNotNull(auth.accessToken()) { "アクセストークンが取得できない" }
                        },
                        platform = "ios",
                        httpClient = checkNotNull(analysisHttpClient),
                        appCheckTokenProvider = AppCheckTokenBridge::getToken,
                    ),
                    authRepository = auth,
                ),
                fallbackAnalyzer = WasmAnalysisRunner(),
            )
        } else {
            // ANALYSIS_BASE_URL未設定ビルドでの graceful degradation（従来の端末エンジン）。
            AnalysisRunner(
                // iOS はプロセス内で1エンジンのみ（in-process制約）のため workers=1。
                workers = 1,
                crashReporter = NoopCrashReporter,
                // 1局の中で複数局面を続けて解析しても局面ごとに置換表がクリアされるよう
                // IsolatedEngine で包む（解析結果が解析順に依存しないようにするため）。
                engineFactory = { IsolatedEngine(IosEngineHost.newGameEngineFactory()()) },
                disposeEngine = IosEngineHost.keepAliveDispose,
            )
        }
    }

    private fun currentDateTimeLabel(): String {
        val formatter = NSDateFormatter()
        formatter.dateFormat = "yyyy-MM-dd HH:mm"
        return formatter.stringFromDate(NSDate())
    }

    // ═══ 検討モード・読み筋延長: ReportViewModel へ委譲 ══════════════

    fun extendBestPv(
        blunderId: Long,
        sfenAtLineEnd: String,
        currentPvStr: String?,
        onUpdated: (blunderId: Long, newPv: String) -> Unit = { _, _ -> },
    ) = reportViewModel.extendBestPv(blunderId, sfenAtLineEnd, currentPvStr, onUpdated)

    fun startStudy(
        baseSfen: String,
        flip: Boolean,
        originIsBestPv: Boolean,
        originPlyIndex: Int,
        originSelectedIdx: Int?,
        originAbsolutePly: Int,
        origin: StudyOrigin,
        tappedSquare: ShogiSquare? = null,
        tappedHandPieceType: PieceType? = null,
    ) = reportViewModel.startStudy(
        baseSfen, flip, originIsBestPv, originPlyIndex, originSelectedIdx, originAbsolutePly, origin,
        tappedSquare, tappedHandPieceType,
    )

    fun onStudySquareTapped(sq: ShogiSquare) = reportViewModel.onStudySquareTapped(sq)
    fun onStudyHandPieceTapped(pieceType: PieceType) = reportViewModel.onStudyHandPieceTapped(pieceType)
    fun onStudyPromoteDecision(promote: Boolean) = reportViewModel.onStudyPromoteDecision(promote)
    fun studyStepBack() = reportViewModel.studyStepBack()
    fun studyResetToStart() = reportViewModel.studyResetToStart()
    fun endStudy() = reportViewModel.endStudy()
    fun onStudyChipTapped(depth: Int) = reportViewModel.onStudyChipTapped(depth)
    fun onStudyBranchChipTapped(depth: Int) = reportViewModel.onStudyBranchChipTapped(depth)
    fun onStudyBranchPopupDismiss() = reportViewModel.onStudyBranchPopupDismiss()
    fun onStudyBranchOptionSelected(depth: Int, moveUsi: String) = reportViewModel.onStudyBranchOptionSelected(depth, moveUsi)
    fun onStudyAnalyze() = reportViewModel.onStudyAnalyze()

    /** リーク厳禁: 呼び出し元（MainViewController）が破棄されるタイミングで呼ぶこと。 */
    fun dispose() {
        reportViewModel.dispose()
    }

    companion object {
        // Android の DebugServerAnalysisReceiver と同じ値（Cloud Runのリクエストタイムアウトより長く取る）。
        private const val SERVER_ANALYSIS_REQUEST_TIMEOUT_MS = 10 * 60 * 1000L
        private const val SERVER_ANALYSIS_SOCKET_TIMEOUT_MS = 5 * 60 * 1000L

        // 検討モード・読み筋延長の単発局面解析用（1局面だけの解析なので1局まるごとの解析用より短くて十分）。
        private const val STUDY_ANALYSIS_REQUEST_TIMEOUT_MS = 30_000L
        private const val STUDY_ANALYSIS_SOCKET_TIMEOUT_MS = 30_000L

        /** 閾値秒。大きくすると二重POSTのリスクは下がるが、プロセス死亡時の復旧が遅れる。 */
        internal const val FOREGROUND_RESUME_IDLE_THRESHOLD_SECONDS = 5L
    }
}

/** 純粋関数として切り出し、IosMainController本体のインスタンス化なしに単体テストできるようにする。 */
internal fun shouldResumeAfterForeground(
    importState: IosMainController.ImportState,
    lastProgressAtEpochSeconds: Long?,
    nowEpochSeconds: Long,
    idleThresholdSeconds: Long = IosMainController.FOREGROUND_RESUME_IDLE_THRESHOLD_SECONDS,
): Boolean {
    if (importState !is IosMainController.ImportState.Analyzing) return false
    if (lastProgressAtEpochSeconds == null) return false
    return nowEpochSeconds - lastProgressAtEpochSeconds >= idleThresholdSeconds
}
