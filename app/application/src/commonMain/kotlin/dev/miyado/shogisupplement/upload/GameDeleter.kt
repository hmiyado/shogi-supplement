package dev.miyado.shogisupplement.upload

import dev.miyado.shogisupplement.db.GameRecord
import dev.miyado.shogisupplement.db.GameRepository

/** 棋譜の削除。 */
class GameDeleter(
    private val gameRepository: GameRepository,
    private val uploadOrchestrator: UploadOrchestrator?,
) {

    /**
     * サーバー削除に失敗したらローカルも残す。端末から消えたのにサーバーには残る状態を作らないため。
     *
     * @param deleteServer アップロード済みの分もサーバーから消すかどうか。
     */
    suspend fun delete(game: GameRecord, deleteServer: Boolean): DeleteGameOutcome {
        if (deleteServer && game.uploadedAt != null) {
            val deleted = uploadOrchestrator?.deleteUploadedGame(game.contentHash) ?: false
            if (!deleted) return DeleteGameOutcome.ServerFailed
        }
        gameRepository.deleteGame(game.id)
        return DeleteGameOutcome.Success
    }
}
