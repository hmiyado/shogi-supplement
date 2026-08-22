package dev.miyado.shogisupplement.upload

import dev.miyado.shogisupplement.auth.AuthRepository
import dev.miyado.shogisupplement.db.GameRepository
import dev.miyado.shogisupplement.db.SettingsRepository
import dev.miyado.shogisupplement.util.currentEpochSeconds

/** アップロードのオーケストレーター。constructor injectionでテスト可能（fakeを注入できる）。 */
class UploadOrchestrator(
    private val authRepository: AuthRepository,
    private val uploadRepository: UploadRepository,
    private val dbRepository: GameRepository,
    private val settingsRepository: SettingsRepository,
) {

    /**
     * 指定ゲームをアップロードする。未ログイン/既アップロード（Duplicate扱い）で
     * 実行しなかった場合は null。
     */
    suspend fun uploadGame(gameId: Long): UploadResult? {
        val user = authRepository.currentUser.value ?: return null  // 未ログイン
        val game = dbRepository.getGameById(gameId) ?: return null
        if (game.uploadedAt != null) return UploadResult.Duplicate  // 既アップロード
        val reports = dbRepository.getReports(gameId)
        val result = uploadRepository.uploadGame(user.id, game, reports)
        if (result is UploadResult.Success || result is UploadResult.Duplicate) {
            dbRepository.updateUploadedAt(gameId, currentEpochSeconds())
        }
        return result
    }

    /**
     * サーバーに保存済みの棋譜を削除する。未ログインなら false。
     */
    suspend fun deleteUploadedGame(contentHash: String): Boolean {
        val user = authRepository.currentUser.value ?: return false
        return uploadRepository.deleteGame(user.id, contentHash)
    }

    /**
     * 全未アップロードゲームをアップロードする。
     * - 未ログインなら空マップを返す
     * - 各ゲームの結果を gameId → UploadResult のマップで返す
     */
    suspend fun uploadAll(): Map<Long, UploadResult> {
        if (authRepository.currentUser.value == null) return emptyMap()
        val games = dbRepository.getNotUploadedGames()
        return games.associate { game ->
            val result = uploadGame(game.id) ?: UploadResult.Failure("未ログイン")
            game.id to result
        }
    }

    /**
     * 自動アップロード設定 ON かつログイン中の場合に解析後アップロードを実行する。
     * 失敗してもアプリ動作に影響させない（例外を呑む）。
     */
    suspend fun maybeAutoUpload(gameId: Long) {
        if (!settingsRepository.getAutoUpload()) return   // 自動アップロードOFF
        if (authRepository.currentUser.value == null) return  // 未ログイン
        try {
            uploadGame(gameId)
        } catch (_: Exception) {
            // 自動アップロードの失敗はサイレント
        }
    }
}
