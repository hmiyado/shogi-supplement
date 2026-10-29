package dev.miyado.shogisupplement.kifu

import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.board.ShogiMove

/** 本譜を先に出し、現在経路の祖先へ戻れる深さ優先順で変化を出力する。 */
class KifTreeWriter {
    fun write(tree: KifuTree): String {
        val byId = tree.nodes.associateBy { it.id }
        require(byId.size == tree.nodes.size) { "Duplicate KIF node ID" }
        val root = requireNotNull(byId[0]) { "Missing KIF root" }
        require(root.parentId == null && root.moveUsi == null && root.endReason == null) { "Invalid KIF root" }
        require(tree.headers["手合割"]?.trim().let { it == null || it == "平手" }) { "Unsupported handicap" }
        tree.nodes.filter { it.id != 0 }.forEach {
            require(it.parentId != null && it.parentId in byId && (it.moveUsi != null) != (it.endReason != null)) { "Invalid KIF parent or move" }
        }
        val children = tree.nodes.filter { it.id != 0 }.groupBy { it.parentId }
        val visited = mutableSetOf(0)
        val board = ShogiBoard()
        val output = StringBuilder()
        fun singleLine(value: String): String {
            require('\n' !in value && '\r' !in value) { "KIF field contains newline" }
            return value
        }
        tree.headers.forEach { (key, value) ->
            output.append(singleLine(key)).append('：').append(singleLine(value)).append('\n')
        }
        output.append("手数----指手---------消費時間--\n")
        fun appendNotes(notes: KifuPositionNotes) {
            notes.comments.forEach { output.append('*').append(singleLine(it)).append('\n') }
            notes.bookmarks.forEach { output.append('&').append(singleLine(it)).append('\n') }
        }
        fun appendContinuation(parent: KifuTreeNode, depth: Int) {
            children[parent.id].orEmpty().forEachIndexed { index, node ->
                require(visited.add(node.id)) { "Cyclic KIF tree" }
                if (index > 0) {
                    output.append("\n変化：").append(depth + 1).append("手\n")
                }
                val move = node.moveUsi?.let(ShogiMove::fromUsi)
                val notation = if (move != null) KifuReconstructor.formatMove(move, board) else singleLine(requireNotNull(node.endReason))
                output.append(depth + 1).append(' ').append(notation)
                node.timeSeconds?.let {
                    require(it >= 0) { "Negative move time" }
                    output.append(" (").append(KifuReconstructor.formatClock(it))
                    node.cumulativeTimeSeconds?.let { total ->
                        require(total >= 0) { "Negative cumulative time" }
                        output.append('/').append((total / 3600).toString().padStart(2, '0'))
                            .append(':').append((total / 60 % 60).toString().padStart(2, '0'))
                            .append(':').append((total % 60).toString().padStart(2, '0'))
                    }
                    output.append(')')
                }
                output.append('\n')
                appendNotes(node.notes)
                if (move != null) {
                    board.push(move)
                    appendContinuation(node, depth + 1)
                    board.pop()
                } else {
                    require(children[node.id].isNullOrEmpty()) { "Terminal node has children" }
                }
            }
        }
        appendNotes(root.notes)
        appendContinuation(root, 0)
        require(visited.size == tree.nodes.size) { "Unreachable KIF nodes" }
        return output.toString()
    }
}
