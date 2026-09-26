package dev.miyado.shogisupplement.ui

import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
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
        onStudyAutoAnalyze: () -> Unit = {},
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
                        onStudyAutoAnalyze = onStudyAutoAnalyze,
                    )
                }
            }
        }
    }

    @Test
    fun failedAutomaticSaveOffersRetry() {
        var attempts = 0
        var study by mutableStateOf(StudyState(
            baseSfen = ShogiBoard().toSfen(), flip = false, originIsBestPv = false,
            originPlyIndex = 0, originSelectedIdx = null, originAbsolutePly = 0,
            origin = dev.miyado.shogisupplement.ui.report.StudyOrigin("開始", null),
            saveFailed = true,
        ))
        composeRule.setContent {
            ShogiTheme {
                ReportScreen(
                    game = sampleGame(), reports = emptyList(), flip = false, onBack = {},
                    studyState = study,
                    onSaveStudy = { callback ->
                        attempts++
                        study = study.copy(saveFailed = attempts < 2)
                        callback(attempts >= 2)
                    },
                )
            }
        }
        composeRule.onNodeWithText(AppStrings.STUDY_SAVE_FAILED).assertIsDisplayed()
        composeRule.onNodeWithText(AppStrings.GAME_RESTORE_RETRY_BUTTON).performClick()
        composeRule.runOnIdle { assertEquals(1, attempts) }
        composeRule.onNodeWithText(AppStrings.GAME_RESTORE_RETRY_BUTTON).performClick()
        composeRule.runOnIdle { assertEquals(2, attempts) }
        composeRule.onNodeWithText(AppStrings.STUDY_SAVE_FAILED).assertDoesNotExist()
        composeRule.onNodeWithText(AppStrings.STUDY_SAVE).assertDoesNotExist()
    }

    @Test
    fun studyArrowsNavigateDisplayedLineAndDisableAtBoundaries() {
        val line = listOf("7g7f", "3c3d")
        var state by mutableStateOf(StudyState(
            baseSfen = ShogiBoard().toSfen(),
            flip = false,
            originIsBestPv = false,
            originPlyIndex = 0,
            originSelectedIdx = null,
            displayLine = line,
            originAbsolutePly = 0,
            origin = dev.miyado.shogisupplement.ui.report.StudyOrigin("開始", null),
        ))
        composeRule.setContent {
            ShogiTheme {
                Surface {
                    ReportScreen(
                        game = sampleGame(),
                        reports = emptyList(),
                        flip = false,
                        onBack = {},
                        studyState = state,
                        onStudyChipTapped = { state = state.copy(moves = line.take(it)) },
                        onStudyStepBack = { state = state.copy(moves = state.moves.dropLast(1)) },
                    )
                }
            }
        }
        val back = composeRule.onNodeWithContentDescription("1手戻る")
        val next = composeRule.onNodeWithContentDescription("1手進む")
        back.assertIsNotEnabled()
        next.assertIsEnabled().performClick()
        composeRule.runOnIdle { assertEquals(line.take(1), state.moves) }
        back.assertIsEnabled()
        next.performClick()
        composeRule.runOnIdle { assertEquals(line, state.moves) }
        next.assertIsNotEnabled()
        back.performClick()
        next.assertIsEnabled().performClick()
        composeRule.runOnIdle { assertEquals(line, state.moves) }
        back.performClick()
        back.performClick()
        back.assertIsNotEnabled()
        next.assertIsEnabled()
    }

    @Test
    fun branchActionsRemainWithoutNotesOrBookmarksAtPhoneWidth() {
        var saved = false
        var deleted = false
        val study = StudyState(
            baseSfen = ShogiBoard().toSfen(), origin = dev.miyado.shogisupplement.ui.report.StudyOrigin("開始", null),
            evalState = dev.miyado.shogisupplement.ui.report.StudyEvalState.Value(
                dev.miyado.shogisupplement.blunder.PositionEvalDisplay.EvalLabel("+128", 1),
                candidates = listOf("3c3d" to "△３四歩", "8c8d" to "△８四歩", "4c4d" to "△４四歩").map {
                    dev.miyado.shogisupplement.ui.report.StudyCandidate(it.first, it.second,
                        dev.miyado.shogisupplement.blunder.PositionEvalDisplay.EvalLabel("+128", 1))
                },
            ),
            moves = listOf("7g7f"), displayLine = listOf("7g7f", "3c3d", "2g2f"), canDeleteBranch = true,
            originIsBestPv = false, originPlyIndex = 0, originSelectedIdx = null, originAbsolutePly = 0, flip = false,
        )
        composeRule.setContent {
            ShogiTheme {
                ReportScreen(
                    game = sampleGame(), reports = emptyList(), flip = false, onBack = {}, studyState = study,
                    onDeleteStudyBranch = { _, _, _ -> deleted = true; true },
                    onSaveStudy = { saved = true; it(true) },
                )
            }
        }
        composeRule.onRoot().captureRoboImage(filePath = "build/outputs/issue89-phone.png")
        composeRule.onNodeWithText(AppStrings.studyEvalPerspective(false)).assertIsDisplayed()
        composeRule.onNodeWithText(AppStrings.studyOriginLine("開始")).assertIsDisplayed()
        composeRule.onNodeWithText("メモ・しおり").assertDoesNotExist()
        composeRule.onNodeWithText("しおり").assertDoesNotExist()
        composeRule.onNodeWithText(AppStrings.STUDY_DELETE_BRANCH).assertDoesNotExist()
        composeRule.onNodeWithText(AppStrings.STUDY_SAVE).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(AppStrings.STUDY_ACTIONS).performClick()
        composeRule.onNodeWithText(AppStrings.STUDY_SAVE).assertDoesNotExist()
        assertEquals(false, saved)
        composeRule.onNodeWithText(AppStrings.STUDY_DELETE_BRANCH).assertIsDisplayed().performClick()
        composeRule.onNodeWithText(AppStrings.STUDY_DELETE_BRANCH_TITLE).assertIsDisplayed()
        assertEquals(false, deleted)
        composeRule.onNodeWithText(AppStrings.CANCEL).performClick()
        assertEquals(false, deleted)
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

    @Test
    fun selectingStudyTabStartsEvaluationAtCurrentPosition() {
        var studyState by mutableStateOf<StudyState?>(null)
        var analyzeCalls = 0
        setReportScreenContent(
            { studyState },
            { studyState = it },
            onStudyAutoAnalyze = { analyzeCalls++ },
        )

        composeRule.onNodeWithText("検討").performClick()
        composeRule.waitForIdle()

        assertNotNull("検討タブで検討状態が開始されること", studyState)
        assertEquals("検討タブを開いた時点で現在局面を評価すること", 1, analyzeCalls)
    }
}
