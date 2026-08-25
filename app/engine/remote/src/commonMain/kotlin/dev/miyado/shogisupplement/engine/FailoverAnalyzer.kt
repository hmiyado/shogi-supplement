package dev.miyado.shogisupplement.engine

import dev.miyado.shogisupplement.util.Logger
import kotlinx.coroutines.CancellationException

/**
 * リモート解析のQuotaExceeded/ConnectionLostだけをfallbackAnalyzerへ切り替える。
 * UpgradeRequired、EngineFailure、認証・入力エラーは経路を変えずに伝播する。
 * fallbackも失敗した場合は元の例外を返し、部分結果を重複通知しない契約を前提とする。
 */
class FailoverAnalyzer(
    private val delegate: GameAnalyzer,
    private val fallbackAnalyzer: GameAnalyzer,
    private val shouldFallback: (RemoteAnalysisException) -> Boolean = ::defaultShouldFailover,
) : GameAnalyzer {
    override suspend fun analyzeGame(
        moves: List<String>,
        onPositionResult: ((ply: Int, pvs: List<PvInfo>) -> Unit)?,
        onProgress: ((done: Int, total: Int) -> Unit)?,
    ): List<List<PvInfo>> {
        return try {
            delegate.analyzeGame(moves, onPositionResult, onProgress)
        } catch (e: RemoteAnalysisException) {
            if (!shouldFallback(e)) throw e
            try {
                fallbackAnalyzer.analyzeGame(moves, onPositionResult, onProgress)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (fallbackFailure: Exception) {
                Logger.e(TAG, "フォールバック解析が失敗したため元のサーバーエラーへ戻す", fallbackFailure)
                throw e
            }
        }
    }

    companion object {
        private const val TAG = "FailoverAnalyzer"
    }
}

/** [FailoverAnalyzer] の既定フォールバック判定。クォータ超過・接続断（5xx含む）のみ対象。 */
internal fun defaultShouldFailover(e: RemoteAnalysisException): Boolean = when (e) {
    is RemoteAnalysisException.QuotaExceeded -> true
    is RemoteAnalysisException.ConnectionLost -> true
    else -> false
}
