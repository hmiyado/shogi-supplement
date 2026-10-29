package dev.miyado.shogisupplement.ui.report

import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.board.ShogiMove
import dev.miyado.shogisupplement.db.GameRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GameShareDraftFactoryTest {
    private fun game(
        userSide: String? = "sente",
        moves: List<String> = listOf("7g7f", "3c3d", "2g2f", "8c8d"),
    ) = GameRecord(
        id = 9L,
        fileName = "秘密の対局.kif",
        contentHash = "hash-9",
        moveCount = moves.size.toLong(),
        senteName = "秘密の先手",
        goteName = "秘密の後手",
        analyzedAt = 1_780_000_000L,
        rating = 1750L,
        coefVersion = "hao_v1",
        movesUsi = moves,
        userSide = userSide,
        kifText = "先手：秘密の先手\n後手：秘密の後手",
    )

    private fun study(baseSfen: String, moves: List<String>, originAbsolutePly: Int, flip: Boolean) =
        StudyState(
            baseSfen = baseSfen,
            moves = moves,
            displayLine = moves,
            origin = StudyOrigin("表示中の局面", null),
            originIsBestPv = false,
            originPlyIndex = 0,
            originSelectedIdx = null,
            originAbsolutePly = originAbsolutePly,
            flip = flip,
        )

    @Test
    fun buildsWholeGameDraftWithoutNamesOrHeaders() {
        val draft = assertNotNull(GameShareDraftFactory.from(game(userSide = "gote"), 2, null, fallbackFlip = false))

        assertEquals(ShogiBoard().toSfen(), draft.initialSfen)
        assertEquals(listOf("7g7f", "3c3d", "2g2f", "8c8d"), draft.mainline)
        assertEquals(2, draft.selectedPly)
        assertEquals(null, draft.branch)
        assertEquals("gote", draft.userSide)
        assertFalse(draft.toString().contains("秘密"))
        assertFalse(draft.toString().contains("先手："))
    }

    @Test
    fun buildsActualBranchFromDisplayedStudyLine() {
        val base = ShogiBoard().also {
            it.push(ShogiMove.fromUsi("7g7f"))
            it.push(ShogiMove.fromUsi("3c3d"))
        }.toSfen()
        val draft = assertNotNull(
            GameShareDraftFactory.from(
                game = game(),
                displayedPly = 0,
                studyState = study(base, listOf("5g5f", "8c8d"), originAbsolutePly = 2, flip = true),
                fallbackFlip = false,
            ),
        )

        assertEquals(4, draft.selectedPly)
        val branch = assertNotNull(draft.branch)
        assertEquals(2, branch.originPly)
        assertEquals(listOf("5g5f", "8c8d"), branch.moves)
        assertEquals(true, draft.flip)
    }

    @Test
    fun exactMainlineStudyHasNoBranch() {
        val base = ShogiBoard().also {
            it.push(ShogiMove.fromUsi("7g7f"))
            it.push(ShogiMove.fromUsi("3c3d"))
        }.toSfen()
        val draft = assertNotNull(
            GameShareDraftFactory.from(
                game = game(),
                displayedPly = 0,
                studyState = study(base, listOf("2g2f", "8c8d"), originAbsolutePly = 2, flip = false),
                fallbackFlip = false,
            ),
        )

        assertEquals(4, draft.selectedPly)
        assertNull(draft.branch)
    }

    @Test
    fun rejectsGameWithoutComparableSide() {
        assertNull(GameShareDraftFactory.from(game(userSide = null), 0, null, fallbackFlip = false))
        assertNull(GameShareDraftFactory.from(game(userSide = "black"), 0, null, fallbackFlip = false))
    }

    @Test
    fun clampsDisplayedPlyToMainline() {
        val draft = assertNotNull(GameShareDraftFactory.from(game(), 99, null, fallbackFlip = false))
        assertEquals(4, draft.selectedPly)
    }
}
