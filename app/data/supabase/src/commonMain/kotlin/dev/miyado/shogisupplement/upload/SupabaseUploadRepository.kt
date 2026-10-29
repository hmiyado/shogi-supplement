package dev.miyado.shogisupplement.upload

import dev.miyado.shogisupplement.crypto.PrivateEncCodec
import dev.miyado.shogisupplement.crypto.TransferSecretKeys
import dev.miyado.shogisupplement.crypto.TransferSecretManager
import dev.miyado.shogisupplement.crypto.TransferSecretStore
import dev.miyado.shogisupplement.db.BlunderRecord
import dev.miyado.shogisupplement.db.GameRecord
import dev.miyado.shogisupplement.download.BlunderReportJson
import dev.miyado.shogisupplement.kifu.StudyKifuBackup
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.query.Columns
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.encodeToJsonElement

/**
 * Why not KIF原文をそのまま送る: 対局者名等は運営者にも読ませない設計のため、
 * 平文列と秘匿フィールドへ分解し、後者だけを端末の鍵で暗号化して送る。
 * AADにcontent_hashを使い、暗号文が別の行へ付け替えられていないことを検証できる形にする。
 */
class SupabaseUploadRepository(
    private val supabase: SupabaseClient,
    private val transferSecretStore: TransferSecretStore,
) : UploadRepository {

    @Serializable
    private data class AnalysisStateResponse(val generation: String? = null, val deleted: Boolean)

    @Serializable
    private data class AnalysisResponse(
        val status: String,
        val generation: String? = null,
        @SerialName("private_written") val privateWritten: Boolean = false,
        @SerialName("initial_private_written") val initialPrivateWritten: Boolean = false,
    )

    override suspend fun getAnalysisRemoteState(contentHash: String): UploadRepository.AnalysisRemoteState? = try {
        val response = supabase.postgrest.rpc("get_analysis_sync_state", buildJsonObject {
            put("p_content_hash", contentHash)
        }).decodeAs<AnalysisStateResponse>()
        UploadRepository.AnalysisRemoteState(response.generation, response.deleted)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    override suspend fun uploadAnalysis(
        userId: String,
        snapshot: dev.miyado.shogisupplement.db.GameRepository.AnalysisUploadSnapshot,
        target: dev.miyado.shogisupplement.db.GameRepository.AnalysisSyncTarget,
    ): UploadRepository.AnalysisUploadOutcome {
        val generation = snapshot.generation
            ?: return UploadRepository.AnalysisUploadOutcome.Failure("解析世代がありません")
        var outcome: UploadRepository.AnalysisUploadOutcome? = null
        val result = uploadGameUsing(userId, snapshot.game, snapshot.reports) { payload ->
            val response = supabase.postgrest.rpc("replace_analysis_generation", buildJsonObject {
                put("p_content_hash", snapshot.game.contentHash)
                put("p_generation", generation)
                put("p_expected_generation", target.expectedGeneration)
                put("p_game", Json.encodeToJsonElement(payload))
                put("p_problems", Json.encodeToJsonElement(snapshot.problems.map {
                    it.toDrillProblemPayload(userId, snapshot.game.contentHash)
                }))
            }).decodeAs<AnalysisResponse>()
            outcome = when (response.status) {
                "applied", "already_applied" -> if (response.generation == generation) {
                    UploadRepository.AnalysisUploadOutcome.Applied(response.privateWritten || response.initialPrivateWritten)
                } else UploadRepository.AnalysisUploadOutcome.Failure("解析世代が一致しません")
                "conflict" -> UploadRepository.AnalysisUploadOutcome.Conflict(response.generation)
                "superseded" -> UploadRepository.AnalysisUploadOutcome.Superseded(response.generation)
                else -> UploadRepository.AnalysisUploadOutcome.Failure("解析の送信結果を確認できません")
            }
            UploadResult.Success
        }
        return outcome ?: UploadRepository.AnalysisUploadOutcome.Failure(
            (result as? UploadResult.Failure)?.message ?: "解析を送信できませんでした",
        )
    }

    @Serializable
    private data class StudyRemoteRow(@SerialName("private_enc") val privateEnc: String?)

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun uploadStudy(
        userId: String,
        snapshot: dev.miyado.shogisupplement.db.GameRepository.StudyUploadSnapshot,
    ): UploadResult {
        return try {
            val stored = transferSecretStore.load() ?: return UploadResult.Failure("復号鍵がありません")
            val secrets = dev.miyado.shogisupplement.crypto.TransferSecrets.fromStored(stored)
                ?: return UploadResult.Failure("復号鍵を読み込めません")
            val key = TransferSecretKeys.deriveEncKey(secrets.encSecret)
            val rows = supabase.from(dev.miyado.shogisupplement.download.UPLOADED_GAMES_TABLE).select(columns = Columns.list("private_enc")) {
                filter { eq("user_id", userId); eq("content_hash", snapshot.contentHash) }
            }.decodeList<StudyRemoteRow>()
            val oldCiphertext = rows.singleOrNull()?.privateEnc ?: return UploadResult.Failure("送信先の棋譜がありません")
            val aad = snapshot.contentHash.encodeToByteArray()
            val remote = PrivateEncCodec.decrypt(key, Base64.decode(oldCiphertext), aad)
            val local = StudyKifuBackup.decompose(snapshot.originalKif, snapshot.studyKif)
            // 他端末の対局者名・元メモ等も上書きしない。
            fun originalFields(fields: dev.miyado.shogisupplement.kifu.PrivateKifuFields) = fields.copy(
                studyKif = null, studyOriginalKif = null,
                // 旧形式が保持していない位置情報は比較せず、comments自体は必ず比較する。
                positionNotes = if (remote.positionNotes == null) null else fields.positionNotes?.takeIf { it.isNotEmpty() },
                // 旧形式には分岐本文もない。保存済みの分岐がある場合は一致を要求する。
                variationKif = if (remote.variationKif == null) null else fields.variationKif,
            )
            if (originalFields(remote) != originalFields(local.private)) {
                return UploadResult.Failure("サーバーの棋譜が変更されています")
            }
            if (remote.studyOriginalKif != null && remote.studyOriginalKif != snapshot.originalKif) {
                return UploadResult.Failure("サーバーの原文が変更されています")
            }
            val current = remote.studyKif ?: snapshot.originalKif
            if (current == snapshot.studyKif) return UploadResult.Success
            if (current != snapshot.expectedRemoteKif) return UploadResult.Failure("別の端末で検討が変更されています")
            val encrypted = Base64.encode(PrivateEncCodec.encrypt(key, local.private, aad))
            val parameters = mapOf(
                "p_content_hash" to snapshot.contentHash,
                "p_expected_private_enc" to oldCiphertext,
                "p_private_enc" to encrypted,
            ).mapValues { kotlinx.serialization.json.JsonPrimitive(it.value) }
            val updated = supabase.postgrest.rpc("update_study_private_enc", kotlinx.serialization.json.JsonObject(parameters)).decodeAs<Boolean>()
            if (updated) UploadResult.Success else UploadResult.Failure("送信中に検討が変更されました")
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            UploadResult.Failure("検討文書を送信できませんでした")
        }
    }

    override suspend fun uploadGame(
        userId: String,
        game: GameRecord,
        reports: List<BlunderRecord>,
    ): UploadResult = uploadGameUsing(userId, game, reports) { payload ->
        supabase.from("uploaded_games").insert(payload)
        UploadResult.Success
    }

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun uploadGameUsing(
        userId: String,
        game: GameRecord,
        reports: List<BlunderRecord>,
        send: suspend (UploadedGamePayload) -> UploadResult,
    ): UploadResult {
        val kifText = game.kifText
            ?: return UploadResult.Failure("KIF原文が無いため v2 形式でアップロードできません（旧解析）")
        return try {
            val decomposed = StudyKifuBackup.decompose(kifText, game.studyKif)

            val secrets = TransferSecretManager.getOrCreateSecrets(transferSecretStore)
            val kEnc = TransferSecretKeys.deriveEncKey(secrets.encSecret)
            val aad = game.contentHash.encodeToByteArray()
            val privateEncBytes = PrivateEncCodec.encrypt(kEnc, decomposed.private, aad)
            val ratingService = game.ratingService
            val ratingDeclaredAt = game.ratingDeclaredAt

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
                ratingService = ratingService,
                ratingRaw = game.ratingRaw?.toInt(),
                ratingRule = game.ratingRule.orEmpty(),
                ratingDeclaredAt = ratingDeclaredAt?.let { Instant.fromEpochSeconds(it).toString() },
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
                engineMeta = game.engineMetaJson?.let(Json::parseToJsonElement),
            )
            if (ratingDeclaredAt != null && ratingService != null) {
                supabase.from("rating_declarations").upsert(
                    RatingDeclarationPayload(
                        userId = userId,
                        ratingService = ratingService,
                        ratingRaw = game.ratingRaw?.toInt(),
                        ratingRule = game.ratingRule.orEmpty(),
                        declaredAt = Instant.fromEpochSeconds(ratingDeclaredAt).toString(),
                    ),
                ) {
                    onConflict = "user_id,declared_at,rating_service,rating_rule"
                    ignoreDuplicates = true
                }
            }
            send(payload)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            uploadFailureOrDuplicate(e)
        }
    }

    override suspend fun uploadGenerationAttempt(
        userId: String, contentHash: String, generation: String, problem: BlunderRecord, attempt: DrillAttemptUpload,
    ): UploadResult = try {
        val accepted = supabase.postgrest.rpc("record_generation_attempt", buildJsonObject {
            put("p_content_hash", contentHash)
            put("p_generation", generation)
            put("p_ply", problem.ply)
            put("p_attempt", buildJsonObject {
                put("user_id", userId)
                put("client_attempt_id", attempt.syncId)
                put("user_move_usi", attempt.userMoveUsi)
                put("is_correct", attempt.isCorrect)
                put("loss_wp", attempt.lossWp)
                put("attempted_at", Instant.fromEpochSeconds(attempt.attemptedAt).toString())
            })
        }).decodeAs<Boolean>()
        if (accepted) UploadResult.Success else UploadResult.Failure("回答の解析世代または内容が一致しません")
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        UploadResult.Failure("回答を送信できませんでした")
    }

    override suspend fun deleteAnalysisGeneration(
        userId: String, contentHash: String, target: dev.miyado.shogisupplement.db.GameRepository.AnalysisDeleteTarget,
    ): Boolean = try {
        supabase.postgrest.rpc("delete_analysis_generation", buildJsonObject {
            put("p_content_hash", contentHash)
            put("p_expected_generation", target.expectedGeneration)
            put("p_delete_generation", target.requestId)
            put("p_expected_user_id", userId)
        }).decodeAs<Boolean>()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }

    override suspend fun deleteGame(userId: String, contentHash: String): Boolean = try {
        supabase.from("uploaded_games").delete {
            filter {
                eq("user_id", userId)
                eq("content_hash", contentHash)
            }
        }
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    override suspend fun syncDrillProblems(
        userId: String,
        contentHash: String,
        problems: List<BlunderRecord>,
        replaceExisting: Boolean,
    ): UploadResult {
        return try {
            if (replaceExisting) {
                return UploadResult.Failure("解析を送信できませんでした")
            }
            if (problems.isEmpty()) return UploadResult.Success
            upsertDrillProblems(userId, contentHash, problems)
            UploadResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            uploadFailureOrDuplicate(e)
        }
    }

    override suspend fun uploadDrillAttempt(
        userId: String,
        contentHash: String,
        problem: BlunderRecord,
        attempt: DrillAttemptUpload,
    ): UploadResult {
        return try {
            // 問題側を先に冪等登録することで、初回送信でもproblem_idを確実に得られる。
            upsertDrillProblems(userId, contentHash, listOf(problem))

            val problemId = findDrillProblemId(userId, contentHash, problem.ply)
                ?: return UploadResult.Failure("ドリル問題が登録されていません")

            supabase.from("drill_attempts").upsert(
                DrillAttemptPayload(
                    userId = userId,
                    problemId = problemId,
                    clientAttemptId = attempt.syncId,
                    userMoveUsi = attempt.userMoveUsi,
                    isCorrect = attempt.isCorrect,
                    lossWp = attempt.lossWp,
                    attemptedAt = Instant.fromEpochSeconds(attempt.attemptedAt).toString(),
                ),
            ) {
                onConflict = "user_id,client_attempt_id"
                ignoreDuplicates = true
            }
            UploadResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // drill_attemptsの重複は、同じclient_attempt_idが既に保存された状態なので成功扱い。
            if (isDuplicate(e)) UploadResult.Success else failure(e)
        }
    }

    private suspend fun upsertDrillProblems(
        userId: String,
        contentHash: String,
        problems: List<BlunderRecord>,
    ) {
        supabase.from("drill_problems").upsert(problems.map { it.toDrillProblemPayload(userId, contentHash) }) {
            onConflict = "user_id,content_hash,ply"
            ignoreDuplicates = true
        }
    }

    private suspend fun findDrillProblemId(
        userId: String,
        contentHash: String,
        ply: Long,
    ): String? {
        return supabase.from("drill_problems")
            .select(columns = Columns.list("id")) {
                filter {
                    eq("user_id", userId)
                    eq("content_hash", contentHash)
                    eq("ply", ply)
                }
            }
            .decodeList<DrillProblemIdRow>()
            .firstOrNull()
            ?.id
    }

    private fun uploadFailureOrDuplicate(error: Exception): UploadResult =
        if (isDuplicate(error)) UploadResult.Duplicate else failure(error)

    private fun failure(error: Exception): UploadResult =
        UploadResult.Failure(error.message?.ifBlank { null } ?: "アップロードに失敗しました")

    /**
     * Why not メッセージの文字列マッチ: エラーメッセージにはURLや違反行の値がそのまま
     * 載ることがあり、"409"等の数字列が偶然含まれると外部キー違反等を重複と誤判定する
     * （誤判定はSuccess扱いになり、未保存の行が送信済みとして二度と再送されなくなる）。
     */
    private fun isDuplicate(error: Exception): Boolean =
        (error as? PostgrestRestException)?.code == "23505"

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
        @SerialName("rating_rule") val ratingRule: String,
        @SerialName("rating_declared_at") val ratingDeclaredAt: String?,
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
        @SerialName("engine_meta") val engineMeta: JsonElement? = null,
    )

    @Serializable
    private data class RatingDeclarationPayload(
        @SerialName("user_id") val userId: String,
        @SerialName("rating_service") val ratingService: String,
        @SerialName("rating_raw") val ratingRaw: Int?,
        @SerialName("rating_rule") val ratingRule: String?,
        @SerialName("declared_at") val declaredAt: String,
    )

    @Serializable
    private data class DrillProblemPayload(
        @SerialName("user_id") val userId: String,
        @SerialName("content_hash") val contentHash: String,
        val ply: Long,
        val side: String,
        @SerialName("sfen_before") val sfenBefore: String,
        @SerialName("move_usi") val moveUsi: String,
        @SerialName("best_usi") val bestUsi: String?,
        @SerialName("loss_wp") val lossWp: Double,
        val category: String,
        val verdict: String,
        val note: String,
        @SerialName("problem_type") val problemType: String,
        val priority: Double,
        @SerialName("second_usi") val secondUsi: String?,
        @SerialName("second_cp") val secondCp: Int?,
    )

    @Serializable
    private data class DrillProblemIdRow(val id: String)

    @Serializable
    private data class DrillAttemptPayload(
        @SerialName("user_id") val userId: String,
        @SerialName("problem_id") val problemId: String,
        @SerialName("client_attempt_id") val clientAttemptId: String,
        @SerialName("user_move_usi") val userMoveUsi: String,
        @SerialName("is_correct") val isCorrect: Boolean,
        @SerialName("loss_wp") val lossWp: Double?,
        @SerialName("attempted_at") val attemptedAt: String,
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

    private fun BlunderRecord.toDrillProblemPayload(
        userId: String,
        contentHash: String,
    ) = DrillProblemPayload(
        userId = userId,
        contentHash = contentHash,
        ply = ply,
        side = side,
        sfenBefore = sfenBefore,
        moveUsi = moveUsi,
        bestUsi = bestUsi,
        lossWp = lossWp,
        category = category,
        verdict = verdict,
        note = note,
        problemType = problemType,
        priority = priority,
        secondUsi = secondUsi,
        secondCp = secondCp?.toInt(),
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
