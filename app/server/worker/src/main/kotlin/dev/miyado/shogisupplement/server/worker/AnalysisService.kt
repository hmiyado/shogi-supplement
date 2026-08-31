package dev.miyado.shogisupplement.server.worker

import dev.miyado.shogisupplement.api.analysis.AnalysisRequest
import dev.miyado.shogisupplement.api.analysis.AnalysisResultJson
import dev.miyado.shogisupplement.api.analysis.EngineMetaJson
import dev.miyado.shogisupplement.api.analysis.PositionPayloadJson
import dev.miyado.shogisupplement.api.analysis.PositionResultJson
import dev.miyado.shogisupplement.api.analysis.PvInfoJson
import dev.miyado.shogisupplement.api.analysis.ProgressJson
import dev.miyado.shogisupplement.api.analysis.toJson
import dev.miyado.shogisupplement.crash.NoopCrashReporter
import dev.miyado.shogisupplement.engine.AnalysisRunner
import dev.miyado.shogisupplement.engine.Engine
import dev.miyado.shogisupplement.engine.PvInfo
import dev.miyado.shogisupplement.server.worker.auth.AppCheckResult
import dev.miyado.shogisupplement.server.worker.auth.AppCheckVerifier
import dev.miyado.shogisupplement.server.worker.auth.AuthResult
import dev.miyado.shogisupplement.server.worker.auth.AuthVerifier
import dev.miyado.shogisupplement.server.worker.auth.extractBearerToken
import dev.miyado.shogisupplement.server.worker.repo.AnalysisJobRecord
import dev.miyado.shogisupplement.server.worker.repo.AnalysisJobRepository
import dev.miyado.shogisupplement.server.worker.repo.AppUsageRepository
import dev.miyado.shogisupplement.server.worker.repo.AnalysisJobStatus
import dev.miyado.shogisupplement.server.worker.repo.AppPolicyGate
import dev.miyado.shogisupplement.server.worker.repo.BanRepository
import dev.miyado.shogisupplement.server.worker.repo.CreateRunningResult
import dev.miyado.shogisupplement.server.worker.repo.QUOTA_RESET_ZONE
import dev.miyado.shogisupplement.server.worker.repo.QuotaLimitRepository
import dev.miyado.shogisupplement.util.sha256Hex
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.Instant

private val log = LoggerFactory.getLogger(AnalysisService::class.java)

sealed class AnalysisRequestOutcome {
    data class Unauthorized(val reason: String) : AnalysisRequestOutcome()
    data object Banned : AnalysisRequestOutcome()
    data class QuotaExceeded(val resetAt: Instant) : AnalysisRequestOutcome()
    data class BadRequest(val reason: String) : AnalysisRequestOutcome()

    /** アプリ版情報のbuildがapp_policy.min_build未満（426を返す）。 */
    data object UpgradeRequired : AnalysisRequestOutcome()

    // [emit] は `write` コールバック（1行=1回呼ぶ。改行はemit内で付与済み）を受け取り、
    // 進捗行→最終行（または即時の最終行のみ）を書き出す。
    data class Stream(val emit: suspend (write: suspend (String) -> Unit) -> Unit) : AnalysisRequestOutcome()
}

class AnalysisWaitTimeoutException(message: String) : Exception(message)

