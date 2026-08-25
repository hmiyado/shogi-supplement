package dev.miyado.shogisupplement.engine

import dev.miyado.shogisupplement.blunder.Score
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** [FailoverEngine] の単体テスト。 */
class FailoverEngineTest {

    /** 呼び出しごとに[response]を1回返す/投げるだけのスタブ。newGame/quitの呼び出し回数も数える。 */
    private class ScriptedEngine(private val response: () -> List<PvInfo>) : Engine {
        var analyzeCallCount = 0
            private set
        var newGameCallCount = 0
            private set
        var quitCallCount = 0
            private set

        override fun analyze(moves: List<String>, nodes: Int): List<PvInfo> = analyzeSfen("startpos", moves, nodes)

        override fun analyzeSfen(sfen: String, additionalMoves: List<String>, nodes: Int): List<PvInfo> {
            analyzeCallCount++
            return response()
        }

        override fun newGame() {
            newGameCallCount++
        }

        override fun quit() {
            quitCallCount++
        }
    }

    private val secondaryResult = listOf(PvInfo(multipv = 1, score = Score.Cp(0), pv = emptyList(), nodes = 0L))

    @Test
    fun `primaryが成功すればsecondaryは呼ばれない`() {
        val primaryResult = listOf(PvInfo(multipv = 1, score = Score.Cp(100), pv = emptyList(), nodes = 0L))
        val primary = ScriptedEngine { primaryResult }
        val secondary = ScriptedEngine { secondaryResult }
        val engine = FailoverEngine(primary, secondary)

        val result = engine.analyzeSfen("startpos")

        assertEquals(primaryResult, result)
        assertEquals(1, primary.analyzeCallCount)
        assertEquals(0, secondary.analyzeCallCount)
    }

    @Test
    fun `primaryが例外を投げたらsecondaryへ切り替わる`() {
        val primary = ScriptedEngine { throw WasmAnalysisException("host not ready") }
        val secondary = ScriptedEngine { secondaryResult }
        val engine = FailoverEngine(primary, secondary)

        val result = engine.analyzeSfen("startpos")

        assertEquals(secondaryResult, result)
        assertEquals(1, secondary.analyzeCallCount)
    }

    @Test
    fun `secondaryも失敗すればその例外が伝播する`() {
        val primary = ScriptedEngine { throw WasmAnalysisException("wasm failed") }
        val secondary = ScriptedEngine { throw IllegalStateException("remote failed") }
        val engine = FailoverEngine(primary, secondary)

        assertFailsWith<IllegalStateException> { engine.analyzeSfen("startpos") }
    }

    @Test
    fun `newGameとquitは両方に転送される`() {
        val primary = ScriptedEngine { secondaryResult }
        val secondary = ScriptedEngine { secondaryResult }
        val engine = FailoverEngine(primary, secondary)

        engine.newGame()
        engine.quit()

        assertEquals(1, primary.newGameCallCount)
        assertEquals(1, secondary.newGameCallCount)
        assertEquals(1, primary.quitCallCount)
        assertEquals(1, secondary.quitCallCount)
    }
}
