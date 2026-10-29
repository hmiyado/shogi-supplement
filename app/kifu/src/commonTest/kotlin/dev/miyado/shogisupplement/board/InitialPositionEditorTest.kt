package dev.miyado.shogisupplement.board

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InitialPositionEditorTest {
    private val pawn = ShogiSquare(7, 7)
    private val empty = ShogiSquare(7, 6)

    @Test fun draggingPreservesSideAndPromotionAndRejectsOccupiedDestination() {
        val initial = InitialPositionEditor.fromSfen()
        val selected = initial.selectBoard(pawn)
        assertEquals(initial.pieces, selected.moveToSquare(ShogiSquare(8, 7)).pieces)
        val moved = selected.moveToSquare(empty)
        assertEquals(initial.pieces[pawn], moved.pieces[empty])
        assertNull(moved.pieces[pawn])
        val promoted = initial.tapSquare(pawn).selectBoard(pawn).moveToSquare(empty)
        assertEquals(PieceType.PROM_PAWN, promoted.pieces[empty]?.type)
    }

    @Test fun boardTapCyclesSideAndPromotion() {
        var editor = InitialPositionEditor.fromSfen()
        for (expected in listOf(
            ShogiPiece(PieceType.PROM_PAWN, Side.BLACK),
            ShogiPiece(PieceType.PAWN, Side.WHITE),
            ShogiPiece(PieceType.PROM_PAWN, Side.WHITE),
            ShogiPiece(PieceType.PAWN, Side.BLACK),
        )) {
            editor = editor.tapSquare(pawn)
            assertEquals(expected, editor.pieces[pawn])
        }
        assertEquals(40, editor.pieces.size)
    }

    @Test fun goldAndKingOnlyChangeSide() {
        for (square in listOf(ShogiSquare(5, 9), ShogiSquare(4, 9))) {
            val editor = InitialPositionEditor.fromSfen()
            assertEquals(editor.pieces[square]?.copy(side = Side.WHITE), editor.tapSquare(square).pieces[square])
            assertEquals(editor.pieces, editor.tapSquare(square).tapSquare(square).pieces)
        }
    }

    @Test fun boardHandBoxAndBoardTransfersConserveStock() {
        val initial = InitialPositionEditor.fromSfen()
        val hand = initial.tapSquare(pawn).moveToHand(Side.WHITE)
        assertNull(hand.pieces[pawn])
        assertEquals(mapOf(PieceType.PAWN to 1), hand.whiteHand)
        assertNull(hand.selected)
        val box = hand.selectHand(Side.WHITE, PieceType.PAWN).moveToBox()
        assertTrue(box.whiteHand.isEmpty())
        assertEquals(mapOf(PieceType.PAWN to 1), box.pieceBox)
        val placed = box.selectBox(PieceType.PAWN).tapSquare(pawn)
        assertEquals(initial.pieces, placed.pieces)
        assertTrue(placed.pieceBox.isEmpty())
        assertEquals(initial.toSfen(), placed.toSfen())
    }

    @Test fun moveToEmptySquareRemovesSourceAndKeepsPromotion() {
        val moved = InitialPositionEditor.fromSfen().tapSquare(pawn).tapSquare(empty)
        assertNull(moved.pieces[pawn])
        assertEquals(ShogiPiece(PieceType.PROM_PAWN, Side.BLACK), moved.pieces[empty])
        assertEquals(40, moved.pieces.size)
        assertNull(moved.selected)
    }

    @Test fun sameTrayTransferDoesNotDuplicatePiece() {
        val hand = InitialPositionEditor.fromSfen().tapSquare(pawn).moveToHand(Side.BLACK)
        assertEquals(hand, hand.selectHand(Side.BLACK, PieceType.PAWN).moveToHand(Side.BLACK))
        val box = hand.selectHand(Side.BLACK, PieceType.PAWN).moveToBox()
        assertEquals(box, box.selectBox(PieceType.PAWN).moveToBox())
    }

    @Test fun kingCanGoToBoxButNotHand() {
        val selected = InitialPositionEditor.fromSfen().tapSquare(ShogiSquare(5, 9))
        assertEquals(selected, selected.moveToHand(Side.BLACK))
        assertEquals(1, selected.moveToBox().pieceBox[PieceType.KING])
    }

    @Test fun missingPiecesAreInBoxAfterSfenReload() {
        val editor = InitialPositionEditor.fromSfen().tapSquare(pawn).moveToBox().withTurn(Side.WHITE)
        assertEquals(editor, InitialPositionEditor.fromSfen(editor.toSfen()))
        assertEquals(Side.WHITE, ShogiBoard.fromSfen(editor.toSfen()).turn)
    }

    @Test fun absentPieceSelectionAndDestinationWithoutSelectionAreNoOps() {
        val editor = InitialPositionEditor.fromSfen()
        assertEquals(editor, editor.selectBox(PieceType.PAWN).tapSquare(empty))
        assertEquals(editor, editor.selectHand(Side.BLACK, PieceType.PAWN).moveToBox())
        assertEquals(editor, editor.moveToHand(Side.WHITE))
    }
}
