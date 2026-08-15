package dev.miyado.shogisupplement.upload

import dev.miyado.shogisupplement.crypto.PrivateEncCodec
import dev.miyado.shogisupplement.crypto.TransferSecretKeys
import dev.miyado.shogisupplement.crypto.TransferSecretManager
import dev.miyado.shogisupplement.crypto.TransferSecretStore
import dev.miyado.shogisupplement.db.BlunderRecord
import dev.miyado.shogisupplement.db.GameRecord
import dev.miyado.shogisupplement.kifu.KifParser
import dev.miyado.shogisupplement.kifu.KifuDecomposer
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Why not KIF原文をそのまま送る: 対局者名等は運営者にも読ませない設計のため、
 * 平文列と秘匿フィールドへ分解し、後者だけを端末の鍵で暗号化して送る。
 * AADにcontent_hashを使い、暗号文が別の行へ付け替えられていないことを検証できる形にする。
 */
class SupabaseUploadRepository(
    private val supabase: SupabaseClient,
    private val transferSecretStore: TransferSecretStore,
) : UploadRepository {

    private val parser = KifParser()

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun uploadGame(
        userId: String,
        game: GameRecord,
        reports: List<BlunderRecord>,
    ): UploadResult {
        val kifText = game.kifText
            ?: return UploadResult.Failure("KIF原文が無いため v2 形式でアップロードできません（旧解析）")
        return try {
            val parsed = parser.parse(kifText)
            val decomposed = KifuDecomposer.decompose(kifText, parsed)

            val secrets = TransferSecretManager.getOrCreateSecrets(transferSecretStore)
            val kEnc = TransferSecretKeys.deriveEncKey(secrets.encSecret)
            val aad = game.contentHash.encodeToByteArray()
            val privateEncBytes = PrivateEncCodec.encrypt(kEnc, decomposed.private, aad)

            val payload = UploadedGamePayload(
                userId = userId,
                contentHash = game.contentHash,
                movesUsi = decomposed.public.movesUsi,
                moveTimes = decomposed.public.moveTimesSeconds,
                headers = decomposed.public.headers,
                result = decomposed.public.result,
                sourcePlace = decomposed.public.source.wireValue,
                side = game.userSide,
                privateEnc = Base64.encode(privateEncBytes),
                ratingService = game.ratingService,
                ratingRaw = game.ratingRaw?.toInt(),
                ratingRule = game.ratingRule,
                userRank = UploadDerivedColumns.rankFor(decomposed.public.headers, game.userSide, own = true),
                opponentRank = UploadDerivedColumns.rankFor(decomposed.public.headers, game.userSide, own = false),
                startedAt = UploadDerivedColumns.parseStartedAtJst(decomposed.public.headers["開始日時"]),
                timeControl = decomposed.public.headers["持ち時間"],
                byoyomi = decomposed.public.headers["秒読み"],
                estimatedRating = game.rating.toInt(),
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
        @SerialName("move_times") val moveTimes: List<Int?>,
        val headers: Map<String, String>,
        val result: String?,
        @SerialName("source_place") val sourcePlace: String,
        val side: String?,
        @SerialName("private_enc") val privateEnc: String,
        @SerialName("rating_service") val ratingService: String?,
        @SerialName("rating_raw") val ratingRaw: Int?,
        @SerialName("rating_rule") val ratingRule: String?,
        @SerialName("user_rank") val userRank: String?,
        @SerialName("opponent_rank") val opponentRank: String?,
        @SerialName("started_at") val startedAt: String?,
        @SerialName("time_control") val timeControl: String?,
        val byoyomi: String?,
        @SerialName("estimated_rating") val estimatedRating: Int?,
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

/**
 * uploaded_gamesの検索用列をアップロード時に導出する。headersが正本で、
 * これらの列はDB検索のための複製。
 */
internal object UploadDerivedColumns {

    /** side基準で先手段級/後手段級をユーザー側/相手側に割り付ける。side未申告ならnull。 */
    fun rankFor(headers: Map<String, String>, userSide: String?, own: Boolean): String? = when (userSide) {
        "sente" -> headers[if (own) "先手段級" else "後手段級"]
        "gote" -> headers[if (own) "後手段級" else "先手段級"]
        else -> null
    }

    /**
     * 分丸め済みの開始日時（例: "2026/06/25 11:34"・曜日入りもあり得る）をISO-8601へ。
     * KIFにタイムゾーン情報は無いため、対象サービスが国内向けであることからJSTとして解釈する。
     * 解釈できない形式はnull（headersに原文が残るため情報は失われない）。
     */
    fun parseStartedAtJst(value: String?): String? {
        if (value == null) return null
        val m = Regex("""(\d{4})/(\d{1,2})/(\d{1,2}).*?(\d{1,2}):(\d{2})${'$'}""").find(value) ?: return null
        val (y, mo, d, h, mi) = m.destructured
        fun pad(v: String) = v.padStart(2, '0')
        return "$y-${pad(mo)}-${pad(d)}T${pad(h)}:$mi:00+09:00"
    }
}