// 認可の順序（不変条件・変更しないこと）: 強制アップデート検証（プラットフォームとbuildが
// 両方揃った場合のみ。片方でも欠如・build不正・ポリシー取得失敗はfail-openでスキップ。
// 1.0クライアント互換のため認証より前段に置く）→
// App Check検証（有効時のみ。ヘッダ欠如/検証失敗は401）→ JWT検証 → 入力検証（400。DBへ
// 触れる前に上限と形式で弾く）→ user_bans照合（BAN即403）→
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
    // 単発局面解析（mode=position。ドリルの二次判定用）の日次上限。1局解析のクォータ
    // （quotaLimitRepository.dailyLimit、既定30）とは完全に別枠（countTodayPositionで
    // 別カウントする）。環境変数 ANALYSIS_POSITION_DAILY_LIMIT 由来（WorkerConfig参照）。
    private val positionDailyLimit: Int = 100,
    // nullは段階導入の無効状態（FIREBASE_PROJECT_NUMBER未設定）を表す。この場合ヘッダの
    // 有無に関わらず検証自体をスキップする（古いアプリバージョンを締め出さないため）。
    private val appCheckVerifier: AppCheckVerifier? = null,
    // 既定はAlwaysAllow（常に非ブロック）。appCheckVerifierのnull既定と同じ段階導入の考え方で、
    // 実装を明示的に注入しない限り強制アップデート検証は無効のまま。
    private val appPolicyGate: AppPolicyGate = AppPolicyGate.AlwaysAllow,
    // 既定はnull（記録しない）。appCheckVerifierと同じ段階導入で、注入しない限り何もしない。
    private val appUsageRepository: AppUsageRepository? = null,
    // RUNNING行をstale（自己修復対象）とみなす経過時間（環境変数 STALE_RUNNING_TIMEOUT_MS
    // 由来。WorkerConfig参照）。resolveExistingのRUNNING分岐でのみ使う。
    private val staleRunningTimeoutMs: Long = 600_000,
    // 解析（analyzeGame/analyzePosition＋markDone/markError）をリクエストのライフサイクルから
    // 切り離すためのスコープ。SupervisorJobなので子（1解析）の失敗は他の解析に伝播しない。
    // Why not リクエストのcoroutineScopeをそのまま使う: クライアント切断でリクエスト側の
    // スコープがキャンセルされると、そこにぶら下がる解析コルーチンもキャンセルされ、
    // markDone/markErrorまで到達できないままrunningの行が残ってしまうため。
    private val analysisScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun handle(
        authorizationHeader: String?,
        request: AnalysisRequest,
        appCheckHeader: String? = null,
        platformHeader: String? = null,
        buildHeader: String? = null,
    ): AnalysisRequestOutcome {
        // 1.0クライアント互換: どちらかのヘッダが無ければ検証自体をスキップする（旧クライアントは
        // ヘッダを送らないため、ここで弾くと全員締め出してしまう）。buildHeaderが数値化できない
        // 場合もfail-open側へ倒す（不正な値で誤ブロックするよりは検証をスキップするほうが安全）。
        if (platformHeader != null && buildHeader != null) {
            val build = buildHeader.toIntOrNull()
            if (build != null && appPolicyGate.isBlocked(platformHeader, build)) {
                return AnalysisRequestOutcome.UpgradeRequired
            }
        }

        if (appCheckVerifier != null) {
            if (appCheckHeader == null) {
                return AnalysisRequestOutcome.Unauthorized("missing app check token")
            }
            when (val appCheck = appCheckVerifier.verify(appCheckHeader)) {
                is AppCheckResult.Invalid -> return AnalysisRequestOutcome.Unauthorized(appCheck.reason)
                AppCheckResult.Valid -> Unit
            }
        }

        val token = extractBearerToken(authorizationHeader)
            ?: return AnalysisRequestOutcome.Unauthorized("missing bearer token")

        val userId = when (val auth = authVerifier.verify(token)) {
            is AuthResult.Invalid -> return AnalysisRequestOutcome.Unauthorized(auth.reason)
            is AuthResult.Valid -> auth.userId
        }

        val input = when (val parsed = request.toEngineInput()) {
            is EngineInputResult.Invalid -> return AnalysisRequestOutcome.BadRequest(parsed.reason)
            is EngineInputResult.Valid -> parsed.input
        }

        if (banRepository.isBanned(userId)) {
            return AnalysisRequestOutcome.Banned
        }

        recordAppUsage(userId, platformHeader, buildHeader)

        val movesHash = sha256Hex(input.hashSeed)

        // Why not クォータ判定を先に: 既存ジョブの再取得は新規消費ではないため、
        // 冪等チェックはクォータ判定より前に行う（不変条件）。
        val existingBeforeQuota = analysisJobRepository.find(userId, movesHash)
        if (existingBeforeQuota != null) {
            return resolveExisting(userId, movesHash, existingBeforeQuota, input)
        }

        // モードごとに完全に独立したクォータで判定する（1局解析=DB管理のquota_limits、
        // 単発局面=環境変数の固定上限）。countToday/countTodayPositionもモードで絞って
        // 別々に数える（SupabaseAnalysisJobRepository参照）。
        val (used, limit) = when (input) {
            is EngineInput.Game ->
                analysisJobRepository.countToday(userId) to quotaLimitRepository.dailyLimit(userId)
            is EngineInput.Position ->
                analysisJobRepository.countTodayPosition(userId) to positionDailyLimit
        }
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
            val ageMs = Duration.between(existing.createdAt, clock.instant()).toMillis()
            if (ageMs > staleRunningTimeoutMs) {
                // Why not waitForCompletion: 切断で解析が中断した行はrunningのまま変化せず、
                // 待機を続けてもタイムアウトするため、ここで自己復旧する。
                analysisJobRepository.resetToRunning(existing.id)
                AnalysisRequestOutcome.Stream(runEmitter(existing.id, input))
            } else {
                val finished = waitForCompletion(userId, movesHash)
                when (finished.status) {
                    AnalysisJobStatus.DONE -> AnalysisRequestOutcome.Stream(cachedEmitter(finished))
                    else -> AnalysisRequestOutcome.Stream(errorEmitter(finished.id, finished.error))
                }
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

    /**
     * [analysisScope]の解析はwriteと独立してmarkDone/markErrorまで完走する。
     * Why not 同じスコープ: write失敗で解析も中断しrunning行が残るため、配信だけ打ち切る。
     */
    private suspend fun recordAppUsage(userId: String, platform: String?, build: String?) {
        val buildNumber = build?.toIntOrNull() ?: return
        if (platform == null) return
        appUsageRepository?.record(userId, platform, buildNumber)
    }

    private fun runEmitter(jobId: String, input: EngineInput): suspend (suspend (String) -> Unit) -> Unit =
        { write ->
            // Why not 容量上限＋古い行の破棄: 局面行が1つ欠けるとクライアントのin-order watermarkが
            // そこで止まり、以降の途中経過を最終行まで出せなくなる。総量は入力の手数上限で抑える。
            val progressChannel = Channel<String>(Channel.UNLIMITED)
            val analysisJob = analysisScope.async {
                try {
                    val resultDto = when (input) {
                        is EngineInput.Game ->
                            analyzeGame(input) { line -> progressChannel.trySend(line) }
                        is EngineInput.Position ->
                            analyzePosition(input) { line -> progressChannel.trySend(line) }
                    }
                    analysisJobRepository.markDone(
                        jobId,
                        resultJson = json.encodeToJsonElement(resultDto.result),
                        engineMeta = json.encodeToJsonElement(resultDto.engineMeta),
                    )
                    json.encodeToString(resultDto) + "\n"
                } catch (e: Exception) {
                    // 詳細はservice_roleしか読めないanalysis_jobs.errorとログに残し、応答は伏せる。
                    analysisJobRepository.markError(jobId, e.message ?: e::class.simpleName ?: "engine error")
                    json.encodeToString(maskedError(log, "analysis failed (job=$jobId)", e)) + "\n"
                } finally {
                    progressChannel.close()
                }
            }

            for (line in progressChannel) {
                runCatching { write(line) }
            }
            // write失敗時は握りつぶす（切断後なので届け先がない）。結果とエラーはanalysisJob内で
            // 既にmarkDone/markErrorへ反映済みのため、ここで失っても実害はない。
            runCatching { write(analysisJob.await()) }
        }

    private suspend fun analyzeGame(
        input: EngineInput.Game,
        emitLine: (String) -> Unit,
    ): AnalysisResultJson {
        // エンジンプロセスの生成と終了はAnalysisRunnerに任せる（プールに最大analysisWorkers本まで
        // 作り、局の解析が終わったら全部quitする）。Threads=1のまま本数で並列度を上げる形なので、
        // 解析条件は1本のときと同一で結果も変わらない。
        val runner = AnalysisRunner(
            workers = analysisWorkers,
            crashReporter = NoopCrashReporter,
            engineFactory = engineFactory,
        )
        val allPv: List<List<PvInfo>> = runner.analyzeGame(
            input.movesUsi,
            // プログレッシブ解析表示向けの局面単位イベント。並列ワーカーの完了順のまま
            // ply順に揃えず送る（クライアント側のin-order watermarkアキュムレータが
            // 順不同着を前提に設計済みのため、ここでの並べ替えは不要）。
            onPositionResult = { ply, pvs ->
                val positionJson = PositionResultJson(PositionPayloadJson(ply, pvs.map { it.toJson() }))
                emitLine(json.encodeToString(positionJson) + "\n")
            },
        ) { done, total ->
            emitLine(json.encodeToString(ProgressJson(done, total)) + "\n")
        }
        return AnalysisResultJson(
            result = allPv.map { pvList -> pvList.map { it.toJson() } },
            engineMeta = engineMetaProvider(),
        )
    }

    private suspend fun analyzePosition(
        input: EngineInput.Position,
        emitLine: (String) -> Unit,
    ): AnalysisResultJson {
        emitLine(json.encodeToString(ProgressJson(0, 1)) + "\n")
        val engine = engineFactory()
        val pvList = try {
            engine.analyzeSfen(input.sfen, input.moves)
        } finally {
            runCatching { engine.quit() }
        }
        emitLine(json.encodeToString(ProgressJson(1, 1)) + "\n")
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

    private fun errorEmitter(jobId: String, detail: String?): suspend (suspend (String) -> Unit) -> Unit = { write ->
        write(json.encodeToString(maskedError(log, "analysis job failed (job=$jobId): $detail")) + "\n")
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
