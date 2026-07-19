package dev.miyado.shogisupplement.upload

import dev.miyado.shogisupplement.auth.AuthRepository
import dev.miyado.shogisupplement.db.GameRepository
import dev.miyado.shogisupplement.db.SettingsRepository
import dev.miyado.shogisupplement.util.currentEpochSeconds

/**
 * アップロードのオーケストレーター。
 * - ログイン状態チェック
 * - 重複チェック（既に uploaded_at が設定済みならスキップ）
 * - アップロード実行
 * - 成功/重複時に uploaded_at を記録
 *
 * constructor injection でテスト可能（fake を注入できる）。
 */
class UploadOrchestrator(
    private val authRepository: AuthRepository,
    private val uploadRepository: UploadRepository,
    private val dbRepository: GameRepository,
    private val settingsRepository: SettingsRepository,
) {

    /**
     * 指定ゲームをアップロードする。
     * - 未ログインなら null を返す（アップロードをスキップ）
     * - 既に uploaded_at が設定されていればスキップ（Duplicate 扱い）
     * - 成功 or 重複なら uploaded_at を記録する
     *
     * @return UploadResult、または未ログイン/既アップロードのため実行しなかった場合は null
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
