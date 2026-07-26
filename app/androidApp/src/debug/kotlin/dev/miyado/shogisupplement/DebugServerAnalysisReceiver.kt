package dev.miyado.shogisupplement

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import dev.miyado.shogisupplement.db.AppDatabase
import dev.miyado.shogisupplement.engine.AnalysisOrchestrator
import dev.miyado.shogisupplement.engine.RemoteAnalysisException
import dev.miyado.shogisupplement.engine.RemoteAnalysisRunner
import dev.miyado.shogisupplement.judge.CoefficientTable
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * デバッグ専用ブロードキャストレシーバ（debug source set にのみ存在）。
 *
 * 端末解析ではなくサーバー解析（Cloud Run の analysis-worker）を経由して1局を解析し、
 * アプリ⇄サーバーの疎通を logcat（タグ: DebugServerAnalysis）で確認するためのもの。
 * Play版の解析導線は端末解析のままで、このレシーバは release ビルドに含まれない。
 *
 * 発火コマンド:
 *   adb push game.kif /sdcard/Download/game.kif
 *   adb shell am broadcast \
 *     -a dev.miyado.shogisupplement.DEBUG_SERVER_ANALYSIS \
 *     -p dev.miyado.shogisupplement \
 *     -e kif_uri file:///sdcard/Download/game.kif
 *
 * ベースURLは local.properties の ANALYSIS_BASE_URL（BuildConfig 経由）。未設定なら何もしない。
 */
class DebugServerAnalysisReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DEBUG_SERVER_ANALYSIS) return
        Log.i(TAG, "=== DEBUG_SERVER_ANALYSIS broadcast received ===")

        val baseUrl = BuildConfig.ANALYSIS_BASE_URL
        if (baseUrl.isBlank()) {
            Log.e(TAG, "ANALYSIS_BASE_URL が未設定（local.properties を確認）")
            return
        }
        val uriString = intent.getStringExtra(EXTRA_KIF_URI)
        if (uriString == null) {
            Log.e(TAG, "kif_uri が指定されていない")
            return
        }

        val pendingResult = goAsync()
        scope.launch {
            try {
                runServerAnalysis(context, baseUrl, uriString)
            } catch (e: Exception) {
                Log.e(TAG, "=== DEBUG_SERVER_ANALYSIS 例外 ===", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun runServerAnalysis(context: Context, baseUrl: String, uriString: String) {
        val app = context.applicationContext as ShogiApp

        // サーバーはJWTでユーザーを識別するため、未ログインならここで匿名サインインする
        // （本番導線ではオンボーディングが担う部分）。
        if (app.authRepository.currentUser.value == null) {
            Log.i(TAG, "未ログインのため匿名サインインする")
            app.authRepository.signInAnonymously()
                .onFailure { e ->
                    Log.e(TAG, "匿名サインイン失敗", e)
                    return
                }
        }
        Log.i(TAG, "ログイン済み: uid=${app.authRepository.currentUser.value?.id}")

        val kifContent = readKif(context, Uri.parse(uriString))
        val coefJson = context.assets.open("coefficients_hao_v1.json").readBytes().decodeToString()

        // 解析は1リクエストで全局面を返すまでストリームを保持するため、既定のタイムアウトでは
        // 途中で切れる。ワーカー側の上限（Cloud Runのリクエストタイムアウト）より長く取る。
        val httpClient = HttpClient(OkHttp) {
            install(HttpTimeout) {
                requestTimeoutMillis = REQUEST_TIMEOUT_MS
                socketTimeoutMillis = SOCKET_TIMEOUT_MS
            }
        }

        try {
            val orchestrator = AnalysisOrchestrator(
                repository = AppDatabase.gameRepository(context),
                coefTable = CoefficientTable.fromJson(coefJson),
                analyzer = RemoteAnalysisRunner(
                    baseUrl = baseUrl,
                    accessTokenProvider = {
                        checkNotNull(app.authRepository.accessToken()) { "アクセストークンが取得できない" }
                    },
                    httpClient = httpClient,
                ),
            )

            val startedAt = System.currentTimeMillis()
            Log.i(TAG, "解析開始: baseUrl=$baseUrl uri=$uriString")
            val outcome = orchestrator.analyzeAndSave(
                kifContent = kifContent,
                fileName = uriString.substringAfterLast('/'),
                onProgress = { done, total -> Log.i(TAG, "進捗 $done/$total") },
            )
            val elapsedMs = System.currentTimeMillis() - startedAt

            when (outcome) {
                is AnalysisOrchestrator.Outcome.Completed -> Log.i(
                    TAG,
                    "=== 完了: gameId=${outcome.gameId} " +
                        "alreadyExisted=${outcome.alreadyExisted} ${elapsedMs}ms ===",
                )
                is AnalysisOrchestrator.Outcome.Failed -> Log.e(
                    TAG,
                    "=== 失敗: ${outcome.message} (${elapsedMs}ms) ===",
                )
            }
        } catch (e: RemoteAnalysisException) {
            // 401/403/429など、サーバーが理由を明示して拒否したケース。
            Log.e(TAG, "=== サーバーが拒否: ${e::class.simpleName} ${e.message} ===")
        } finally {
            httpClient.close()
        }
    }

    private fun readKif(context: Context, uri: Uri): String =
        if (uri.scheme == "file") {
            java.io.File(uri.path!!).readText()
        } else {
            context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
        }

    companion object {
        const val ACTION_DEBUG_SERVER_ANALYSIS = "dev.miyado.shogisupplement.DEBUG_SERVER_ANALYSIS"
        private const val EXTRA_KIF_URI = "kif_uri"
        private const val TAG = "DebugServerAnalysis"
        private const val REQUEST_TIMEOUT_MS = 10 * 60 * 1000L
        private const val SOCKET_TIMEOUT_MS = 5 * 60 * 1000L
    }
}
