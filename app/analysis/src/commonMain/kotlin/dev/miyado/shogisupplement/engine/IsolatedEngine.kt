package dev.miyado.shogisupplement.engine

/**
 * 局面ごとに `usinewgame` を送り、置換表・履歴を引き継がせないようにするラッパー。
 *
 * 素の[Engine]は1プロセス/1インスタンスで複数局面を続けて解析するため、直前に解析した
 * 局面の置換表が次の局面の探索に効く。固定ノード数では「どこまで読めたか」がそれで
 * 変わるので、解析結果は**その局面をどの順番で解析したか**に依存してしまう。
 * これを挟むことで解析結果を解析順・並列度から独立させる。
 *
 * 置換表のクリアは毎局面ぶん走るのでその分は遅くなる。
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
