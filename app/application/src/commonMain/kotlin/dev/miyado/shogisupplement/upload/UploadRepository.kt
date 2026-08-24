package dev.miyado.shogisupplement.upload

import dev.miyado.shogisupplement.db.BlunderRecord
import dev.miyado.shogisupplement.db.GameRecord

/** Supabaseへ送信する1回分のドリル解答。 */
data class DrillAttemptUpload(
    val syncId: String,
    val userMoveUsi: String,
    val isCorrect: Boolean,
    val lossWp: Double?,
    val attemptedAt: Long,
)

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

    /** 指定棋譜のドリル問題を、問題キーで冪等に登録する。 */
    suspend fun syncDrillProblems(
        userId: String,
        contentHash: String,
        problems: List<BlunderRecord>,
    ): UploadResult

    /** 指定問題に対するドリル解答を、クライアントIDで冪等に登録する。 */
    suspend fun uploadDrillAttempt(
        userId: String,
        contentHash: String,
        problem: BlunderRecord,
        attempt: DrillAttemptUpload,
    ): UploadResult
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
