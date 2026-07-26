package dev.miyado.shogisupplement.server.worker

import dev.miyado.shogisupplement.api.analysis.EngineMetaJson
import dev.miyado.shogisupplement.api.analysis.ErrorJson
import dev.miyado.shogisupplement.engine.EngineInvariants
import dev.miyado.shogisupplement.engine.UsiEngineSubprocess
import dev.miyado.shogisupplement.server.worker.auth.RemoteJwkSetProvider
import dev.miyado.shogisupplement.server.worker.auth.SupabaseJwtAuthVerifier
import dev.miyado.shogisupplement.server.worker.repo.SupabaseAnalysisJobRepository
import dev.miyado.shogisupplement.server.worker.repo.SupabaseBanRepository
import dev.miyado.shogisupplement.server.worker.repo.SupabaseQuotaLimitRepository
import dev.miyado.shogisupplement.server.worker.repo.supabaseJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

private val engineLog = LoggerFactory.getLogger(UsiEngineSubprocess::class.java)

fun main() {
    val config = WorkerConfig.fromEnv()
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
        module(config)
    }.start(wait = true)
}

fun Application.module(config: WorkerConfig) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
    install(CallLogging)
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respond(HttpStatusCode.InternalServerError, ErrorJson(cause.message ?: "internal error"))
        }
    }

    // service_roleキーでのPostgRESTアクセス専用クライアント。
    val restClient = HttpClient(CIO) {
        install(ClientContentNegotiation) {
            json(supabaseJson)
        }
    }

    val banRepository = SupabaseBanRepository(restClient, config.supabaseUrl, config.supabaseServiceRoleKey)
    val quotaLimitRepository =
        SupabaseQuotaLimitRepository(restClient, config.supabaseUrl, config.supabaseServiceRoleKey)
    val analysisJobRepository =
        SupabaseAnalysisJobRepository(restClient, config.supabaseUrl, config.supabaseServiceRoleKey)

    val jwkSetProvider = RemoteJwkSetProvider(config.supabaseJwksUrl)
    val authVerifier = SupabaseJwtAuthVerifier(jwkSetProvider, issuer = config.supabaseJwtIssuer)

    val service = AnalysisService(
        authVerifier = authVerifier,
        banRepository = banRepository,
        quotaLimitRepository = quotaLimitRepository,
        analysisJobRepository = analysisJobRepository,
        engineFactory = {
            val engine = UsiEngineSubprocess.create(
                enginePath = config.enginePath,
                evalDir = config.engineEvalDir,
                logLifecycle = engineLog::info,
                logIo = engineLog::debug,
            )
            if (config.isolatePositions) IsolatedEngine(engine) else engine
        },
        engineMetaProvider = {
            EngineMetaJson(
                engineRev = config.engineRev,
                evalSha256 = config.evalSha256,
                nodes = EngineInvariants.NODES,
                threads = EngineInvariants.THREADS,
                multiPv = EngineInvariants.MULTI_PV,
                usiHash = EngineInvariants.USI_HASH_MB,
                fvScale = EngineInvariants.FV_SCALE,
            )
        },
        analysisWorkers = config.analysisWorkers,
    )

    routing {
        registerAnalysisRoutes(service)
    }
}
