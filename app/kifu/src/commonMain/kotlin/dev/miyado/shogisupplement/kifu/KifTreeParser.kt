package dev.miyado.shogisupplement.kifu

/** ID 0は開始局面。手数ではなく親IDで同じ手数の兄弟を区別する。 */
data class KifuTreeNode(
    val id: Int,
    val parentId: Int?,
    val moveUsi: String?,
    val timeSeconds: Int? = null,
    val notes: KifuPositionNotes = KifuPositionNotes(),
    val endReason: String? = null,
    val cumulativeTimeSeconds: Int? = null,
)

data class KifuTree(val headers: Map<String, String>, val nodes: List<KifuTreeNode>) {
    /** 注釈を除いた本譜の内容。終局手の時間も比較対象に含める。 */
    fun mainLineContent(): List<KifuMainLineStep> {
        val children = nodes.filter { it.id != 0 }.groupBy { it.parentId }
        val visited = mutableSetOf(0)
        val steps = mutableListOf<KifuMainLineStep>()
        var parent = 0
        while (true) {
            val node = children[parent]?.firstOrNull() ?: break
            require(visited.add(node.id)) { "Cyclic KIF main line" }
            steps.add(KifuMainLineStep(node.moveUsi, node.timeSeconds, node.endReason, node.cumulativeTimeSeconds))
            if (node.endReason != null) break
            parent = node.id
        }
        return steps
    }
}

data class KifuMainLineStep(val moveUsi: String?, val timeSeconds: Int?, val endReason: String?, val cumulativeTimeSeconds: Int? = null)

/** 本譜用パーサーとは独立して、変化セクションを現在経路の祖先へ接続する。 */
class KifTreeParser {
    fun parse(text: String): KifuTree {
        val parser = KifParser()
        val main = parser.parse(text)
        val nodes = mutableListOf(KifuTreeNode(0, null, null))
        val destinations = mutableMapOf<Int, KifParser.Square>()
        val path = mutableListOf(0)
        var expectedBranchPly: Int? = null
        var finished = false
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            if (line.startsWith("変化")) {
                if (expectedBranchPly != null) throw KifuParseException("変化に指し手がありません", line)
                if (nodes[path.last()].endReason != null) path.removeAt(path.lastIndex)
                val match = variation.matchEntire(line)
                    ?: throw KifuParseException("変化の開始手数を読めません", line)
                val ply = match.groupValues[1].toIntOrNull()
                    ?: throw KifuParseException("変化の開始手数が不正です", line)
                if (ply !in 1..path.size) throw KifuParseException("変化の親局面がありません", line)
                while (path.size > ply) path.removeAt(path.lastIndex)
                expectedBranchPly = ply
                finished = false
                continue
            }
            if (line.startsWith("*") || line.startsWith("&")) {
                // 見出し直後の注釈は親への帰属が実装間で揺れるので黙って移さない。
                if (expectedBranchPly != null) throw KifuParseException("変化の初手前の注釈には未対応です", line)
                val id = path.last()
                val node = nodes[id]
                val value = raw.trimStart().substring(1)
                val notes = if (line.startsWith("*")) node.notes.copy(comments = node.notes.comments + value)
                    else node.notes.copy(bookmarks = node.notes.bookmarks + value)
                nodes[id] = node.copy(notes = notes)
                continue
            }
            val move = parser.parseMoveLine(line) ?: continue
            val ply = line.takeWhile { it in '0'..'9' }.toIntOrNull()
                ?: throw KifuParseException("指し手の手数が不正です", line)
            if (finished) continue
            if (ply != path.size || (expectedBranchPly != null && ply != expectedBranchPly)) {
                throw KifuParseException("指し手の手数と分岐経路が一致しません", line)
            }
            expectedBranchPly = null
            val parent = path.last()
            if (move.terminal) {
                val id = nodes.size
                nodes.add(KifuTreeNode(id, parent, null, move.timeSeconds, endReason = move.moveText, cumulativeTimeSeconds = move.cumulativeTimeSeconds))
                path.add(id)
                finished = true
                continue
            }
            val (usi, destination) = parser.convertMove(move.moveText, destinations[parent], line)
            val id = nodes.size
            nodes.add(KifuTreeNode(id, parent, usi, move.timeSeconds, cumulativeTimeSeconds = move.cumulativeTimeSeconds))
            destinations[id] = destination
            path.add(id)
        }
        if (expectedBranchPly != null) throw KifuParseException("変化に指し手がありません")
        return KifuTree(main.headers, nodes)
    }

    private val variation = Regex("変化[：:]\\s*([0-9]+)\\s*手")
}
