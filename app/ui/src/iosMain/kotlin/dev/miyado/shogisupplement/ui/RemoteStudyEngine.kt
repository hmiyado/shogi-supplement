package dev.miyado.shogisupplement.ui

import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.engine.Engine
import dev.miyado.shogisupplement.engine.PvInfo
import kotlinx.coroutines.runBlocking

/**
 * 検討モード・読み筋延長（StudyController/PvExtensionRunner）向けの [Engine] アダプタ。
 *
 * どちらも `() -> Engine` の同期API（[Engine.analyzeSfen] は suspend ではない）でしか
 * 解析関数を受け取れないため、サーバーの単発局面解析（[analyzePosition]）を
 * [runBlocking] で同期化してこの型に合わせる。
 *
 * 型付き例外（429/401/切断等）を個別の文言に変換せず、呼び出し側の runCatching へ
 * そのまま伝播させる。検討・読み筋延長は「解析できた/できなかった」以上の情報を
 * 表示に必要としないため。
 *
 * @param analyzePosition サーバーへの単発局面解析。呼び出し元が匿名サインイン保証込みの
 *   クロージャを渡す想定。
 */
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
