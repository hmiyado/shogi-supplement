package dev.miyado.shogisupplement.server.worker.repo

import kotlinx.serialization.json.JsonElement

enum class AnalysisJobStatus {
    RUNNING, DONE, ERROR;

    companion object {
        fun fromDb(value: String): AnalysisJobStatus = when (value) {
            "running" -> RUNNING
            "done" -> DONE
            "error" -> ERROR
            else -> error("unknown analysis_jobs.status: $value")
        }
    }

    fun toDb(): String = name.lowercase()
}

// result_json/moves_usiは7日でNULL化されるが、engine_metaは来歴記録のためTTL対象外。
// 別列で管理する。
data class AnalysisJobRecord(
    val id: String,
    val userId: String,
    val movesHash: String,
    val status: AnalysisJobStatus,
    val resultJson: JsonElement?,
    val engineMeta: JsonElement?,
    val error: String?,
)

sealed class CreateRunningResult {
    data class Created(val record: AnalysisJobRecord) : CreateRunningResult()

    // unique(user_id, moves_hash) 制約に阻まれて既存行が採用された（同時リクエストの競合）。
    data class AlreadyExists(val record: AnalysisJobRecord) : CreateRunningResult()
}

// [createRunning] は unique(user_id, moves_hash) 制約への upsert-ignore で実装すること。
// 同じ手順を投げた2リクエストが両方 running 行を作る競合を、アプリ側の排他制御ではなく
// DB制約で防ぐ。
interface AnalysisJobRepository {
    suspend fun find(userId: String, movesHash: String): AnalysisJobRecord?

    // 当日（JST日境界）のクォータ消費件数。status=errorの行は除外すること
    // （結果を返せなかったジョブはクォータを消費させない）。1局まるごとの解析
    // （mode=game。moves_usi jsonb内のmodeフィールドで絞る）のみをカウントする。
    // 単発局面（mode=position。ドリルの二次判定用）は別枠クォータのため countTodayPosition
    // で別途カウントする。
    suspend fun countToday(userId: String): Int

    // 当日（JST日境界）の単発局面解析（mode=position）のクォータ消費件数。
    // status=errorの行は除外する。1局解析のクォータ（countToday）とは完全に独立した別枠。
    suspend fun countTodayPosition(userId: String): Int

    /**
     * status=running の行を新規作成する。既に同じ (userId, movesHash) の行があれば
     * 新規作成はせず、その既存行を [CreateRunningResult.AlreadyExists] として返す。
     */
    suspend fun createRunning(
        userId: String,
        movesHash: String,
        storagePayload: JsonElement,
    ): CreateRunningResult

    suspend fun markDone(id: String, resultJson: JsonElement, engineMeta: JsonElement)

    suspend fun markError(id: String, error: String)

    // エラー後の再試行は新規行を作らずこの行を running に戻して使う（unique制約に阻まれるため）。
    suspend fun resetToRunning(id: String)
}
