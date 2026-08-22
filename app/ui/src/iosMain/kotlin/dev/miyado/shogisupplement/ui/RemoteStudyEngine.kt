package dev.miyado.shogisupplement.ui

import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.engine.Engine
import dev.miyado.shogisupplement.engine.PvInfo
import kotlinx.coroutines.runBlocking

/** サーバーの単発局面解析をEngineへ適合させる。例外はそのまま伝播する。 @param analyzePosition 局面解析関数。 */
internal class RemoteStudyEngine(
    private val analyzePosition: suspend (sfen: String, moves: List<String>) -> List<PvInfo>,
) : Engine {
    override fun analyze(moves: List<String>, nodes: Int): List<PvInfo> =
        analyzeSfen(ShogiBoard().toSfen(), moves, nodes)

    override fun analyzeSfen(sfen: String, additionalMoves: List<String>, nodes: Int): List<PvInfo> =
        // nodes: サーバー側が探索条件（ノード数含む）を自前で固定するため受け取っても使わない。
        runBlocking { analyzePosition(sfen, additionalMoves) }

    // サーバーAPIには「局の区切り」という概念が無く、局面ごとに独立解析されるため無視してよい。
    override fun newGame() { /* no-op */ }

    // HTTPクライアントの生存期間は呼び出し元が持つため、ここでは何も破棄しない。
    override fun quit() { /* no-op */ }
}
