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

    override fun getDrillCandidatesByGame(gameId: Long): List<BlunderRecord> {
        return database.shogiSupplementQueries
            .getDrillCandidatesByGame(gameId)
            .executeAsList()
            .map { it.toBlunderRecord() }
    }

    override fun getBlunderById(id: Long): BlunderRecord? {
        return database.shogiSupplementQueries
            .getBlunderById(id)
            .executeAsOneOrNull()
            ?.toBlunderRecord()
    }

    override fun saveDrillAttempt(
        blunderReportId: Long,
        userMoveUsi: String,
        isCorrect: Boolean,
        lossWp: Double?,
        attemptedAt: Long,
        readPv: String?,
    ): Long {
        return database.transactionWithResult {
            database.shogiSupplementQueries.insertDrillAttempt(
                blunder_report_id = blunderReportId,
                user_move_usi = userMoveUsi,
                is_correct = if (isCorrect) 1L else 0L,
                loss_wp = lossWp,
                attempted_at = attemptedAt,
                read_pv = readPv,
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
                    syncId = it.sync_id,
                    uploadedAt = it.uploaded_at,
                    readPv = it.read_pv,
                )
            }
    }

    override fun getDrillAttemptsNotUploaded(limit: Int): List<DrillAttemptRecord> {
        return database.shogiSupplementQueries
            .getDrillAttemptsNotUploaded(limit.toLong())
            .executeAsList()
            .map { it.toDrillAttemptRecord() }
    }

    override fun updateDrillAttemptSyncId(id: Long, syncId: String) {
        database.shogiSupplementQueries.updateDrillAttemptSyncId(syncId, id)
    }

    override fun updateDrillAttemptUploadedAt(id: Long, epochSeconds: Long) {
        database.shogiSupplementQueries.updateDrillAttemptUploadedAt(epochSeconds, id)
    }

    override fun getDrillAttemptActiveDayCount(windowDays: Int, asOfEpochSeconds: Long): Int {
        return database.shogiSupplementQueries
            .getDrillAttemptActiveDaysSince(asOfEpochSeconds.toString(), (windowDays - 1).toLong())
            .executeAsList()
            .size
    }

    override fun getDrillAttemptCountTotal(): Int {
        return database.shogiSupplementQueries
            .getDrillAttemptCountTotal()
            .executeAsOne()
            .toInt()
    }

    override fun getDrillAttemptWeekStreakCount(): Int {
        val dayNumbers = database.shogiSupplementQueries
            .getDrillAttemptDayNumbers()
            .executeAsList()
        var weekStreakCount = 0
        var runLength = 0
        for (i in dayNumbers.indices) {
            runLength = if (i > 0 && dayNumbers[i] == dayNumbers[i - 1] + 1L) runLength + 1 else 1
            if (runLength % 7 == 0) weekStreakCount++
        }
        return weekStreakCount
    }

    private fun dev.miyado.shogisupplement.db.Drill_attempt.toDrillAttemptRecord() =
        DrillAttemptRecord(
            id = id,
            blunderReportId = blunder_report_id,
            userMoveUsi = user_move_usi,
            isCorrect = is_correct != 0L,
            lossWp = loss_wp,
            attemptedAt = attempted_at,
            syncId = sync_id,
            uploadedAt = uploaded_at,
            readPv = read_pv,
        )
}
