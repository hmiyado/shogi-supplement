package dev.miyado.shogisupplement.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.miyado.shogisupplement.ShogiApp
import dev.miyado.shogisupplement.board.PieceType
import dev.miyado.shogisupplement.board.ShogiSquare
import dev.miyado.shogisupplement.db.AppDatabase
import dev.miyado.shogisupplement.db.DrillRepository
import dev.miyado.shogisupplement.db.GameRepository
import dev.miyado.shogisupplement.db.RatingSettings
import dev.miyado.shogisupplement.db.SettingsRepository
import dev.miyado.shogisupplement.engine.Engine
import dev.miyado.shogisupplement.engine.UsiEngineProcess
import dev.miyado.shogisupplement.judge.CoefficientTable
import dev.miyado.shogisupplement.kifu.KifParser
import dev.miyado.shogisupplement.kifu.SideSuggestion
import dev.miyado.shogisupplement.kifu.UserSideSuggester
import dev.miyado.shogisupplement.service.AnalysisService
import dev.miyado.shogisupplement.service.AnalysisServiceBus
import dev.miyado.shogisupplement.service.AnalysisServiceBus.ServiceEvent
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.common.PvExtState
import dev.miyado.shogisupplement.ui.home.HomeViewModel
import dev.miyado.shogisupplement.ui.report.ReportViewModel
import dev.miyado.shogisupplement.ui.report.StudyState
import dev.miyado.shogisupplement.upload.UploadResult
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * アプリ全体のナビゲーション状態（[MainUiState]）を保持するトップレベル ViewModel。
 *
 * 画面別の表示ロジック・計算は :ui commonMain の協力オブジェクトに委譲しており
 * （[HomeViewModel] = ホーム画面のロード、[ReportViewModel] = レポート表示状態・
 * 読み筋延長・検討モード）、本クラスは以下のみを担う:
 * - MainUiState（ナビゲーション状態）の保持・遷移
 * - Android専用の配線（AnalysisService起動・AnalysisServiceBus購読・通知deep-link・
 *   認証状態監視・エンジンプロセス生成）
 * - DB設定値（棋力申告・テーマ・評価表示単位・先後確認省略）の読み書きパススルー
 *
 * 単一の MainUiState が全画面のナビゲーションを一元管理するアーキテクチャ（Loading/
 * Home/Analyzing/ShowReport/... の sealed class を1箇所で when 分岐する設計）を保つため、
 * MainViewModel は廃止せず「薄いルーター」として残している。HomeViewModel/ReportViewModel は
 * Compose の viewModel() から独立取得するのではなく、この MainViewModel が保持するプレーンな
 * 協力オブジェクトにしている（二重ライフサイクル管理を避けるため。詳細は各クラスの KDoc参照）。
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val gameRepository = AppDatabase.gameRepository(application)
    private val drillRepository = AppDatabase.drillRepository(application)
    private val settingsRepository = AppDatabase.settingsRepository(application)
    private val app get() = getApplication<ShogiApp>()

    private val _state = MutableStateFlow<MainUiState>(MainUiState.Loading)
    val state: StateFlow<MainUiState> = _state

    /** テーマモード（'system' / 'light' / 'dark'）。DBから読み込んで即時反映する。 */
    private val _themeMode = MutableStateFlow("system")
    val themeMode: StateFlow<String> = _themeMode

    /** 形勢の表示単位（'cp' = 評価値 / 'wp' = 勝率）。DBから読み込んで即時反映する。 */
    private val _evalDisplay = MutableStateFlow("cp")
    val evalDisplay: StateFlow<String> = _evalDisplay

    /** 先後確認の省略設定。DBから読み込んで即時反映する。 */
    private val _skipSideConfirm = MutableStateFlow(false)
    val skipSideConfirm: StateFlow<Boolean> = _skipSideConfirm

    /**
     * 通知タップから起動した場合の gameId。
     * これが設定されている間は loadHome() の結果で上書きしない。
     */
    private var pendingNotificationGameId: Long? = null

    private val coefTable: CoefficientTable by lazy {
        val json = getApplication<ShogiApp>().assets
            .open("coefficients_hao_v1.json").readBytes().decodeToString()
        CoefficientTable.fromJson(json)
    }

    /** ホーム画面（games一覧・推定棋力カード・今日の1問）のロードを担う協力オブジェクト。 */
    private val homeViewModel: HomeViewModel by lazy {
        HomeViewModel(
            gameRepository = gameRepository,
            drillRepository = drillRepository,
            settingsRepository = settingsRepository,
            coefTable = coefTable,
        )
    }

    /** レポート表示状態・読み筋延長・検討モードを担う協力オブジェクト。 */
    private val reportViewModel: ReportViewModel by lazy {
        ReportViewModel(
            scope = viewModelScope,
            repository = gameRepository,
            coefTable = coefTable,
            engineFactory = ::createEngine,
            evalDisplayProvider = { _evalDisplay.value },
        )
    }

    /** 読み筋オンデマンド延長の状態 Map（blunderId → PvExtState）。ReportViewModel へ委譲。 */
    val pvExtState: StateFlow<Map<Long, PvExtState>> get() = reportViewModel.pvExtState

    /** レポート画面の検討モード状態（null = 検討していない）。ReportViewModel へ委譲。 */
    val studyState: StateFlow<StudyState?> get() = reportViewModel.studyState

    init {
        // DBからテーマモードを読み込む
        viewModelScope.launch {
            _themeMode.value = withContext(Dispatchers.IO) { settingsRepository.getThemeMode() }
        }
        // DBから形勢の表示単位を読み込む
        viewModelScope.launch {
            _evalDisplay.value = withContext(Dispatchers.IO) { settingsRepository.getEvalDisplay() }
        }
        // DBから先後確認の省略設定を読み込む
        viewModelScope.launch {
            _skipSideConfirm.value = withContext(Dispatchers.IO) { settingsRepository.getSkipSideConfirm() }
        }
        loadHome()
        // ServiceBus からの完了イベントを監視
        viewModelScope.launch {
            AnalysisServiceBus.events.collect { event ->
                when (event) {
                    is ServiceEvent.Completed -> onAnalysisCompleted(event.gameId)
                    is ServiceEvent.Failed -> onAnalysisFailed(event.message)
                    is ServiceEvent.Progress -> onProgress(event.done, event.total)
                }
            }
        }
        // 認証状態の変化を監視（ホーム画面の isLoggedIn を更新）
        viewModelScope.launch {
            app.authRepository.currentUser.collect { user ->
                val s = _state.value
                if (s is MainUiState.Home) {
                    _state.value = s.copy(isLoggedIn = user != null)
                }
            }
        }
    }

    /**
     * 通知タップで起動したとき、gameId のレポートを直接表示する。
     * onCreate/onNewIntent から呼ぶ。
     */
    fun handleNotificationIntent(gameId: Long) {
        pendingNotificationGameId = gameId
        showReport(gameId)
    }

    /** ホーム画面（過去の解析一覧）を読み込む。 */
    fun loadHome() {
        viewModelScope.launch {
            // 通知タップ pending がある場合は loadHome の結果で上書きしない
            if (pendingNotificationGameId != null) return@launch
            val isLoggedIn = app.authRepository.currentUser.value != null
            val result = homeViewModel.loadHomeData()
            _state.value = MainUiState.Home(
                result.games,
                isLoggedIn = isLoggedIn,
                strengthCard = result.strengthCard,
                todaysDrillHint = result.todaysDrillHint,
            )
        }
    }

    /** ドリル画面に遷移する。 */
    fun startDrill() {
        _state.value = MainUiState.Drill
    }

    /** アカウント画面に遷移する。 */
    fun openAccount() {
        _state.value = MainUiState.Account
    }

    /** OSSライセンス一覧画面に遷移する。 */
    fun openLicenses() {
        _state.value = MainUiState.Licenses
    }

    /** 設定画面に遷移する。 */
    fun openSettings() {
        _state.value = MainUiState.Settings
    }

    /** デバッグ画面に遷移する（BuildConfig.DEBUG のみ呼ばれる）。 */
    fun openDebug() {
        _state.value = MainUiState.Debug
    }

    /** 棋譜一覧画面に遷移する。 */
    fun openGameList() {
        viewModelScope.launch {
            val games = withContext(Dispatchers.IO) { gameRepository.getAllGames() }
            val isLoggedIn = app.authRepository.currentUser.value != null
            val pendingCount = if (isLoggedIn) {
                withContext(Dispatchers.IO) { gameRepository.getNotUploadedGames().size }
            } else 0
            _state.value = MainUiState.GameList(games, pendingUploadCount = pendingCount)
        }
    }

    /** 棋譜一覧画面から未アップロード局を一括アップロードする。 */
    fun uploadFromGameList() {
        val s = _state.value as? MainUiState.GameList ?: return
        if (s.isUploading) return
        _state.value = s.copy(isUploading = true, uploadResult = null)
        viewModelScope.launch {
            val results = withContext(Dispatchers.IO) { app.uploadOrchestrator.uploadAll() }
            val success = results.values.count {
                it is UploadResult.Success || it is UploadResult.Duplicate
            }
            val failed = results.values.count { it is UploadResult.Failure }
            val newGames = withContext(Dispatchers.IO) { gameRepository.getAllGames() }
            val pendingCount = withContext(Dispatchers.IO) { gameRepository.getNotUploadedGames().size }
            val cur = _state.value as? MainUiState.GameList ?: return@launch
            _state.value = cur.copy(
                games = newGames,
                pendingUploadCount = pendingCount,
                isUploading = false,
                uploadResult = AppStrings.accountUploadResult(success, failed),
            )
        }
    }

    /**
     * KIF 解析を開始する。
     *
     * 相応判定のレートは申告値ではなく StrengthEstimator（実測悪手率）が算出する。
     * service / ratingRaw / ratingRule は記録専用（較正データ）。
     */
    fun startAnalysis(
        uri: Uri,
        service: String? = null,
        ratingRaw: Int? = null,
        userSide: String? = null,
        ratingRule: String? = null,
    ) {
        _state.value = MainUiState.Analyzing(0, 0)
        if (userSide != null) settingsRepository.saveLastUserSide(userSide)

        val ctx = getApplication<Application>()
        val intent = Intent(ctx, AnalysisService::class.java).apply {
            putExtra(AnalysisService.EXTRA_KIF_URI, uri.toString())
            if (service != null) putExtra(AnalysisService.EXTRA_RATING_SERVICE, service)
            if (ratingRaw != null) putExtra(AnalysisService.EXTRA_RATING_RAW, ratingRaw)
            if (userSide != null) putExtra(AnalysisService.EXTRA_USER_SIDE, userSide)
            if (ratingRule != null) putExtra(AnalysisService.EXTRA_RATING_RULE, ratingRule)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(intent)
        } else {
            ctx.startService(intent)
        }
    }

    /** 特定のゲームIDのレポートを表示する（DB再取得）。 */
    fun showReport(gameId: Long) {
        viewModelScope.launch {
            val result = reportViewModel.loadReport(gameId)
            pendingNotificationGameId = null
            if (result.game != null) {
                // 別モジュール（:ui）宣言のプロパティのためスマートキャスト不可
                // （DrillViewModel.kt の同種コメント参照）。直前の != null 判定で保証済み。
                _state.value = MainUiState.ShowReport(
                    game = result.game!!,
                    reports = result.reports,
                    flip = result.flip,
                    strengthDisplayText = result.strengthText,
                    evalDisplay = _evalDisplay.value,
                    positionEvals = result.positionEvals,
                )
            } else {
                _state.value = MainUiState.Error(AppStrings.gameNotFound(gameId))
            }
        }
    }

    /** 未アップロードの全ゲームをアップロードする。 */
    fun uploadAll() {
        val s = _state.value
        if (s !is MainUiState.Home) return
        _state.value = s.copy(isUploading = true)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                app.uploadOrchestrator.uploadAll()
            }
            loadHome()
        }
    }

    /**
     * KIF ファイルの対局者名（先手・後手）をパースして返す。
     * 読み込み・パースに失敗した場合は (null, null)。
     */
    suspend fun parseKifPlayers(uri: Uri): Pair<String?, String?> = withContext(Dispatchers.IO) {
        runCatching {
            val content = if (uri.scheme == "file") {
                java.io.File(uri.path!!).readText()
            } else {
                getApplication<Application>().contentResolver
                    .openInputStream(uri)!!.use { it.readBytes().decodeToString() }
            }
            val headers = KifParser().parse(content).headers
            headers["先手"] to headers["後手"]
        }.getOrElse { null to null }
    }

    /**
     * KIFの対局者名と設定済みアカウント名を比較して自分の側を推定する。
     *
     * 全サービスのアカウント名のいずれかと先手・後手名が一致すればその側を返す。
     * 一致しなければ最後に選んだ側（last_user_side）にフォールバック。
     */
    suspend fun suggestUserSide(senteName: String?, goteName: String?): String? =
        suggestUserSideWithMatch(senteName, goteName).side

    /**
     * 先後推定とアカウント名一致の有無を返す。
     * 一致フラグは確認省略（skip_side_confirm）の判定に使う。
     */
    suspend fun suggestUserSideWithMatch(senteName: String?, goteName: String?): SideSuggestion =
        withContext(Dispatchers.IO) {
            UserSideSuggester.suggest(
                senteName = senteName,
                goteName = goteName,
                accountNames = settingsRepository.getAllServiceAccounts().values.toSet(),
                lastUserSide = settingsRepository.getLastUserSide(),
            )
        }

    /**
     * 側選択ダイアログを省略できるか。
     * skip_side_confirm が ON かつアカウント名一致で側が確定した場合のみ true。
     */
    suspend fun shouldSkipSideConfirm(suggestion: SideSuggestion): Boolean =
        withContext(Dispatchers.IO) {
            UserSideSuggester.shouldSkipConfirm(suggestion, settingsRepository.getSkipSideConfirm())
        }

    /** 先後確認の省略設定を保存し StateFlow に反映する。 */
    fun saveSkipSideConfirm(skip: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { settingsRepository.saveSkipSideConfirm(skip) }
            _skipSideConfirm.value = skip
        }
    }

    /** 保存済みレートを取得する（ダイアログのデフォルト値として使用）。 */
    fun getSavedRating(): Int = settingsRepository.getRating()

    /** 保存済みレート・サービス・raw値を取得する。 */
    fun getSavedRatingFull(): Triple<Int, String, Int> = settingsRepository.getRatingFull()

    /** 保存済みのレート設定を取得する（棋力設定ダイアログのデフォルト値として使用）。 */
    fun getSavedRatingSettings(): RatingSettings = settingsRepository.getRatingSettings()

    /** 保存済みのサービスアカウント名を返す（未設定なら null）。旧API（後方互換）。 */
    fun getSavedServiceAccountName(): String? = settingsRepository.getServiceAccountName()

    /** いずれかのサービスにアカウント名が設定されているかどうか。 */
    fun hasAnyServiceAccount(): Boolean = settingsRepository.hasAnyServiceAccount()

    /** 全サービスのアカウント名を取得する（棋力設定ダイアログ用）。 */
    fun getAllServiceAccounts(): Map<String, String> = settingsRepository.getAllServiceAccounts()

    /**
     * サービス申告情報をまとめて保存する（棋力設定ダイアログの確定時に呼ぶ）。
     *
     * 相応判定には使わない（記録・較正データ収集のみ）。
     * アカウント名はサービスごとに service_account テーブルへ保存する。
     * @param service "lishogi" / "shogi_wars" / "kiou"（null = 未申告）
     * @param ratingRaw サービス上のraw値（null = 未申告）
     * @param ratingRule ルール文字列（null = 選択なし）
     * @param serviceAccounts サービス → アカウント名のマップ（先後自動選択に使用）
     */
    fun saveRatingSettings(
        service: String?,
        ratingRaw: Int?,
        ratingRule: String?,
        serviceAccounts: Map<String, String>,
        serviceRanks: Map<String, Map<String, Int>> = emptyMap(),
    ) {
        // 旧 user_settings.service_account_name には現在選択中サービスの名前を残す（後方互換）
        val currentServiceAccount = service?.let { serviceAccounts[it] }
        settingsRepository.saveRatingSettings(service, ratingRaw, ratingRule, currentServiceAccount)
        for ((svc, name) in serviceAccounts) {
            settingsRepository.upsertServiceAccount(svc, name)
        }
        for ((svc, rules) in serviceRanks) {
            for ((rule, rankRaw) in rules) {
                settingsRepository.saveServiceRank(svc, rule, rankRaw)
            }
        }
    }

    /** 最後に選んだ user_side を取得する。 */
    fun getSavedUserSide(): String? = settingsRepository.getLastUserSide()

    /** 全サービスのルール別棋力を取得する（棋力設定ダイアログ用）。 */
    fun getAllServiceRanks(): Map<String, Map<String, Int>> = settingsRepository.getAllServiceRanks()

    /**
     * テーマモードを保存し StateFlow を即時更新する。
     * @param themeMode 'system' / 'light' / 'dark'
     */
    fun saveThemeMode(themeMode: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { settingsRepository.saveThemeMode(themeMode) }
            _themeMode.value = themeMode
        }
    }

    /**
     * 形勢の表示単位を保存し StateFlow を即時更新する。
     * @param mode 'cp'（評価値）/ 'wp'（勝率）
     */
    fun saveEvalDisplay(mode: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { settingsRepository.saveEvalDisplay(mode) }
            _evalDisplay.value = mode
        }
    }

    private fun onAnalysisCompleted(gameId: Long) {
        showReport(gameId)
    }

    private fun onAnalysisFailed(message: String) {
        viewModelScope.launch {
            val games = withContext(Dispatchers.IO) {
                gameRepository.getAllGames()
            }
            _state.value = MainUiState.Error(message, games)
        }
    }

    private fun onProgress(done: Int, total: Int) {
        if (_state.value is MainUiState.Analyzing) {
            _state.value = MainUiState.Analyzing(done, total)
        }
    }

    /**
     * 読み筋のオンデマンド延長。ロジック本体は ReportViewModel が持つ。
     * ここでは延長成功時に現在表示中の MainUiState.ShowReport.reports を更新する
     * （レポート表示状態は MainUiState 側にあるため、その反映だけがこの層の責務）。
     */
    fun extendBestPv(blunderId: Long, sfenAtLineEnd: String, currentPvStr: String?) {
        reportViewModel.extendBestPv(blunderId, sfenAtLineEnd, currentPvStr) { id, newPv ->
            val s = _state.value
            if (s is MainUiState.ShowReport) {
                _state.value = s.copy(
                    reports = s.reports.map { r -> if (r.id == id) r.copy(bestPv = newPv) else r },
                )
            }
        }
    }

    // ═══ 検討モード: ReportViewModel（内部の StudyController）へ委譲 ══════════════

    fun startStudy(
        baseSfen: String,
        flip: Boolean,
        originIsBestPv: Boolean,
        originPlyIndex: Int,
        originSelectedIdx: Int?,
        originAbsolutePly: Int,
        tappedSquare: ShogiSquare? = null,
    ) = reportViewModel.startStudy(
        baseSfen, flip, originIsBestPv, originPlyIndex, originSelectedIdx, originAbsolutePly, tappedSquare,
    )

    fun onStudySquareTapped(sq: ShogiSquare) = reportViewModel.onStudySquareTapped(sq)
    fun onStudyHandPieceTapped(pieceType: PieceType) = reportViewModel.onStudyHandPieceTapped(pieceType)
    fun onStudyPromoteDecision(promote: Boolean) = reportViewModel.onStudyPromoteDecision(promote)
    fun studyStepBack() = reportViewModel.studyStepBack()
    fun studyResetToStart() = reportViewModel.studyResetToStart()
    fun endStudy() = reportViewModel.endStudy()

    override fun onCleared() {
        super.onCleared()
        // リーク厳禁: ViewModel破棄時に検討エンジンが生きていればquitする（ReportViewModel委譲）。
        reportViewModel.dispose()
    }

    /** ReportViewModel/StudyController に注入するエンジン生成関数（Android実装）。 */
    private fun createEngine(): Engine {
        val appInfo = getApplication<Application>().applicationInfo
        val evalDir = File(getApplication<Application>().filesDir, "eval")
        return UsiEngineProcess.create(appInfo, evalDir)
    }
}
