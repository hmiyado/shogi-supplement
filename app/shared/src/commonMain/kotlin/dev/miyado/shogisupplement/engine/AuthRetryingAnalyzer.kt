package dev.miyado.shogisupplement.engine

import dev.miyado.shogisupplement.auth.AuthRepository

/**
 * Unauthorized時にrefreshSessionを一度だけ試して再実行するGameAnalyzerラッパー。
 * Why not signInAnonymously: 新規アカウントで既存データとの連続性が切れるため。
 */
class AuthRetryingAnalyzer(
    private val delegate: GameAnalyzer,
    private val authRepository: AuthRepository,
) : GameAnalyzer {
    override suspend fun analyzeGame(
        moves: List<String>,
        onPositionResult: ((ply: Int, pvs: List<PvInfo>) -> Unit)?,
        onProgress: ((done: Int, total: Int) -> Unit)?,
    ): List<List<PvInfo>> {
        return try {
            delegate.analyzeGame(moves, onPositionResult, onProgress)
        } catch (e: RemoteAnalysisException.Unauthorized) {
            val refreshed = authRepository.refreshSession()
            if (refreshed.isFailure) throw e
            // リトライは1回のみ。再度Unauthorizedが出た場合はそのまま呼び出し元へ伝播する。
            delegate.analyzeGame(moves, onPositionResult, onProgress)
        }
    }
}
