package dev.miyado.shogisupplement.server.worker

import dev.miyado.shogisupplement.api.analysis.AnalysisRequest
import dev.miyado.shogisupplement.api.analysis.AnalysisResultJson
import dev.miyado.shogisupplement.api.analysis.EngineMetaJson
import dev.miyado.shogisupplement.api.analysis.ErrorJson
import dev.miyado.shogisupplement.api.analysis.PvInfoJson
import dev.miyado.shogisupplement.api.analysis.ProgressJson
import dev.miyado.shogisupplement.api.analysis.ScoreJson
import dev.miyado.shogisupplement.engine.Engine
import dev.miyado.shogisupplement.server.worker.fakes.FakeAnalysisJobRepository
import dev.miyado.shogisupplement.server.worker.fakes.FakeAppCheckVerifier
import dev.miyado.shogisupplement.server.worker.fakes.FakeAppPolicyGate
import dev.miyado.shogisupplement.server.worker.fakes.FakeAuthVerifier
import dev.miyado.shogisupplement.server.worker.fakes.FakeBanRepository
import dev.miyado.shogisupplement.server.worker.fakes.FakeEngine
import dev.miyado.shogisupplement.server.worker.fakes.FakeQuotaLimitRepository
import dev.miyado.shogisupplement.server.worker.repo.AnalysisJobRecord
import dev.miyado.shogisupplement.server.worker.repo.AnalysisJobStatus
import dev.miyado.shogisupplement.server.worker.repo.AppPolicyGate
import dev.miyado.shogisupplement.util.sha256Hex
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * AnalysisService の認可・クォータ・冪等・NDJSONストリーミングの分岐網羅テスト。
 *
 * Ktor（HTTP層）を一切使わない: AnalysisService は純粋なsuspend関数として実装されているため、
 * フェイクのAuthVerifier/リポジトリ/Engineファクトリを注入するだけで全分岐を検証できる。
 */
class AnalysisServiceTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val fixedInstant = Instant.parse("2026-07-26T10:00:00Z") // JST 19:00
    private val fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC)

    private fun engineMeta() = EngineMetaJson(
        engineRev = "test-rev",
        evalSha256 = "test-sha",
        nodes = 400_000,
        threads = 1,
        multiPv = 2,
        usiHash = 128,
        fvScale = 20,
    )

    private fun buildService(
        authVerifier: FakeAuthVerifier = FakeAuthVerifier(mapOf("valid-token" to "user-1")),
        banRepository: FakeBanRepository = FakeBanRepository(),
        quotaLimitRepository: FakeQuotaLimitRepository = FakeQuotaLimitRepository(),
        analysisJobRepository: FakeAnalysisJobRepository = FakeAnalysisJobRepository(),
        engine: FakeEngine = FakeEngine(),
        clock: Clock = fixedClock,
        pollIntervalMs: Long = 1,
        pollTimeoutMs: Long = 2_000,
        analysisWorkers: Int = 1,
        engineFactory: () -> Engine = { engine },
        positionDailyLimit: Int = 100,
        appCheckVerifier: FakeAppCheckVerifier? = null,
        staleRunningTimeoutMs: Long = 600_000,
        appPolicyGate: AppPolicyGate = AppPolicyGate.AlwaysAllow,
    ) = AnalysisService(
        authVerifier = authVerifier,
        banRepository = banRepository,
        quotaLimitRepository = quotaLimitRepository,
        analysisJobRepository = analysisJobRepository,
        engineFactory = engineFactory,
        engineMetaProvider = { engineMeta() },
        clock = clock,
        pollIntervalMs = pollIntervalMs,
        pollTimeoutMs = pollTimeoutMs,
        analysisWorkers = analysisWorkers,
        positionDailyLimit = positionDailyLimit,
        appCheckVerifier = appCheckVerifier,
        staleRunningTimeoutMs = staleRunningTimeoutMs,
        appPolicyGate = appPolicyGate,
    )

    private suspend fun AnalysisRequestOutcome.Stream.collectLines(): List<String> {
        val lines = mutableListOf<String>()
        emit { line -> lines.add(line.removeSuffix("\n")) }
        return lines
    }

    // ── 426: 強制アップデート（X-App-Platform/X-App-Build。認証より前段でゲートする） ──

    @Test
    fun `blocked platform and build returns UpgradeRequired before checking JWT`() = runTest {
        val authVerifier = FakeAuthVerifier(mapOf("valid-token" to "user-1"))
        val gate = FakeAppPolicyGate(blockedPlatforms = setOf("ios"))
        val service = buildService(authVerifier = authVerifier, appPolicyGate = gate)

        val outcome = service.handle(
            "Bearer valid-token",
            AnalysisRequest(movesUsi = listOf("7g7f")),
            platformHeader = "ios",
            buildHeader = "1",
        )

        assertIs<AnalysisRequestOutcome.UpgradeRequired>(outcome)
        assertEquals(null, authVerifier.lastVerifiedToken, "強制アップデート検証がJWT検証より前段でゲートするはず")
        assertEquals(listOf("ios" to 1), gate.calls)
    }

    @Test
    fun `non-blocked build proceeds past the gate to analysis`() = runTest {
        val gate = FakeAppPolicyGate(blockedPlatforms = setOf("ios"))
        val service = buildService(appPolicyGate = gate)

        val outcome = service.handle(
            "Bearer valid-token",
            AnalysisRequest(movesUsi = listOf("7g7f")),
            platformHeader = "android",
            buildHeader = "1",
        )

        assertIs<AnalysisRequestOutcome.Stream>(outcome)
    }

    @Test
    fun `missing platform or build header skips the gate entirely (1_0 client compatibility)`() = runTest {
        val gate = FakeAppPolicyGate(blockedPlatforms = setOf("ios"))
        val service = buildService(appPolicyGate = gate)

        // moves_usiを変えて2件を別ジョブにする（同一だとmoves_hashが衝突し、2件目が
        // 1件目のRUNNING行の完了待ちに回ってpollTimeoutMsまでポーリングし続けてしまうため）。
        val platformOnly = service.handle(
            "Bearer valid-token",
            AnalysisRequest(movesUsi = listOf("7g7f")),
            platformHeader = "ios",
            buildHeader = null,
        )
        val buildOnly = service.handle(
            "Bearer valid-token",
            AnalysisRequest(movesUsi = listOf("2g2f")),
            platformHeader = null,
            buildHeader = "1",
        )

        assertIs<AnalysisRequestOutcome.Stream>(platformOnly)
        assertIs<AnalysisRequestOutcome.Stream>(buildOnly)
        assertEquals(emptyList(), gate.calls, "ヘッダが片方でも欠けていればゲートを呼ばないはず")
    }

    @Test
    fun `unparseable build header skips the gate (fail-open)`() = runTest {
        val gate = FakeAppPolicyGate(blockedPlatforms = setOf("ios"))
        val service = buildService(appPolicyGate = gate)

        val outcome = service.handle(
            "Bearer valid-token",
            AnalysisRequest(movesUsi = listOf("7g7f")),
            platformHeader = "ios",
            buildHeader = "not-a-number",
        )

        assertIs<AnalysisRequestOutcome.Stream>(outcome)
        assertEquals(emptyList(), gate.calls)
    }

    @Test
    fun `policy fetch failure surfaces as non-blocked via AppPolicyGate fail-open contract`() = runTest {
        // ゲート自体の取得失敗時fail-openはSupabaseAppPolicyGate（AppPolicyGateTest）が担う。
        // ここではAnalysisService側がゲートの戻り値をそのまま使うだけであることを確かめる。
        val gate = object : AppPolicyGate {
            override suspend fun isBlocked(platform: String, build: Int) = false
        }
        val service = buildService(appPolicyGate = gate)

        val outcome = service.handle(
            "Bearer valid-token",
            AnalysisRequest(movesUsi = listOf("7g7f")),
            platformHeader = "ios",
            buildHeader = "1",
        )

        assertIs<AnalysisRequestOutcome.Stream>(outcome)
    }

    // ── 401: App Check（段階導入。有効時のみJWT検証より前段でゲートする） ──────

    @Test
    fun `app check disabled (default null) proceeds without any app check header`() = runTest {
        // FIREBASE_PROJECT_NUMBER未設定を模した既定状態。ヘッダが無くても素通りする。
        val service = buildService()
        val outcome = service.handle("Bearer valid-token", AnalysisRequest(movesUsi = listOf("7g7f")))
        assertIs<AnalysisRequestOutcome.Stream>(outcome)
    }

    @Test
    fun `app check enabled and missing header returns Unauthorized before checking JWT`() = runTest {
        val authVerifier = FakeAuthVerifier(mapOf("valid-token" to "user-1"))
        val service = buildService(
            authVerifier = authVerifier,
            appCheckVerifier = FakeAppCheckVerifier(setOf("valid-app-check-token")),
        )
        val outcome = service.handle(
            "Bearer valid-token",
            AnalysisRequest(movesUsi = listOf("7g7f")),
            appCheckHeader = null,
        )
        assertIs<AnalysisRequestOutcome.Unauthorized>(outcome)
        assertEquals(null, authVerifier.lastVerifiedToken, "App CheckがJWT検証より前段でゲートするはず")
    }

    @Test
    fun `app check enabled and invalid token returns Unauthorized`() = runTest {
        val service = buildService(appCheckVerifier = FakeAppCheckVerifier(setOf("valid-app-check-token")))
        val outcome = service.handle(
            "Bearer valid-token",
            AnalysisRequest(movesUsi = listOf("7g7f")),
            appCheckHeader = "forged-token",
        )
        assertIs<AnalysisRequestOutcome.Unauthorized>(outcome)
    }

    @Test
    fun `app check enabled and valid token proceeds to JWT verification`() = runTest {
        val service = buildService(appCheckVerifier = FakeAppCheckVerifier(setOf("valid-app-check-token")))
        val outcome = service.handle(
            "Bearer valid-token",
            AnalysisRequest(movesUsi = listOf("7g7f")),
            appCheckHeader = "valid-app-check-token",
        )
        assertIs<AnalysisRequestOutcome.Stream>(outcome)
    }

    // ── 401: JWT無効/期限切れ ──────────────────────────────────────────────

    @Test
    fun `missing authorization header returns Unauthorized`() = runTest {
        val service = buildService()
        val outcome = service.handle(null, AnalysisRequest(movesUsi = listOf("7g7f")))
        assertIs<AnalysisRequestOutcome.Unauthorized>(outcome)
    }

    @Test
    fun `malformed authorization header returns Unauthorized`() = runTest {
        val service = buildService()
        val outcome = service.handle("NotBearer xyz", AnalysisRequest(movesUsi = listOf("7g7f")))
        assertIs<AnalysisRequestOutcome.Unauthorized>(outcome)
    }

    @Test
    fun `invalid or expired token returns Unauthorized`() = runTest {
        // FakeAuthVerifier に無いトークン = 無効/期限切れの両方を代表する
        val service = buildService(authVerifier = FakeAuthVerifier(emptyMap()))
        val outcome = service.handle("Bearer expired-token", AnalysisRequest(movesUsi = listOf("7g7f")))
        assertIs<AnalysisRequestOutcome.Unauthorized>(outcome)
    }

    // ── 403: BAN ──────────────────────────────────────────────────────────

    @Test
    fun `banned user returns Banned`() = runTest {
        val service = buildService(
            authVerifier = FakeAuthVerifier(mapOf("valid-token" to "banned-user")),
            banRepository = FakeBanRepository(bannedUserIds = setOf("banned-user")),
        )
        val outcome = service.handle("Bearer valid-token", AnalysisRequest(movesUsi = listOf("7g7f")))
        assertIs<AnalysisRequestOutcome.Banned>(outcome)
    }

    // ── 429: クォータ超過（翌日リセット時刻つき） ────────────────────────────

    @Test
    fun `quota exceeded returns QuotaExceeded with next-day JST reset time`() = runTest {
        val jobs = FakeAnalysisJobRepository()
        // 既に3件解析済み（クォータ=3）にしておく
        repeat(3) { i ->
            jobs.seed(
                AnalysisJobRecord(
                    id = "job-seed-$i",
                    userId = "user-1",
                    movesHash = "hash-$i",
                    status = AnalysisJobStatus.DONE,
                    resultJson = null,
                    engineMeta = null,
                    error = null,
                    createdAt = fixedInstant,
                ),
            )
        }
        val service = buildService(
            quotaLimitRepository = FakeQuotaLimitRepository(mapOf("user-1" to 3)),
            analysisJobRepository = jobs,
        )
        val outcome = service.handle("Bearer valid-token", AnalysisRequest(movesUsi = listOf("7g7f")))
        assertIs<AnalysisRequestOutcome.QuotaExceeded>(outcome)
        // fixedInstant = 2026-07-26T10:00:00Z = JST 19:00 → 翌日 2026-07-27T00:00 JST = 2026-07-26T15:00:00Z
        assertEquals(Instant.parse("2026-07-26T15:00:00Z"), outcome.resetAt)
    }

    @Test
    fun `under quota proceeds to analysis`() = runTest {
        val service = buildService(quotaLimitRepository = FakeQuotaLimitRepository(mapOf("user-1" to 30)))
        val outcome = service.handle("Bearer valid-token", AnalysisRequest(movesUsi = listOf("7g7f")))
        assertIs<AnalysisRequestOutcome.Stream>(outcome)
    }

    @Test
    fun `quota already at limit but idempotent hit on the same moves returns cached result, not 429`() = runTest {
        // 上限ちょうどの状態で同じmoves_usiを再POSTする（切断復旧）シナリオ。追加のクォータ判定なしに返す。
        val movesUsi = listOf("7g7f", "3c3d")
        val movesHash = sha256Hex(movesUsi.joinToString(" "))
        val cachedResult = AnalysisResultJson(
            result = listOf(listOf(PvInfoJson(1, ScoreJson("cp", 50), listOf("7g7f"), 400_000))),
            engineMeta = engineMeta(),
        )
        val jobs = FakeAnalysisJobRepository()
        // クォータ上限(3)ちょうどまで既に消費済み。うち1件が今回と同じmoves_hash。
        jobs.seed(
            AnalysisJobRecord(
                id = "job-target",
                userId = "user-1",
                movesHash = movesHash,
                status = AnalysisJobStatus.DONE,
                resultJson = json.encodeToJsonElement(cachedResult.result),
                engineMeta = json.encodeToJsonElement(cachedResult.engineMeta),
                error = null,
                createdAt = fixedInstant,
            ),
        )
        repeat(2) { i ->
            jobs.seed(
                AnalysisJobRecord(
                    id = "job-other-$i",
                    userId = "user-1",
                    movesHash = "other-hash-$i",
                    status = AnalysisJobStatus.DONE,
                    resultJson = null,
                    engineMeta = null,
                    error = null,
                    createdAt = fixedInstant,
                ),
            )
        }
        val engine = FakeEngine()
        val service = buildService(
            quotaLimitRepository = FakeQuotaLimitRepository(mapOf("user-1" to 3)),
            analysisJobRepository = jobs,
            engine = engine,
        )

        val outcome = service.handle("Bearer valid-token", AnalysisRequest(movesUsi = movesUsi))

        assertIs<AnalysisRequestOutcome.Stream>(outcome)
        val lines = outcome.collectLines()
        assertEquals(1, lines.size)
        assertEquals(cachedResult, json.decodeFromString(AnalysisResultJson.serializer(), lines.single()))
        assertEquals(0, engine.analyzeCallCount, "cache hit must not re-run the engine")
    }

    @Test
    fun `quota already at limit and a genuinely new moves_hash still returns 429`() = runTest {
        // 上で復旧できるようになったのは「同一moves_hashの再POST」だけ。新規手順は従来どおり弾く。
        val jobs = FakeAnalysisJobRepository()
        repeat(3) { i ->
            jobs.seed(
                AnalysisJobRecord(
                    id = "job-seed-$i",
                    userId = "user-1",
                    movesHash = "existing-hash-$i",
                    status = AnalysisJobStatus.DONE,
                    resultJson = null,
                    engineMeta = null,
                    error = null,
                    createdAt = fixedInstant,
                ),
            )
        }
        val service = buildService(
            quotaLimitRepository = FakeQuotaLimitRepository(mapOf("user-1" to 3)),
            analysisJobRepository = jobs,
        )
        val outcome = service.handle("Bearer valid-token", AnalysisRequest(movesUsi = listOf("brand-new-move")))
        assertIs<AnalysisRequestOutcome.QuotaExceeded>(outcome)
    }

    @Test
    fun `error jobs do not count toward the daily quota`() = runTest {
        val jobs = FakeAnalysisJobRepository()
        // 上限1のところ、error行が1件ある状態。errorはカウント対象外なので使用量は実質0のはず。
        jobs.seed(
            AnalysisJobRecord(
                id = "job-errored",
                userId = "user-1",
                movesHash = "errored-hash",
                status = AnalysisJobStatus.ERROR,
                resultJson = null,
                engineMeta = null,
                error = "engine crashed",
                createdAt = fixedInstant,
            ),
        )
        val service = buildService(
            quotaLimitRepository = FakeQuotaLimitRepository(mapOf("user-1" to 1)),
            analysisJobRepository = jobs,
        )
        val outcome = service.handle("Bearer valid-token", AnalysisRequest(movesUsi = listOf("7g7f")))
        assertIs<AnalysisRequestOutcome.Stream>(outcome)
    }

    // ── クォータのモード分離（1局解析 vs 単発局面） ─────────────────────────

    @Test
    fun `game quota exhausted does not block a position mode request (separate quota)`() = runTest {
        val jobs = FakeAnalysisJobRepository()
        // 1局解析(game)のクォータ(=1)は使い切っているが、単発局面(position)は未使用。
        jobs.seed(
            AnalysisJobRecord(
                id = "job-game-1",
                userId = "user-1",
                movesHash = "game-hash-1",
                status = AnalysisJobStatus.DONE,
                resultJson = null,
                engineMeta = null,
                error = null,
                createdAt = fixedInstant,
            ),
            mode = "game",
        )
        val service = buildService(
            quotaLimitRepository = FakeQuotaLimitRepository(mapOf("user-1" to 1)),
            analysisJobRepository = jobs,
            positionDailyLimit = 100,
        )

        val outcome = service.handle(
            "Bearer valid-token",
            AnalysisRequest(sfen = "lnsgkgsnl/1r5b1/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL b - 1"),
        )

        assertIs<AnalysisRequestOutcome.Stream>(outcome, "position quota is independent from the exhausted game quota")
    }

    @Test
    fun `position quota exhausted returns 429 while game quota still has room`() = runTest {
        val jobs = FakeAnalysisJobRepository()
        jobs.seed(
            AnalysisJobRecord(
                id = "job-position-1",
                userId = "user-1",
                movesHash = "position-hash-1",
                status = AnalysisJobStatus.DONE,
                resultJson = null,
                engineMeta = null,
                error = null,
                createdAt = fixedInstant,
            ),
            mode = "position",
        )
        val service = buildService(
            quotaLimitRepository = FakeQuotaLimitRepository(mapOf("user-1" to 30)),
            analysisJobRepository = jobs,
            positionDailyLimit = 1,
        )

        // 単発局面は上限(1)ちょうど消費済みなので新規は弾かれる。
        val positionOutcome = service.handle(
            "Bearer valid-token",
            AnalysisRequest(sfen = "lnsgkgsnl/1r5b1/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL b - 1"),
        )
        assertIs<AnalysisRequestOutcome.QuotaExceeded>(positionOutcome)

        // 一方、1局解析(game)は未消費なので通る。
        val gameOutcome = service.handle("Bearer valid-token", AnalysisRequest(movesUsi = listOf("7g7f")))
        assertIs<AnalysisRequestOutcome.Stream>(gameOutcome)
    }

    // ── 400: 不正リクエスト ───────────────────────────────────────────────

    @Test
    fun `request without moves_usi or sfen returns BadRequest`() = runTest {
        val service = buildService()
        val outcome = service.handle("Bearer valid-token", AnalysisRequest())
        assertIs<AnalysisRequestOutcome.BadRequest>(outcome)
    }

    // ── 冪等: 解析済み(done)は再解析せず即返却 ────────────────────────────

    @Test
    fun `idempotent hit on done job returns cached result without re-analysis`() = runTest {
        val movesUsi = listOf("7g7f", "3c3d")
        val movesHash = sha256Hex(movesUsi.joinToString(" "))
        val cachedResult = AnalysisResultJson(
            result = listOf(
                listOf(PvInfoJson(1, ScoreJson("cp", 50), listOf("7g7f"), 400_000)),
            ),
            engineMeta = engineMeta(),
        )
        val jobs = FakeAnalysisJobRepository()
        jobs.seed(
            AnalysisJobRecord(
                id = "job-done",
                userId = "user-1",
                movesHash = movesHash,
                status = AnalysisJobStatus.DONE,
                resultJson = json.encodeToJsonElement(cachedResult.result),
                engineMeta = json.encodeToJsonElement(cachedResult.engineMeta),
                error = null,
                createdAt = fixedInstant,
            ),
        )
        val engine = FakeEngine()
        val service = buildService(analysisJobRepository = jobs, engine = engine)

        val outcome = service.handle("Bearer valid-token", AnalysisRequest(movesUsi = movesUsi))
        assertIs<AnalysisRequestOutcome.Stream>(outcome)
        val lines = outcome.collectLines()

        assertEquals(1, lines.size, "cache hit should emit exactly the final result line, no progress lines")
        val decoded = json.decodeFromString(AnalysisResultJson.serializer(), lines.single())
        assertEquals(cachedResult, decoded)
        assertEquals(0, engine.analyzeCallCount, "engine must not be invoked on a cache hit")
    }

    // ── 並列度 ───────────────────────────────────────────────────────────

    @Test
    fun `analysisWorkers runs that many engine processes at once and quits them all`() = runTest {
        val workers = 3
        // 3本が同時にanalyzeへ入るまで各エンジンを待たせる。並列度が3未満だと
        // 最初の1本がここで待ち続け、awaitがタイムアウトして落ちる。
        val allStarted = CountDownLatch(workers)
        val engines = CopyOnWriteArrayList<FakeEngine>()
        val service = buildService(
            analysisWorkers = workers,
            engineFactory = {
                FakeEngine(
                    onAnalyzeCalled = {
                        allStarted.countDown()
                        assertTrue(
                            allStarted.await(10, TimeUnit.SECONDS),
                            "エンジンが${workers}本同時に走らなかった",
                        )
                    },
                ).also { engines.add(it) }
            },
        )

        val outcome = service.handle(
            "Bearer valid-token",
            AnalysisRequest(movesUsi = listOf("7g7f", "3c3d", "2g2f")),
        )
        assertIs<AnalysisRequestOutcome.Stream>(outcome)
        outcome.collectLines()

        assertEquals(workers, engines.size, "プールはanalysisWorkers本を超えて作らない")
        assertTrue(engines.all { it.quitCalled }, "作ったエンジンは全てquitする")
    }

    // ── 実行中の同一ジョブ: 完了を待って返却 ─────────────────────────────

    @Test
    fun `concurrent identical job waits for completion then returns result`() = runTest {
        val movesUsi = listOf("7g7f")
        val movesHash = sha256Hex(movesUsi.joinToString(" "))
        val jobs = FakeAnalysisJobRepository()
        jobs.seed(
            AnalysisJobRecord(
                id = "job-running",
                userId = "user-1",
                movesHash = movesHash,
                status = AnalysisJobStatus.RUNNING,
                resultJson = null,
                engineMeta = null,
                error = null,
                createdAt = fixedInstant,
            ),
        )
        val finalResult = AnalysisResultJson(
            result = listOf(listOf(PvInfoJson(1, ScoreJson("cp", 12), listOf("2g2f"), 400_000))),
            engineMeta = engineMeta(),
        )
        // 3回 find() された時点で完了とみなす（ポーリングの往復を実際に踏む）
        jobs.completeAfter(
            userId = "user-1",
            movesHash = movesHash,
            calls = 3,
            result = AnalysisJobRecord(
                id = "job-running",
                userId = "user-1",
                movesHash = movesHash,
                status = AnalysisJobStatus.DONE,
                resultJson = json.encodeToJsonElement(finalResult.result),
                engineMeta = json.encodeToJsonElement(finalResult.engineMeta),
                error = null,
                createdAt = fixedInstant,
            ),
        )
        val engine = FakeEngine()
        val service = buildService(analysisJobRepository = jobs, engine = engine)

        val outcome = service.handle("Bearer valid-token", AnalysisRequest(movesUsi = movesUsi))
        assertIs<AnalysisRequestOutcome.Stream>(outcome)
        val lines = outcome.collectLines()

        assertEquals(1, lines.size)
        val decoded = json.decodeFromString(AnalysisResultJson.serializer(), lines.single())
        assertEquals(finalResult, decoded)
        assertEquals(0, engine.analyzeCallCount, "waiting request must not run its own engine")
        assertTrue(jobs.findCallCount.get() >= 3, "must have polled find() at least until completion")
    }

    // ── 実行中の同一ジョブ: staleなrunning行の自己修復 ───────────────────

    @Test
    fun `retrying a stale RUNNING job past the timeout resets it and re-analyzes instead of waiting`() = runTest {
        val movesUsi = listOf("7g7f")
        val movesHash = sha256Hex(movesUsi.joinToString(" "))
        val staleTimeoutMs = 600_000L
        val jobs = FakeAnalysisJobRepository()
        jobs.seed(
            AnalysisJobRecord(
                id = "job-stuck",
                userId = "user-1",
                movesHash = movesHash,
                status = AnalysisJobStatus.RUNNING,
                resultJson = null,
                engineMeta = null,
                error = null,
                // fixedClockの「今」から見て閾値を超えて古いcreated_at。切断でmarkErrorに
                // 到達できないまま止まった行を模す。
                createdAt = fixedInstant.minusMillis(staleTimeoutMs + 1_000),
            ),
        )
        val engine = FakeEngine()
        val service = buildService(
            analysisJobRepository = jobs,
            engine = engine,
            staleRunningTimeoutMs = staleTimeoutMs,
        )

        val outcome = service.handle("Bearer valid-token", AnalysisRequest(movesUsi = movesUsi))
        assertIs<AnalysisRequestOutcome.Stream>(outcome)
        val lines = outcome.collectLines()

        assertTrue(engine.analyzeCallCount > 0, "stale行はwaitForCompletionではなく自分で再解析するはず")
        val finalLine = lines.last()
        json.decodeFromString(AnalysisResultJson.serializer(), finalLine)
        assertEquals(AnalysisJobStatus.DONE, jobs.find("user-1", movesHash)?.status)
    }

    @Test
    fun `retrying a RUNNING job within the timeout still waits for completion`() = runTest {
        val movesUsi = listOf("7g7f")
        val movesHash = sha256Hex(movesUsi.joinToString(" "))
        val staleTimeoutMs = 600_000L
        val jobs = FakeAnalysisJobRepository()
        jobs.seed(
            AnalysisJobRecord(
                id = "job-running-young",
                userId = "user-1",
                movesHash = movesHash,
                status = AnalysisJobStatus.RUNNING,
                resultJson = null,
                engineMeta = null,
                error = null,
                // 閾値未満なのでまだ解析中とみなし、待つ側の分岐に回るはず。
                createdAt = fixedInstant.minusMillis(staleTimeoutMs - 1_000),
            ),
        )
        val finalResult = AnalysisResultJson(
            result = listOf(listOf(PvInfoJson(1, ScoreJson("cp", 1), listOf("7g7f"), 400_000))),
            engineMeta = engineMeta(),
        )
        jobs.completeAfter(
            userId = "user-1",
            movesHash = movesHash,
            calls = 2,
            result = AnalysisJobRecord(
                id = "job-running-young",
                userId = "user-1",
                movesHash = movesHash,
                status = AnalysisJobStatus.DONE,
                resultJson = json.encodeToJsonElement(finalResult.result),
                engineMeta = json.encodeToJsonElement(finalResult.engineMeta),
                error = null,
                createdAt = fixedInstant.minusMillis(staleTimeoutMs - 1_000),
            ),
        )
        val engine = FakeEngine()
        val service = buildService(
            analysisJobRepository = jobs,
            engine = engine,
            staleRunningTimeoutMs = staleTimeoutMs,
        )

        val outcome = service.handle("Bearer valid-token", AnalysisRequest(movesUsi = movesUsi))
        assertIs<AnalysisRequestOutcome.Stream>(outcome)
        val lines = outcome.collectLines()

        assertEquals(0, engine.analyzeCallCount, "閾値未満はwaitForCompletionに回り自分では解析しないはず")
        assertEquals(1, lines.size)
        assertEquals(finalResult, json.decodeFromString(AnalysisResultJson.serializer(), lines.single()))
    }

    // ── 正常系: NDJSON progress行 → 最終行にresult ───────────────────────

    @Test
    fun `fresh analysis streams progress lines then a final result line`() = runTest {
        val movesUsi = listOf("7g7f", "3c3d", "2g2f")
        val engine = FakeEngine()
        val service = buildService(engine = engine)

        val outcome = service.handle("Bearer valid-token", AnalysisRequest(movesUsi = movesUsi))
        assertIs<AnalysisRequestOutcome.Stream>(outcome)
        val lines = outcome.collectLines()

        // 局面数 = moves.size + 1 (0手目〜3手目)
        val progressLines = lines.dropLast(1)
        val finalLine = lines.last()

        assertTrue(progressLines.isNotEmpty(), "expected at least one progress line")
        progressLines.forEach { line ->
            val progress = json.decodeFromString(ProgressJson.serializer(), line)
            assertTrue(progress.progress in 1..progress.total)
            assertEquals(movesUsi.size + 1, progress.total)
        }

        val result = json.decodeFromString(AnalysisResultJson.serializer(), finalLine)
        assertEquals(movesUsi.size + 1, result.result.size)
        assertEquals("test-rev", result.engineMeta.engineRev)
        assertTrue(engine.analyzeCallCount > 0)
        assertTrue(engine.quitCalled, "engine must be released after the job finishes")
    }

    @Test
    fun `single position sfen mode analyzes exactly once`() = runTest {
        val engine = FakeEngine()
        val service = buildService(engine = engine)
        val sfen = "lnsgkgsnl/1r5b1/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL b - 1"

        val outcome = service.handle(
            "Bearer valid-token",
            AnalysisRequest(sfen = sfen, moves = listOf("7g7f")),
        )
        assertIs<AnalysisRequestOutcome.Stream>(outcome)
        val lines = outcome.collectLines()

        val finalLine = lines.last()
        val result = json.decodeFromString(AnalysisResultJson.serializer(), finalLine)
        assertEquals(1, result.result.size)
        assertEquals(1, engine.analyzeCallCount)
    }

    // ── エンジン失敗時: エラー行を出しつつジョブはerrorとして保存される ───────

    @Test
    fun `engine failure marks job as error and emits an error line`() = runTest {
        val jobs = FakeAnalysisJobRepository()
        val engine = FakeEngine(fail = true)
        val service = buildService(analysisJobRepository = jobs, engine = engine)

        val outcome = service.handle("Bearer valid-token", AnalysisRequest(movesUsi = listOf("7g7f")))
        assertIs<AnalysisRequestOutcome.Stream>(outcome)
        val lines = outcome.collectLines()

        assertEquals(1, lines.size)
        val error = json.decodeFromString(ErrorJson.serializer(), lines.single())
        assertTrue(error.error.isNotBlank())
        assertTrue(engine.quitCalled)
    }

    // ── 切断耐性: writeが失敗しても解析はリクエストと独立して完走する ─────────

    @Test
    fun `write failure on every line does not stop a successful analysis from marking the job done`() = runTest {
        val movesUsi = listOf("7g7f", "3c3d")
        val movesHash = sha256Hex(movesUsi.joinToString(" "))
        val jobs = FakeAnalysisJobRepository()
        val engine = FakeEngine()
        val service = buildService(analysisJobRepository = jobs, engine = engine)

        val outcome = service.handle("Bearer valid-token", AnalysisRequest(movesUsi = movesUsi))
        assertIs<AnalysisRequestOutcome.Stream>(outcome)

        // クライアント切断を模す: どの行のwriteも例外を投げる。
        outcome.emit { _ -> throw IllegalStateException("client disconnected") }

        assertTrue(engine.quitCalled, "write失敗でも解析asyncは独立して完走するはず")
        val record = jobs.find("user-1", movesHash)
        assertEquals(AnalysisJobStatus.DONE, record?.status, "write失敗でもmarkDoneまで到達するはず")
    }

    @Test
    fun `write failure combined with an engine failure still marks the job as error`() = runTest {
        val movesUsi = listOf("7g7f")
        val movesHash = sha256Hex(movesUsi.joinToString(" "))
        val jobs = FakeAnalysisJobRepository()
        val engine = FakeEngine(fail = true)
        val service = buildService(analysisJobRepository = jobs, engine = engine)

        val outcome = service.handle("Bearer valid-token", AnalysisRequest(movesUsi = movesUsi))
        assertIs<AnalysisRequestOutcome.Stream>(outcome)

        outcome.emit { _ -> throw IllegalStateException("client disconnected") }

        val record = jobs.find("user-1", movesHash)
        assertEquals(AnalysisJobStatus.ERROR, record?.status, "write失敗と解析失敗が重なってもmarkErrorされるはず")
    }
}
