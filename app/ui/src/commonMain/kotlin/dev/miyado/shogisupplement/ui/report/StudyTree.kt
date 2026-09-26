package dev.miyado.shogisupplement.ui.report

import dev.miyado.shogisupplement.kifu.KifuPositionNotes
import dev.miyado.shogisupplement.kifu.KifuTree
import dev.miyado.shogisupplement.kifu.KifuTreeNode

/** 終局も独立した枝。通常の指し手の何番目より前にあるかを保持する。 */
data class StudyEnding(val beforeMoveIndex: Int, val reason: String, val timeSeconds: Int?, val notes: KifuPositionNotes, val cumulativeTimeSeconds: Int? = null)

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
    val notes: KifuPositionNotes = KifuPositionNotes(),
    val timeSeconds: Int? = null,
    val endings: List<StudyEnding> = emptyList(),
    val cumulativeTimeSeconds: Int? = null,
)

/** 同じ指し手の兄弟は選択したノードIDで区別する。選択状態はKIFには保存しない。 */
data class StudyTree(
    val rootChildren: List<StudyNode> = emptyList(),
    val rootNotes: KifuPositionNotes = KifuPositionNotes(),
    val rootEndings: List<StudyEnding> = emptyList(),
    val selectedChildren: Map<Long, Long> = emptyMap(),
    val rootEvalState: StudyEvalState = StudyEvalState.None,
) {

    /** 保存時のIDは深さ優先順に振り直す。編集用IDとは独立して親子関係を記録する。 */
    fun toKifu(headers: Map<String, String>): KifuTree {
        val nodes = mutableListOf(KifuTreeNode(0, null, null, notes = rootNotes))
        fun append(children: List<StudyNode>, endings: List<StudyEnding>, parentId: Int) {
            require(endings.all { it.beforeMoveIndex in 0..children.size }) { "Invalid terminal position" }
            for (index in 0..children.size) {
                endings.filter { it.beforeMoveIndex == index }.forEach {
                    nodes.add(KifuTreeNode(nodes.size, parentId, null, it.timeSeconds, it.notes, it.reason, it.cumulativeTimeSeconds))
                }
                val node = children.getOrNull(index) ?: continue
                val id = nodes.size
                nodes.add(KifuTreeNode(id, parentId, node.moveUsi, node.timeSeconds, node.notes, cumulativeTimeSeconds = node.cumulativeTimeSeconds))
                append(node.children, node.endings, id)
            }
        }
        append(rootChildren, rootEndings, 0)
        return KifuTree(headers, nodes)
    }

    companion object {
        /** 任意の読込局面を検討開始点にする。ID・順序・局面注釈を維持する。 */
        fun fromKifu(tree: KifuTree, rootId: Int = 0): StudyTree {
            val byId = tree.nodes.associateBy { it.id }
            require(byId.size == tree.nodes.size) { "Duplicate KIF node ID" }
            val root = requireNotNull(byId[rootId]) { "Missing KIF root" }
            require(root.endReason == null) { "Terminal cannot be a study root" }
            val children = tree.nodes.groupBy { it.parentId }
            fun endings(id: Int): List<StudyEnding> {
                var moveIndex = 0
                return children[id].orEmpty().mapNotNull {
                    val reason = it.endReason
                    if (reason == null) { moveIndex++; null }
                    else {
                        require(children[it.id].isNullOrEmpty()) { "Terminal node has children" }
                        StudyEnding(moveIndex, reason, it.timeSeconds, it.notes, it.cumulativeTimeSeconds)
                    }
                }
            }
            val visiting = mutableSetOf<Int>()
            fun convert(id: Int): StudyNode {
                require(visiting.add(id)) { "Cyclic KIF tree" }
                val node = byId.getValue(id)
                val result = StudyNode(
                    id = node.id.toLong(),
                    moveUsi = requireNotNull(node.moveUsi),
                    children = children[id].orEmpty().filter { it.endReason == null }.map { convert(it.id) },
                    notes = node.notes,
                    timeSeconds = node.timeSeconds,
                    cumulativeTimeSeconds = node.cumulativeTimeSeconds,
                    endings = endings(id),
                )
                visiting.remove(id)
                return result
            }
            visiting.add(rootId)
            return StudyTree(
                rootChildren = children[rootId].orEmpty().filter { it.endReason == null }.map { convert(it.id) },
                rootNotes = root.notes,
                rootEndings = endings(rootId),
            )
        }
    }

    /** 同じ手数でも経路が違えば別のメモとして扱う。存在しない経路はnull。 */
    fun notesAt(moves: List<String>): KifuPositionNotes? {
        if (moves.isEmpty()) return rootNotes
        val path = pathForMoves(moves)
        if (path.size != moves.size) return null
        return nodesAtPath(path).last().notes
    }

    fun subtree(moves: List<String>): StudyTree? {
        if (moves.isEmpty()) return this
        val path = pathForMoves(moves)
        if (path.size != moves.size) return null
        val node = nodesAtPath(path).last()
        return StudyTree(node.children, node.notes, node.endings, rootEvalState = node.evalState)
    }

    /** 開始点より前の本譜・兄弟・消費時間を変更せず、検討部分だけを戻す。 */
    fun withSubtree(moves: List<String>, subtree: StudyTree): StudyTree {
        if (moves.isEmpty()) return subtree
        val path = pathForMoves(moves)
        require(path.size == moves.size) { "Study origin does not exist" }
        return copy(rootChildren = updateNodeRecursive(rootChildren, path) {
            it.copy(children = subtree.rootChildren, notes = subtree.rootNotes, endings = subtree.rootEndings)
        })
    }

    /** 古い開始局面キャッシュからは、基準から変更した部分だけを現在の木へ反映する。 */
    fun mergeEdits(base: StudyTree, edited: StudyTree): StudyTree {
        fun <T> mergeValue(before: T, current: T, after: T): T = when {
            after == before -> current
            current == before || current == after -> after
            else -> throw IllegalStateException("Conflicting study edits")
        }
        fun sameContent(a: StudyNode, b: StudyNode): Boolean =
            StudyTree(listOf(a)).toKifu(emptyMap()) == StudyTree(listOf(b)).toKifu(emptyMap())
        fun reposition(endings: List<StudyEnding>, source: List<StudyNode>, merged: List<StudyNode>): List<StudyEnding> =
            endings.map { ending ->
                require(ending.beforeMoveIndex in 0..source.size)
                val repeated = (source.groupingBy { it.moveUsi }.eachCount().filterValues { it > 1 }.keys +
                    merged.groupingBy { it.moveUsi }.eachCount().filterValues { it > 1 }.keys)
                fun key(node: StudyNode) = if (node.moveUsi in repeated) "id:${node.id}" else node.moveUsi
                val preceding = source.take(ending.beforeMoveIndex).map(::key).toSet()
                ending.copy(beforeMoveIndex = merged.indexOfLast { key(it) in preceding } + 1)
            }
        fun mergeChildren(before: List<StudyNode>, current: List<StudyNode>, after: List<StudyNode>): List<StudyNode> {
            val repeated = before.groupingBy { it.moveUsi }.eachCount().filterValues { it > 1 }.keys
            fun key(node: StudyNode) = if (node.moveUsi in repeated) "id:${node.id}" else node.moveUsi
            val old = before.associateBy(::key)
            val now = current.associateBy(::key)
            val next = after.associateBy(::key)
            require(old.size == before.size && now.size == current.size && next.size == after.size)
            return (current.map(::key) + after.map(::key)).distinct().mapNotNull { move ->
                val b = old[move]
                val c = now[move]
                val e = next[move]
                when {
                    b != null && e == null -> {
                        check(c == null || sameContent(b, c)) { "Deleted branch was edited elsewhere" }
                        null
                    }
                    b != null && c == null -> {
                        check(e == null || sameContent(b, e)) { "Edited branch was deleted elsewhere" }
                        null
                    }
                    e == null -> c
                    c == null -> e
                    else -> {
                        val baseline = b ?: StudyNode(e.id, e.moveUsi)
                        val children = mergeChildren(baseline.children, c.children, e.children)
                        c.copy(
                            notes = mergeValue(baseline.notes, c.notes, e.notes),
                            timeSeconds = mergeValue(baseline.timeSeconds, c.timeSeconds, e.timeSeconds),
                            cumulativeTimeSeconds = mergeValue(baseline.cumulativeTimeSeconds, c.cumulativeTimeSeconds, e.cumulativeTimeSeconds),
                            endings = mergeValue(
                                reposition(baseline.endings, baseline.children, children),
                                reposition(c.endings, c.children, children),
                                reposition(e.endings, e.children, children),
                            ),
                            children = children,
                        )
                    }
                }
            }
        }
        val children = mergeChildren(base.rootChildren, rootChildren, edited.rootChildren)
        return copy(
            rootNotes = mergeValue(base.rootNotes, rootNotes, edited.rootNotes),
            rootEndings = mergeValue(
                reposition(base.rootEndings, base.rootChildren, children),
                reposition(rootEndings, rootChildren, children),
                reposition(edited.rootEndings, edited.rootChildren, children),
            ),
            rootChildren = children,
        )
    }

    fun withNotes(moves: List<String>, notes: KifuPositionNotes): StudyTree {
        if (moves.isEmpty()) return copy(rootNotes = notes)
        val path = pathForMoves(moves)
        if (path.size != moves.size) return this
        return copy(rootChildren = updateNodeRecursive(rootChildren, path) { it.copy(notes = notes) })
    }

    /** 指定した分岐とその子孫だけを削除する。開始局面と兄弟は残す。 */
    fun withoutBranch(moves: List<String>): StudyTree {
        if (moves.isEmpty()) return this
        val path = pathForMoves(moves)
        if (path.size != moves.size) return this
        val parent = path.dropLast(1)
        val remaining = childrenAtPath(parent).filterIndexed { index, _ -> index != path.last() }
        val updated = replaceChildrenAtPath(parent, remaining)
        fun shifted(endings: List<StudyEnding>) = endings.map {
            if (it.beforeMoveIndex > path.last()) it.copy(beforeMoveIndex = it.beforeMoveIndex - 1) else it
        }
        return if (parent.isEmpty()) updated.copy(rootEndings = shifted(updated.rootEndings))
        else updated.copy(rootChildren = updateNodeRecursive(updated.rootChildren, parent) { it.copy(endings = shifted(it.endings)) })
    }

    /** 木に無い手があれば手前で打ち切る。 */
    fun pathForMoves(moves: List<String>): List<Int> {
        val path = mutableListOf<Int>()
        var siblings = rootChildren
        var parentId = 0L
        for (m in moves) {
            val selected = siblings.indexOfFirst { it.id == selectedChildren[parentId] && it.moveUsi == m }
            val idx = if (selected >= 0) selected else siblings.indexOfFirst { it.moveUsi == m }
            if (idx < 0) break
            path.add(idx)
            parentId = siblings[idx].id
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

    fun branchFlags(moves: List<String>): List<Boolean> =
        moves.indices.map { childrenAtPath(pathForMoves(moves.take(it))).size > 1 }

    fun selectChild(parentMoves: List<String>, nodeId: Long): StudyTree {
        val path = pathForMoves(parentMoves)
        require(path.size == parentMoves.size)
        require(childrenAtPath(path).any { it.id == nodeId })
        val parentId = nodesAtPath(path).lastOrNull()?.id ?: 0L
        return copy(selectedChildren = selectedChildren + (parentId to nodeId))
    }

    fun pathToNode(nodeId: Long): List<StudyNode>? {
        if (nodeId == 0L) return emptyList()
        fun find(nodes: List<StudyNode>): List<StudyNode>? {
            for (node in nodes) {
                if (node.id == nodeId) return listOf(node)
                val child = find(node.children)
                if (child != null) return listOf(node) + child
            }
            return null
        }
        return find(rootChildren)
    }

    fun continuation(moves: List<String>): List<String> {
        val path = pathForMoves(moves)
        if (path.size != moves.size) return moves
        val result = moves.toMutableList()
        var parentId = nodesAtPath(path).lastOrNull()?.id ?: 0L
        var children = childrenAtPath(path)
        while (children.isNotEmpty()) {
            val node = children.firstOrNull { it.id == selectedChildren[parentId] } ?: children.first()
            result.add(node.moveUsi)
            parentId = node.id
            children = node.children
        }
        return result
    }

    /** moves と同じ長さで返す。木に無い手は打ち切らず None で埋める。 */
    fun evalStatesAlong(moves: List<String>): List<StudyEvalState> {
        val nodes = nodesAtPath(pathForMoves(moves))
        return moves.indices.map { i -> nodes.getOrNull(i)?.evalState ?: StudyEvalState.None }
    }

    fun evalStateAt(moves: List<String>): StudyEvalState {
        if (moves.isEmpty()) return rootEvalState
        val path = pathForMoves(moves)
        if (path.size != moves.size) return StudyEvalState.None
        return nodesAtPath(path).last().evalState
    }

    /** 同じ位置に同じ手が既にあれば変更なし。無ければ兄弟の末尾に追加する（既存の兄弟は保持する）。 */
    fun withMovePlayed(moves: List<String>, moveUsi: String, newId: Long): StudyTree {
        val parentPath = pathForMoves(moves)
        if (parentPath.size != moves.size) return this
        val siblings = childrenAtPath(parentPath)
        if (siblings.any { it.moveUsi == moveUsi }) return this
        return replaceChildrenAtPath(parentPath, siblings + StudyNode(id = newId, moveUsi = moveUsi))
    }

    /** moves が木に無ければ変更なし。 */
    fun withEvalState(moves: List<String>, evalState: StudyEvalState): StudyTree {
        if (moves.isEmpty()) return copy(rootEvalState = evalState)
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
