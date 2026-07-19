package dev.miyado.shogisupplement.upload

import dev.miyado.shogisupplement.db.BlunderRecord
import dev.miyado.shogisupplement.db.GameRecord
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Supabase postgrest-kt を使った UploadRepository 実装。
 * uploaded_games テーブルに insert する。
 * unique(user_id, content_hash) 違反（重複）は Duplicate として吸収する。
 *
 * @param supabase Auth + Postgrest プラグインを持つ共有 Supabase クライアント
 */
class SupabaseUploadRepository(
    private val supabase: SupabaseClient,
) : UploadRepository {

    override suspend fun uploadGame(
        userId: String,
        game: GameRecord,
        reports: List<BlunderRecord>,
    ): UploadResult {
        return try {
            val payload = UploadedGamePayload(
                userId = userId,
                contentHash = game.contentHash,
                movesUsi = game.movesUsi,
                kifText = game.kifText,
                rating = game.rating.toInt(),
                ratingSampleMoves = game.ratingSampleMoves?.toInt(),
                moveCount = game.moveCount,
                coefVersion = game.coefVersion,
                analysisJson = reports.map { it.toJson() },
            )
            supabase.from("uploaded_games").insert(payload)
            UploadResult.Success
        } catch (e: Exception) {
            val msg = e.message ?: ""
            // 23505 = PostgreSQL unique_violation, 409 = HTTP Conflict
            if (msg.contains("23505") || msg.contains("409") || msg.contains("unique") ||
                msg.contains("duplicate")
            ) {
                UploadResult.Duplicate
            } else {
                UploadResult.Failure(msg.ifBlank { "アップロードに失敗しました" })
            }
        }
    }

    // ─── payload ─────────────────────────────────────────────────────────────

    @Serializable
    private data class UploadedGamePayload(
        @SerialName("user_id") val userId: String,
        @SerialName("content_hash") val contentHash: String,
        @SerialName("moves_usi") val movesUsi: List<String>,
        @SerialName("kif_text") val kifText: String?,
        val rating: Int?,
        @SerialName("rating_sample_moves") val ratingSampleMoves: Int?,
        @SerialName("move_count") val moveCount: Long,
        @SerialName("coef_version") val coefVersion: String,
        @SerialName("analysis_json") val analysisJson: List<BlunderReportJson>,
    )

    @Serializable
    private data class BlunderReportJson(
        val ply: Long,
        val side: String,
        @SerialName("move_usi") val moveUsi: String,
        @SerialName("best_usi") val bestUsi: String?,
        @SerialName("loss_wp") val lossWp: Double,
        val category: String,
        val verdict: String,
        val note: String,
        @SerialName("problem_type") val problemType: String,
        val priority: Double,
    )

    private fun BlunderRecord.toJson() = BlunderReportJson(
        ply = ply,
        side = side,
        moveUsi = moveUsi,
        bestUsi = bestUsi,
        lossWp = lossWp,
        category = category,
        verdict = verdict,
        note = note,
        problemType = problemType,
        priority = priority,
    )
}
