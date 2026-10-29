package dev.miyado.shogisupplement.server.worker

import dev.miyado.shogisupplement.api.analysis.AnalysisRequest
import dev.miyado.shogisupplement.api.analysis.AnalysisResultJson
import dev.miyado.shogisupplement.api.analysis.EngineMetaJson
import dev.miyado.shogisupplement.api.analysis.PositionAnalysisPurpose
import dev.miyado.shogisupplement.engine.UsiEngineSubprocess
import dev.miyado.shogisupplement.server.worker.fakes.FakeAnalysisJobRepository
import dev.miyado.shogisupplement.server.worker.fakes.FakeAuthVerifier
import dev.miyado.shogisupplement.server.worker.fakes.FakeBanRepository
import dev.miyado.shogisupplement.server.worker.fakes.FakeEngine
import dev.miyado.shogisupplement.server.worker.fakes.FakeQuotaLimitRepository
import dev.miyado.shogisupplement.util.sha256Hex
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StudyEngineProfileTest {
    private val sfen = "lnsgkgsnl/1r5b1/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL b - 1"
    private val config = StudyEngineConfig("/candidate", "/eval", "candidate@980", "a".repeat(64), 40)
    private val json = Json { ignoreUnknownKeys = true }

    private fun service(repo: FakeAnalysisJobRepository, normal: FakeEngine, study: StudyEngineProfile?) = AnalysisService(
        authVerifier = FakeAuthVerifier(mapOf("token" to "user")),
        banRepository = FakeBanRepository(),
        quotaLimitRepository = FakeQuotaLimitRepository(),
        analysisJobRepository = repo,
        engineFactory = { normal },
        engineMetaProvider = { multiPv -> EngineMetaJson("baseline", "b".repeat(64), 400000, 1, multiPv, 128, 20) },
        cacheKeyPrefix = "baseline",
        studyEngineProfile = study,
    )

    private suspend fun AnalysisService.result(request: AnalysisRequest): AnalysisResultJson {
        val lines = mutableListOf<String>()
        assertIs<AnalysisRequestOutcome.Stream>(handle("Bearer token", request)).emit { lines += it }
        return json.decodeFromString(lines.last())
    }

    @Test
    fun `study uses its own engine and metadata while drill and old requests keep baseline`() = runTest {
        val repo = FakeAnalysisJobRepository()
        val normal = FakeEngine()
        val candidate = FakeEngine()
        val profile = StudyEngineProfile(config) { selected -> assertEquals(config, selected); candidate }
        val worker = service(repo, normal, profile)
        val study = AnalysisRequest(sfen = sfen, multiPv = 3, purpose = PositionAnalysisPurpose.STUDY)
        val first = worker.result(study)
        assertEquals(40, first.engineMeta.fvScale)
        assertEquals(3, first.engineMeta.multiPv)
        assertEquals(config.evalSha256, first.engineMeta.evalSha256)
        assertEquals(1, candidate.analyzeCallCount)
        assertTrue(candidate.quitCalled)
        assertEquals(first, worker.result(study))
        assertEquals(1, candidate.analyzeCallCount)

        val input = assertIs<EngineInputResult.Valid>(study.toEngineInput()).input
        val saved = repo.find("user", sha256Hex("${profile.cacheKeyPrefix}|${input.hashSeed}"))!!
        assertEquals(first.engineMeta, json.decodeFromJsonElement<EngineMetaJson>(saved.engineMeta!!))
        assertEquals(20, worker.result(AnalysisRequest(sfen = sfen, multiPv = 3)).engineMeta.fvScale)
        val drill = worker.result(AnalysisRequest(sfen = sfen, multiPv = 3, purpose = PositionAnalysisPurpose.DRILL))
        assertEquals(20, drill.engineMeta.fvScale)
        assertEquals(2, drill.engineMeta.multiPv)
        assertEquals(2, normal.analyzeCallCount)
        assertEquals(20, worker.result(AnalysisRequest(movesUsi = listOf("7g7f"))).engineMeta.fvScale)
        assertEquals(1, candidate.analyzeCallCount)
        assertEquals(2, worker.result(study.copy(multiPv = 2)).engineMeta.multiPv)
        assertEquals(2, candidate.analyzeCallCount)
    }

    @Test
    fun `changing scale or candidate and rolling back cannot return another profile cache`() = runTest {
        val repo = FakeAnalysisJobRepository()
        val normal = FakeEngine()
        val candidate = FakeEngine()
        val request = AnalysisRequest(sfen = sfen, purpose = PositionAnalysisPurpose.STUDY)
        val first = service(repo, normal, StudyEngineProfile(config) { candidate }).result(request)
        for (changed in listOf(config.copy(fvScale = 41), config.copy(engineRev = "candidate@next"),
            config.copy(evalSha256 = "c".repeat(64)))) {
            val second = service(repo, normal, StudyEngineProfile(changed) { candidate }).result(request)
            assertEquals(changed.fvScale, second.engineMeta.fvScale)
            assertNotEquals(first.engineMeta.conditionName, second.engineMeta.conditionName)
        }
        assertEquals(4, candidate.analyzeCallCount)
        assertEquals(first, service(repo, normal, StudyEngineProfile(config) { candidate }).result(request))
        assertEquals(4, candidate.analyzeCallCount)
        assertEquals(20, service(repo, normal, null).result(request).engineMeta.fvScale)
        assertEquals(1, normal.analyzeCallCount)
    }

    @Test
    fun `study purpose is rejected on whole game analysis`() {
        assertIs<EngineInputResult.Invalid>(AnalysisRequest(movesUsi = listOf("7g7f"),
            purpose = PositionAnalysisPurpose.STUDY).toEngineInput())
    }

    @Test
    fun `optional configuration must be complete and valid`() {
        assertNull(StudyEngineConfig.fromEnv { null })
        assertFailsWith<IllegalArgumentException> {
            StudyEngineConfig.fromEnv { if (it == "STUDY_ENGINE_PATH") "/engine" else null }
        }
        assertFailsWith<IllegalArgumentException> { config.copy(fvScale = 0) }
        assertFailsWith<IllegalArgumentException> { config.copy(evalSha256 = "not-a-sha") }
        val values = mapOf("STUDY_ENGINE_PATH" to config.enginePath, "STUDY_ENGINE_EVAL_DIR" to config.evalDir,
            "STUDY_ENGINE_REV" to config.engineRev, "STUDY_ENGINE_EVAL_SHA256" to config.evalSha256,
            "STUDY_ENGINE_FV_SCALE" to "40")
        assertEquals(config, StudyEngineConfig.fromEnv(values::get))
    }

    @Test
    fun `evaluation hash mismatch is rejected before startup`() {
        val directory = Files.createTempDirectory("study-eval").toFile()
        try {
            directory.resolve("nn.bin").writeText("fixture")
            config.copy(evalDir = directory.absolutePath, evalSha256 = sha256Hex("fixture")).verifyEvaluation()
            assertFailsWith<IllegalArgumentException> { config.copy(evalDir = directory.absolutePath).verifyEvaluation() }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `subprocess sends selected scale and retains default scale for old callers`() {
        val directory = Files.createTempDirectory("study-usi").toFile()
        try {
            val log = directory.resolve("commands")
            val executable = directory.resolve("engine.sh")
            executable.writeText("""#!/bin/sh
                |while IFS= read -r line; do
                |  printf '%s\n' "${'$'}line" >> '${log.absolutePath}'
                |  case "${'$'}line" in
                |    usi) echo usiok ;;
                |    isready) echo readyok ;;
                |    quit) exit 0 ;;
                |  esac
                |done
                |""".trimMargin())
            assertTrue(executable.setExecutable(true))
            UsiEngineSubprocess.create(executable.absolutePath, directory.absolutePath, fvScale = 40).quit()
            UsiEngineSubprocess.create(executable.absolutePath, directory.absolutePath).quit()
            val commands = log.readLines()
            assertEquals(1, commands.count { it == "setoption name FV_SCALE value 40" })
            assertEquals(1, commands.count { it == "setoption name FV_SCALE value 20" })
        } finally {
            directory.deleteRecursively()
        }
    }
}
