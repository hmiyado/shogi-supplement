package dev.miyado.shogisupplement.repertoire

import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.board.ShogiMove
import dev.miyado.shogisupplement.db.GameRecord
import dev.miyado.shogisupplement.kifu.KifTreeParser

/** 手番を除く局面・片側の形を索引化する。本譜と検討分岐の重複は集合でまとめる。 */
fun GameRecord.positionKeys(): Set<String> = buildSet {
    fun record(board: ShogiBoard) {
        PositionLabelScope.entries.forEach { add(positionLabelKey(board, it)) }
    }
    val board = ShogiBoard()
    record(board)
    for (move in movesUsi) {
        val parsed = runCatching { ShogiMove.fromUsi(move) }.getOrNull() ?: break
        if (runCatching { board.push(parsed) }.isFailure) break
        record(board)
    }
    val tree = studyKif?.let { runCatching { KifTreeParser().parse(it) }.getOrNull() }
    if (tree != null) {
        val children = tree.nodes.groupBy { it.parentId }
        val queue = ArrayDeque<Pair<Int, String>>()
        val visited = mutableSetOf<Int>()
        queue.add(0 to ShogiBoard().toSfen())
        while (queue.isNotEmpty()) {
            val (parent, sfen) = queue.removeFirst()
            if (!visited.add(parent)) continue
            for (node in children[parent].orEmpty()) {
                val move = node.moveUsi ?: continue
                val position = runCatching { ShogiBoard.fromSfen(sfen).also { it.push(ShogiMove.fromUsi(move)) } }.getOrNull() ?: continue
                record(position)
                queue.add(node.id to position.toSfen())
            }
        }
    }
}
