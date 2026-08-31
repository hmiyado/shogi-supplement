package dev.miyado.shogisupplement.engine

import dev.miyado.shogisupplement.blunder.Score
import dev.miyado.shogisupplement.crash.NoopCrashReporter
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 一部のワーカーが失敗しても、生成したエンジンが1本残らず破棄されることを保証する。 */
class AnalysisRunnerDisposalTest {

    private class TrackedEngine(
        private val onAnalyze: () -> Unit,
    ) : Engine {
        var quitCount = 0
            private set

        override fun analyze(moves: List<String>, nodes: Int): List<PvInfo> {
            onAnalyze()
            return listOf(PvInfo(multipv = 1, score = Score.Cp(0), pv = emptyList(), nodes = 0L))
        }

        override fun analyzeSfen(sfen: String, additionalMoves: List<String>, nodes: Int): List<PvInfo> =
            analyze(additionalMoves, nodes)

        override fun quit() {
            quitCount += 1
        }

        override fun newGame() {}
    }

    @Test
    fun `1本が失敗しても生成済みのエンジンは全て破棄される`() {
        val engines = CopyOnWriteArrayList<TrackedEngine>()
        val createdCount = AtomicInteger(0)
        // 2局面を2本で1つずつ持たせるための同期。1本が両方さらうと、失敗側が待つ相手が
        // いなくなる。
        val bothStarted = CountDownLatch(2)
        // 失敗は健全な側が局面を終えてから起こす。手放されて手つかずになったエンジンが
        // 取り残される、という再現条件そのもの。
        val healthyFinished = CountDownLatch(1)
        val runner = AnalysisRunner(
            workers = 2,
            crashReporter = NoopCrashReporter,
            engineFactory = {
                val isFailing = createdCount.getAndIncrement() == 0
                TrackedEngine(
                    onAnalyze = {
                        bothStarted.countDown()
                        assertTrue(bothStarted.await(10, TimeUnit.SECONDS), "2本が同時に走らなかった")
                        if (isFailing) {
                            assertTrue(healthyFinished.await(10, TimeUnit.SECONDS), "健全な側が先に終わらなかった")
                            throw RuntimeException("engine crashed")
                        }
                    },
                ).also { engines.add(it) }
            },
        )

        var thrown: Throwable? = null
        try {
            runBlocking {
                runner.analyzeGame(listOf("7g7f")) { _, _ -> healthyFinished.countDown() }
            }
        } catch (e: Throwable) {
            thrown = e
        }

        assertTrue(thrown != null, "失敗したワーカーの例外は伝播すること")
        assertEquals(2, engines.size, "workers本のエンジンが生成されること")
        engines.forEach { assertEquals(1, it.quitCount, "生成したエンジンはちょうど1回破棄されること") }
    }
}
