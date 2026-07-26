package dev.miyado.shogisupplement.server.worker

import dev.miyado.shogisupplement.engine.Engine
import dev.miyado.shogisupplement.engine.PvInfo

/**
 * 局面ごとに `usinewgame` を送り、置換表・履歴を引き継がせないようにするラッパー。
 *
 * 素の[Engine]は1プロセスで複数局面を続けて解析するため、直前に解析した局面の置換表が
 * 次の局面の探索に効く。固定ノード数では「どこまで読めたか」がそれで変わるので、
 * 解析結果は**その局面をどの順番で解析したか**に依存する。
 * 解析の並列度や到着順で結果が変わってほしくない場合にこれを挟む。
 *
 * 置換表のクリアは毎局面ぶん走るのでその分は遅くなる。既定で有効にはせず、
 * ANALYSIS_ISOLATE_POSITIONS で切り替える（[WorkerConfig.isolatePositions]）。
 */
class IsolatedEngine(private val delegate: Engine) : Engine {

    override fun analyze(moves: List<String>, nodes: Int): List<PvInfo> {
        delegate.newGame()
        return delegate.analyze(moves, nodes)
    }

    override fun analyzeSfen(sfen: String, additionalMoves: List<String>, nodes: Int): List<PvInfo> {
        delegate.newGame()
        return delegate.analyzeSfen(sfen, additionalMoves, nodes)
    }

    override fun newGame() = delegate.newGame()

    override fun quit() = delegate.quit()
}
