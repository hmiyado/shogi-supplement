package dev.miyado.shogisupplement.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.app.NotificationCompat
import dev.miyado.shogisupplement.MainActivity
import dev.miyado.shogisupplement.ShogiApp
import dev.miyado.shogisupplement.crash.SentryCrashReporter
import dev.miyado.shogisupplement.crash.isAlreadyReported
import dev.miyado.shogisupplement.db.AppDatabase
import dev.miyado.shogisupplement.engine.AnalysisOrchestrator
import dev.miyado.shogisupplement.engine.EvalLoader
import dev.miyado.shogisupplement.engine.createAndroidAnalysisRunner
import dev.miyado.shogisupplement.judge.CoefficientTable
import dev.miyado.shogisupplement.kifu.KifParser
import dev.miyado.shogisupplement.pipeline.InProgressAnalysisRegistry
import dev.miyado.shogisupplement.service.AnalysisServiceBus.ServiceEvent
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.util.sha256Hex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Androidの通知と解析プロセスの生存期間を管理するフォアグラウンドサービス。 */
class AnalysisService : Service() {

    private val TAG = "AnalysisService"
    private val NOTIF_ID = 1001

    private val crashReporter = SentryCrashReporter()

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + job)

    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val uriString = intent?.getStringExtra(EXTRA_KIF_URI)
        val gameId = intent?.getLongExtra(EXTRA_GAME_ID, -1L)?.takeIf { it >= 0L }
        if (uriString == null && gameId == null) {
            Log.w(TAG, "No KIF URI provided")
            stopSelf()
            return START_NOT_STICKY
        }
        val userSide = intent.getStringExtra(EXTRA_USER_SIDE)
        val ratingService = intent.getStringExtra(EXTRA_RATING_SERVICE)
        val ratingRaw = intent.getIntExtra(EXTRA_RATING_RAW, -1).takeIf { it >= 0 }
        val ratingRule = intent.getStringExtra(EXTRA_RATING_RULE)

        startForeground(NOTIF_ID, buildProgressNotification(0, 0))

        scope.launch(Dispatchers.IO) {
            runAnalysis(uriString, gameId, userSide, ratingService, ratingRaw?.toLong(), ratingRule)
        }

        return START_NOT_STICKY
    }

    private suspend fun runAnalysis(
        uriString: String?,
        gameId: Long?,
        userSide: String? = null,
        ratingService: String? = null,
        ratingRaw: Long? = null,
        ratingRule: String? = null,
    ) {
        // ID確定前の失敗ではレジストリへの登録もないため、nullならfinishしない。
        var sessionId: String? = null
        try {
            val repository = AppDatabase.gameRepository(this)
            val storedGame = gameId?.let(repository::getGameById)
            val uri = uriString?.let(Uri::parse)
            val kifContent = storedGame?.kifText ?: uri?.let(::readKifContent)
                ?: error("KIF content is missing")
            val fileName = storedGame?.fileName ?: uri?.let(::getFileName) ?: "unknown.kif"
            val effectiveUserSide = storedGame?.userSide ?: userSide
            val effectiveRatingService = storedGame?.ratingService ?: ratingService
            val effectiveRatingRaw = storedGame?.ratingRaw ?: ratingRaw
            val effectiveRatingRule = storedGame?.ratingRule ?: ratingRule

            Log.i(TAG, "Starting analysis: $fileName")

            // 手順・content_hashはKIFパース直後（エンジン解析の開始前）から確定するため、
            // orchestrator呼び出しより先にレジストリへ登録できる。
            // AnalysisOrchestrator側でも同じ入力から同じハッシュを計算するため値は一致する。
            val id = storedGame?.contentHash ?: sha256Hex(kifContent)
            sessionId = id
            val moves = runCatching { KifParser().parse(kifContent).moves }.getOrElse { emptyList() }
            InProgressAnalysisRegistry.shared.start(id, fileName, moves, effectiveUserSide)

            // 係数読み込み
            val coefJson = assets.open(CoefficientTable.COEFFICIENTS_FILE_NAME).readBytes().decodeToString()
            val coef = CoefficientTable.fromJson(coefJson)

            // eval dir 準備
            val evalDir = EvalLoader.ensureReady(this)

            val orchestrator = AnalysisOrchestrator(
                repository = repository,
                coefTable = coef,
                analyzer = createAndroidAnalysisRunner(
                    appInfo = applicationInfo,
                    evalDir = evalDir,
                    crashReporter = crashReporter,
                ),
                crashReporter = crashReporter,
            )

            val outcome = orchestrator.analyzeAndSave(
                kifContent = kifContent,
                fileName = fileName,
                userSide = effectiveUserSide,
                ratingService = effectiveRatingService,
                ratingRaw = effectiveRatingRaw,
                ratingRule = effectiveRatingRule,
                contentHash = storedGame?.contentHash,
                sourcePlaceOverride = storedGame?.sourcePlace,
                onProgress = { done, total ->
                    if (done % 5 == 0 || done == total) {
                        updateProgressNotification(done, total)
                    }
                },
                onPositionResult = { ply, pvs ->
                    InProgressAnalysisRegistry.shared.updatePosition(id, ply, pvs)
                    AnalysisServiceBus.emit(ServiceEvent.PositionResult(ply, pvs))
                },
            )

            when (outcome) {
                is AnalysisOrchestrator.Outcome.Completed -> {
                    Log.i(
                        TAG,
                        "Analysis completed: gameId=${outcome.gameId} alreadyExisted=${outcome.alreadyExisted}",
                    )
                    AnalysisServiceBus.emit(ServiceEvent.Completed(outcome.gameId, outcome.alreadyExisted))
                    showCompletionNotification(outcome.gameId)

                    if (!outcome.alreadyExisted) {
                        // 子コルーチンはstopSelf後にキャンセルされるため、直接完了を待つ。
                        try {
                            val app = applicationContext as ShogiApp
                            Log.i("AutoUpload", "Starting auto upload for gameId=${outcome.gameId}")
                            app.uploadOrchestrator.maybeAutoUpload(outcome.gameId)
                            Log.i("AutoUpload", "Auto upload completed for gameId=${outcome.gameId}")
                        } catch (e: Exception) {
                            Log.w("AutoUpload", "Auto upload failed (non-fatal) for gameId=${outcome.gameId}", e)
                        }
                    }
                }
                is AnalysisOrchestrator.Outcome.Failed -> {
                    Log.e(TAG, "Analysis failed: ${outcome.message}")
                    AnalysisServiceBus.emit(ServiceEvent.Failed(outcome.message))
                    showErrorNotification(outcome.message)
                }
            }
        } catch (e: Exception) {
            // AnalysisOrchestrator 内部で捕捉されない例外（URI読み込み失敗等）
            Log.e(TAG, "Analysis failed (outer)", e)
            if (!e.isAlreadyReported()) {
                crashReporter.captureException(e)
            }
            AnalysisServiceBus.emit(ServiceEvent.Failed(e.message ?: AppStrings.UNKNOWN_ERROR))
            showErrorNotification(e.message ?: AppStrings.UNKNOWN_ERROR)
        } finally {
            // 完了・失敗・想定外の例外いずれの経路でも、登録済みなら必ずレジストリから外す
            // （ここを分岐ごとに書くと呼び忘れの余地が出るため、finally一箇所に集約する）。
            sessionId?.let { InProgressAnalysisRegistry.shared.finish(it) }
            stopSelf()
        }
    }

    private fun readKifContent(uri: Uri): String {
        return if (uri.scheme == "file") {
            java.io.File(uri.path!!).readText()
        } else {
            contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
        }
    }

    private fun getFileName(uri: Uri): String? {
        return if (uri.scheme == "file") {
            uri.lastPathSegment
        } else {
            contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null, null, null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                } else null
            }
        }
    }

    private fun buildProgressNotification(done: Int, total: Int): Notification {
        val progress = if (total > 0) (done * 100 / total) else 0
        return NotificationCompat.Builder(this, ShogiApp.CHANNEL_ANALYSIS)
            .setContentTitle(AppStrings.NOTIF_ANALYZING_TITLE)
            .setContentText(if (total > 0) AppStrings.notifProgress(done, total, progress) else AppStrings.NOTIF_PREPARING)
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setOngoing(true)
            .setProgress(100, progress, total == 0)
            .build()
    }

    private fun updateProgressNotification(done: Int, total: Int) {
        notificationManager.notify(NOTIF_ID, buildProgressNotification(done, total))
    }

    private fun showCompletionNotification(gameId: Long) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_GAME_ID, gameId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = NotificationCompat.Builder(this, ShogiApp.CHANNEL_ANALYSIS)
            .setContentTitle(AppStrings.NOTIF_DONE_TITLE)
            .setContentText(AppStrings.NOTIF_DONE_TEXT)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        notificationManager.notify(NOTIF_COMPLETE_ID, notif)
    }

    private fun showErrorNotification(message: String) {
        val notif = NotificationCompat.Builder(this, ShogiApp.CHANNEL_ANALYSIS)
            .setContentTitle(AppStrings.NOTIF_ERROR_TITLE)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(NOTIF_ERROR_ID, notif)
    }

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_KIF_URI = "kif_uri"
        const val EXTRA_GAME_ID = "game_id"
        const val EXTRA_RATING = "rating"
        const val EXTRA_USER_SIDE = "user_side"
        const val EXTRA_RATING_SERVICE = "rating_service"
        const val EXTRA_RATING_RAW = "rating_raw"
        const val EXTRA_RATING_RULE = "rating_rule"
        private const val NOTIF_COMPLETE_ID = 1002
        private const val NOTIF_ERROR_ID = 1003
    }
}
