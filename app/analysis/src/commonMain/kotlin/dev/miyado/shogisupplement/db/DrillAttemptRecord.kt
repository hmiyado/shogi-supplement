package dev.miyado.shogisupplement.db

/** ドリル解答履歴のドメインモデル（UI用）。 */
data class DrillAttemptRecord(
    val id: Long,
    val blunderReportId: Long,
    val userMoveUsi: String,
    val isCorrect: Boolean,
    val lossWp: Double?,
    val attemptedAt: Long,
)
