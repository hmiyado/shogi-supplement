package dev.miyado.shogisupplement.ui.report

import dev.miyado.shogisupplement.db.PositionEvalRow
import dev.miyado.shogisupplement.kifu.KifTreeParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class StudySavedEvaluationsTest {
    private val moves = listOf("7g7f", "3c3d", "2g2f")
    private val tree = StudyTree.fromKifu(KifTreeParser().parse(
        "1 ７六歩(77)\n2 ３四歩(33)\n3 ２六歩(27)\n4 投了\n変化：2手\n2 ８四歩(83)\n3 ２六歩(27)",
    ))
    private val rows = listOf(PositionEvalRow(0, 10, null, "7g7f"), PositionEvalRow(1, 20, null, "3c3d"),
        PositionEvalRow(2, 30, null, "2g2f"), PositionEvalRow(3, null, 5))

    @Test
    fun seedsEntireMainlineWithoutChangingSavedKifOrOtherBranches() {
        val seeded = tree.withSavedMainlineEvaluations(moves, emptyList(), rows, false, "cp")
        assertEquals("+10", assertIs<StudyEvalState.Value>(seeded.rootEvalState).label.text)
        assertEquals("+20", assertIs<StudyEvalState.Value>(seeded.evalStateAt(moves.take(1))).label.text)
        assertEquals("3c3d", assertIs<StudyEvalState.Value>(seeded.evalStateAt(moves.take(1))).candidates.single().moveUsi)
        assertIs<StudyEvalState.Value>(seeded.evalStateAt(moves))
        assertEquals(StudyEvalState.None, seeded.evalStateAt(listOf("7g7f", "8c8d")))
        assertEquals(tree.toKifu(emptyMap()), seeded.toKifu(emptyMap()))
    }

    @Test
    fun offsetsMidgameOriginsAndUsesGotePerspective() {
        val seeded = tree.subtree(moves.take(1))!!.withSavedMainlineEvaluations(moves, moves.take(1), rows, true, "cp")
        assertEquals("−20", assertIs<StudyEvalState.Value>(seeded.rootEvalState).label.text)
        assertEquals("−30", assertIs<StudyEvalState.Value>(seeded.evalStateAt(listOf("3c3d"))).label.text)
        assertTrue(assertIs<StudyEvalState.Value>(seeded.evalStateAt(listOf("3c3d", "2g2f"))).label.sign < 0)
    }

    @Test
    fun missingRowsAndNonMainlineOriginsRemainUnevaluated() {
        val seeded = tree.withSavedMainlineEvaluations(moves, emptyList(), rows.take(1), false, "cp")
        assertEquals(StudyEvalState.None, seeded.evalStateAt(moves.take(1)))
        val branch = tree.subtree(listOf("7g7f", "8c8d"))!!
        assertEquals(branch, branch.withSavedMainlineEvaluations(moves, listOf("7g7f", "8c8d"), rows, false, "cp"))
    }
}
