package dev.miyado.shogisupplement.server.worker

import dev.miyado.shogisupplement.api.analysis.AnalysisResultJson
import dev.miyado.shogisupplement.api.analysis.EngineMetaJson
import dev.miyado.shogisupplement.blunder.Score
import dev.miyado.shogisupplement.engine.Engine
import dev.miyado.shogisupplement.engine.PvInfo
import dev.miyado.shogisupplement.server.worker.fakes.*
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlin.test.*

class MateGamePipelineTest {
    @Test fun initialGameAnalysisPreservesMateProofAndReportedWinAcrossHttpAndCache() = testApplication {
        var searches = 0
        val service = AnalysisService(
            authVerifier = FakeAuthVerifier(mapOf("test-token" to "test-user")),
            banRepository = FakeBanRepository(),
            quotaLimitRepository = FakeQuotaLimitRepository(),
            analysisJobRepository = FakeAnalysisJobRepository(),
            engineFactory = { object : Engine {
                override fun analyze(moves: List<String>, nodes: Int): List<PvInfo> {
                    searches++
                    return listOf(PvInfo(1, Score.Mate(if (moves.isEmpty()) 3 else -7,
                        verified = moves.isEmpty()), listOf("7g7f"), nodes.toLong()))
                }
                override fun analyzeSfen(sfen: String, additionalMoves: List<String>, nodes: Int, multiPv: Int): List<PvInfo> = error("Game request must not use study analysis")
                override fun quit() {}
                override fun newGame() {}
            } },
            engineMetaProvider = { EngineMetaJson("test+dfpn2", "test-eval", 400000, 1, it, 128, 20) },
        )
        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            routing { registerAnalysisRoutes(service) }
        }
        val results = mutableListOf<AnalysisResultJson>()
        repeat(2) {
            val response = client.post("/v1/analyses") {
                header("Authorization", "Bearer test-token")
                contentType(ContentType.Application.Json)
                setBody("""{"moves_usi":["7g7f"]}""")
            }
            assertEquals(HttpStatusCode.OK, response.status)
            results += Json.decodeFromString<AnalysisResultJson>(response.bodyAsText().trim().lines().last())
        }
        assertEquals(results[0], results[1])
        assertEquals(2, searches)
        val positions = results.first().result
        assertEquals(2, positions.size)
        assertEquals(true, positions[0].single().score.verified)
        assertEquals(3, positions[0].single().score.value)
        assertEquals(false, positions[1].single().score.verified)
        assertEquals(-7, positions[1].single().score.value)
    }
}
