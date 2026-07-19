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
import dev.miyado.shogisupplement.engine.UsiEngineProcess
import dev.miyado.shogisupplement.judge.CoefficientTable
import dev.miyado.shogisupplement.service.AnalysisServiceBus.ServiceEvent
import dev.miyado.shogisupplement.text.AppStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * KIF解析を行うフォアグラウンドサービス。
 * 解析完了後は AnalysisServiceBus で ViewModel に通知する。
 * 自動アップロードON＋ログイン中なら解析後に uploaded_games へ送信する。
 *
 * 解析のコア処理（KIFパース→エンジン解析→悪手判定→強さ推定→DB保存）は
 * shared/commonMain の [AnalysisOrchestrator] に委譲している。本クラスが持つのは
 * Android専用の処理（フォアグラウンド通知・Intent処理・URI読み込み・ファイル名解決・
 * 自動アップロード呼び出し）のみ。エンジンは局ごとに新規プロセスを起動し、局の解析が
 * 終わったら終了する（[UsiEngineProcess.create]/[UsiEngineProcess.quit]）挙動は
 * AnalysisOrchestrator の engineFactory/disposeEngine 経由で行う。
 */
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
        val uriString = intent?.getStringExtra(EXTRA_KIF_URI) ?: run {
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
            runAnalysis(uriString, userSide, ratingService, ratingRaw?.toLong(), ratingRule)
        }

        return START_NOT_STICKY
    }

    private suspend fun runAnalysis(
        uriString: String,
        userSide: String? = null,
        ratingService: String? = null,
        ratingRaw: Long? = null,
        ratingRule: String? = null,
    ) {
        try {
            val uri = Uri.parse(uriString)
            val kifContent = readKifContent(uri)
            val fileName = getFileName(uri) ?: "unknown.kif"

            Log.i(TAG, "Starting analysis: $fileName")

            val repository = AppDatabase.gameRepository(this)

            // 係数読み込み
            val coefJson = assets.open("coefficients_hao_v1.json").readBytes().decodeToString()
            val coef = CoefficientTable.fromJson(coefJson)

            // eval dir 準備
            val evalDir = EvalLoader.ensureReady(this)

            val orchestrator = AnalysisOrchestrator(
                repository = repository,
                coefTable = coef,
                workers = 4,
                // 局ごとに新規プロセスを起動する（既存挙動そのまま）。
                // disposeEngine は既定値（quit()で毎局プロセスを終了）を使う。
                engineFactory = { UsiEngineProcess.create(applicationInfo, evalDir) },
                crashReporter = crashReporter,
            )

            val outcome = orchestrator.analyzeAndSave(
                kifContent = kifContent,
                fileName = fileName,
                userSide = userSide,
                ratingService = ratingService,
                ratingRaw = ratingRaw,
                ratingRule = ratingRule,
                onProgress = { done, total ->
                    if (done % 5 == 0 || done == total) {
                        updateProgressNotification(done, total)
                        AnalysisServiceBus.emit(ServiceEvent.Progress(done, total))
                    }
                },
            )

            when (outcome) {
                is AnalysisOrchestrator.Outcome.Completed -> {
                    Log.i(
                        TAG,
                        "Analysis completed: gameId=${outcome.gameId} alreadyExisted=${outcome.alreadyExisted}",
                    )
                    AnalysisServiceBus.emit(ServiceEvent.Completed(outcome.gameId))
                    showCompletionNotification(outcome.gameId)

                    if (!outcome.alreadyExisted) {
                        // 自動アップロード（失敗してもアプリ動作に影響させない）
                        // NOTE: scope.launch{} を使わず直接 suspend 呼び出しにする。
                        // 子コルーチンにすると finally の stopSelf() → onDestroy() → job.cancel() で
                        // キャンセルされてアップロードが完了しない競合が起きる。
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
        const val EXTRA_RATING = "rating"
        const val EXTRA_USER_SIDE = "user_side"
        const val EXTRA_RATING_SERVICE = "rating_service"
        const val EXTRA_RATING_RAW = "rating_raw"
        const val EXTRA_RATING_RULE = "rating_rule"
        private const val NOTIF_COMPLETE_ID = 1002
        private const val NOTIF_ERROR_ID = 1003
    }
}
