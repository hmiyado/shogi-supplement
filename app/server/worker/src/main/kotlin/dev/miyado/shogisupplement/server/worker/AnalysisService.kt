package dev.miyado.shogisupplement.server.worker

import dev.miyado.shogisupplement.api.analysis.AnalysisRequest
import dev.miyado.shogisupplement.api.analysis.AnalysisResultJson
import dev.miyado.shogisupplement.api.analysis.EngineMetaJson
import dev.miyado.shogisupplement.api.analysis.ErrorJson
import dev.miyado.shogisupplement.api.analysis.PvInfoJson
import dev.miyado.shogisupplement.api.analysis.ProgressJson
import dev.miyado.shogisupplement.api.analysis.toJson
import dev.miyado.shogisupplement.crash.NoopCrashReporter
import dev.miyado.shogisupplement.engine.AnalysisRunner
import dev.miyado.shogisupplement.engine.Engine
import dev.miyado.shogisupplement.engine.PvInfo
import dev.miyado.shogisupplement.server.worker.auth.AuthResult
import dev.miyado.shogisupplement.server.worker.auth.AuthVerifier
import dev.miyado.shogisupplement.server.worker.auth.extractBearerToken
import dev.miyado.shogisupplement.server.worker.repo.AnalysisJobRecord
import dev.miyado.shogisupplement.server.worker.repo.AnalysisJobRepository
import dev.miyado.shogisupplement.server.worker.repo.AnalysisJobStatus
import dev.miyado.shogisupplement.server.worker.repo.BanRepository
import dev.miyado.shogisupplement.server.worker.repo.CreateRunningResult
import dev.miyado.shogisupplement.server.worker.repo.QUOTA_RESET_ZONE
import dev.miyado.shogisupplement.server.worker.repo.QuotaLimitRepository
import dev.miyado.shogisupplement.util.sha256Hex
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import java.time.Clock
import java.time.Instant

sealed class AnalysisRequestOutcome {
    data class Unauthorized(val reason: String) : AnalysisRequestOutcome()
    data object Banned : AnalysisRequestOutcome()
    data class QuotaExceeded(val resetAt: Instant) : AnalysisRequestOutcome()
    data class BadRequest(val reason: String) : AnalysisRequestOutcome()

    // [emit] は `write` コールバック（1行=1回呼ぶ。改行はemit内で付与済み）を受け取り、
    // 進捗行→最終行（または即時の最終行のみ）を書き出す。
    data class Stream(val emit: suspend (write: suspend (String) -> Unit) -> Unit) : AnalysisRequestOutcome()
}

class AnalysisWaitTimeoutException(message: String) : Exception(message)

