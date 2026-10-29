package dev.miyado.shogisupplement.opening

import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.board.ShogiMove
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdditionalOpeningTest {
    @Test
    fun allNewExamplesUseLegalMoves() {
        (ADDITIONAL_CASTLE_DEFS + ADDITIONAL_STRATEGY_DEFS).forEach { def ->
            def.samples.forEach { sample ->
                val board = ShogiBoard()
                sample.usiMoves.forEach { usi ->
                    val move = ShogiMove.fromUsi(usi)
                    assertTrue(board.legalMoves().contains(move), "${def.slug}: $usi at ${board.toSfen()}")
                    board.push(move)
                }
            }
        }
    }

    @Test
    fun gokigenRejectsPreviouslyClosedBishopPath() {
        val moves = alternating("7g7f 6g6f 6f6e 5g5f 2h5h 5f5e")
        assertFalse("ゴキゲン中飛車" in OpeningClassifier.classify(moves).black.tags)
        assertTrue("中飛車" in OpeningClassifier.classify(moves).black.tags)
    }

    @Test
    fun ureshinoNeedsInitialSilverMoveRatherThanMatchingFinalPlacement() {
        assertFalse("嬉野流" in OpeningClassifier.classify(alternating("5g5f 7i6h 6h5g")).black.tags)
        assertFalse("嬉野流" in OpeningClassifier.classify(alternating("7i6h 5g5f 7g7f 6h5g")).black.tags)
        val moves = ADDITIONAL_STRATEGY_DEFS.first { it.slug == "ureshino" }.samples.first().usiMoves
        assertEquals("嬉野流", OpeningClassifier.classify(moves).black.style)
    }

    @Test
    fun castleDevelopmentDoesNotLeaveIntermediateCastleAsRepresentative() {
        val moves = alternating("7i6h 6i7h 4i5h 5i6i 7g7f 6h7g 6g6f 5h6g 8h5e 6i7i 7i8h")
        val result = OpeningClassifier.classify(moves).black
        assertTrue("カニ囲い" in result.achievedCastles)
        assertTrue("矢倉" in result.achievedCastles)
        assertEquals("矢倉", result.castle)
        val tenshukaku = ADDITIONAL_CASTLE_DEFS.first { it.slug == "tenshukaku-mino" }.samples.first().usiMoves
        val developed = OpeningClassifier.classify(tenshukaku).black
        assertTrue("左美濃囲い" in developed.achievedCastles)
        assertEquals("天守閣美濃", developed.castle)
    }

    private fun alternating(moves: String): List<String> = moves.split(" ").flatMapIndexed { i, move ->
        listOf(move, if (i % 2 == 0) "4a4b" else "4b4a")
    }
}
