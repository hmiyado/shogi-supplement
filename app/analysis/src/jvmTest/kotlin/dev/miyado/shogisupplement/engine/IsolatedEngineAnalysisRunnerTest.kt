package dev.miyado.shogisupplement.engine

import dev.miyado.shogisupplement.blunder.Score
import dev.miyado.shogisupplement.crash.NoopCrashReporter
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * AnalysisRunner + IsolatedEngine の組み合わせで、局面ごとに newGame が呼ばれることを検証する。
 *
 * AnalysisRunner はプールが空のときだけ engineFactory を呼ぶため、workers=1 なら1局につき
 * 最初の1回しか engineFactory は呼ばれず、以降の局面は同じ Engine インスタンスを使い回す。
 * IsolatedEngine で包んでいれば analyze のたびに delegate.newGame() が挟まるので、
 * インスタンスの使い回しに関係なく局面ごとにクリアされることを確認する。
 */
class IsolatedEngineAnalysisRunnerTest {

    private class RecordingFakeEngine : Engine {
        val callLog = mutableListOf<String>()

        override fun analyze(moves: List<String>, nodes: Int): List<PvInfo> {
            callLog.add("analyze:${moves.size}")
            return listOf(PvInfo(multipv = 1, score = Score.Cp(0), pv = emptyList(), nodes = 0L))
        }

        override fun analyzeSfen(sfen: String, additionalMoves: List<String>, nodes: Int): List<PvInfo> =
            analyze(additionalMoves, nodes)

        override fun quit() {
            callLog.add("quit")
        }

        override fun newGame() {
            callLog.add("newGame")
        }
    }

    @Test
    fun `IsolatedEngineで包むとanalyzeのたびに直前にnewGameが呼ばれる`() = runBlocking {
        val fakeEngine = RecordingFakeEngine()
        val runner = AnalysisRunner(
            workers = 1,
            crashReporter = NoopCrashReporter,
            engineFactory = { IsolatedEngine(fakeEngine) },
        )

        runner.analyzeGame(listOf("7g7f", "3c3d"))

        val analyzeIndices = fakeEngine.callLog.withIndex().filter { it.value.startsWith("analyze:") }
        assertTrue(analyzeIndices.isNotEmpty(), "analyzeが少なくとも1回は呼ばれること")
        analyzeIndices.forEach { (i, _) ->
            assertEquals("newGame", fakeEngine.callLog[i - 1], "analyze呼び出し直前にnewGameが呼ばれること")
        }
    }
}
