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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import dev.miyado.shogisupplement.board.PieceType
import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.board.ShogiSquare
import dev.miyado.shogisupplement.db.GameRecord
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
    fun inlineBranchesUseAbsolutePlyAndKeepBoundsWhenEvaluationArrives() {
        val options = listOf(
            dev.miyado.shogisupplement.ui.report.StudyBranchOption("7g7f", dev.miyado.shogisupplement.ui.report.StudyEvalState.None, true, 1),
            dev.miyado.shogisupplement.ui.report.StudyBranchOption("2g2f", dev.miyado.shogisupplement.ui.report.StudyEvalState.None, false, 2),
        )
        var study by mutableStateOf(StudyState(
            baseSfen = ShogiBoard().toSfen(), origin = dev.miyado.shogisupplement.ui.report.StudyOrigin("24手目", null),
            originIsBestPv = false, originPlyIndex = 24, originSelectedIdx = null, originAbsolutePly = 24, flip = false,
            moves = listOf("7g7f"), displayLine = listOf("7g7f", "3c3d"), moveOptions = listOf(options),
        ))
        var selected: Pair<Int, Long>? = null
        composeRule.setContent {
            ShogiTheme {
                ReportScreen(game = sampleGame(), reports = emptyList(), flip = false, onBack = {}, studyState = study,
                    onStudyBranchOptionSelected = { depth, id -> selected = depth to id })
            }
        }
        composeRule.onNodeWithText("25", useUnmergedTree = true).assertIsDisplayed()
        val first = composeRule.onNodeWithTag("study_move_option_25_1")
        val row = composeRule.onNodeWithTag("study_move_row_26")
        val before = first.fetchSemanticsNode().boundsInRoot
        val nextTop = row.fetchSemanticsNode().boundsInRoot.top
        composeRule.runOnIdle {
            study = study.copy(moveOptions = listOf(options.map { it.copy(evalState =
                dev.miyado.shogisupplement.ui.report.StudyEvalState.Value(
                    dev.miyado.shogisupplement.blunder.PositionEvalDisplay.EvalLabel("+9999", 1))) }))
        }
        composeRule.waitForIdle()
        assertEquals(before, first.fetchSemanticsNode().boundsInRoot)
        assertEquals(nextTop, row.fetchSemanticsNode().boundsInRoot.top)
        composeRule.onRoot().captureRoboImage(filePath = "src/test/snapshots/issue110-inline-branches.png")
        composeRule.onNodeWithTag("study_move_option_25_2").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(0 to 2L, selected) }
        composeRule.onNodeWithText("1. ▲７六歩").assertDoesNotExist()
    }

    @Test
    fun navigatingLongLineKeepsCurrentRowVisible() {
        val line = listOf("7g7f", "3c3d", "2g2f", "4c4d", "3i4h", "3a4b", "5g5f", "5c5d",
            "4i5h", "4b4c", "5i6h", "8b3b")
        var study by mutableStateOf(StudyState(
            baseSfen = ShogiBoard().toSfen(), origin = dev.miyado.shogisupplement.ui.report.StudyOrigin("開始", null),
            originIsBestPv = false, originPlyIndex = 0, originSelectedIdx = null, originAbsolutePly = 0, flip = false,
            moves = line, displayLine = line,
        ))
        composeRule.setContent {
            ShogiTheme { ReportScreen(game = sampleGame(), reports = emptyList(), flip = false, onBack = {}, studyState = study) }
        }
        composeRule.onNodeWithTag("study_move_row_12").assertIsDisplayed()
        composeRule.runOnIdle { study = study.copy(moves = emptyList()) }
        composeRule.onNodeWithTag("study_move_row_1").assertIsDisplayed()
        composeRule.runOnIdle { study = study.copy(moves = line) }
        composeRule.onNodeWithTag("study_move_row_12").assertIsDisplayed()
        composeRule.runOnIdle { study = study.copy(moves = line.take(1)) }
        composeRule.onNodeWithTag("study_move_row_1").assertIsDisplayed()
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
        composeRule.onNodeWithText("保存できませんでした。編集内容は画面に残っています。").assertIsDisplayed()
        composeRule.onNodeWithText("再試行").performClick()
        composeRule.runOnIdle { assertEquals(1, attempts) }
        composeRule.onNodeWithText("再試行").performClick()
        composeRule.runOnIdle { assertEquals(2, attempts) }
        composeRule.onNodeWithText("保存できませんでした。編集内容は画面に残っています。").assertDoesNotExist()
        composeRule.onNodeWithText("検討を端末に保存").assertDoesNotExist()
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
        composeRule.onRoot().captureRoboImage(filePath = "src/test/snapshots/issue89-phone.png")
        composeRule.onNodeWithText("先手の評価値").assertIsDisplayed()
        composeRule.onNodeWithText("開始から分岐").assertIsDisplayed()
        composeRule.onNodeWithText("メモ・しおり").assertDoesNotExist()
        composeRule.onNodeWithText("しおり").assertDoesNotExist()
        composeRule.onNodeWithText("分岐を削除").assertDoesNotExist()
        composeRule.onNodeWithText("検討を端末に保存").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("検討の操作").performClick()
        composeRule.onNodeWithText("検討を端末に保存").assertDoesNotExist()
        assertEquals(false, saved)
        composeRule.onNodeWithText("分岐を削除").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("この分岐を削除しますか？").assertIsDisplayed()
        assertEquals(false, deleted)
        composeRule.onNodeWithText("キャンセル").performClick()
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
        composeRule.onNodeWithText("検討開始局面")
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
        composeRule.onNodeWithText("▲番です", substring = true)
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
