package dev.miyado.shogisupplement.db

import dev.miyado.shogisupplement.util.currentEpochSeconds

/** ドリル出題・解答履歴の永続化リポジトリのインターフェース。 */
interface DrillRepository {

    /**
     * ドリル出題候補（verdict が ◎ または ○）を優先度順に返す。
     * ◎ → ○ の順、同格内は priority 降順。
     */
    fun getDrillCandidates(): List<BlunderRecord>

    /** ドリル解答を保存する。 @param blunderReportId 出題元レコード。 @param userMoveUsi ユーザーの手。 @param isCorrect 正解か。 @param lossWp 勝率差。 @param attemptedAt 解答時刻。 @return 作成されたID。 */
    fun saveDrillAttempt(
        blunderReportId: Long,
        userMoveUsi: String,
        isCorrect: Boolean,
        lossWp: Double?,
        attemptedAt: Long = currentEpochSeconds(),
    ): Long

    /** 全blunder_reportの解答回数を、IDから件数へのマップで返す。履歴がないIDは含めない。 */
    fun getDrillAttemptCounts(): Map<Long, Int>

    /** 指定 blunder_report の解答履歴を返す（新しい順）。 */
    fun getDrillAttempts(blunderReportId: Long): List<DrillAttemptRecord>
}
