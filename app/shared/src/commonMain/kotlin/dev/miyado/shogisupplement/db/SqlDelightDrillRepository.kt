package dev.miyado.shogisupplement.db

import dev.miyado.shogisupplement.util.currentEpochSeconds

/** ドリル出題・解答履歴のDB永続化リポジトリ（[DrillRepository]のSQLDelight実装）。 */
class SqlDelightDrillRepository(private val database: ShogiSupplementDatabase) : DrillRepository {

    override fun getDrillCandidates(): List<BlunderRecord> {
        return database.shogiSupplementQueries
            .getDrillCandidates()
            .executeAsList()
            .map { it.toBlunderRecord() }
    }

    override fun saveDrillAttempt(
        blunderReportId: Long,
        userMoveUsi: String,
        isCorrect: Boolean,
        lossWp: Double?,
        attemptedAt: Long,
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

    override fun getDrillAttemptCounts(): Map<Long, Int> {
        return database.shogiSupplementQueries
            .getDrillAttemptCountAll()
            .executeAsList()
            .associate { it.blunder_report_id to it.attempt_count.toInt() }
    }

    override fun getDrillAttempts(blunderReportId: Long): List<DrillAttemptRecord> {
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
