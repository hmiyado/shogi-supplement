package dev.miyado.shogisupplement.db

import dev.miyado.shogisupplement.util.currentEpochSeconds

/** ドリル出題・解答履歴の永続化リポジトリのインターフェース。 */
interface DrillRepository {

    /**
     * ドリル出題候補（verdict が ◎ または ○）を優先度順に返す。
     * ◎ → ○ の順、同格内は priority 降順。
     */
    fun getDrillCandidates(): List<BlunderRecord>

    /**
     * ドリル解答を保存する。
     *
     * @param blunderReportId 出題元の blunder_report.id
     * @param userMoveUsi     ユーザーが指した手（降参なら "[降参]"）
     * @param isCorrect       正解なら true
     * @param lossWp          最善手との勝率差（エンジン判定不能なら null）
     * @param attemptedAt     解答時刻（Unix epoch 秒）
     * @return 作成された drill_attempt.id
     */
    fun saveDrillAttempt(
        blunderReportId: Long,
        userMoveUsi: String,
        isCorrect: Boolean,
        lossWp: Double?,
        attemptedAt: Long = currentEpochSeconds(),
    ): Long

    /**
     * 全 blunder_report の解答回数マップを返す。
     * キーは blunder_report_id、値は drill_attempt 件数。
     * 解答履歴がない問題はマップに含まれない（呼び出し側で 0 とみなす）。
     */
    fun getDrillAttemptCounts(): Map<Long, Int>

    /** 指定 blunder_report の解答履歴を返す（新しい順）。 */
    fun getDrillAttempts(blunderReportId: Long): List<DrillAttemptRecord>
}
