package dev.miyado.shogisupplement.ui.report

import dev.miyado.shogisupplement.gameshare.GameShareSnapshot

fun GameShareSnapshot.selectedPath(): List<String> {
    val branch = branch ?: return mainline.take(selectedPly)
    val prefix = mainline.take(branch.originPly)
    return prefix + branch.moves.take((selectedPly - branch.originPly).coerceAtLeast(0))
}

fun GameShareSnapshot.initialStudyTree(): StudyTree {
    var tree = StudyTree()
    var nextId = 1L

    fun seed(path: List<String>) {
        path.forEachIndexed { index, move ->
            tree = tree.withMovePlayed(path.take(index), move, nextId++)
        }
    }

    seed(mainline)
    branch?.let {
        seed(mainline.take(it.originPly.coerceIn(0, mainline.size)) + it.moves)
    }
    return tree
}

fun StudyTree.hasSameMovesAs(other: StudyTree): Boolean {
    fun same(left: List<StudyNode>, right: List<StudyNode>): Boolean =
        left.size == right.size && left.zip(right).all { (a, b) ->
            a.moveUsi == b.moveUsi && same(a.children, b.children)
        }
    return same(rootChildren, other.rootChildren)
}

fun GameShareSnapshot.playersLine(selfLabel: String, opponentLabel: String): String =
    if (userSide == "sente") "▲$selfLabel　△$opponentLabel" else "▲$opponentLabel　△$selfLabel"
