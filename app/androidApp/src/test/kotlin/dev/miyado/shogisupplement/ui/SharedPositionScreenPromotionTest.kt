package dev.miyado.shogisupplement.ui

import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.miyado.shogisupplement.board.PieceType
import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.board.ShogiSquare
import dev.miyado.shogisupplement.engine.PvInfo
import dev.miyado.shogisupplement.engine.StudyEngine
import dev.miyado.shogisupplement.ui.report.SharedPositionScreen
import dev.miyado.shogisupplement.ui.report.StudyController
import dev.miyado.shogisupplement.ui.report.StudyOrigin
import dev.miyado.shogisupplement.ui.report.computeSfenAtStep
import dev.miyado.shogisupplement.ui.theme.ShogiTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
class SharedPositionScreenPromotionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun sharedReceiverPromotesAndUnpromotesOptionalPawnMove() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val controller = StudyController(
            scope = scope,
            studyEngineFactory = { NoEvalStudyEngine },
            evalDisplayProvider = { "cp" },
        )
        controller.startStudy(
            baseSfen = OPTIONAL_PAWN_PROMOTION_SFEN,
            flip = false,
            originIsBestPv = false,
            originPlyIndex = 0,
            originSelectedIdx = null,
            originAbsolutePly = 0,
            origin = StudyOrigin("共有局面", null),
        )
        composeRule.setContent {
            ShogiTheme {
                Surface {
                    val state by controller.studyState.collectAsState()
                    state?.let {
                        SharedPositionScreen(
                            studyState = it,
                            onBack = {},
                            onStudySquareTapped = controller::onStudySquareTapped,
                            onStudyHandPieceTapped = controller::onStudyHandPieceTapped,
                            onStudyStepBack = controller::studyStepBack,
                            onStudyStepForward = {},
                            onStudyChipTapped = controller::onChipTapped,
                            onStudyBranchChipTapped = controller::onBranchChipTapped,
                            onStudyBranchPopupDismiss = controller::onBranchPopupDismiss,
                            onStudyBranchOptionSelected = controller::onBranchNodeSelected,
                            onStudyAnalyze = controller::analyzeCurrentPosition,
                            onStudyCandidateSelected = controller::onCandidateSelected,
                            onStudyPromoteDecision = controller::onStudyPromoteDecision,
                        )
                    }
                }
            }
        }

        composeRule.runOnIdle {
            controller.onStudySquareTapped(ShogiSquare(5, 4))
            controller.onStudySquareTapped(ShogiSquare(5, 3))
        }
        composeRule.onNodeWithText("成りますか？").assertIsDisplayed()
        composeRule.onNodeWithText("成る").performClick()
        composeRule.runOnIdle {
            val state = controller.studyState.value!!
            assertEquals(listOf("5d5c+"), state.moves)
            assertFalse(state.showPromoteDialog)
            assertNull(state.pendingPromoteMove)
            val board = ShogiBoard.fromSfen(computeSfenAtStep(state.baseSfen, state.moves, state.moves.size))
            assertEquals(PieceType.PROM_PAWN, board.pieceAt(ShogiSquare(5, 3))?.type)
        }
        composeRule.onNodeWithText("成りますか？").assertDoesNotExist()

        composeRule.runOnIdle {
            controller.studyStepBack()
            controller.onStudySquareTapped(ShogiSquare(5, 4))
            controller.onStudySquareTapped(ShogiSquare(5, 3))
        }
        composeRule.onNodeWithText("成りますか？").assertIsDisplayed()
        composeRule.onNodeWithText("成らない").performClick()
        composeRule.runOnIdle {
            val state = controller.studyState.value!!
            assertEquals(listOf("5d5c"), state.moves)
            assertFalse(state.showPromoteDialog)
            assertNull(state.pendingPromoteMove)
            val board = ShogiBoard.fromSfen(computeSfenAtStep(state.baseSfen, state.moves, state.moves.size))
            assertEquals(PieceType.PAWN, board.pieceAt(ShogiSquare(5, 3))?.type)
        }
        composeRule.onNodeWithText("成りますか？").assertDoesNotExist()

        controller.dispose()
        scope.cancel()
    }

    private object NoEvalStudyEngine : StudyEngine {
        override suspend fun analyzeSfen(
            sfen: String,
            additionalMoves: List<String>,
            nodes: Int,
            multiPv: Int,
        ): List<PvInfo> = emptyList()

        override fun quit() = Unit
    }

    private companion object {
        const val OPTIONAL_PAWN_PROMOTION_SFEN = "4k4/9/9/4P4/9/9/9/9/4K4 b - 1"
    }
}
