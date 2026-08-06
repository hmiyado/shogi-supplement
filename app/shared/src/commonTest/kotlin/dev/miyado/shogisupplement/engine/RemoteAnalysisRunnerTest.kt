package dev.miyado.shogisupplement.engine

import dev.miyado.shogisupplement.blunder.Score
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.writeStringUtf8
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * [RemoteAnalysisRunner] のHTTPをMockEngineで差し替えたテスト。
 *
 * NDJSON行の形は app/server/worker の実応答（RoutesTest.kt・FakeEngine.kt）に合わせている:
 * progress行={"progress":n,"total":m}、最終行={"result":[[{multipv,score,pv,nodes}...]...],"engine_meta":{...}}、
 * エラー行={"error":"..."}、429応答={"error":"quota_exceeded","reset_at":"..."}。
 */
class RemoteAnalysisRunnerTest {

    private val ndjsonHeaders = headersOf(HttpHeaders.ContentType, "application/x-ndjson")

    private val resultLine =
        """{"result":[[{"multipv":1,"score":{"type":"cp","value":30},"pv":["7g7f"],"nodes":400000},""" +
            """{"multipv":2,"score":{"type":"mate","value":-3},"pv":["2g2f"],"nodes":400000}]],""" +
            """"engine_meta":{"engine_rev":"rev","eval_sha256":"sha","nodes":400000,"threads":1,""" +
            """"multi_pv":2,"usi_hash":128,"fv_scale":20}}"""

    private fun runner(
        client: HttpClient,
        maxRetries: Int = 3,
        retryBackoffMs: Long = 1,
        appCheckTokenProvider: (suspend () -> String?)? = null,
        platform: String = "ios",
    ) = RemoteAnalysisRunner(
        baseUrl = "https://analysis-worker.example",
        accessTokenProvider = { "test-jwt" },
        platform = platform,
        httpClient = client,
        maxRetries = maxRetries,
        retryBackoffMs = retryBackoffMs,
        appCheckTokenProvider = appCheckTokenProvider,
    )

    // 本文を最後まで受け取る前に進捗が届くこと＝ストリーミングが機能していることを確かめる。
    // レスポンス本文を読み切ってから処理する実装（HttpClient.post）だと、最終行を書くまで
    // 進捗コールバックが呼ばれず、ここは書き込み待ちのままタイムアウトする。
    @Test
    fun `progress is delivered before the response body is complete`() = runTest {
        // 実ディスパッチャで動かす: runTestの仮想時間ではボディの書き込み待ちが進まず、
        // ストリーミングの成否に関係なくwithTimeoutが即座に成立してしまう。
        withContext(Dispatchers.Default) {
            val firstProgress = CompletableDeferred<Unit>()
            val channel = ByteChannel(autoFlush = true)
            val engine = MockEngine {
                respond(content = channel, status = HttpStatusCode.OK, headers = ndjsonHeaders)
            }

            launch {
                channel.writeStringUtf8("""{"progress":1,"total":1}""" + "\n")
                firstProgress.await()
                channel.writeStringUtf8(resultLine + "\n")
                channel.flushAndClose()
            }

            val result = withTimeout(5_000) {
                runner(HttpClient(engine)).analyzeGame(listOf("7g7f")) { _, _ ->
                    firstProgress.complete(Unit)
                }
            }

            assertEquals(1, result.size)
        }
    }

    @Test
    fun `progress lines are relayed in order and the final line builds PvInfo`() = runTest {
        val body = buildString {
            appendLine("""{"progress":1,"total":2}""")
            appendLine("""{"progress":2,"total":2}""")
            appendLine(resultLine)
        }
        val engine = MockEngine { request ->
            assertEquals("Bearer test-jwt", request.headers[HttpHeaders.Authorization])
            respond(content = ByteReadChannel(body), status = HttpStatusCode.OK, headers = ndjsonHeaders)
        }
        val progressEvents = mutableListOf<Pair<Int, Int>>()

        val result = runner(HttpClient(engine)).analyzeGame(listOf("7g7f")) { done, total ->
            progressEvents.add(done to total)
        }

        assertEquals(listOf(1 to 2, 2 to 2), progressEvents)
        assertEquals(1, result.size)
        assertEquals(2, result[0].size)
        assertEquals(30, (result[0][0].score as Score.Cp).value)
        assertEquals(listOf("7g7f"), result[0][0].pv)
        assertEquals(-3, (result[0][1].score as Score.Mate).plies)
    }

