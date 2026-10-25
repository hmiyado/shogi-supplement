package dev.miyado.shogisupplement.api.gameshare

import dev.miyado.shogisupplement.board.ShogiBoard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GameShareSnapshotValidatorTest {
    private val startSfen = ShogiBoard().toSfen()

    private fun request(
        initialSfen: String = startSfen,
        mainline: List<String> = listOf("7g7f", "3c3d"),
        selectedPly: Int = 2,
        branch: GameShareBranchJson? = null,
        userSide: String = "sente",
        gameKey: String = "content-hash",
        winner: String? = null,
        endReason: String? = null,
    ) = CreateGameShareRequest(
        gameKey = gameKey,
        initialSfen = initialSfen,
        mainline = mainline,
        selectedPly = selectedPly,
        branch = branch,
        userSide = userSide,
        flip = true,
        winner = winner,
        endReason = endReason,
    )

    @Test
    fun acceptsLegalMainlineAndSelectedPly() {
        val value = assertNotNull(GameShareSnapshotValidator.validate(request()))
        assertEquals(startSfen, value.initialSfen)
        assertEquals(listOf("7g7f", "3c3d"), value.mainline)
        assertEquals(2, value.selectedPly)
        assertEquals("sente", value.userSide)
        assertEquals(true, value.flip)
    }

    @Test
    fun acceptsLegalBranchAndSelectsInsideIt() {
        val branch = GameShareBranchJson(originPly = 2, moves = listOf("2g2f", "8c8d"))
        val value = assertNotNull(
            GameShareSnapshotValidator.validate(request(selectedPly = 4, branch = branch)),
        )
        assertEquals(branch, value.branch)
        assertEquals(4, value.selectedPly)
    }

    @Test
    fun acceptsNonStandardButValidInitialPosition() {
        val sfen = "4k4/9/9/9/9/9/9/9/4K4 b - 1"
        val value = assertNotNull(
            GameShareSnapshotValidator.validate(
                request(initialSfen = sfen, mainline = listOf("5i5h"), selectedPly = 1),
            ),
        )
        assertEquals(sfen, value.initialSfen)
    }

    @Test
    fun rejectsIllegalMainlineMove() {
        assertNull(
            GameShareSnapshotValidator.validate(
                request(mainline = listOf("7g7f", "7g7f")),
            ),
        )
    }

    @Test
    fun rejectsIllegalBranchMove() {
        assertNull(
            GameShareSnapshotValidator.validate(
                request(
                    branch = GameShareBranchJson(originPly = 2, moves = listOf("2g2f", "2g2f")),
                    selectedPly = 4,
                ),
            ),
        )
    }

    @Test
    fun rejectsBranchSelectionBeforeOriginAndOutOfRangeMainlineSelection() {
        val branch = GameShareBranchJson(originPly = 1, moves = listOf("2g2f"))
        assertNull(
            GameShareSnapshotValidator.validate(request(selectedPly = 0, branch = branch)),
        )
        assertNull(
            GameShareSnapshotValidator.validate(request(selectedPly = 3)),
        )
    }

    @Test
    fun rejectsMissingOrUnknownSide() {
        assertNull(GameShareSnapshotValidator.validate(request(userSide = "")))
        assertNull(GameShareSnapshotValidator.validate(request(userSide = "black")))
        assertNull(GameShareSnapshotValidator.validate(request(winner = "draw")))
    }

    @Test
    fun rejectsMalformedSfenGameKeyAndOversizedInputs() {
        assertNull(
            GameShareSnapshotValidator.validate(
                request(initialSfen = "9/9/9/9/9/9/9/9/9 b - 1"),
            ),
        )
        assertNull(GameShareSnapshotValidator.validate(request(gameKey = "contains space")))
        assertNull(GameShareSnapshotValidator.validate(request(gameKey = "a".repeat(129))))
        assertNull(
            GameShareSnapshotValidator.validate(
                request(mainline = List(GameShareLimits.MAX_MAINLINE_MOVES + 1) { "7g7f" }),
            ),
        )
    }

    @Test
    fun rejectsPhysicallyImpossiblePieceInventories() {
        assertNull(
            GameShareSnapshotValidator.validate(
                request(
                    initialSfen = "4k4/9/9/9/9/9/9/9/GGGGGK3 b - 1",
                    mainline = emptyList(),
                    selectedPly = 0,
                ),
            ),
        )
        assertNull(
            GameShareSnapshotValidator.validate(
                request(
                    initialSfen = "4k4/9/9/9/9/9/9/9/GGGGK4 b G 1",
                    mainline = emptyList(),
                    selectedPly = 0,
                ),
            ),
        )
    }

    @Test
    fun countsPromotedBoardPiecesTowardTheSameLimitAsHands() {
        assertNull(
            GameShareSnapshotValidator.validate(
                request(
                    initialSfen = "4k4/9/9/9/9/9/9/9/+R+R3K3 b R 1",
                    mainline = emptyList(),
                    selectedPly = 0,
                ),
            ),
        )
    }

    @Test
    fun acceptsLegalCapturedPieceInHand() {
        val sfen = "lnsgkgsnl/1r5b1/ppppppppp/9/9/9/PPPPPPPPP/1B7/LNSGKGSNL b R 1"
        assertNotNull(
            GameShareSnapshotValidator.validate(
                request(initialSfen = sfen, mainline = emptyList(), selectedPly = 0),
            ),
        )
    }

    @Test
    fun validatesShareIdShape() {
        assertEquals(true, GameShareSnapshotValidator.isValidShareId("AbCdEfGhIjKlMnOpQrStUv"))
        assertEquals(false, GameShareSnapshotValidator.isValidShareId("short"))
        assertEquals(false, GameShareSnapshotValidator.isValidShareId("bad/id/with/slashes/123456"))
    }
}
