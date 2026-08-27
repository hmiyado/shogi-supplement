package dev.miyado.shogisupplement.server.worker

import dev.miyado.shogisupplement.api.ApiHeaders
import dev.miyado.shogisupplement.api.analysis.EngineMetaJson
import dev.miyado.shogisupplement.api.analysis.ErrorJson
import dev.miyado.shogisupplement.engine.EngineInvariants
import dev.miyado.shogisupplement.engine.IsolatedEngine
import dev.miyado.shogisupplement.engine.UsiEngineSubprocess
import dev.miyado.shogisupplement.server.worker.auth.FirebaseAppCheckVerifier
import dev.miyado.shogisupplement.server.worker.auth.GoTrueTransferSessionIssuer
import dev.miyado.shogisupplement.server.worker.auth.RemoteJwkSetProvider
import dev.miyado.shogisupplement.server.worker.auth.SupabaseJwtAuthVerifier
import dev.miyado.shogisupplement.server.worker.ratelimit.InMemoryIpRateLimiter
import dev.miyado.shogisupplement.server.worker.repo.SupabaseAnalysisJobRepository
import dev.miyado.shogisupplement.server.worker.repo.SupabaseAppUsageRepository
import dev.miyado.shogisupplement.server.worker.repo.SupabaseAppPolicyGate
import dev.miyado.shogisupplement.server.worker.repo.SupabaseBanRepository
import dev.miyado.shogisupplement.server.worker.repo.SupabaseQuotaLimitRepository
import dev.miyado.shogisupplement.server.worker.repo.SupabaseTransferSecretRepository
import dev.miyado.shogisupplement.server.worker.repo.supabaseJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.http.HttpHeaders
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
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
    // Web版（docs/mypage.html）からのfetchはブラウザのプリフライトで弾かれるため必須。
    install(CORS) {
        allowHost("shogi-supplement.miyado.dev", schemes = listOf("https"))
        allowHost("localhost:8000", schemes = listOf("http"))
        allowMethod(io.ktor.http.HttpMethod.Post)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(ApiHeaders.APP_CHECK)
        allowHeader(ApiHeaders.APP_PLATFORM)
        allowHeader(ApiHeaders.APP_BUILD)
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
    val appPolicyGate = SupabaseAppPolicyGate(restClient, config.supabaseUrl, config.supabaseServiceRoleKey)
    val appUsageRepository =
        SupabaseAppUsageRepository(restClient, config.supabaseUrl, config.supabaseServiceRoleKey)

    val jwkSetProvider = RemoteJwkSetProvider(config.supabaseJwksUrl)
    val authVerifier = SupabaseJwtAuthVerifier(jwkSetProvider, issuer = config.supabaseJwtIssuer)

    // 空文字列（未設定）ならnull＝検証自体を無効化する（段階導入。WorkerConfig.firebaseProjectNumber参照）。
    val appCheckVerifier = config.firebaseProjectNumber.takeIf { it.isNotBlank() }?.let { projectNumber ->
        val appCheckJwkSetProvider = RemoteJwkSetProvider(FirebaseAppCheckVerifier.JWKS_URL)
        FirebaseAppCheckVerifier(appCheckJwkSetProvider, projectNumber = projectNumber)
    }

    val service = AnalysisService(
        authVerifier = authVerifier,
        appCheckVerifier = appCheckVerifier,
        banRepository = banRepository,
        quotaLimitRepository = quotaLimitRepository,
        analysisJobRepository = analysisJobRepository,
        appPolicyGate = appPolicyGate,
        appUsageRepository = appUsageRepository,
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
        positionDailyLimit = config.analysisPositionDailyLimit,
        staleRunningTimeoutMs = config.staleRunningTimeoutMs,
    )

    val transferSecretRepository =
        SupabaseTransferSecretRepository(restClient, config.supabaseUrl, config.supabaseServiceRoleKey)
    // Postgrest（restClient）とは別に、GoTrue Admin API（/auth/v1/admin/*・/auth/v1/verify）を
    // 叩く専用クライアント。ContentNegotiationはSupabaseTransferSecretRepository等と
    // 同じsupabaseJson（encodeDefaults=true。GoTrueへ送るtype等の既定値付きフィールドが
    // 欠落しないようにする必要がある。TransferSessionIssuer参照）で揃える。
    val authClient = HttpClient(CIO) {
        install(ClientContentNegotiation) {
            json(supabaseJson)
        }
    }
    val transferSessionIssuer =
        GoTrueTransferSessionIssuer(authClient, config.supabaseUrl, config.supabaseServiceRoleKey)
    val transferRateLimiter = InMemoryIpRateLimiter(
        limit = config.transferRateLimitPerMinute,
        windowMs = 60_000,
    )
    val transferService = TransferService(
        transferSecretRepository = transferSecretRepository,
        sessionIssuer = transferSessionIssuer,
        rateLimiter = transferRateLimiter,
        appCheckVerifier = appCheckVerifier,
        appPolicyGate = appPolicyGate,
    )

    routing {
        registerAnalysisRoutes(service)
        registerTransferRoutes(transferService)
    }
}
