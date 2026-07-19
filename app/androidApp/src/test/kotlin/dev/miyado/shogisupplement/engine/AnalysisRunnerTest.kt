package dev.miyado.shogisupplement.engine

import dev.miyado.shogisupplement.crash.FakeCrashReporter
import dev.miyado.shogisupplement.crash.isAlreadyReported
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])

/**
 * AnalysisRunner のエンジン異常終了検知テスト。
 *
 * FakeEngine と FakeCrashReporter を使い、実エンジンプロセスを起動せずに
 * 「異常終了 → CrashReporter へのイベント送信」の結線を検証する。
 */
class AnalysisRunnerTest {

    // ─── フェイクエンジン ─────────────────────────────────────────────────────────

    /** analyze() を呼ぶと EngineAbnormalExitException をスローするフェイク。 */
    private class AbnormalExitEngine(
        private val exitCode: Int = 139,
        private val lastCmd: String = "go",
    ) : Engine {
        override fun analyze(moves: List<String>, nodes: Int): List<PvInfo> =
            throw EngineAbnormalExitException(
                message = "Engine stdout closed unexpectedly (test)",
                exitCode = exitCode,
                lastCommandName = lastCmd,
            )

        override fun analyzeSfen(sfen: String, additionalMoves: List<String>, nodes: Int): List<PvInfo> =
            throw EngineAbnormalExitException(
                message = "Engine stdout closed unexpectedly (test)",
                exitCode = exitCode,
                lastCommandName = lastCmd,
            )

        override fun quit() {}
        override fun newGame() {}
    }

    /** analyze() を呼ぶと汎用 RuntimeException をスローするフェイク。 */
    private class GenericCrashEngine : Engine {
        override fun analyze(moves: List<String>, nodes: Int): List<PvInfo> =
            throw RuntimeException("unexpected generic error")

        override fun analyzeSfen(sfen: String, additionalMoves: List<String>, nodes: Int): List<PvInfo> =
            throw RuntimeException("unexpected generic error")

        override fun quit() {}
        override fun newGame() {}
    }

    /** analyze() が正常終了するフェイク。 */
    private class HealthyEngine : Engine {
        override fun analyze(moves: List<String>, nodes: Int): List<PvInfo> = emptyList()
        override fun analyzeSfen(sfen: String, additionalMoves: List<String>, nodes: Int): List<PvInfo> = emptyList()
        override fun quit() {}
        override fun newGame() {}
    }

    // ─── ヘルパー ─────────────────────────────────────────────────────────────

    private fun buildRunner(
        engine: Engine,
        crashReporter: FakeCrashReporter,
        workers: Int = 1,
    ) = AnalysisRunner(
        workers = workers,
        crashReporter = crashReporter,
        engineFactory = { engine },
    )

    // ─── テスト ──────────────────────────────────────────────────────────────

    @Test
    fun `EngineAbnormalExitException が発生したとき CrashReporter にイベントが送信される`() = runTest {
        val fakeCrashReporter = FakeCrashReporter()
        val runner = buildRunner(AbnormalExitEngine(exitCode = 139, lastCmd = "go"), fakeCrashReporter)

        try {
            runner.analyzeGame(listOf("7g7f", "3c3d"))
        } catch (_: Exception) {
            // 例外は再スローされることが期待される
        }

        assertTrue("CrashReporter に少なくとも 1 件送信されること", fakeCrashReporter.events.isNotEmpty())
        val event = fakeCrashReporter.events.first()
        assertTrue(
            "EngineAbnormalExitException が送信されること",
            event.exception is EngineAbnormalExitException,
        )
        assertEquals("exitCode が含まれること", "139", event.extras["exitCode"])
        assertEquals("lastCommandName が含まれること", "go", event.extras["lastCommandName"])
        assertTrue("done が含まれること", event.extras.containsKey("done"))
        assertTrue("total が含まれること", event.extras.containsKey("total"))
        assertTrue("workerId が含まれること", event.extras.containsKey("workerId"))
    }

    @Test
    fun `再スローされる例外は送信済みマーカー付き（上位での二重送信を抑止）`() = runTest {
        val fakeCrashReporter = FakeCrashReporter()
        val runner = buildRunner(AbnormalExitEngine(exitCode = 139, lastCmd = "go"), fakeCrashReporter)

        var thrown: Throwable? = null
        try {
            runner.analyzeGame(listOf("7g7f"))
        } catch (e: Exception) {
            thrown = e
        }

        val e = thrown
        assertTrue("例外が再スローされること", e != null)
        checkNotNull(e)
        assertTrue("原因チェーンに送信済みマーカーがあること", e.isAlreadyReported())
        // Runnerからの送信件数は失敗ワーカー数に依存する（兄弟キャンセルとの競合で1〜2件）。
        // このテストの検証対象は「Service側の再送判定で件数が増えないこと」
        val capturedByRunner = fakeCrashReporter.events.size
        assertTrue("Runnerから少なくとも1件送信されること", capturedByRunner >= 1)
        // AnalysisService側の挙動と同じ判定: マーカー付きなら再送しない
        if (!e.isAlreadyReported()) {
            fakeCrashReporter.captureException(e)
        }
        assertEquals("Service側で再送されないこと", capturedByRunner, fakeCrashReporter.events.size)
    }

    @Test
    fun `異常終了時に再スローされた例外で analyzeGame が失敗する`() = runTest {
        val fakeCrashReporter = FakeCrashReporter()
        val runner = buildRunner(AbnormalExitEngine(), fakeCrashReporter)

        var threwException = false
        try {
            runner.analyzeGame(listOf("7g7f"))
        } catch (_: Exception) {
            threwException = true
        }

        assertTrue("analyzeGame は例外を再スローすること", threwException)
    }

    @Test
    fun `汎用 RuntimeException でも CrashReporter にイベントが送信される`() = runTest {
        val fakeCrashReporter = FakeCrashReporter()
        val runner = buildRunner(GenericCrashEngine(), fakeCrashReporter)

        try {
            runner.analyzeGame(listOf("7g7f"))
        } catch (_: Exception) {}

        assertTrue(fakeCrashReporter.events.isNotEmpty())
        val event = fakeCrashReporter.events.first()
        assertFalse(
            "EngineAbnormalExitException でない場合は exitCode/lastCommandName を含まない",
            event.extras.containsKey("exitCode"),
        )
        assertTrue("done は含まれること", event.extras.containsKey("done"))
        assertTrue("total は含まれること", event.extras.containsKey("total"))
    }

    @Test
    fun `正常終了時は CrashReporter に送信しない`() = runTest {
        val fakeCrashReporter = FakeCrashReporter()
        val runner = buildRunner(HealthyEngine(), fakeCrashReporter)

        runner.analyzeGame(listOf("7g7f", "3c3d"))

        assertTrue("正常終了時は CrashReporter にイベントが送信されないこと", fakeCrashReporter.events.isEmpty())
    }
}
