package dev.miyado.shogisupplement.ui.report

import dev.miyado.shogisupplement.blunder.PositionEvalDisplay
import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.db.PositionEvalRow
import dev.miyado.shogisupplement.notation.JapaneseNotation

/** 同じ手数の別分岐へ本譜の評価を流用しない。 */
fun StudyTree.withSavedMainlineEvaluations(
    mainline: List<String>,
    originMoves: List<String>,
    evaluations: List<PositionEvalRow>,
    userIsGote: Boolean,
    evalDisplay: String,
): StudyTree {
    if (originMoves.size > mainline.size || mainline.take(originMoves.size) != originMoves) return this
    val rows = evaluations.associateBy { it.ply }
    fun value(ply: Int): StudyEvalState.Value? {
        val row = rows[ply] ?: return null
        fun label(cp: Int?, mate: Int?) = PositionEvalDisplay.format(cp, mate, userIsGote, evalDisplay, ply)
        val primary = label(row.scoreCp, row.mateIn) ?: return null
        val board = runCatching { ShogiBoard.fromSfen(computeSfenAtStep(null, mainline, ply)) }.getOrNull()
        val candidates = listOf(row.bestUsi to primary, row.secondUsi to label(row.secondScoreCp, row.secondMateIn))
            .mapNotNull { (move, evaluation) ->
                if (move == null || evaluation == null || board == null) return@mapNotNull null
                val notation = runCatching { JapaneseNotation.format(move, board) }.getOrNull() ?: return@mapNotNull null
                StudyCandidate(move, notation, evaluation)
            }
        return StudyEvalState.Value(primary, row.scoreCp?.let { if (userIsGote) -it else it }, candidates)
    }
    fun seed(nodes: List<StudyNode>, ply: Int): List<StudyNode> {
        val first = nodes.firstOrNull() ?: return nodes
        if (first.moveUsi != mainline.getOrNull(ply)) return nodes
        return listOf(first.copy(
            evalState = if (first.evalState == StudyEvalState.None) value(ply + 1) ?: first.evalState else first.evalState,
            children = seed(first.children, ply + 1),
        )) + nodes.drop(1)
    }
    return copy(
        rootEvalState = if (rootEvalState == StudyEvalState.None) value(originMoves.size) ?: rootEvalState else rootEvalState,
        rootChildren = seed(rootChildren, originMoves.size),
    )
}
