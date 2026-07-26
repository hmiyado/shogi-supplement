package dev.miyado.shogisupplement.server.worker

import dev.miyado.shogisupplement.api.analysis.AnalysisRequest
import dev.miyado.shogisupplement.api.analysis.AnalysisResultJson
import dev.miyado.shogisupplement.api.analysis.EngineMetaJson
import dev.miyado.shogisupplement.api.analysis.ErrorJson
import dev.miyado.shogisupplement.api.analysis.PvInfoJson
import dev.miyado.shogisupplement.api.analysis.ProgressJson
import dev.miyado.shogisupplement.api.analysis.ScoreJson
import dev.miyado.shogisupplement.server.worker.fakes.FakeAnalysisJobRepository
import dev.miyado.shogisupplement.server.worker.fakes.FakeAuthVerifier
import dev.miyado.shogisupplement.server.worker.fakes.FakeBanRepository
import dev.miyado.shogisupplement.server.worker.fakes.FakeEngine
import dev.miyado.shogisupplement.server.worker.fakes.FakeQuotaLimitRepository
import dev.miyado.shogisupplement.server.worker.repo.AnalysisJobRecord
import dev.miyado.shogisupplement.server.worker.repo.AnalysisJobStatus
import dev.miyado.shogisupplement.util.sha256Hex
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
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
    ) = AnalysisService(
        authVerifier = authVerifier,
        banRepository = banRepository,
        quotaLimitRepository = quotaLimitRepository,
        analysisJobRepository = analysisJobRepository,
        engineFactory = { engine },
        engineMetaProvider = { engineMeta() },
        clock = clock,
        pollIntervalMs = pollIntervalMs,
        pollTimeoutMs = pollTimeoutMs,
    )

    private suspend fun AnalysisRequestOutcome.Stream.collectLines(): List<String> {
        val lines = mutableListOf<String>()
        emit { line -> lines.add(line.removeSuffix("\n")) }
        return lines
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
            ),
        )
        val service = buildService(
            quotaLimitRepository = FakeQuotaLimitRepository(mapOf("user-1" to 1)),
            analysisJobRepository = jobs,
        )
        val outcome = service.handle("Bearer valid-token", AnalysisRequest(movesUsi = listOf("7g7f")))
        assertIs<AnalysisRequestOutcome.Stream>(outcome)
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
}
