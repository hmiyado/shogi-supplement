package dev.miyado.shogisupplement.ui

import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.miyado.shogisupplement.board.PieceType
import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.board.ShogiSquare
import dev.miyado.shogisupplement.db.GameRecord
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.report.ReportScreen
import dev.miyado.shogisupplement.ui.report.StudyState
import dev.miyado.shogisupplement.ui.report.buildInitialStudyState
import dev.miyado.shogisupplement.ui.theme.ShogiTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    qualifiers = "w400dp-h800dp-xxhdpi",
)
class ReportScreenStudyInteractionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleGame() = GameRecord(
        id = 1L,
        fileName = "miyado_game1.kif",
        contentHash = "hash1",
        moveCount = 74L,
        senteName = "miyado",
        goteName = "相手",
        analyzedAt = 1_780_000_000L,
        rating = 1750L,
        coefVersion = "hao_v1",
        movesUsi = listOf("7g7f", "3c3d", "2g2f", "8c8d"),
        userSide = "sente",
    )

    private fun sampleGameWithHandPiece() = sampleGame().copy(
        moveCount = 4L,
        movesUsi = listOf("7g7f", "3c3d", "8h2b+", "9c9d"),
    )

    private fun setReportScreenContent(
        getState: () -> StudyState?,
        setState: (StudyState?) -> Unit,
        game: GameRecord = sampleGame(),
        initialPlyIndex: Int = 0,
    ) {
        composeRule.setContent {
            ShogiTheme {
                Surface {
                    ReportScreen(
                        game = game,
                        reports = emptyList(),
                        flip = false,
                        onBack = {},
                        studyState = getState(),
                        initialPlyIndex = initialPlyIndex,
                        onStartStudy = { baseSfen, flip, bestPv, ply, idx, absPly, origin, sq, pieceType ->
                            val board = ShogiBoard.fromSfen(baseSfen)
                            setState(
                                buildInitialStudyState(
                                    baseSfen = baseSfen,
                                    flip = flip,
                                    originIsBestPv = bestPv,
                                    originPlyIndex = ply,
                                    originSelectedIdx = idx,
                                    originAbsolutePly = absPly,
                                    origin = origin,
                                    tappedSquare = sq,
                                    tappedHandPieceType = pieceType,
                                    board = board,
                                ),
                            )
                        },
                    )
                }
            }
        }
    }

    @Test
    fun tappingOwnPieceStartsStudyWithImmediateSelection() {
        var studyState by mutableStateOf<StudyState?>(null)
        setReportScreenContent({ studyState }, { studyState = it })

        composeRule.onNodeWithTag("board_sq_7_7").performClick()
        composeRule.waitForIdle()

        val s = studyState
        assertNotNull("駒タップで検討モードが開始されること", s)
        assertEquals("開始タップの駒が即選択されること", ShogiSquare(7, 7), s!!.selectedFrom)
        assertTrue(
            "選択駒の合法手（７六）が legalDestinations に入ること",
            ShogiSquare(7, 6) in s.legalDestinations,
        )
        composeRule.onNodeWithText(AppStrings.STUDY_START_POSITION)
            .assertIsDisplayed()
    }

    @Test
    fun tappingOpponentPieceStartsStudyWithTurnHint() {
        var studyState by mutableStateOf<StudyState?>(null)
        setReportScreenContent({ studyState }, { studyState = it })

        composeRule.onNodeWithTag("board_sq_3_3").performClick()
        composeRule.waitForIdle()

        val s = studyState
        assertNotNull("駒タップで検討モードは開始されること", s)
        assertNull("手番でない駒は選択されないこと", s!!.selectedFrom)
        assertTrue("legalDestinations は空のこと", s.legalDestinations.isEmpty())
        assertTrue("手番ヒントのフラグが立つこと", s.showTurnHint)
        composeRule.onNodeWithText(AppStrings.studyTurnHint(senteToMove = true), substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun tappingHandPieceStartsStudyWithDropSelected() {
        var studyState by mutableStateOf<StudyState?>(null)
        setReportScreenContent(
            { studyState },
            { studyState = it },
            game = sampleGameWithHandPiece(),
            initialPlyIndex = 4,
        )

        composeRule.onNodeWithTag("hand_piece_sente_B").performClick()
        composeRule.waitForIdle()

        val s = studyState
        assertNotNull("持ち駒タップで検討モードが開始されること", s)
        assertNull("盤上マスの選択(selectedFrom)は無いこと", s!!.selectedFrom)
        assertEquals(
            "タップした持ち駒種別が打ちの選択状態になること",
            PieceType.BISHOP,
            s.selectedDropType,
        )
        assertTrue(
            "選択した持ち駒の合法な打ち先が legalDestinations に入ること",
            s.legalDestinations.isNotEmpty(),
        )
    }
}
