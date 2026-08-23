package dev.miyado.shogisupplement.upload

import dev.miyado.shogisupplement.db.BlunderRecord
import dev.miyado.shogisupplement.db.GameRecord

/**
 * テスト用 UploadRepository 実装。
 * uploadGame の返却値をあらかじめ指定できる。
 */
class FakeUploadRepository(
    private var result: UploadResult = UploadResult.Success,
    private var deleteResult: Boolean = true,
    private var drillProblemsResult: UploadResult = UploadResult.Success,
    private var drillAttemptResult: UploadResult = UploadResult.Success,
) : UploadRepository {

    /** 呼び出し履歴（テスト検証用）。 */
    val calls = mutableListOf<Triple<String, GameRecord, List<BlunderRecord>>>()
    val deleteCalls = mutableListOf<Pair<String, String>>()
    val drillProblemCalls = mutableListOf<Triple<String, String, List<BlunderRecord>>>()
    val drillAttemptCalls = mutableListOf<DrillAttemptCall>()

    override suspend fun uploadGame(
        userId: String,
        game: GameRecord,
        reports: List<BlunderRecord>,
    ): UploadResult {
        calls.add(Triple(userId, game, reports))
        return result
    }

    override suspend fun deleteGame(userId: String, contentHash: String): Boolean {
        deleteCalls.add(userId to contentHash)
        return deleteResult
    }

    override suspend fun syncDrillProblems(
        userId: String,
        contentHash: String,
        problems: List<BlunderRecord>,
    ): UploadResult {
        drillProblemCalls += Triple(userId, contentHash, problems)
        return drillProblemsResult
    }

    override suspend fun uploadDrillAttempt(
        userId: String,
        contentHash: String,
        problem: BlunderRecord,
        attempt: DrillAttemptUpload,
    ): UploadResult {
        drillAttemptCalls += DrillAttemptCall(userId, contentHash, problem, attempt)
        return drillAttemptResult
    }

    /** 次の呼び出しに返す結果を変更する。 */
    fun setResult(r: UploadResult) {
        result = r
    }

    /** 次の削除呼び出しに返す結果を変更する。 */
    fun setDeleteResult(value: Boolean) {
        deleteResult = value
    }

    fun setDrillProblemsResult(r: UploadResult) {
        drillProblemsResult = r
    }

    fun setDrillAttemptResult(r: UploadResult) {
        drillAttemptResult = r
    }
}

data class DrillAttemptCall(
    val userId: String,
    val contentHash: String,
    val problem: BlunderRecord,
    val attempt: DrillAttemptUpload,
)
