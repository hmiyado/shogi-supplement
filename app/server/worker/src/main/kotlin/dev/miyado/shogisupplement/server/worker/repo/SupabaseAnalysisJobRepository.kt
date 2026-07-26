package dev.miyado.shogisupplement.server.worker.repo

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

// クォータ「当日」の日境界（日本のユーザー向けサービスのためJST）。
val QUOTA_RESET_ZONE: ZoneId = ZoneId.of("Asia/Tokyo")

// 冪等キー作成競合の解消はunique(user_id, moves_hash)制約 + `Prefer:
// resolution=ignore-duplicates`に委ねる。同時に同じ手順を投げた2リクエストのうち
// 1つだけがCreatedになり、もう一方はAlreadyExistsとして既存行を読みに行く。
class SupabaseAnalysisJobRepository(
    private val httpClient: HttpClient,
    private val supabaseUrl: String,
    private val serviceRoleKey: String,
    private val clock: Clock = Clock.systemUTC(),
) : AnalysisJobRepository {

    @Serializable
    private data class JobRow(
        val id: String,
        @SerialName("user_id") val userId: String,
        @SerialName("moves_hash") val movesHash: String,
        val status: String,
        @SerialName("result_json") val resultJson: JsonElement? = null,
        @SerialName("engine_meta") val engineMeta: JsonElement? = null,
        val error: String? = null,
    )

    private fun JobRow.toRecord() = AnalysisJobRecord(
        id = id,
        userId = userId,
        movesHash = movesHash,
        status = AnalysisJobStatus.fromDb(status),
        resultJson = resultJson,
        engineMeta = engineMeta,
        error = error,
    )

    override suspend fun find(userId: String, movesHash: String): AnalysisJobRecord? {
        val response = httpClient.get(restUrl(supabaseUrl, "analysis_jobs")) {
            parameter("user_id", "eq.$userId")
            parameter("moves_hash", "eq.$movesHash")
            parameter("select", "id,user_id,moves_hash,status,result_json,engine_meta,error")
            supabaseServiceRoleHeaders(serviceRoleKey)
        }
        check(response.status.isSuccess()) { "analysis_jobs find failed: ${response.status}" }
        val rows: List<JobRow> = response.body()
        return rows.firstOrNull()?.toRecord()
    }

    override suspend fun countToday(userId: String): Int {
        val startOfDayJst = Instant.now(clock).atZone(QUOTA_RESET_ZONE).toLocalDate()
            .atStartOfDay(QUOTA_RESET_ZONE).toInstant()
        // HEAD + Prefer:count=exact で行データを一切転送させず、PostgRESTに
        // Content-Range: <range>/<total> の形で総件数だけ計算・返却させる。クォータ判定は
        // 毎リクエスト走るため、行を返してアプリ側でカウントする方式だと保存件数に比例して
        // 転送量・デシリアライズコストが増える。
        val response = httpClient.head(restUrl(supabaseUrl, "analysis_jobs")) {
            parameter("user_id", "eq.$userId")
            parameter("created_at", "gte.$startOfDayJst")
            // status=errorは消費済みクォータとしてカウントしない（結果を返せなかったジョブのため）。
            parameter("status", "neq.error")
            header("Prefer", "count=exact")
            supabaseServiceRoleHeaders(serviceRoleKey)
        }
        check(response.status.isSuccess()) { "analysis_jobs countToday failed: ${response.status}" }
        val contentRange = response.headers[HttpHeaders.ContentRange]
            ?: error("analysis_jobs countToday: missing Content-Range (Prefer:count=exact not honored?)")
        return contentRange.substringAfterLast('/').toIntOrNull()
            ?: error("analysis_jobs countToday: unparseable Content-Range \"$contentRange\"")
    }

    @Serializable
    private data class CreateRunningPayload(
        @SerialName("user_id") val userId: String,
        @SerialName("moves_hash") val movesHash: String,
        @SerialName("moves_usi") val movesUsi: JsonElement,
        val status: String = "running",
    )

    override suspend fun createRunning(
        userId: String,
        movesHash: String,
        storagePayload: JsonElement,
    ): CreateRunningResult {
        val response = httpClient.post(restUrl(supabaseUrl, "analysis_jobs")) {
            parameter("on_conflict", "user_id,moves_hash")
            header("Prefer", "resolution=ignore-duplicates,return=representation")
            contentType(ContentType.Application.Json)
            supabaseServiceRoleHeaders(serviceRoleKey)
            setBody(listOf(CreateRunningPayload(userId = userId, movesHash = movesHash, movesUsi = storagePayload)))
        }
        check(response.status.isSuccess()) { "analysis_jobs createRunning failed: ${response.status}" }
        val rows: List<JobRow> = response.body()
        val created = rows.firstOrNull()
        if (created != null) {
            return CreateRunningResult.Created(created.toRecord())
        }
        // ignore-duplicates で無視された = 既存行がある。読みに行って呼び出し側へ合流させる
        val existing = find(userId, movesHash)
            ?: error("createRunning: conflict reported but no existing row found (user=$userId)")
        return CreateRunningResult.AlreadyExists(existing)
    }

    @Serializable
    private data class MarkDonePayload(
        val status: String = "done",
        @SerialName("result_json") val resultJson: JsonElement,
        @SerialName("engine_meta") val engineMeta: JsonElement,
        @SerialName("finished_at") val finishedAt: String,
    )

    override suspend fun markDone(id: String, resultJson: JsonElement, engineMeta: JsonElement) {
        val response = httpClient.patch(restUrl(supabaseUrl, "analysis_jobs")) {
            parameter("id", "eq.$id")
            contentType(ContentType.Application.Json)
            supabaseServiceRoleHeaders(serviceRoleKey)
            setBody(
                MarkDonePayload(
                    resultJson = resultJson,
                    engineMeta = engineMeta,
                    finishedAt = Instant.now(clock).toString(),
                ),
            )
        }
        check(response.status.isSuccess()) { "analysis_jobs markDone failed: ${response.status}" }
    }

    @Serializable
    private data class MarkErrorPayload(
        val status: String = "error",
        val error: String,
        @SerialName("finished_at") val finishedAt: String,
    )

    override suspend fun markError(id: String, error: String) {
        val response = httpClient.patch(restUrl(supabaseUrl, "analysis_jobs")) {
            parameter("id", "eq.$id")
            contentType(ContentType.Application.Json)
            supabaseServiceRoleHeaders(serviceRoleKey)
            setBody(MarkErrorPayload(error = error, finishedAt = Instant.now(clock).toString()))
        }
        check(response.status.isSuccess()) { "analysis_jobs markError failed: ${response.status}" }
    }

    @Serializable
    private data class ResetToRunningPayload(
        val status: String = "running",
        val error: JsonElement = JsonNull,
        @SerialName("finished_at") val finishedAt: JsonElement = JsonNull,
    )

    override suspend fun resetToRunning(id: String) {
        val response = httpClient.patch(restUrl(supabaseUrl, "analysis_jobs")) {
            parameter("id", "eq.$id")
            contentType(ContentType.Application.Json)
            supabaseServiceRoleHeaders(serviceRoleKey)
            setBody(ResetToRunningPayload())
        }
        check(response.status.isSuccess()) { "analysis_jobs resetToRunning failed: ${response.status}" }
    }
}
