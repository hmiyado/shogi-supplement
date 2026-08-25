package dev.miyado.shogisupplement.engine

import dev.miyado.shogisupplement.blunder.Score
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** [FailoverAnalyzer] の単体テスト。 */
class FailoverAnalyzerTest {

    /** 呼び出しごとに [responses] を先頭から1つずつ消費して返す/投げる GameAnalyzer。 */
    private class ScriptedAnalyzer(private val responses: List<() -> List<List<PvInfo>>>) : GameAnalyzer {
        var callCount: Int = 0
            private set

        override suspend fun analyzeGame(
            moves: List<String>,
            onPositionResult: ((ply: Int, pvs: List<PvInfo>) -> Unit)?,
            onProgress: ((done: Int, total: Int) -> Unit)?,
        ): List<List<PvInfo>> {
            val response = responses[callCount]
            callCount++
            return response()
        }
    }

    private val fakeResult = listOf(listOf(PvInfo(multipv = 1, score = Score.Cp(0), pv = emptyList(), nodes = 0L)))

    @Test
    fun `429クォータ超過ならフォールバックが発動する`() = runBlocking {
        val delegate = ScriptedAnalyzer(listOf({ throw RemoteAnalysisException.QuotaExceeded("2026-01-01") }))
        val fallback = ScriptedAnalyzer(listOf({ fakeResult }))
        val analyzer = FailoverAnalyzer(delegate, fallback)

        val result = analyzer.analyzeGame(listOf("7g7f"))

        assertEquals(fakeResult, result)
        assertEquals(1, fallback.callCount)
    }

    @Test
    fun `接続断リトライ上限ならフォールバックが発動する`() = runBlocking {
        val delegate = ScriptedAnalyzer(listOf({ throw RemoteAnalysisException.ConnectionLost("timeout") }))
        val fallback = ScriptedAnalyzer(listOf({ fakeResult }))
        val analyzer = FailoverAnalyzer(delegate, fallback)

        val result = analyzer.analyzeGame(listOf("7g7f"))

        assertEquals(fakeResult, result)
        assertEquals(1, fallback.callCount)
    }

    @Test
    fun `426強制アップデートはフォールバックせずそのまま伝播する`() = runBlocking {
        val delegate = ScriptedAnalyzer(listOf({ throw RemoteAnalysisException.UpgradeRequired("please update") }))
        val fallback = ScriptedAnalyzer(listOf({ fakeResult }))
        val analyzer = FailoverAnalyzer(delegate, fallback)

        assertFailsWith<RemoteAnalysisException.UpgradeRequired> {
            analyzer.analyzeGame(listOf("7g7f"))
        }
        assertEquals(0, fallback.callCount)
    }

    @Test
    fun `401等のクライアント起因エラーはフォールバックしない`() = runBlocking {
        for (buildException in listOf<() -> RemoteAnalysisException>(
            { RemoteAnalysisException.Unauthorized("expired") },
            { RemoteAnalysisException.Banned },
            { RemoteAnalysisException.BadRequest("bad") },
            { RemoteAnalysisException.EngineFailure("engine crashed") },
        )) {
            val delegate = ScriptedAnalyzer(listOf({ throw buildException() }))
            val fallback = ScriptedAnalyzer(listOf({ fakeResult }))
            val analyzer = FailoverAnalyzer(delegate, fallback)

            val thrown = assertFailsWith<RemoteAnalysisException> {
                analyzer.analyzeGame(listOf("7g7f"))
            }
            assertEquals(0, fallback.callCount, "フォールバックが発動してはいけない: ${thrown::class.simpleName}")
        }
    }

    @Test
    fun `フォールバック自体が失敗したら元のサーバー例外に戻す`() = runBlocking {
        val original = RemoteAnalysisException.ConnectionLost("timeout")
        val delegate = ScriptedAnalyzer(listOf({ throw original }))
        val fallback = ScriptedAnalyzer(listOf({ throw IllegalStateException("assets unreachable") }))
        val analyzer = FailoverAnalyzer(delegate, fallback)

        val e = assertFailsWith<RemoteAnalysisException.ConnectionLost> {
            analyzer.analyzeGame(listOf("7g7f"))
        }
        assertEquals(original, e)
    }

    @Test
    fun `キャンセルはフォールバックを試みずそのまま伝播する`() = runBlocking {
        val delegate = ScriptedAnalyzer(listOf({ throw CancellationException("cancelled") }))
        val fallback = ScriptedAnalyzer(listOf({ fakeResult }))
        val analyzer = FailoverAnalyzer(delegate, fallback)

        assertFailsWith<CancellationException> {
            analyzer.analyzeGame(listOf("7g7f"))
        }
        assertEquals(0, fallback.callCount)
    }

    @Test
    fun `フォールバック自体のキャンセルもそのまま伝播する`() = runBlocking {
        val delegate = ScriptedAnalyzer(listOf({ throw RemoteAnalysisException.QuotaExceeded("2026-01-01") }))
        val fallback = ScriptedAnalyzer(listOf({ throw CancellationException("cancelled during fallback") }))
        val analyzer = FailoverAnalyzer(delegate, fallback)

        val e = assertFailsWith<CancellationException> {
            analyzer.analyzeGame(listOf("7g7f"))
        }
        assertEquals("cancelled during fallback", e.message)
    }

    @Test
    fun `既定のshouldFallback判定を差し替えられる`() = runBlocking {
        val delegate = ScriptedAnalyzer(listOf({ throw RemoteAnalysisException.BadRequest("bad") }))
        val fallback = ScriptedAnalyzer(listOf({ fakeResult }))
        val analyzer = FailoverAnalyzer(delegate, fallback, shouldFallback = { true })

        val result = analyzer.analyzeGame(listOf("7g7f"))

        assertEquals(fakeResult, result)
        assertTrue(fallback.callCount == 1)
    }
}
