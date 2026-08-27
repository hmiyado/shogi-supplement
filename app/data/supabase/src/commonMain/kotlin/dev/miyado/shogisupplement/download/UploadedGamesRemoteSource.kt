package dev.miyado.shogisupplement.download

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal const val UPLOADED_GAMES_TABLE = "uploaded_games"
private const val PAGE_SIZE = 200L

/** `uploaded_games`テーブルの取得。DB取込([SupabaseGameDownloadService])と一覧表示の両方から使う。 */
internal class UploadedGamesRemoteSource(private val supabase: SupabaseClient) {

    /** created_at昇順で全ページを取得する。件数は日次50行上限があるため通常は1ページで収まる。 */
    suspend fun fetchAllRows(): List<UploadedGameRow> {
        val rows = mutableListOf<UploadedGameRow>()
        var offset = 0L
        while (true) {
            // RLSが自分の行だけに絞るため、user_idでの絞り込みは書かない。
            val page = supabase.from(UPLOADED_GAMES_TABLE)
                .select {
                    order("created_at", Order.ASCENDING)
                    range(offset, offset + PAGE_SIZE - 1)
                }
                .decodeList<UploadedGameRow>()
            rows += page
            if (page.size < PAGE_SIZE) break
            offset += PAGE_SIZE
        }
        return rows
    }

    /** 1件だけ取得する（詳細画面表示用）。無ければnull。 */
    suspend fun fetchRowByContentHash(contentHash: String): UploadedGameRow? =
        supabase.from(UPLOADED_GAMES_TABLE)
            .select {
                filter { eq("content_hash", contentHash) }
            }
            .decodeSingleOrNull<UploadedGameRow>()
}

@Serializable
internal data class UploadedGameRow(
    val id: String,
    @SerialName("content_hash") val contentHash: String,
    @SerialName("moves_usi") val movesUsi: List<String>,
    @SerialName("move_times") val moveTimes: List<Int?>? = null,
    val headers: Map<String, String>? = null,
    val result: String? = null,
    @SerialName("source_place") val sourcePlace: String? = null,
    val side: String? = null,
    @SerialName("private_enc") val privateEnc: String? = null,
    @SerialName("rating_service") val ratingService: String? = null,
    @SerialName("rating_raw") val ratingRaw: Int? = null,
    @SerialName("rating_rule") val ratingRule: String? = null,
    @SerialName("move_count") val moveCount: Int? = null,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("estimated_rating") val estimatedRating: Int? = null,
    @SerialName("coef_version") val coefVersion: String? = null,
    @SerialName("analysis_json") val analysisJson: List<BlunderReportJson>? = null,
)

/**
 * `uploaded_games.analysis_json`の1件分。[dev.miyado.shogisupplement.db.BlunderRecord]の
 * サーバー保存用サブセット（対局者名同様、評価値・読み筋はエンジン解析結果でありサーバーに送らない）。
 */
@Serializable
internal data class BlunderReportJson(
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