    @Test
    fun `disconnected stream is recovered by re-posting the same request`() = runTest {
        var attempt = 0
        val engine = MockEngine { _ ->
            attempt++
            if (attempt == 1) {
                // 進捗だけでストリームが切れる＝切断。最終行(result/error)が無い。
                respond(
                    content = ByteReadChannel("""{"progress":1,"total":2}""" + "\n"),
                    status = HttpStatusCode.OK,
                    headers = ndjsonHeaders,
                )
            } else {
                respond(
                    content = ByteReadChannel(
                        """{"result":[[{"multipv":1,"score":{"type":"cp","value":10},"pv":[],"nodes":400000}]],""" +
                            """"engine_meta":{"engine_rev":"r","eval_sha256":"s","nodes":400000,"threads":1,""" +
                            """"multi_pv":2,"usi_hash":128,"fv_scale":20}}""",
                    ),
                    status = HttpStatusCode.OK,
                    headers = ndjsonHeaders,
                )
            }
        }

        val result = runner(HttpClient(engine)).analyzeGame(listOf("7g7f"))

        assertEquals(2, attempt)
        assertEquals(10, (result[0][0].score as Score.Cp).value)
    }

    @Test
    fun `retry limit is respected and a ConnectionLost is thrown`() = runTest {
        var attempt = 0
        val engine = MockEngine { _ ->
            attempt++
            // 常に途中で切れる＝毎回切断。
            respond(
                content = ByteReadChannel("""{"progress":1,"total":2}""" + "\n"),
                status = HttpStatusCode.OK,
                headers = ndjsonHeaders,
            )
        }

        val exception = assertFailsWith<RemoteAnalysisException.ConnectionLost> {
            runner(HttpClient(engine), maxRetries = 2).analyzeGame(listOf("7g7f"))
        }

        // 初回 + maxRetries(2) = 3回で打ち切り、無限リトライしない。
        assertEquals(3, attempt)
        assertTrue(exception.message.orEmpty().isNotBlank())
    }

