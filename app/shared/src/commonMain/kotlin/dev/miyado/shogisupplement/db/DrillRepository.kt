package dev.miyado.shogisupplement.db

import dev.miyado.shogisupplement.util.currentEpochSeconds

/**
 * ドリル出題・解答履歴のDB永続化リポジトリ。
 */
class DrillRepository(private val database: ShogiSupplementDatabase) {

    /**
     * ドリル出題候補（verdict が ◎ または ○）を優先度順に返す。
     * ◎ → ○ の順、同格内は priority 降順。
     */
    fun getDrillCandidates(): List<BlunderRecord> {
        return database.shogiSupplementQueries
            .getDrillCandidates()
            .executeAsList()
            .map { it.toBlunderRecord() }
    }

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
    ): Long {
        return database.transactionWithResult {
            database.shogiSupplementQueries.insertDrillAttempt(
                blunder_report_id = blunderReportId,
                user_move_usi = userMoveUsi,
                is_correct = if (isCorrect) 1L else 0L,
                loss_wp = lossWp,
                attempted_at = attemptedAt,
            )
            database.shogiSupplementQueries.getLastInsertRowId().executeAsOne()
        }
    }

    /**
     * 全 blunder_report の解答回数マップを返す。
     * キーは blunder_report_id、値は drill_attempt 件数。
     * 解答履歴がない問題はマップに含まれない（呼び出し側で 0 とみなす）。
     */
    fun getDrillAttemptCounts(): Map<Long, Int> {
        return database.shogiSupplementQueries
            .getDrillAttemptCountAll()
            .executeAsList()
            .associate { it.blunder_report_id to it.attempt_count.toInt() }
    }

    /**
     * 指定 blunder_report の解答履歴を返す（新しい順）。
     */
    fun getDrillAttempts(blunderReportId: Long): List<DrillAttemptRecord> {
        return database.shogiSupplementQueries
            .getDrillAttemptsByBlunder(blunderReportId)
            .executeAsList()
            .map {
                DrillAttemptRecord(
                    id = it.id,
                    blunderReportId = it.blunder_report_id,
                    userMoveUsi = it.user_move_usi,
                    isCorrect = it.is_correct != 0L,
                    lossWp = it.loss_wp,
                    attemptedAt = it.attempted_at,
                )
            }
    }
}

/** ドリル解答履歴のドメインモデル（UI用）。 */
data class DrillAttemptRecord(
    val id: Long,
    val blunderReportId: Long,
    val userMoveUsi: String,
    val isCorrect: Boolean,
    val lossWp: Double?,
    val attemptedAt: Long,
)
