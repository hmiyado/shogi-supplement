package dev.miyado.shogisupplement.ui.report

/**
 * @param id ノードの一意ID（生成順）
 * @param evalState moveUsi を指した後の局面の解析結果
 * @param children このノードから指し直した手
 */
data class StudyNode(
    val id: Long,
    val moveUsi: String,
    val evalState: StudyEvalState = StudyEvalState.None,
    val children: List<StudyNode> = emptyList(),
)

/**
 * 検討手順の木。ルート自体は指し手を持たない（分岐元局面）ため、最初の分岐候補は
 * [rootChildren] に並ぶ。
 *
 * 表示は常に「現在たどっているライン」の線形リストだが、内部データはこの木として
 * 保持し、指し直しで兄弟ノードを追加する。既存の兄弟は上書きしない。
 *
 * 全メソッドは moves（現在ラインの手USI列）を受け取り、都度 [pathForMoves] で木上の
 * 位置に変換する。呼び出し側が path を保持しないのは、兄弟間で moveUsi が重複しない
 * 不変条件により moves から位置が一意に定まるため。
 */
data class StudyTree(val rootChildren: List<StudyNode> = emptyList()) {

    /** 木に無い手があれば手前で打ち切る。 */
    fun pathForMoves(moves: List<String>): List<Int> {
        val path = mutableListOf<Int>()
        var siblings = rootChildren
        for (m in moves) {
            val idx = siblings.indexOfFirst { it.moveUsi == m }
            if (idx < 0) break
            path.add(idx)
            siblings = siblings[idx].children
        }
        return path
    }

    fun nodesAtPath(path: List<Int>): List<StudyNode> {
        val nodes = mutableListOf<StudyNode>()
        var siblings = rootChildren
        for (idx in path) {
            val node = siblings.getOrNull(idx) ?: break
            nodes.add(node)
            siblings = node.children
        }
        return nodes
    }

    /** 兄弟グループにはそのノード自身を含む。depth=0 はルート直下。 */
    fun siblingsAtDepth(moves: List<String>, depth: Int): List<StudyNode> =
        childrenAtPath(pathForMoves(moves.take(depth)))

    fun branchFlags(moves: List<String>): List<Boolean> {
        val flags = mutableListOf<Boolean>()
        var siblings = rootChildren
        for (m in moves) {
            flags.add(siblings.size > 1)
            val idx = siblings.indexOfFirst { it.moveUsi == m }
            siblings = if (idx >= 0) siblings[idx].children else emptyList()
        }
        return flags
    }

    /** moves と同じ長さで返す。木に無い手は打ち切らず None で埋める。 */
    fun evalStatesAlong(moves: List<String>): List<StudyEvalState> {
        val nodes = nodesAtPath(pathForMoves(moves))
        return moves.indices.map { i -> nodes.getOrNull(i)?.evalState ?: StudyEvalState.None }
    }

    fun evalStateAt(moves: List<String>): StudyEvalState {
        if (moves.isEmpty()) return StudyEvalState.None
        val path = pathForMoves(moves)
        if (path.size != moves.size) return StudyEvalState.None
        return nodesAtPath(path).last().evalState
    }

    /** 同じ位置に同じ手が既にあれば変更なし。無ければ兄弟の末尾に追加する（既存の兄弟は保持する）。 */
    fun withMovePlayed(moves: List<String>, moveUsi: String, newId: Long): StudyTree {
        val parentPath = pathForMoves(moves)
        val siblings = childrenAtPath(parentPath)
        if (siblings.any { it.moveUsi == moveUsi }) return this
        return replaceChildrenAtPath(parentPath, siblings + StudyNode(id = newId, moveUsi = moveUsi))
    }

    /** moves が木に無ければ変更なし。 */
    fun withEvalState(moves: List<String>, evalState: StudyEvalState): StudyTree {
        if (moves.isEmpty()) return this
        val path = pathForMoves(moves)
        if (path.size != moves.size) return this
        return copy(rootChildren = updateNodeRecursive(rootChildren, path) { it.copy(evalState = evalState) })
    }

    private fun childrenAtPath(path: List<Int>): List<StudyNode> =
        if (path.isEmpty()) rootChildren else nodesAtPath(path).lastOrNull()?.children ?: emptyList()

    private fun replaceChildrenAtPath(parentPath: List<Int>, newChildren: List<StudyNode>): StudyTree =
        if (parentPath.isEmpty()) {
            copy(rootChildren = newChildren)
        } else {
            copy(rootChildren = replaceChildrenRecursive(rootChildren, parentPath, newChildren))
        }

    private fun replaceChildrenRecursive(
        siblings: List<StudyNode>,
        parentPath: List<Int>,
        newChildren: List<StudyNode>,
    ): List<StudyNode> {
        val idx = parentPath.first()
        val node = siblings.getOrNull(idx) ?: return siblings
        val updated = if (parentPath.size == 1) {
            node.copy(children = newChildren)
        } else {
            node.copy(children = replaceChildrenRecursive(node.children, parentPath.drop(1), newChildren))
        }
        return siblings.toMutableList().also { it[idx] = updated }
    }

    private fun updateNodeRecursive(
        siblings: List<StudyNode>,
        path: List<Int>,
        transform: (StudyNode) -> StudyNode,
    ): List<StudyNode> {
        val idx = path.first()
        val node = siblings.getOrNull(idx) ?: return siblings
        val updated = if (path.size == 1) {
            transform(node)
        } else {
            node.copy(children = updateNodeRecursive(node.children, path.drop(1), transform))
        }
        return siblings.toMutableList().also { it[idx] = updated }
    }
}