    @Test
    fun `401 is reported as Unauthorized`() = runTest {
        val engine = MockEngine { _ ->
            respond(
                content = ByteReadChannel("""{"error":"invalid or expired token"}"""),
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        assertFailsWith<RemoteAnalysisException.Unauthorized> {
            runner(HttpClient(engine)).analyzeGame(listOf("7g7f"))
        }
    }

    @Test
    fun `403 is reported as Banned`() = runTest {
        val engine = MockEngine { _ ->
            respond(
                content = ByteReadChannel("""{"error":"banned"}"""),
                status = HttpStatusCode.Forbidden,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        assertFailsWith<RemoteAnalysisException.Banned> {
            runner(HttpClient(engine)).analyzeGame(listOf("7g7f"))
        }
    }

    @Test
    fun `429 is reported as QuotaExceeded with the reset_at from the server`() = runTest {
        val engine = MockEngine { _ ->
            respond(
                content = ByteReadChannel("""{"error":"quota_exceeded","reset_at":"2026-07-27T15:00:00Z"}"""),
                status = HttpStatusCode.TooManyRequests,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val exception = assertFailsWith<RemoteAnalysisException.QuotaExceeded> {
            runner(HttpClient(engine)).analyzeGame(listOf("7g7f"))
        }
        assertEquals("2026-07-27T15:00:00Z", exception.resetAt)
    }

    @Test
    fun `426 is reported as UpgradeRequired`() = runTest {
        val engine = MockEngine { _ ->
            respond(
                content = ByteReadChannel("""{"error":"app update required"}"""),
                status = HttpStatusCode.UpgradeRequired,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        assertFailsWith<RemoteAnalysisException.UpgradeRequired> {
            runner(HttpClient(engine)).analyzeGame(listOf("7g7f"))
        }
    }

    // ─── X-App-Platform / X-App-Build（Worker側の強制アップデート検証向け） ────────

    @Test
    fun `analyzeGame always sends X-App-Platform and X-App-Build headers`() = runTest {
        var capturedPlatform: String? = null
        var capturedBuild: String? = null
        val engine = MockEngine { request ->
            capturedPlatform = request.headers["X-App-Platform"]
            capturedBuild = request.headers["X-App-Build"]
            respond(content = ByteReadChannel(resultLine), status = HttpStatusCode.OK, headers = ndjsonHeaders)
        }

        runner(HttpClient(engine), platform = "android").analyzeGame(listOf("7g7f"))

        assertEquals("android", capturedPlatform)
        // JVMターゲットのcurrentBuildNumber()実装は常にInt.MAX_VALUE
        // （BuildNumber.jvm.kt。実APKを持たないテスト/開発ツール用途のため）。
        assertEquals(Int.MAX_VALUE.toString(), capturedBuild)
    }

    @Test
    fun `analyzePosition also sends X-App-Platform and X-App-Build headers`() = runTest {
        var capturedPlatform: String? = null
        val engine = MockEngine { request ->
            capturedPlatform = request.headers["X-App-Platform"]
            respond(
                content = ByteReadChannel(
                    """{"result":[[{"multipv":1,"score":{"type":"cp","value":1},"pv":[],"nodes":400000}]],""" +
                        """"engine_meta":{"engine_rev":"r","eval_sha256":"s","nodes":400000,"threads":1,""" +
                        """"multi_pv":2,"usi_hash":128,"fv_scale":20}}""",
                ),
                status = HttpStatusCode.OK,
                headers = ndjsonHeaders,
            )
        }

        runner(HttpClient(engine), platform = "ios").analyzePosition("startpos")

        assertEquals("ios", capturedPlatform)
    }

    // ─── Firebase App Checkトークンの注入（SDK組み込み自体は別タスク） ────────────

    @Test
    fun `appCheckTokenProvider unset means no X-Firebase-AppCheck header is sent`() = runTest {
        var capturedHeader: String? = null
        val engine = MockEngine { request ->
            capturedHeader = request.headers["X-Firebase-AppCheck"]
            respond(content = ByteReadChannel(resultLine), status = HttpStatusCode.OK, headers = ndjsonHeaders)
        }

        runner(HttpClient(engine)).analyzeGame(listOf("7g7f"))

        assertEquals(null, capturedHeader)
    }

    @Test
    fun `appCheckTokenProvider returning null means no X-Firebase-AppCheck header is sent`() = runTest {
        var capturedHeader: String? = null
        val engine = MockEngine { request ->
            capturedHeader = request.headers["X-Firebase-AppCheck"]
            respond(content = ByteReadChannel(resultLine), status = HttpStatusCode.OK, headers = ndjsonHeaders)
        }

        runner(HttpClient(engine), appCheckTokenProvider = { null }).analyzeGame(listOf("7g7f"))

        assertEquals(null, capturedHeader)
    }

    @Test
    fun `appCheckTokenProvider returning a token attaches X-Firebase-AppCheck header on analyzeGame`() = runTest {
        var capturedHeader: String? = null
        val engine = MockEngine { request ->
            capturedHeader = request.headers["X-Firebase-AppCheck"]
            respond(content = ByteReadChannel(resultLine), status = HttpStatusCode.OK, headers = ndjsonHeaders)
        }

        runner(HttpClient(engine), appCheckTokenProvider = { "app-check-token" }).analyzeGame(listOf("7g7f"))

        assertEquals("app-check-token", capturedHeader)
    }

    @Test
    fun `appCheckTokenProvider returning a token attaches X-Firebase-AppCheck header on analyzePosition`() = runTest {
        var capturedHeader: String? = null
        val engine = MockEngine { request ->
            capturedHeader = request.headers["X-Firebase-AppCheck"]
            respond(
                content = ByteReadChannel(
                    """{"result":[[{"multipv":1,"score":{"type":"cp","value":1},"pv":[],"nodes":400000}]],""" +
                        """"engine_meta":{"engine_rev":"r","eval_sha256":"s","nodes":400000,"threads":1,""" +
                        """"multi_pv":2,"usi_hash":128,"fv_scale":20}}""",
                ),
                status = HttpStatusCode.OK,
                headers = ndjsonHeaders,
            )
        }

        runner(HttpClient(engine), appCheckTokenProvider = { "app-check-token" }).analyzePosition("startpos")

        assertEquals("app-check-token", capturedHeader)
    }

    // ─── analyzePosition（ドリル二次判定の単発局面解析） ──────────────────────

    private fun HttpRequestData.bodyText(): String =
        (body as OutgoingContent.ByteArrayContent).bytes().decodeToString()

    @Test
    fun `analyzePosition sends sfen and moves and unwraps the single position result`() = runTest {
        var capturedBody: String? = null
        val sfen = "lnsgkgsnl/1r5b1/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL b - 1"
        val engine = MockEngine { request ->
            capturedBody = request.bodyText()
            respond(
                content = ByteReadChannel(
                    """{"result":[[{"multipv":1,"score":{"type":"cp","value":42},"pv":["7g7f"],"nodes":400000}]],""" +
                        """"engine_meta":{"engine_rev":"r","eval_sha256":"s","nodes":400000,"threads":1,""" +
                        """"multi_pv":2,"usi_hash":128,"fv_scale":20}}""",
                ),
                status = HttpStatusCode.OK,
                headers = ndjsonHeaders,
            )
        }

        val pvList = runner(HttpClient(engine)).analyzePosition(sfen, moves = listOf("2g2f"))

        val sentBody = Json.parseToJsonElement(capturedBody!!).jsonObject
        assertEquals(sfen, sentBody["sfen"]?.jsonPrimitive?.content)
        assertEquals(listOf("2g2f"), sentBody["moves"]?.jsonArray?.map { it.jsonPrimitive.content })
        assertEquals(null, sentBody["moves_usi"])
        assertEquals(1, pvList.size)
        assertEquals(42, (pvList[0].score as Score.Cp).value)
    }

    @Test
    fun `analyzePosition retries on a disconnected stream just like analyzeGame`() = runTest {
        var attempt = 0
        val engine = MockEngine { _ ->
            attempt++
            if (attempt == 1) {
                respond(content = ByteReadChannel(""), status = HttpStatusCode.OK, headers = ndjsonHeaders)
            } else {
                respond(
                    content = ByteReadChannel(
                        """{"result":[[{"multipv":1,"score":{"type":"cp","value":5},"pv":[],"nodes":400000}]],""" +
                            """"engine_meta":{"engine_rev":"r","eval_sha256":"s","nodes":400000,"threads":1,""" +
                            """"multi_pv":2,"usi_hash":128,"fv_scale":20}}""",
                    ),
                    status = HttpStatusCode.OK,
                    headers = ndjsonHeaders,
                )
            }
        }

        val pvList = runner(HttpClient(engine)).analyzePosition("startpos")

        assertEquals(2, attempt)
        assertEquals(5, (pvList[0].score as Score.Cp).value)
    }

    @Test
    fun `a terminal error line is reported as EngineFailure`() = runTest {
        val engine = MockEngine { _ ->
            respond(
                content = ByteReadChannel(
                    """{"progress":1,"total":2}""" + "\n" + """{"error":"engine crashed"}""" + "\n",
                ),
                status = HttpStatusCode.OK,
                headers = ndjsonHeaders,
            )
        }

        val exception = assertFailsWith<RemoteAnalysisException.EngineFailure> {
            runner(HttpClient(engine)).analyzeGame(listOf("7g7f"))
        }
        assertEquals("engine crashed", exception.message)
    }
}
