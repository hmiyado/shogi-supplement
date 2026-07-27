package dev.miyado.shogisupplement.engine

import dev.miyado.shogisupplement.auth.AuthRepository

/**
 * [delegate] が [RemoteAnalysisException.Unauthorized] を投げたとき、
 * [authRepository.refreshSession] で1回だけセッション再取得を試み、成功したら1回だけ
 * リトライする [GameAnalyzer] ラッパー。refreshSession も失敗、または再試行後も401なら
 * そのまま例外を伝播する（[AnalysisOrchestrator] が [RemoteAnalysisErrorMapper] で
 * 日本語メッセージへ変換する）。
 *
 * Why not signInAnonymously: 匿名認証を自動で再実行すると新規アカウントが発行され、
 * 既存ユーザーのデータ（提供済み棋譜・引き継ぎコードでの復元対象）との連続性が切れる。
 * refreshSession はトークンの再発行のみでアカウントを作り直さないため、ここでの
 * 自動復旧に使えるのは refreshSession だけ。
 */
class AuthRetryingAnalyzer(
    private val delegate: GameAnalyzer,
    private val authRepository: AuthRepository,
) : GameAnalyzer {
    override suspend fun analyzeGame(
        moves: List<String>,
        onProgress: ((done: Int, total: Int) -> Unit)?,
    ): List<List<PvInfo>> {
        return try {
            delegate.analyzeGame(moves, onProgress)
        } catch (e: RemoteAnalysisException.Unauthorized) {
            val refreshed = authRepository.refreshSession()
            if (refreshed.isFailure) throw e
            // リトライは1回のみ。再度Unauthorizedが出た場合はそのまま呼び出し元へ伝播する。
            delegate.analyzeGame(moves, onProgress)
        }
    }
}
