package dev.miyado.shogisupplement.upload

import dev.miyado.shogisupplement.db.BlunderRecord
import dev.miyado.shogisupplement.db.GameRecord

/**
 * 棋譜アップロードリポジトリのインターフェース。
 * 実装: SupabaseUploadRepository（androidApp）、FakeUploadRepository（テスト）
 */
interface UploadRepository {
    /**
     * ゲームとレポートを uploaded_games テーブルにアップロードする。
     * @return UploadResult（成功 / 重複=成功扱い / 失敗）
     */
    suspend fun uploadGame(
        userId: String,
        game: GameRecord,
        reports: List<BlunderRecord>,
    ): UploadResult

    /**
     * uploaded_games から指定ゲームを削除する。
     * @return 成功したら true、失敗したら false
     */
    suspend fun deleteGame(userId: String, contentHash: String): Boolean
}

/** アップロード結果を表す sealed class。 */
sealed class UploadResult {
    /** アップロード成功。 */
    object Success : UploadResult()

    /**
     * 既にアップロード済み（unique(user_id, content_hash) 違反）。
     * 重複は成功扱いとして uploaded_at を記録する。
     */
    object Duplicate : UploadResult()

    /** アップロード失敗（ネットワークエラー等）。アプリ動作には影響させない。 */
    data class Failure(val message: String) : UploadResult()
}
