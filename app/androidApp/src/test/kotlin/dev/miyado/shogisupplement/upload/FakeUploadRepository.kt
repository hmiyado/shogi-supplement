package dev.miyado.shogisupplement.upload

import dev.miyado.shogisupplement.db.BlunderRecord
import dev.miyado.shogisupplement.db.GameRecord

/**
 * テスト用 UploadRepository 実装。
 * uploadGame の返却値をあらかじめ指定できる。
 */
class FakeUploadRepository(
    private var result: UploadResult = UploadResult.Success,
) : UploadRepository {

    /** 呼び出し履歴（テスト検証用）。 */
    val calls = mutableListOf<Triple<String, GameRecord, List<BlunderRecord>>>()

    override suspend fun uploadGame(
        userId: String,
        game: GameRecord,
        reports: List<BlunderRecord>,
    ): UploadResult {
        calls.add(Triple(userId, game, reports))
        return result
    }

    /** 次の呼び出しに返す結果を変更する。 */
    fun setResult(r: UploadResult) {
        result = r
    }
}
