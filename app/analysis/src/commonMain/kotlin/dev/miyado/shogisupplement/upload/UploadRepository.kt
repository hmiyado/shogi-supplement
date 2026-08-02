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
     *
     * @param userId 認証済みユーザーID
     * @param game ゲームレコード（contentHash / rating / moveCount / coefVersion / kifText / movesUsi）
     * @param reports 悪手レポートリスト（analysis_json として送付）
     * @return UploadResult（成功 / 重複=成功扱い / 失敗）
     */
    suspend fun uploadGame(
        userId: String,
        game: GameRecord,
        reports: List<BlunderRecord>,
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
