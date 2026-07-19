package dev.miyado.shogisupplement

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.miyado.shogisupplement.db.AppDatabase
import dev.miyado.shogisupplement.upload.UploadResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * デバッグ専用ブロードキャストレシーバ（debug source set にのみ存在）。
 *
 * 未アップロード分の一括アップロードを adb から手動発火し、
 * logcat（タグ: DebugUpload）で根本原因を診断するためのもの。
 *
 * 発火コマンド:
 *   adb shell am broadcast \
 *     -a dev.miyado.shogisupplement.DEBUG_UPLOAD \
 *     -p dev.miyado.shogisupplement
 *
 * release ビルドにはこのファイルが含まれないため、manifest登録も debug source set に分離。
 */
class DebugUploadReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DEBUG_UPLOAD) return
        Log.i(TAG, "=== DEBUG_UPLOAD broadcast received ===")

        val pendingResult = goAsync()
        scope.launch {
            try {
                val app = context.applicationContext as ShogiApp
                val gameRepository = AppDatabase.gameRepository(context)
                val settingsRepository = AppDatabase.settingsRepository(context)

                // 未アップロード対象を列挙
                val notUploaded = gameRepository.getNotUploadedGames()
                Log.i(TAG, "対象: ${notUploaded.size}局")

                if (notUploaded.isEmpty()) {
                    // 対象が 0 局の場合はアップロード条件のバグを診断する
                    val allGames = gameRepository.getAllGames()
                    Log.i(TAG, "全局数: ${allGames.size}局（すべてアップロード済みか解析対象外）")
                    val currentUser = app.authRepository.currentUser.value
                    Log.i(TAG, "ログイン状態: ${if (currentUser != null) "ログイン中" else "未ログイン"}")
                    val autoUpload = settingsRepository.getAutoUpload()
                    Log.i(TAG, "自動アップロード設定: $autoUpload")
                    Log.i(TAG, "=== DEBUG_UPLOAD 完了: 対象0局（アップロード条件またはデータを確認） ===")
                    return@launch
                }

                var success = 0
                var failed = 0
                notUploaded.forEach { game ->
                    Log.d(TAG, "アップロード試行: gameId=${game.id} fileName=${game.fileName}")
                    val result = try {
                        app.uploadOrchestrator.uploadGame(game.id)
                    } catch (e: Exception) {
                        Log.e(TAG, "例外: gameId=${game.id}", e)
                        UploadResult.Failure("例外: ${e.message}")
                    }
                    when (result) {
                        is UploadResult.Success -> {
                            success++
                            Log.i(TAG, "成功: gameId=${game.id} fileName=${game.fileName}")
                        }
                        is UploadResult.Duplicate -> {
                            success++
                            Log.i(TAG, "重複（済み扱い）: gameId=${game.id} fileName=${game.fileName}")
                        }
                        is UploadResult.Failure -> {
                            failed++
                            Log.e(TAG, "失敗: gameId=${game.id} reason=${result.message}")
                        }
                        null -> {
                            failed++
                            Log.w(TAG, "スキップ（未ログインまたは既アップロード）: gameId=${game.id}")
                        }
                    }
                }
                Log.i(
                    TAG,
                    "=== DEBUG_UPLOAD 完了: 対象${notUploaded.size}局 / 成功${success}局 / 失敗${failed}局 ===",
                )
            } catch (e: Exception) {
                Log.e(TAG, "=== DEBUG_UPLOAD 例外 ===", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_DEBUG_UPLOAD = "dev.miyado.shogisupplement.DEBUG_UPLOAD"
        private const val TAG = "DebugUpload"
    }
}
