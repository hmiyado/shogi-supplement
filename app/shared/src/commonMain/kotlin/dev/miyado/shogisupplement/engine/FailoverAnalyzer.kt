package dev.miyado.shogisupplement.engine

import dev.miyado.shogisupplement.util.Logger
import kotlinx.coroutines.CancellationException

/**
 * [delegate] が [RemoteAnalysisException] を投げたとき、[shouldFallback] の判定に応じて
 * [fallbackAnalyzer] で同じ棋譜を最初から解析し直す [GameAnalyzer] デコレータ。
 *
 * フォールバック対象は既定で [RemoteAnalysisException.QuotaExceeded] と
 * [RemoteAnalysisException.ConnectionLost]（5xx・再POST上限到達を含む）のみ（[shouldFallback]）。
 * - [RemoteAnalysisException.UpgradeRequired] はフォールバックしない: 426は
 *   「このビルドはもう使わせない」という意思表示のため、[fallbackAnalyzer] で解析を
 *   続行させると強制アップデートを迂回させてしまう。
 * - [RemoteAnalysisException.EngineFailure] もフォールバックしない: [delegate] と
 *   [fallbackAnalyzer] が同一の解析条件（[GameAnalyzer] KDoc参照）を保証する実装である前提では、
 *   片方でエンジンが落ちた入力はもう片方でも同じ結果になる見込みが高く、フォールバックしても
 *   救えないまま二重に長い処理を待たせるだけになりやすい。
 * - [RemoteAnalysisException.Unauthorized]/[Banned]/[BadRequest] はクライアント起因
 *   （またはユーザーそのものの状態）のエラーで、解析経路を変えても解決しない。
 *
 * [fallbackAnalyzer] 自体が失敗した場合はフォールバック自体を諦め、元の [delegate] の
 * 例外をそのまま伝播させる（呼び出し側は従来どおり [RemoteAnalysisErrorMapper] 経由の
 * 既存エラー表示に落ちる。fail-safe）。
 *
 * [onPositionResult] の再通知が重複しない前提: [delegate] は例外を投げる前に
 * [onPositionResult] を部分的に呼ばない契約（[GameAnalyzer] KDoc参照）に依存する。
 * この契約に反する実装を [delegate] に渡すと、フォールバック後の [fallbackAnalyzer] 側の
 * 通知と重複しうる。
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
