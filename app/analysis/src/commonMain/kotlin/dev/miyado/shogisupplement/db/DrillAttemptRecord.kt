package dev.miyado.shogisupplement.db

/** ドリル解答履歴のドメインモデル（UI用）。 */
data class DrillAttemptRecord(
    val id: Long,
    val blunderReportId: Long,
    val userMoveUsi: String,
    val isCorrect: Boolean,
    val lossWp: Double?,
    val attemptedAt: Long,
    /** Supabase同期時に割り当てるクライアント側の冪等キー。 */
    val syncId: String? = null,
    /** Supabaseへの送信成功時刻（Unix epoch秒）。 */
    val uploadedAt: Long? = null,
)
