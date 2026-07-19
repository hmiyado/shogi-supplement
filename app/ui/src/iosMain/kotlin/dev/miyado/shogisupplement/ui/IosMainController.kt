package dev.miyado.shogisupplement.ui

import dev.miyado.shogisupplement.board.PieceType
import dev.miyado.shogisupplement.board.ShogiSquare
import dev.miyado.shogisupplement.db.DrillRepository
import dev.miyado.shogisupplement.db.GameRepository
import dev.miyado.shogisupplement.db.RatingSettings
import dev.miyado.shogisupplement.db.SettingsRepository
import dev.miyado.shogisupplement.engine.AnalysisOrchestrator
import dev.miyado.shogisupplement.engine.IosCoefficients
import dev.miyado.shogisupplement.engine.IosEngineHost
import dev.miyado.shogisupplement.kifu.ClipboardKifValidator
import dev.miyado.shogisupplement.kifu.KifParser
import dev.miyado.shogisupplement.kifu.UserSideSuggester
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.common.PvExtState
import dev.miyado.shogisupplement.ui.common.defaultIoDispatcher
import dev.miyado.shogisupplement.ui.home.HomeViewModel
import dev.miyado.shogisupplement.ui.report.ReportViewModel
import dev.miyado.shogisupplement.ui.report.StudyState
import dev.miyado.shogisupplement.upload.UploadOrchestrator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.UIKit.UIPasteboard

/**
 * iOSデモアプリのトップレベル状態管理。
 *
 * androidApp の MainViewModel（「薄いルーター」。同クラスKDoc参照）と同じ役割を、
 * androidx ViewModel/Application が存在しない iOS 側で担う。DrillViewModel と同じ
 * 「StateFlow + defaultIoDispatcher」パターンのプレーンな Kotlin クラスとして実装し、
 * MainViewController.kt から `remember { IosMainController(...) }` で保持する。
 *
 * - ホーム表示データの計算は [HomeViewModel]（:ui commonMain）に一本化している。
 * - レポート表示・読み筋延長・検討モードは [ReportViewModel]（内部で [StudyController] を
 *   保持）に一本化している。androidApp の MainViewModel と同じく、これらは androidx
 *   ViewModel のライフサイクルに縛られないプレーンな協力オブジェクトとして本クラスが保持する。
 * - クリップボード取込フロー（KIF検証→先後選択→解析）はVM外の構造をそのまま使う。
 * - テーマ・形勢表示単位・先後確認省略の user_settings 読み書きも本クラスが担う。
 *
 * 検討モード・読み筋延長に注入するエンジンは [IosEngineHost.studyEngineFactory] を使う。
 * androidApp 版は呼び出しごとに使い捨てプロセスを生成するため ReportViewModel/StudyController
 * が `engine.quit()` を無条件に呼んでも無害だが、iOS はプロセス内エンジンを一度しか起動できない
 * ため、quit() を no-op にする委譲ラッパー（[IosEngineHost.studyEngineFactory] 内部実装）で
 * 常駐エンジンを守っている（ドリル判定・取込解析と検討モード・読み筋延長は同一の常駐エンジンを
 * 共有する）。
 */
