package dev.miyado.shogisupplement.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import dev.miyado.shogisupplement.db.BlunderRecord
import dev.miyado.shogisupplement.db.GameRecord
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.common.ShogiThinTopBar
import dev.miyado.shogisupplement.ui.drill.DrillQuestionContent
import dev.miyado.shogisupplement.ui.drill.DrillUiState
import dev.miyado.shogisupplement.ui.report.ReportScreen
import dev.miyado.shogisupplement.ui.theme.ShogiTheme
import org.junit.Assert.assertEquals
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
class DrillBoardSizeParityTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** 次の一手画面とレポート画面で、同じ大きさのマス（＝同じ大きさの駒）が描かれる。 */
    @Test
    fun drillBoardCellMatchesReportBoardCell() {
        var showDrill by mutableStateOf(false)
        composeRule.setContent {
            ShogiTheme {
                Surface {
                    if (showDrill) {
                        DrillHostLikeContent()
                    } else {
                        ReportScreen(game = sampleGame(), reports = emptyList(), onBack = {})
                    }
                }
            }
        }
        composeRule.waitForIdle()
        val reportCellWidth = boardCellWidth()

        showDrill = true
        composeRule.waitForIdle()

        assertEquals(reportCellWidth, boardCellWidth(), 0.5f)
    }

    /** 出題画面をScaffoldと薄いトップバーの下に置く、ホストと同じ入れ子。 */
    @Composable
    private fun DrillHostLikeContent() {
        Scaffold { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                ShogiThinTopBar(title = AppStrings.DRILL_TITLE, onBack = {})
                DrillQuestionContent(
                    state = DrillUiState.Question(
                        blunder = sampleBlunder(),
                        sfenCurrent = SFEN,
                        attemptCount = 1,
                        totalCandidates = 3,
                    ),
                    onSquareTapped = {},
                    onHandPieceTapped = {},
                    onPromoteDecision = {},
                    onSurrender = {},
                )
            }
        }
    }

    private fun boardCellWidth(): Float =
        composeRule.onNodeWithTag("board_sq_5_5").fetchSemanticsNode().boundsInRoot.width

    private fun sampleGame() = GameRecord(
        id = 1L,
        fileName = "game1.kif",
        contentHash = "hash1",
        moveCount = 4L,
        senteName = "先手",
        goteName = "後手",
        analyzedAt = 1_780_000_000L,
        rating = 1750L,
        coefVersion = "hao_v1",
        movesUsi = listOf("7g7f", "3c3d", "2g2f", "8c8d"),
        userSide = "sente",
    )

    private fun sampleBlunder() = BlunderRecord(
        id = 1L,
        gameId = 1L,
        ply = 41L,
        side = "sente",
        moveUsi = "B*3d",
        bestUsi = "2f6f",
        lossWp = 0.225,
        sfenBefore = SFEN,
        category = "駒損（タクティクス）",
        diffMaterial = -11L,
        punishChecks = 0L,
        tookMovedPiece = false,
        missedMateIn = null,
        verdict = "○ 出題対象",
        note = "",
        problemType = "手筋 (両取り・素抜き) の問題",
        priority = 3.0,
        bestPv = "2f6f 2d2e",
        punishPv = "2d2e 2f2e",
    )

    private companion object {
        const val SFEN = "ln2g3l/2ks1s3/1pppppnr1/p7p/5gpp1/P1P4RP/1PSPPSP2/1KGG5/LN5NL b BPbp 41"
    }
}
