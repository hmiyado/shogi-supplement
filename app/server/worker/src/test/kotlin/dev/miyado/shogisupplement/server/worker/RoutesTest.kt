package dev.miyado.shogisupplement.server.worker

import dev.miyado.shogisupplement.api.analysis.EngineMetaJson
import dev.miyado.shogisupplement.server.worker.fakes.FakeAnalysisJobRepository
import dev.miyado.shogisupplement.server.worker.fakes.FakeAppCheckVerifier
import dev.miyado.shogisupplement.server.worker.fakes.FakeAppPolicyGate
import dev.miyado.shogisupplement.server.worker.fakes.FakeAuthVerifier
import dev.miyado.shogisupplement.server.worker.fakes.FakeBanRepository
import dev.miyado.shogisupplement.server.worker.fakes.FakeEngine
import dev.miyado.shogisupplement.server.worker.fakes.FakeQuotaLimitRepository
import dev.miyado.shogisupplement.server.worker.repo.AppPolicyGate
import io.ktor.client.request.get
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * ルート層（Ktor HTTP変換）の統合テスト。AnalysisServiceの分岐網羅は
 * AnalysisServiceTest（Ktor非依存）が担うため、ここではHTTPステータス・
 * Content-Type・NDJSON本文の組み立てが正しいことだけを最小限確認する。
 */
class RoutesTest {

    private fun buildService(
        authVerifier: FakeAuthVerifier = FakeAuthVerifier(mapOf("valid-token" to "user-1")),
        appPolicyGate: AppPolicyGate = AppPolicyGate.AlwaysAllow,
    ) = AnalysisService(
        authVerifier = authVerifier,
        banRepository = FakeBanRepository(),
        quotaLimitRepository = FakeQuotaLimitRepository(),
        analysisJobRepository = FakeAnalysisJobRepository(),
        appPolicyGate = appPolicyGate,
        engineFactory = { FakeEngine() },
        engineMetaProvider = {
            EngineMetaJson(
                engineRev = "test-rev",
                evalSha256 = "test-sha",
                nodes = 400_000,
                threads = 1,
                multiPv = 2,
                usiHash = 128,
                fvScale = 20,
            )
        },
        pollIntervalMs = 1,
    )

    @Test
    fun `health returns 200`() = testApplication {
        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            routing { registerAnalysisRoutes(buildService()) }
        }
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `missing authorization returns 401`() = testApplication {
        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            routing { registerAnalysisRoutes(buildService()) }
        }
        val response = client.post("/v1/analyses") {
            contentType(ContentType.Application.Json)
            setBody("""{"moves_usi":["7g7f"]}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `banned user returns 403`() = testApplication {
        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            routing {
                registerAnalysisRoutes(
                    AnalysisService(
                        authVerifier = FakeAuthVerifier(mapOf("valid-token" to "banned-user")),
                        banRepository = FakeBanRepository(setOf("banned-user")),
                        quotaLimitRepository = FakeQuotaLimitRepository(),
                        analysisJobRepository = FakeAnalysisJobRepository(),
                        engineFactory = { FakeEngine() },
                        engineMetaProvider = {
                            EngineMetaJson("rev", "sha", 400_000, 1, 2, 128, 20)
                        },
                    ),
                )
            }
        }
        val response = client.post("/v1/analyses") {
            header("Authorization", "Bearer valid-token")
            contentType(ContentType.Application.Json)
            setBody("""{"moves_usi":["7g7f"]}""")
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `app check enabled and missing header returns 401`() = testApplication {
        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            routing {
                registerAnalysisRoutes(
                    AnalysisService(
                        authVerifier = FakeAuthVerifier(mapOf("valid-token" to "user-1")),
                        banRepository = FakeBanRepository(),
                        quotaLimitRepository = FakeQuotaLimitRepository(),
                        analysisJobRepository = FakeAnalysisJobRepository(),
                        engineFactory = { FakeEngine() },
                        engineMetaProvider = {
                            EngineMetaJson("rev", "sha", 400_000, 1, 2, 128, 20)
                        },
                        appCheckVerifier = FakeAppCheckVerifier(setOf("valid-app-check-token")),
                    ),
                )
            }
        }
        val response = client.post("/v1/analyses") {
            header("Authorization", "Bearer valid-token")
            contentType(ContentType.Application.Json)
            setBody("""{"moves_usi":["7g7f"]}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `app check enabled and valid header streams NDJSON`() = testApplication {
        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            routing {
                registerAnalysisRoutes(
                    AnalysisService(
                        authVerifier = FakeAuthVerifier(mapOf("valid-token" to "user-1")),
                        banRepository = FakeBanRepository(),
                        quotaLimitRepository = FakeQuotaLimitRepository(),
                        analysisJobRepository = FakeAnalysisJobRepository(),
                        engineFactory = { FakeEngine() },
                        engineMetaProvider = {
                            EngineMetaJson("rev", "sha", 400_000, 1, 2, 128, 20)
                        },
                        appCheckVerifier = FakeAppCheckVerifier(setOf("valid-app-check-token")),
                    ),
                )
            }
        }
        val response = client.post("/v1/analyses") {
            header("Authorization", "Bearer valid-token")
            header("X-Firebase-AppCheck", "valid-app-check-token")
            contentType(ContentType.Application.Json)
            setBody("""{"moves_usi":["7g7f"]}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `successful analysis streams NDJSON with a final result line`() = testApplication {
        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            routing { registerAnalysisRoutes(buildService()) }
        }
        val response = client.post("/v1/analyses") {
            header("Authorization", "Bearer valid-token")
            contentType(ContentType.Application.Json)
            setBody("""{"moves_usi":["7g7f","3c3d"]}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val lines = response.bodyAsText().trim().lines()
        assertTrue(lines.isNotEmpty())
        assertTrue(lines.last().contains("\"result\""))
        assertTrue(lines.last().contains("\"engine_meta\""))
    }

    // ── 426: 強制アップデート（X-App-Platform/X-App-Build） ───────────────────

    @Test
    fun `blocked platform and build returns 426 with the existing error body shape`() = testApplication {
        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            routing {
                registerAnalysisRoutes(buildService(appPolicyGate = FakeAppPolicyGate(setOf("ios"))))
            }
        }
        val response = client.post("/v1/analyses") {
            header("Authorization", "Bearer valid-token")
            header("X-App-Platform", "ios")
            header("X-App-Build", "1")
            contentType(ContentType.Application.Json)
            setBody("""{"moves_usi":["7g7f"]}""")
        }
        assertEquals(HttpStatusCode.UpgradeRequired, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertTrue(body.containsKey("error"), "既存のErrorJson形式（errorフィールド1本）を踏襲するはず")
    }

    @Test
    fun `missing X-App-Platform or X-App-Build headers skips the check (1_0 client compatibility)`() = testApplication {
        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            routing {
                registerAnalysisRoutes(buildService(appPolicyGate = FakeAppPolicyGate(setOf("ios"))))
            }
        }
        val response = client.post("/v1/analyses") {
            header("Authorization", "Bearer valid-token")
            // X-App-Platform/X-App-Build を送らない旧クライアントを模す。
            contentType(ContentType.Application.Json)
            setBody("""{"moves_usi":["7g7f"]}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }
}
