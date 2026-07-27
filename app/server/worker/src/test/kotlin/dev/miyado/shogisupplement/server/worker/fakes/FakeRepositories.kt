package dev.miyado.shogisupplement.server.worker.fakes

import dev.miyado.shogisupplement.server.worker.repo.AnalysisJobRecord
import dev.miyado.shogisupplement.server.worker.repo.AnalysisJobRepository
import dev.miyado.shogisupplement.server.worker.repo.AnalysisJobStatus
import dev.miyado.shogisupplement.server.worker.repo.BanRepository
import dev.miyado.shogisupplement.server.worker.repo.CreateRunningResult
import dev.miyado.shogisupplement.server.worker.repo.DEFAULT_DAILY_QUOTA
import dev.miyado.shogisupplement.server.worker.repo.QuotaLimitRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.atomic.AtomicInteger

class FakeBanRepository(private val bannedUserIds: Set<String> = emptySet()) : BanRepository {
    override suspend fun isBanned(userId: String): Boolean = userId in bannedUserIds
}

class FakeQuotaLimitRepository(
    private val limits: Map<String, Int> = emptyMap(),
) : QuotaLimitRepository {
    override suspend fun dailyLimit(userId: String): Int = limits[userId] ?: DEFAULT_DAILY_QUOTA
}

// unique(user_id, moves_hash)制約はcreateRunning内でmutex+事前存在チェックにより模倣する
// （実運用のPostgreSQL upsert-ignoreを素朴なロックで再現する）。
class FakeAnalysisJobRepository : AnalysisJobRepository {
    private val mutex = Mutex()
    private val records = LinkedHashMap<Pair<String, String>, AnalysisJobRecord>()
    // AnalysisJobRecord自体はmodeを持たない（実DBのmoves_usi jsonb内にしか無い値のため）。
    // countToday/countTodayPositionを別枠クォータとしてテストで検証できるよう、フェイクだけの
    // 内部状態としてmodeをキーごとに追跡する（既定"game"。seed()経由の呼び出しは大半が
    // 1局解析シナリオのため、明示しない限りgameとして扱う）。
    private val modes = mutableMapOf<Pair<String, String>, String>()
    private var nextId = 0
    val findCallCount = AtomicInteger(0)

    /** テストから直接状態を仕込むためのヘルパー。 */
    suspend fun seed(record: AnalysisJobRecord, mode: String = "game") = mutex.withLock {
        val key = record.userId to record.movesHash
        records[key] = record
        modes[key] = mode
    }

    /** 実行中ジョブが指定回数 find() された後、DONE/ERRORへ遷移させる（ポーリング待ちのテスト用）。 */
    suspend fun completeAfter(userId: String, movesHash: String, calls: Int, result: AnalysisJobRecord) {
        pendingCompletions[userId to movesHash] = calls to result
    }

    private val pendingCompletions = mutableMapOf<Pair<String, String>, Pair<Int, AnalysisJobRecord>>()
    private val pollCounts = mutableMapOf<Pair<String, String>, Int>()

    override suspend fun find(userId: String, movesHash: String): AnalysisJobRecord? = mutex.withLock {
        findCallCount.incrementAndGet()
        val key = userId to movesHash
        val pending = pendingCompletions[key]
        if (pending != null) {
            val (afterCalls, result) = pending
            val count = (pollCounts[key] ?: 0) + 1
            pollCounts[key] = count
            if (count >= afterCalls) {
                records[key] = result
                pendingCompletions.remove(key)
            }
        }
        records[key]
    }

    override suspend fun countToday(userId: String): Int = countTodayByMode(userId, mode = "game")

    override suspend fun countTodayPosition(userId: String): Int = countTodayByMode(userId, mode = "position")

    private suspend fun countTodayByMode(userId: String, mode: String): Int = mutex.withLock {
        // status=errorは消費済みクォータに数えない（SupabaseAnalysisJobRepositoryと同じ規約。
        // 日境界は実装しない簡易フェイク: AnalysisServiceTestは全件を「当日」として扱う想定）。
        records.entries.count { (key, record) ->
            record.userId == userId &&
                record.status != AnalysisJobStatus.ERROR &&
                (modes[key] ?: "game") == mode
        }
    }

    override suspend fun createRunning(
        userId: String,
        movesHash: String,
        storagePayload: JsonElement,
    ): CreateRunningResult = mutex.withLock {
        val key = userId to movesHash
        val existing = records[key]
        if (existing != null) {
            return@withLock CreateRunningResult.AlreadyExists(existing)
        }
        val record = AnalysisJobRecord(
            id = "job-${nextId++}",
            userId = userId,
            movesHash = movesHash,
            status = AnalysisJobStatus.RUNNING,
            resultJson = null,
            engineMeta = null,
            error = null,
        )
        records[key] = record
        // 実DBのmoves_usi jsonb同様、mode はストレージペイロード内のフィールドから取り出す
        // （AnalysisService.toStoragePayload参照）。
        modes[key] = (storagePayload as? JsonObject)?.get("mode")?.jsonPrimitive?.content ?: "game"
        CreateRunningResult.Created(record)
    }

    override suspend fun markDone(id: String, resultJson: JsonElement, engineMeta: JsonElement) = mutex.withLock {
        val entry = records.entries.first { it.value.id == id }
        records[entry.key] = entry.value.copy(
            status = AnalysisJobStatus.DONE,
            resultJson = resultJson,
            engineMeta = engineMeta,
            error = null,
        )
        Unit
    }

    override suspend fun markError(id: String, error: String) = mutex.withLock {
        val entry = records.entries.first { it.value.id == id }
        records[entry.key] = entry.value.copy(status = AnalysisJobStatus.ERROR, error = error)
        Unit
    }

    override suspend fun resetToRunning(id: String) = mutex.withLock {
        val entry = records.entries.first { it.value.id == id }
        records[entry.key] = entry.value.copy(
            status = AnalysisJobStatus.RUNNING,
            error = null,
            resultJson = null,
            engineMeta = null,
        )
        Unit
    }
}