// 認可の順序（不変条件・変更しないこと）: JWT検証 → user_bans照合（BAN即403）→
// クォータ判定（超過は429＋翌日リセット時刻）→ moves_hash冪等チェック（解析済みなら即返却／
// 実行中なら完了を待って返却）→ 解析。
class AnalysisService(
    private val authVerifier: AuthVerifier,
    private val banRepository: BanRepository,
    private val quotaLimitRepository: QuotaLimitRepository,
    private val analysisJobRepository: AnalysisJobRepository,
    private val engineFactory: () -> Engine,
    private val engineMetaProvider: () -> EngineMetaJson,
    private val clock: Clock = Clock.systemUTC(),
    private val pollIntervalMs: Long = 500,
    private val pollTimeoutMs: Long = 280_000,
    private val analysisWorkers: Int = 1,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun handle(authorizationHeader: String?, request: AnalysisRequest): AnalysisRequestOutcome {
        val token = extractBearerToken(authorizationHeader)
            ?: return AnalysisRequestOutcome.Unauthorized("missing bearer token")

        val userId = when (val auth = authVerifier.verify(token)) {
            is AuthResult.Invalid -> return AnalysisRequestOutcome.Unauthorized(auth.reason)
            is AuthResult.Valid -> auth.userId
        }

        if (banRepository.isBanned(userId)) {
            return AnalysisRequestOutcome.Banned
        }

        val input = request.toEngineInput()
            ?: return AnalysisRequestOutcome.BadRequest("moves_usi または sfen のいずれかが必要です")
        val movesHash = sha256Hex(input.hashSeed)

        // Why not クォータ判定を先に: 既存ジョブの再取得は新規消費ではないため、
        // 冪等チェックはクォータ判定より前に行う（不変条件）。
        val existingBeforeQuota = analysisJobRepository.find(userId, movesHash)
        if (existingBeforeQuota != null) {
            return resolveExisting(userId, movesHash, existingBeforeQuota, input)
        }

        val limit = quotaLimitRepository.dailyLimit(userId)
        val used = analysisJobRepository.countToday(userId)
        if (used >= limit) {
            return AnalysisRequestOutcome.QuotaExceeded(resetAt = nextQuotaResetInstant())
        }

        return when (val created = analysisJobRepository.createRunning(userId, movesHash, input.toStoragePayload())) {
            is CreateRunningResult.Created ->
                AnalysisRequestOutcome.Stream(runEmitter(created.record.id, input))
            is CreateRunningResult.AlreadyExists ->
                // find()とcreateRunning()の間に別リクエストが行を作った競合。
                resolveExisting(userId, movesHash, created.record, input)
        }
    }

    /** find() または createRunning() の競合で見つかった既存行を、状態に応じて処理する。 */
    private suspend fun resolveExisting(
        userId: String,
        movesHash: String,
        existing: AnalysisJobRecord,
        input: EngineInput,
    ): AnalysisRequestOutcome = when (existing.status) {
        AnalysisJobStatus.DONE -> AnalysisRequestOutcome.Stream(cachedEmitter(existing))
        AnalysisJobStatus.RUNNING -> {
            val finished = waitForCompletion(userId, movesHash)
            when (finished.status) {
                AnalysisJobStatus.DONE -> AnalysisRequestOutcome.Stream(cachedEmitter(finished))
                else -> AnalysisRequestOutcome.Stream(errorEmitter(finished.error ?: "analysis failed"))
            }
        }
        AnalysisJobStatus.ERROR -> {
            // エラー後の再試行は同じ行を running に戻して使う（新規行を作ると unique制約に阻まれるため）。
            analysisJobRepository.resetToRunning(existing.id)
            AnalysisRequestOutcome.Stream(runEmitter(existing.id, input))
        }
    }

    /** 実行中(running)の同一ジョブが完了するまでポーリングで待つ。 */
    private suspend fun waitForCompletion(userId: String, movesHash: String): AnalysisJobRecord {
        val deadline = clock.instant().plusMillis(pollTimeoutMs)
        while (true) {
            val record = analysisJobRepository.find(userId, movesHash)
            if (record != null && record.status != AnalysisJobStatus.RUNNING) return record
            if (clock.instant().isAfter(deadline)) {
                throw AnalysisWaitTimeoutException("timed out waiting for running job (user=$userId)")
            }
            delay(pollIntervalMs)
        }
    }

    /** 新規解析を実行し、進捗→最終行をNDJSONで書き出すエミッタ。 */
    private fun runEmitter(jobId: String, input: EngineInput): suspend (suspend (String) -> Unit) -> Unit =
        { write ->
            try {
                val resultDto = when (input) {
                    is EngineInput.Game -> analyzeGame(input, write)
                    is EngineInput.Position -> analyzePosition(input, write)
                }
                analysisJobRepository.markDone(
                    jobId,
                    resultJson = json.encodeToJsonElement(resultDto.result),
                    engineMeta = json.encodeToJsonElement(resultDto.engineMeta),
                )
                write(json.encodeToString(resultDto) + "\n")
            } catch (e: Exception) {
                val message = e.message ?: e::class.simpleName ?: "engine error"
                analysisJobRepository.markError(jobId, message)
                write(json.encodeToString(ErrorJson(message)) + "\n")
            }
        }

    private suspend fun analyzeGame(
        input: EngineInput.Game,
        write: suspend (String) -> Unit,
    ): AnalysisResultJson = coroutineScope {
        // エンジンプロセスの生成と終了はAnalysisRunnerに任せる（プールに最大analysisWorkers本まで
        // 作り、局の解析が終わったら全部quitする）。Threads=1のまま本数で並列度を上げる形なので、
        // 解析条件は1本のときと同一で結果も変わらない。
        val runner = AnalysisRunner(
            workers = analysisWorkers,
            crashReporter = NoopCrashReporter,
            engineFactory = engineFactory,
        )
        // AnalysisRunner.onProgressは非suspendコールバックのため、suspendなwrite（NDJSON書き込み）
        // を直接呼べない。Channelで一方向にブリッジし、書き込みは専用コルーチンに直列化する。
        val progressChannel = Channel<ProgressJson>(Channel.UNLIMITED)
        val writerJob = launch {
            for (p in progressChannel) {
                write(json.encodeToString(p) + "\n")
            }
        }
        val allPv: List<List<PvInfo>> = try {
            runner.analyzeGame(input.movesUsi) { done, total ->
                progressChannel.trySend(ProgressJson(done, total))
            }
        } finally {
            progressChannel.close()
        }
        writerJob.join()
        AnalysisResultJson(
            result = allPv.map { pvList -> pvList.map { it.toJson() } },
            engineMeta = engineMetaProvider(),
        )
    }

    private suspend fun analyzePosition(
        input: EngineInput.Position,
        write: suspend (String) -> Unit,
    ): AnalysisResultJson {
        write(json.encodeToString(ProgressJson(0, 1)) + "\n")
        val engine = engineFactory()
        val pvList = try {
            engine.analyzeSfen(input.sfen, input.moves)
        } finally {
            runCatching { engine.quit() }
        }
        write(json.encodeToString(ProgressJson(1, 1)) + "\n")
        return AnalysisResultJson(
            result = listOf(pvList.map { it.toJson() }),
            engineMeta = engineMetaProvider(),
        )
    }

    /** 冪等ヒット（status=done）を再解析せず即返却するエミッタ。result_json/engine_metaは別列。 */
    private fun cachedEmitter(record: AnalysisJobRecord): suspend (suspend (String) -> Unit) -> Unit = { write ->
        val resultElement = record.resultJson
            ?: error("done job (id=${record.id}) has no result_json")
        val engineMetaElement = record.engineMeta
            ?: error("done job (id=${record.id}) has no engine_meta")
        val dto = AnalysisResultJson(
            result = json.decodeFromJsonElement(
                ListSerializer(ListSerializer(PvInfoJson.serializer())),
                resultElement,
            ),
            engineMeta = json.decodeFromJsonElement(EngineMetaJson.serializer(), engineMetaElement),
        )
        write(json.encodeToString(dto) + "\n")
    }

    private fun errorEmitter(message: String): suspend (suspend (String) -> Unit) -> Unit = { write ->
        write(json.encodeToString(ErrorJson(message)) + "\n")
    }

    private fun nextQuotaResetInstant(): Instant =
        Instant.now(clock).atZone(QUOTA_RESET_ZONE).toLocalDate()
            .plusDays(1)
            .atStartOfDay(QUOTA_RESET_ZONE)
            .toInstant()
}

// analysis_jobs.moves_usi (jsonb) 保存用ペイロード。テーブル定義はmoves_usi jsonb列1本のみで
// sfen用の列を持たないため、同じ列にモード情報つきJSONを保存して両モードに対応する。
private fun EngineInput.toStoragePayload(): JsonElement = when (this) {
    is EngineInput.Game -> buildJsonObject {
        put("mode", "game")
        put("moves_usi", JsonArray(movesUsi.map { JsonPrimitive(it) }))
    }
    is EngineInput.Position -> buildJsonObject {
        put("mode", "position")
        put("sfen", sfen)
        put("moves", JsonArray(moves.map { JsonPrimitive(it) }))
    }
}
