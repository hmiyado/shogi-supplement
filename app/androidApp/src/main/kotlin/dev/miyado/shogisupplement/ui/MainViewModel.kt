package dev.miyado.shogisupplement.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.miyado.shogisupplement.ShogiApp
import dev.miyado.shogisupplement.board.PieceType
import dev.miyado.shogisupplement.board.ShogiSquare
import dev.miyado.shogisupplement.db.AppDatabase
import dev.miyado.shogisupplement.db.DrillRepository
import dev.miyado.shogisupplement.db.GameRecord
import dev.miyado.shogisupplement.db.GameRepository
import dev.miyado.shogisupplement.db.RatingSettings
import dev.miyado.shogisupplement.db.SettingsRepository
import dev.miyado.shogisupplement.engine.Engine
import dev.miyado.shogisupplement.engine.PvInfo
import dev.miyado.shogisupplement.engine.UsiEngineProcess
import dev.miyado.shogisupplement.db.saveRatingSettingsBundle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import dev.miyado.shogisupplement.kifu.GameImportFlow
import dev.miyado.shogisupplement.kifu.KifImportController
import dev.miyado.shogisupplement.kifu.KifImportRequest
import dev.miyado.shogisupplement.pipeline.InProgressAnalysisRegistry
import dev.miyado.shogisupplement.pipeline.ProgressiveReportState
import dev.miyado.shogisupplement.service.AnalysisService
import dev.miyado.shogisupplement.service.AnalysisServiceBus
import dev.miyado.shogisupplement.service.AnalysisServiceBus.ServiceEvent
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.common.AppSettingsController
import dev.miyado.shogisupplement.ui.common.PvExtState
import dev.miyado.shogisupplement.ui.home.HomeViewModel
import dev.miyado.shogisupplement.ui.report.ReportViewModel
import dev.miyado.shogisupplement.ui.strength.StrengthDetailViewModel
import dev.miyado.shogisupplement.ui.report.StudyOrigin
import dev.miyado.shogisupplement.ui.report.StudyState
import dev.miyado.shogisupplement.upload.DeleteGameOutcome
import dev.miyado.shogisupplement.upload.GameDeleter
import dev.miyado.shogisupplement.upload.resultMessage
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** [MainUiState]とAndroid固有の解析配線を管理するトップレベルViewModel。 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    data class ManualKifRequest(val text: String, val fileName: String)

    private val gameRepository = AppDatabase.gameRepository(application)
    private val drillRepository = AppDatabase.drillRepository(application)
    private val settingsRepository = AppDatabase.settingsRepository(application)
    private val app get() = getApplication<ShogiApp>()

    private val _state = MutableStateFlow<MainUiState>(MainUiState.Loading)
    val state: StateFlow<MainUiState> = _state

    private val _manualKifRequest = MutableStateFlow<ManualKifRequest?>(null)
    val manualKifRequest: StateFlow<ManualKifRequest?> = _manualKifRequest

    fun enqueueManualKif(text: String, fileName: String = "manual.kif") {
        _manualKifRequest.value = ManualKifRequest(text, fileName)
    }

    fun consumeManualKifRequest() {
        _manualKifRequest.value = null
    }

    private val appSettings = AppSettingsController(settingsRepository, viewModelScope)

    /** テーマモード（'system' / 'light' / 'dark'）。 */
    val themeMode: StateFlow<String> = appSettings.themeMode

    /** 形勢の表示単位（'cp' = 評価値 / 'wp' = 勝率）。 */
    val evalDisplay: StateFlow<String> = appSettings.evalDisplay

    val skipSideConfirm: StateFlow<Boolean> = appSettings.skipSideConfirm

    /**
     * 通知タップから起動した場合の gameId。
     * これが設定されている間は loadHome() の結果で上書きしない。
     */
    private var pendingNotificationGameId: Long? = null

    /** ホーム画面（games一覧・推定棋力カード・今日の1問）のロードを担う協力オブジェクト。 */
    private val homeViewModel: HomeViewModel by lazy {
        HomeViewModel(
            gameRepository = gameRepository,
            drillRepository = drillRepository,
            settingsRepository = settingsRepository,
        )
    }

    /** 推定棋力詳細画面のロードを担う協力オブジェクト。 */
    private val strengthDetailViewModel: StrengthDetailViewModel by lazy {
        StrengthDetailViewModel(gameRepository = gameRepository, settingsRepository = settingsRepository)
    }

    /** レポート表示状態・読み筋延長・検討モードを担う協力オブジェクト。 */
    private val reportViewModel: ReportViewModel by lazy {
        ReportViewModel(
            scope = viewModelScope,
            repository = gameRepository,
            engineFactory = ::createEngine,
            evalDisplayProvider = { appSettings.evalDisplay.value },
        )
    }

    /** 読み筋オンデマンド延長の状態 Map（blunderId → PvExtState）。ReportViewModel へ委譲。 */
    val pvExtState: StateFlow<Map<Long, PvExtState>> get() = reportViewModel.pvExtState

    /** レポート画面の検討モード状態（null = 検討していない）。ReportViewModel へ委譲。 */
    val studyState: StateFlow<StudyState?> get() = reportViewModel.studyState

    init {
        loadHome()
        // ServiceBus からの完了イベントを監視
        viewModelScope.launch {
            AnalysisServiceBus.events.collect { event ->
                when (event) {
                    is ServiceEvent.Completed -> onAnalysisCompleted(event.gameId, event.alreadyExisted)
                    is ServiceEvent.Failed -> onAnalysisFailed(event.message)
                    is ServiceEvent.PositionResult -> onPositionResult(event.ply, event.pvs)
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
        // 解析中セッションの一覧を監視し、ホーム表示中なら進捗をそのまま反映する
        // （書き手はAnalysisService。本ViewModelは読み手専属——バック操作でHomeへ戻っても
        // セッション自体はAnalysisServiceが生存期間を管理しているため失われない）。
        viewModelScope.launch {
            InProgressAnalysisRegistry.shared.sessions.collect { sessions ->
                val s = _state.value
                if (s is MainUiState.Home) {
                    _state.value = s.copy(analyzingSessions = sessions.values.toList())
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
                drillRecordCard = result.drillRecordCard,
                // ここで同期スナップショットを取る（init の sessions.collect は「変化」でしか
                // 発火しないため、既に進行中のセッションがある状態でHomeへ戻ったときに
                // 次の局面到着まで一覧が空のままになるのを防ぐ）。
                analyzingSessions = InProgressAnalysisRegistry.shared.sessions.value.values.toList(),
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

    /**
     * 推定棋力詳細画面に遷移する（ホーム画面の推定棋力カードタップ）。
     * ロード結果が null（解析済み対局が無い）の場合は何もしない
     * （カード自体がその場合は表示されないため、通常到達しない）。
     */
    fun openStrengthDetail() {
        viewModelScope.launch {
            val data = strengthDetailViewModel.loadStrengthDetail() ?: return@launch
            _state.value = MainUiState.StrengthDetail(data)
        }
    }

    /** デバッグ画面に遷移する（BuildConfig.DEBUG のみ呼ばれる）。 */
    fun openDebug() {
        _state.value = MainUiState.Debug
    }

    /** 棋譜一覧画面に遷移する。 */
    fun openGameList() {
        viewModelScope.launch {
            reloadGameList()
        }
    }

    private suspend fun reloadGameList() {
        val games = withContext(Dispatchers.IO) { gameRepository.getAllGames() }
        val isLoggedIn = app.authRepository.currentUser.value != null
        val pendingCount = if (isLoggedIn) {
            withContext(Dispatchers.IO) { gameRepository.getNotUploadedGames().size }
        } else 0
        _state.value = MainUiState.GameList(games, pendingUploadCount = pendingCount)
    }

    /**
     * 棋譜1局を削除する。棋譜一覧から呼ばれたときは一覧を再読込し、それ以外（レポート画面）はホームへ戻る。
     * 再読込は完了を待ってから返す（投げっぱなしだと連続呼び出し時に完了順が入れ替わり、
     * 削除済みの棋譜が一覧に残って見えることがある）。
     */
    fun deleteGame(
        game: GameRecord,
        deleteServer: Boolean,
        onResult: (DeleteGameOutcome) -> Unit,
    ) {
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                GameDeleter(gameRepository, app.uploadOrchestrator).delete(game, deleteServer)
            }
            if (outcome == DeleteGameOutcome.Success) {
                if (_state.value is MainUiState.GameList) reloadGameList() else loadHome()
            }
            onResult(outcome)
        }
    }

    fun updatePlayers(gameId: Long, senteName: String?, goteName: String?) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { gameRepository.updateGamePlayers(gameId, senteName, goteName) }
            val current = _state.value as? MainUiState.ShowReport ?: return@launch
            if (current.game.id != gameId) return@launch
            _state.value = current.copy(game = current.game.copy(senteName = senteName, goteName = goteName))
        }
    }

    /** 棋譜一覧画面から未アップロード局を一括アップロードする。 */
    fun uploadFromGameList() {
        val s = _state.value as? MainUiState.GameList ?: return
        if (s.isUploading) return
        _state.value = s.copy(isUploading = true, uploadResult = null)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { app.uploadOrchestrator.uploadAll() }
            val newGames = withContext(Dispatchers.IO) { gameRepository.getAllGames() }
            val pendingCount = withContext(Dispatchers.IO) { gameRepository.getNotUploadedGames().size }
            val cur = _state.value as? MainUiState.GameList ?: return@launch
            _state.value = cur.copy(
                games = newGames,
                pendingUploadCount = pendingCount,
                isUploading = false,
                uploadResult = result.resultMessage(),
            )
        }
    }

    /** KIFを受け取ってから保存を依頼するまでの手順。 */
    val kifImport = KifImportController(
        settingsRepository = settingsRepository,
        scope = viewModelScope,
        dateTimeLabel = { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date()) },
        onImport = { request -> importConfirmedKif(request) },
    )

    /**
     * 選ばれたURIのKIFを読む。表示名と本文を1回の読み取りで返す
     * （本文はUTF-8で読めなければnull）。
     */
    suspend fun readKifFromUri(uri: Uri): Pair<String, String?> = withContext(Dispatchers.IO) {
        resolveKifFileName(uri) to runCatching { readKifContentFromUri(uri) }.getOrNull()
    }

    /** KIFを取り込み、新規なら解析を即開始する（単発の取り込みは「即解析」体験を保つ）。 */
    private suspend fun importConfirmedKif(request: KifImportRequest) {
        val next = withContext(Dispatchers.IO) {
            GameImportFlow(gameRepository).import(
                kifContent = request.kifText,
                fileName = request.fileName,
                userSide = request.userSide,
                ratingService = request.ratingService,
                ratingRaw = request.ratingRaw,
                ratingRule = request.ratingRule,
            )
        }
        when (next) {
            is GameImportFlow.Next.Analyze -> analyzeStoredGame(next.game)
            is GameImportFlow.Next.OpenReport -> showReport(next.gameId)
            is GameImportFlow.Next.Failed -> _state.value = MainUiState.Error(next.message)
        }
    }

    fun analyzeStoredGame(game: GameRecord) {
        if (game.kifText == null) return
        _state.value = MainUiState.AnalyzingReport(
            titleHint = game.fileName,
            moves = game.movesUsi,
            userSide = game.userSide,
            progressive = ProgressiveReportState.initial(game.movesUsi),
        )
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, AnalysisService::class.java).apply {
            putExtra(AnalysisService.EXTRA_GAME_ID, game.id)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(intent)
        } else {
            ctx.startService(intent)
        }
    }

    /** @param justCompleted trueなら完了通知バナーを一度だけ表示する。 */
    fun showReport(gameId: Long, justCompleted: Boolean = false) {
        viewModelScope.launch {
            val result = reportViewModel.loadReport(gameId)
            pendingNotificationGameId = null
            if (result.game != null) {
                // 別モジュール（:ui）宣言のプロパティのためスマートキャスト不可
                // （DrillViewModel.kt の同種コメント参照）。直前の != null 判定で保証済み。
                val game = result.game!!
                _state.value = MainUiState.ShowReport(
                    game = game,
                    reports = result.reports,
                    flip = result.flip,
                    strengthDisplayText = result.strengthText,
                    evalDisplay = appSettings.evalDisplay.value,
                    positionEvals = result.positionEvals,
                    matchRateDisplayText = result.matchRateText,
                    blunderRateDisplayText = result.blunderRateText,
                    justCompleted = justCompleted,
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
    /** KIF原文を読む（ファイルURI/コンテンツURIの両対応）。 */
    private fun readKifContentFromUri(uri: Uri): String =
        if (uri.scheme == "file") {
            java.io.File(uri.path!!).readText()
        } else {
            getApplication<Application>().contentResolver
                .openInputStream(uri)!!.use { it.readBytes().decodeToString() }
        }

    /** content URIでは末尾パスが表示名とは限らないため、Providerの表示名を優先する。 */
    private fun resolveKifFileName(uri: Uri): String {
        if (uri.scheme == "file") return uri.lastPathSegment ?: "unknown.kif"
        val displayName = runCatching {
            getApplication<Application>().contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                } else {
                    null
                }
            }
        }.getOrNull()
        return displayName ?: uri.lastPathSegment ?: "unknown.kif"
    }

    fun saveSkipSideConfirm(skip: Boolean) = appSettings.saveSkipSideConfirm(skip)

    /** 保存済みレートを取得する（ダイアログのデフォルト値として使用）。 */
    fun getSavedRating(): Int = settingsRepository.getRating()

    /** 保存済みレート・サービス・raw値を取得する。 */
    fun getSavedRatingFull(): Triple<Int, String, Int> = settingsRepository.getRatingFull()

    /** 保存済みのレート設定を取得する（棋力設定ダイアログのデフォルト値として使用）。 */
    fun getSavedRatingSettings(): RatingSettings = settingsRepository.getRatingSettings()

    /** 保存済みのサービスアカウント名を返す（未設定なら null）。旧API（後方互換）。 */
    fun getSavedServiceAccountName(): String? = settingsRepository.getServiceAccountName()

    /** 全サービスのアカウント名を取得する（棋力設定ダイアログ用）。 */
    fun getAllServiceAccounts(): Map<String, String> = settingsRepository.getAllServiceAccounts()

    /** 申告情報は強さ判定には含めず、記録と較正にのみ保存する。 */
    fun saveRatingSettings(
        service: String?,
        ratingRaw: Int?,
        ratingRule: String?,
        serviceAccounts: Map<String, String>,
        serviceRanks: Map<String, Map<String, Int>> = emptyMap(),
    ) = settingsRepository.saveRatingSettingsBundle(service, ratingRaw, ratingRule, serviceAccounts, serviceRanks)

    /** 最後に選んだ user_side を取得する。 */
    fun getSavedUserSide(): String? = settingsRepository.getLastUserSide()

    /** 全サービスのルール別棋力を取得する（棋力設定ダイアログ用）。 */
    fun getAllServiceRanks(): Map<String, Map<String, Int>> = settingsRepository.getAllServiceRanks()

    /** @param themeMode 'system' / 'light' / 'dark' */
    fun saveThemeMode(themeMode: String) = appSettings.saveThemeMode(themeMode)

    /** @param mode 'cp'（評価値）/ 'wp'（勝率） */
    fun saveEvalDisplay(mode: String) = appSettings.saveEvalDisplay(mode)

    /** 完了との競合でセッションが消えていれば、画面遷移しない。 */
    fun resumeAnalyzing(id: String) {
        val session = InProgressAnalysisRegistry.shared.snapshot(id) ?: return
        _state.value = MainUiState.AnalyzingReport(
            titleHint = session.fileName,
            moves = session.progressive.moves,
            userSide = session.userSide,
            progressive = session.progressive,
        )
    }

    private fun onAnalysisCompleted(gameId: Long, alreadyExisted: Boolean) {
        // 解析中レポート画面を実際に見ているときだけレポートへ遷移する。
        // ホーム等の他画面にいる間に裏で完了しても、その画面から強制的に連れ去らない
        // （画面はレジストリの購読者に過ぎず、遷移は「見ている」ときの一度きりの体験でよい）。
        val wasWatching = _state.value is MainUiState.AnalyzingReport
        if (wasWatching) {
            // alreadyExisted=true は重複KIFの再取込（実際には何も解析していない）ため、
            // 完了通知バナーは出さない。
            showReport(gameId, justCompleted = !alreadyExisted)
        } else if (_state.value is MainUiState.Home) {
            loadHome()
        }
    }

    private fun onAnalysisFailed(message: String) {
        val wasWatching = _state.value is MainUiState.AnalyzingReport
        if (wasWatching) {
            viewModelScope.launch {
                val games = withContext(Dispatchers.IO) {
                    gameRepository.getAllGames()
                }
                _state.value = MainUiState.Error(message, games)
            }
        } else if (_state.value is MainUiState.Home) {
            // 失敗はシステム通知で既に伝わっている。ここではホームの解析中カードを
            // 消すためだけにリロードする（新規UIは作らない）。
            loadHome()
        }
    }

    private fun onPositionResult(ply: Int, pvs: List<PvInfo>) {
        val s = _state.value
        if (s is MainUiState.AnalyzingReport) {
            _state.value = s.copy(progressive = s.progressive.withPosition(ply, pvs))
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
