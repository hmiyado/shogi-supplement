package dev.miyado.shogisupplement.server.worker.fakes

import dev.miyado.shogisupplement.blunder.Score
import dev.miyado.shogisupplement.engine.Engine
import dev.miyado.shogisupplement.engine.PvInfo

/**
 * 実バイナリ無しでロジックを検証するためのフェイクエンジン。
 * 局面ごとに固定の1手PVを返す（内容は問わないテストなので単純な値で良い）。
 */
class FakeEngine(
    private val onAnalyzeCalled: (() -> Unit)? = null,
    private val fail: Boolean = false,
) : Engine {
    var quitCalled: Boolean = false
        private set
    var newGameCalled: Boolean = false
        private set
    var analyzeCallCount: Int = 0
        private set

    override fun analyze(moves: List<String>, nodes: Int): List<PvInfo> {
        analyzeCallCount++
        onAnalyzeCalled?.invoke()
        if (fail) error("fake engine failure")
        return listOf(
            PvInfo(multipv = 1, score = Score.Cp(30 + moves.size), pv = listOf("7g7f"), nodes = nodes.toLong()),
            PvInfo(multipv = 2, score = Score.Cp(10 + moves.size), pv = listOf("2g2f"), nodes = nodes.toLong()),
        )
    }

    override fun analyzeSfen(sfen: String, additionalMoves: List<String>, nodes: Int): List<PvInfo> {
        analyzeCallCount++
        if (fail) error("fake engine failure")
        return listOf(
            PvInfo(multipv = 1, score = Score.Mate(3), pv = listOf("5i5h"), nodes = nodes.toLong()),
        )
    }

    override fun quit() {
        quitCalled = true
    }

    override fun newGame() {
        newGameCalled = true
    }
}