class IosMainController(
    private val gameRepository: GameRepository,
    private val drillRepository: DrillRepository,
    private val settingsRepository: SettingsRepository,
    /** null = Supabase未設定ビルド（自動アップロードなし）。 */
    private val uploadOrchestrator: UploadOrchestrator? = null,
) {

    /** クリップボード取込フローの状態。 */
    sealed class ImportState {
        /** 取込フロー未実行。 */
        object Idle : ImportState()

        /**
         * KIF検証OKだがアカウント名が未設定。先に棋力設定ダイアログを出す
         * （androidApp の KifImportFlow と同じ初回導線。保存後に先後選択へ進む）。
         */
        data class RatingSetup(
            val kifText: String,
            val senteName: String?,
            val goteName: String?,
            val sourceFileName: String? = null,
        ) : ImportState()

        /** KIF検証OK。先後選択待ち。 */
        data class SideConfirm(
            val kifText: String,
            val senteName: String?,
            val goteName: String?,
            /**
             * ファイル取込の場合の実ファイル名（DocumentPickerで選んだファイル名）。
             * null = クリップボード取込（[AppStrings.clipboardFileName] で生成する）。
             */
            val sourceFileName: String? = null,
            /** アカウント名一致 or 前回選択からの先後の推定（ダイアログの初期選択）。 */
            val suggestedSide: String? = null,
            /** 推定がアカウント名一致によるものか（一致時のみ省略チェックボックスを出す）。 */
            val suggestedByAccount: Boolean = false,
        ) : ImportState()

        /** 解析中（進捗%）。 */
        data class Analyzing(val done: Int, val total: Int) : ImportState()

        /**
         * クリップボード/ファイルが空・不正、または解析失敗。
         * [fromFile] でエラーダイアログのタイトルを取込元別に出し分ける。
         */
        data class Error(val message: String, val fromFile: Boolean = false) : ImportState()
    }

    private val scope = CoroutineScope(SupervisorJob() + defaultIoDispatcher)
    private val coefTable = IosCoefficients.getInstance()

    /** ホーム画面（games一覧・推定棋力カード・今日の1問）のロードを担う協力オブジェクト。 */
    private val homeViewModel = HomeViewModel(
        gameRepository = gameRepository,
        drillRepository = drillRepository,
        settingsRepository = settingsRepository,
        coefTable = coefTable,
    )

    /** 形勢の表示単位（'cp'/'wp'）。DBから読み込み、以後は即時反映する。 */
    private val _evalDisplay = MutableStateFlow("cp")
    val evalDisplay: StateFlow<String> = _evalDisplay.asStateFlow()

    /** レポート表示状態・読み筋延長・検討モードを担う協力オブジェクト（androidApp と同型）。 */
    val reportViewModel: ReportViewModel = ReportViewModel(
        scope = scope,
        repository = gameRepository,
        coefTable = coefTable,
        engineFactory = IosEngineHost.studyEngineFactory(),
        evalDisplayProvider = { _evalDisplay.value },
    )

    /** 読み筋オンデマンド延長の状態 Map（blunderId → PvExtState）。ReportViewModel へ委譲。 */
    val pvExtState: StateFlow<Map<Long, PvExtState>> get() = reportViewModel.pvExtState

    /** レポート画面の検討モード状態（null = 検討していない）。ReportViewModel へ委譲。 */
    val studyState: StateFlow<StudyState?> get() = reportViewModel.studyState

    private val _homeData = MutableStateFlow<HomeViewModel.HomeResult?>(null)
    val homeData: StateFlow<HomeViewModel.HomeResult?> = _homeData.asStateFlow()

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    /**
     * 解析が完了した直後のゲームID。UI側（MainViewController）がレポート画面への遷移に
     * 使い、遷移したら [consumeCompletedGameId] で消費する（androidApp の
     * onAnalysisCompleted → showReport と同じ「完了したらレポートへ」の挙動）。
     * ImportState に Completed を足さない理由: 遷移は一度きりのイベントで、
     * 状態として残すと再コンポーズのたびに遷移が再発火する。
     */
    private val _completedGameId = MutableStateFlow<Long?>(null)
    val completedGameId: StateFlow<Long?> = _completedGameId.asStateFlow()

    fun consumeCompletedGameId() {
        _completedGameId.value = null
    }

    /** テーマモード（'system'/'light'/'dark'）。DBから読み込み、以後は即時反映する。 */
    private val _themeMode = MutableStateFlow("system")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    /** 先後確認の省略設定。DBから読み込み、以後は即時反映する。 */
    private val _skipSideConfirm = MutableStateFlow(false)
    val skipSideConfirm: StateFlow<Boolean> = _skipSideConfirm.asStateFlow()

    init {
        scope.launch { _themeMode.value = settingsRepository.getThemeMode() }
        scope.launch { _evalDisplay.value = settingsRepository.getEvalDisplay() }
        scope.launch { _skipSideConfirm.value = settingsRepository.getSkipSideConfirm() }
        reloadHome()
    }

    fun reloadHome() {
        scope.launch { _homeData.value = homeViewModel.loadHomeData() }
    }

    /** 特定のゲームIDのレポート表示状態をDBから読み込む（ReportViewModel へ委譲）。 */
    suspend fun loadReport(gameId: Long): ReportViewModel.ReportResult = reportViewModel.loadReport(gameId)

    /** テーマモードを保存し StateFlow を即時更新する。 */
    fun saveThemeMode(mode: String) {
        scope.launch {
            settingsRepository.saveThemeMode(mode)
            _themeMode.value = mode
        }
    }

    /** 形勢の表示単位を保存し StateFlow を即時更新する。 */
    fun saveEvalDisplay(mode: String) {
        scope.launch {
            settingsRepository.saveEvalDisplay(mode)
            _evalDisplay.value = mode
        }
    }

    /** 先後確認の省略設定を保存し StateFlow を即時更新する。 */
    fun saveSkipSideConfirm(skip: Boolean) {
        scope.launch {
            settingsRepository.saveSkipSideConfirm(skip)
            _skipSideConfirm.value = skip
        }
    }

    // ─── クリップボード取込フロー ──────────────────────────────

    /**
     * クリップボードを読み取って取込フローへ渡す。
     *
     * UIPasteboard.string の同期読みは pasteboardd とのIPC・Universal Clipboard の
     * iCloudフェッチで数秒ブロックすることがあり、メインスレッド（タップハンドラ）で
     * 呼ぶと App Hang になる（Sentry実測）。そのためバックグラウンドの [scope] で読む。
     */
    fun handleClipboardImport() {
        scope.launch {
            val text = UIPasteboard.generalPasteboard.string
            handlePastedText(text)
        }
    }

    /**
     * クリップボードから取得した生テキストを検証する。
     * 有効なKIFなら先後選択（[ImportState.SideConfirm]）へ、無効/空なら [ImportState.Error] へ。
     */
    fun handlePastedText(text: String?) {
        when {
            text.isNullOrBlank() -> _importState.value = ImportState.Error(AppStrings.KIF_CLIPBOARD_EMPTY)
            !ClipboardKifValidator.isValidKif(text) ->
                _importState.value = ImportState.Error(AppStrings.KIF_CLIPBOARD_INVALID)
            else -> {
                val headers = runCatching { KifParser().parse(text).headers }.getOrElse { emptyMap() }
                proceedAfterKifValidated(
                    kifText = text,
                    senteName = headers["先手"],
                    goteName = headers["後手"],
                    sourceFileName = null,
                )
            }
        }
    }

    /**
     * KIF検証後の分岐（androidApp の KifImportFlow.startKifFlow と同じ判断）:
     * 1. アカウント名が全サービス未設定 → 先に棋力設定（[ImportState.RatingSetup]）
     * 2. アカウント名一致＋省略設定ON → 確認なしで即解析
     * 3. それ以外 → 先後選択ダイアログ（推定側を初期選択に）
     */
    private fun proceedAfterKifValidated(
        kifText: String,
        senteName: String?,
        goteName: String?,
        sourceFileName: String?,
    ) {
        if (!settingsRepository.hasAnyServiceAccount()) {
            _importState.value = ImportState.RatingSetup(kifText, senteName, goteName, sourceFileName)
            return
        }
        val suggestion = UserSideSuggester.suggest(
            senteName = senteName,
            goteName = goteName,
            accountNames = settingsRepository.getAllServiceAccounts().values.toSet(),
            lastUserSide = settingsRepository.getLastUserSide(),
        )
        val skip = UserSideSuggester.shouldSkipConfirm(suggestion, settingsRepository.getSkipSideConfirm())
        _importState.value = ImportState.SideConfirm(
            kifText = kifText,
            senteName = senteName,
            goteName = goteName,
            sourceFileName = sourceFileName,
            suggestedSide = suggestion.side,
            suggestedByAccount = suggestion.matchedByAccount,
        )
        // suggestion.side は別モジュールの公開プロパティのためsmart castできない（K/N制約）。
        val confirmedSide = suggestion.side
        if (skip && confirmedSide != null) {
            confirmSideAndAnalyze(confirmedSide)
        }
    }

    /**
     * 取込フロー中の棋力設定を保存して先後選択へ進む（[ImportState.RatingSetup] の確定）。
     * 保存内容は androidApp の MainViewModel.saveRatingSettings と同じ分解で
     * user_settings / service_account / service_rank へ書き込む。
     */
    fun completeRatingSetup(
        service: String?,
        ratingRaw: Int?,
        ratingRule: String?,
        serviceAccounts: Map<String, String>,
        serviceRanks: Map<String, Map<String, Int>>,
    ) {
        val current = _importState.value as? ImportState.RatingSetup ?: return
        saveRatingSettings(service, ratingRaw, ratingRule, serviceAccounts, serviceRanks)
        proceedAfterKifValidated(
            kifText = current.kifText,
            senteName = current.senteName,
            goteName = current.goteName,
            sourceFileName = current.sourceFileName,
        )
    }

    /** 棋力設定の一括保存（設定画面・取込フロー共用）。 */
    fun saveRatingSettings(
        service: String?,
        ratingRaw: Int?,
        ratingRule: String?,
        serviceAccounts: Map<String, String>,
        serviceRanks: Map<String, Map<String, Int>>,
    ) {
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

    /** 棋力設定ダイアログの初期値（設定画面・取込フロー共用）。 */
    fun getRatingSettings(): RatingSettings = settingsRepository.getRatingSettings()

    fun getAllServiceAccounts(): Map<String, String> = settingsRepository.getAllServiceAccounts()

    fun getAllServiceRanks(): Map<String, Map<String, Int>> = settingsRepository.getAllServiceRanks()

    /** 取込フローを閉じる（ダイアログの「キャンセル」/エラーダイアログの確認）。 */
    fun dismissImport() {
        _importState.value = ImportState.Idle
    }

    /**
     * DocumentPicker（Swift側 KifFilePickerCoordinator）で選ばれたファイルの
     * テキストを検証する。[handlePastedText] のファイル版。text が null はデコード失敗
     * （UTF-8/Shift_JISいずれでも読めなかった等）を表し、空/不正と同じエラー表示に流す。
     */
    fun handleFileImport(fileName: String, text: String?) {
        when {
            text.isNullOrBlank() ->
                _importState.value = ImportState.Error(AppStrings.KIF_FILE_EMPTY, fromFile = true)
            !ClipboardKifValidator.isValidKif(text) ->
                _importState.value = ImportState.Error(AppStrings.KIF_FILE_INVALID, fromFile = true)
            else -> {
                val headers = runCatching { KifParser().parse(text).headers }.getOrElse { emptyMap() }
                proceedAfterKifValidated(
                    kifText = text,
                    senteName = headers["先手"],
                    goteName = headers["後手"],
                    sourceFileName = fileName,
                )
            }
        }
    }

    /** 先後を確定し、解析を開始する。 */
    fun confirmSideAndAnalyze(userSide: String) {
        val current = _importState.value
        if (current !is ImportState.SideConfirm) return
        // 次回の先後推定のフォールバックに使う（androidApp の startAnalysis と同じ）。
        settingsRepository.saveLastUserSide(userSide)
        _importState.value = ImportState.Analyzing(0, 0)

        scope.launch {
            val orchestrator = AnalysisOrchestrator(
                repository = gameRepository,
                coefTable = coefTable,
                // iOS はプロセス内で1エンジンのみ（in-process制約）のため workers=1。
                workers = 1,
                engineFactory = IosEngineHost.newGameEngineFactory(),
                disposeEngine = IosEngineHost.keepAliveDispose,
            )
            val outcome = orchestrator.analyzeAndSave(
                kifContent = current.kifText,
                fileName = current.sourceFileName ?: AppStrings.clipboardFileName(currentDateTimeLabel()),
                userSide = userSide,
                onProgress = { done, total -> _importState.value = ImportState.Analyzing(done, total) },
            )
            when (outcome) {
                is AnalysisOrchestrator.Outcome.Completed -> {
                    // 自動アップロード設定ON＋ログイン中のときだけ実行される
                    // （androidApp の AnalysisService と同じ配線・失敗はサイレント）。
                    uploadOrchestrator?.maybeAutoUpload(outcome.gameId)
                    reloadHome()
                    _importState.value = ImportState.Idle
                    _completedGameId.value = outcome.gameId
                }
                is AnalysisOrchestrator.Outcome.Failed -> {
                    _importState.value = ImportState.Error(outcome.message)
                }
            }
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

    /** リーク厳禁: 呼び出し元（MainViewController）が破棄されるタイミングで呼ぶこと。 */
    fun dispose() {
        reportViewModel.dispose()
    }
}
