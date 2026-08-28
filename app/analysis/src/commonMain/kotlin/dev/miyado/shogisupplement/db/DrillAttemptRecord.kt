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
    /**
     * ユーザーが入力した読み筋（USI手列をスペース区切り）。予測手自体は含まない。未入力ならnull。
     * Why not Supabase同期に含める: 受け側のスキーマが未対応のため、当面は端末内表示専用の情報として扱う。
     */
    val readPv: String? = null,
)
