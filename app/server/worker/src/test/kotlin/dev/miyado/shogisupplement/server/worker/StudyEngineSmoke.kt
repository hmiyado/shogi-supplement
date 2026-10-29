package dev.miyado.shogisupplement.server.worker

import dev.miyado.shogisupplement.api.analysis.AnalysisResultJson
import dev.miyado.shogisupplement.engine.UsiEngineSubprocess
import dev.miyado.shogisupplement.server.worker.fakes.FakeAnalysisJobRepository
import dev.miyado.shogisupplement.server.worker.fakes.FakeAuthVerifier
import dev.miyado.shogisupplement.server.worker.fakes.FakeBanRepository
import dev.miyado.shogisupplement.server.worker.fakes.FakeQuotaLimitRepository
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicInteger

fun main() {
    val config = requireNotNull(StudyEngineConfig.fromEnv(System::getenv)) { "Set the five STUDY_ENGINE variables" }
    config.verifyEvaluation()
    val starts = AtomicInteger()
    val profile = StudyEngineProfile(config) { selected ->
        starts.incrementAndGet()
        UsiEngineSubprocess.create(selected.enginePath, selected.evalDir, fvScale = selected.fvScale)
    }
    val service = AnalysisService(
        authVerifier = FakeAuthVerifier(mapOf("smoke-token" to "smoke-user")),
        banRepository = FakeBanRepository(),
        quotaLimitRepository = FakeQuotaLimitRepository(),
        analysisJobRepository = FakeAnalysisJobRepository(),
        engineFactory = { error("Study request used baseline factory") },
        engineMetaProvider = { error("Study request used baseline metadata") },
        studyEngineProfile = profile,
    )
    testApplication {
        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            routing { registerAnalysisRoutes(service) }
        }
        val results = mutableListOf<AnalysisResultJson>()
        repeat(2) {
            val response = client.post("/v1/analyses") {
                header("Authorization", "Bearer smoke-token")
                contentType(ContentType.Application.Json)
                setBody("""{"sfen":"lnsgkgsnl/1r5b1/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL b - 1","moves":["7g7f","3c3d"],"multi_pv":3,"purpose":"study"}""")
            }
            check(response.status == HttpStatusCode.OK)
            val result = Json.decodeFromString<AnalysisResultJson>(response.bodyAsText().trim().lines().last())
            check(result.engineMeta == profile.meta(3))
            check(result.result.single().map { it.multipv }.toSet() == setOf(1, 2, 3))
            results += result
        }
        check(results[0] == results[1])
        check(starts.get() == 1)
        println("STUDY_HTTP_RESULT=" + Json.encodeToString(results.first()))
        println("STUDY_HTTP_CACHE_HIT=true")
    }
}
