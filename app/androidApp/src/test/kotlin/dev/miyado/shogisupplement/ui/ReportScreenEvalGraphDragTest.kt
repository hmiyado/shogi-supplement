package dev.miyado.shogisupplement.ui

import androidx.compose.material3.Surface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import dev.miyado.shogisupplement.db.BlunderRecord
import dev.miyado.shogisupplement.db.GameRecord
import dev.miyado.shogisupplement.db.PositionEvalRow
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.report.ReportScreen
import dev.miyado.shogisupplement.ui.theme.ShogiTheme
import org.junit.Rule
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.junit.runner.RunWith

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    qualifiers = "w400dp-h800dp-xxhdpi",
)
class ReportScreenEvalGraphDragTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleGame() = GameRecord(
        id = 1L,
        fileName = "miyado_game1.kif",
        contentHash = "hash1",
        moveCount = 4L,
        senteName = "miyado",
        goteName = "相手",
        analyzedAt = 1_780_000_000L,
        rating = 1750L,
        coefVersion = "hao_v1",
        movesUsi = listOf("7g7f", "3c3d", "2g2f", "8c8d"),
        userSide = "sente",
    )

    private fun samplePositionEvals() = listOf(
        PositionEvalRow(ply = 0, scoreCp = 50, mateIn = null),
        PositionEvalRow(ply = 1, scoreCp = -30, mateIn = null),
        PositionEvalRow(ply = 2, scoreCp = -400, mateIn = null),
        PositionEvalRow(ply = 3, scoreCp = -350, mateIn = null),
        PositionEvalRow(ply = 4, scoreCp = -300, mateIn = null),
    )

    private fun sampleBlunder(ply: Long) = BlunderRecord(
        id = 1L,
        gameId = 1L,
        ply = ply,
        side = "gote",
        moveUsi = "2g2f",
        bestUsi = "3c3d",
        lossWp = 0.2,
        sfenBefore = "lnsgkgsnl/1r5b1/ppppppppp/9/9/2P6/PP1PPPPPP/1B5R1/LNSGKGSNL w - 2",
        category = "駒損（タクティクス）",
        diffMaterial = -5L,
        punishChecks = 0L,
        tookMovedPiece = false,
        missedMateIn = null,
        verdict = "○ 出題対象",
        note = "テスト用note",
        problemType = "テスト用problem",
        priority = 1.0,
    )

    @Test
    fun draggingGraphMovesCurrentPlyContinuously() {
        composeRule.setContent {
            ShogiTheme {
                Surface {
                    ReportScreen(
                        game = sampleGame(),
                        reports = emptyList(),
                        flip = false,
                        positionEvals = samplePositionEvals(),
                        onBack = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText(AppStrings.VIEWER_START_POSITION, substring = true).assertIsDisplayed()

        composeRule.onNodeWithTag("eval_graph_canvas").performTouchInput {
            swipe(start = Offset(1f, height / 2f), end = Offset(width - 1f, height / 2f), durationMillis = 200)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(AppStrings.VIEWER_START_POSITION, substring = true).assertDoesNotExist()

        composeRule.onNodeWithTag("eval_graph_canvas").performTouchInput {
            swipe(start = Offset(width - 1f, height / 2f), end = Offset(1f, height / 2f), durationMillis = 200)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(AppStrings.VIEWER_START_POSITION, substring = true).assertIsDisplayed()
    }

    @Test
    fun draggingThroughBlunderMarkerDoesNotSwitchToList() {
        val blunder = sampleBlunder(ply = 2L)
        composeRule.setContent {
            ShogiTheme {
                Surface {
                    ReportScreen(
                        game = sampleGame(),
                        reports = listOf(blunder),
                        flip = false,
                        positionEvals = samplePositionEvals(),
                        onBack = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText(AppStrings.VIEW_BLUNDER_LIST).assertIsDisplayed()

        composeRule.onNodeWithTag("eval_graph_canvas").performTouchInput {
            swipe(start = Offset(1f, height / 2f), end = Offset(width - 1f, height / 2f), durationMillis = 200)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(AppStrings.VIEW_BLUNDER_LIST).assertIsDisplayed()
        composeRule.onNodeWithText(AppStrings.BACK_TO_SUMMARY).assertDoesNotExist()
    }

    @Test
    fun tappingBlunderMarkerStillSwitchesToList() {
        val blunder = sampleBlunder(ply = 2L)
        composeRule.setContent {
            ShogiTheme {
                Surface {
                    ReportScreen(
                        game = sampleGame(),
                        reports = listOf(blunder),
                        flip = false,
                        positionEvals = samplePositionEvals(),
                        onBack = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText(AppStrings.VIEW_BLUNDER_LIST).assertIsDisplayed()

        composeRule.onNodeWithTag("eval_graph_canvas").performTouchInput {
            click(Offset(width / 2f, height / 2f))
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(AppStrings.BACK_TO_SUMMARY).assertIsDisplayed()
    }
}
