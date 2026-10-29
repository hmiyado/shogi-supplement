package dev.miyado.shogisupplement.ui.report

import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.board.ShogiMove
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MoveListNotationTest {
    @Test
    fun consecutiveCapturesUseSameSquareNotation() {
        val labels = moveListNotations(null, listOf("7g7f", "3c3d", "8h2b+", "3a2b"))
        assertTrue(labels[2].contains("２二"))
        assertEquals("△同　銀", labels[3])
    }

    @Test
    fun firstMoveUsesSharedStartingPositionTurn() {
        val board = ShogiBoard()
        board.push(ShogiMove.fromUsi("7g7f"))
        assertEquals(listOf("△３四歩"), moveListNotations(board.toSfen(), listOf("3c3d")))
    }
}
