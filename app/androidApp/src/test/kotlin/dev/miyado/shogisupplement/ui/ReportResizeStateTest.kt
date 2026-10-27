package dev.miyado.shogisupplement.ui

import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.miyado.shogisupplement.db.BlunderRecord
import dev.miyado.shogisupplement.db.GameRecord
import dev.miyado.shogisupplement.ui.common.LocalWindowWidthClass
import dev.miyado.shogisupplement.ui.common.WindowWidthClass
import dev.miyado.shogisupplement.ui.drill.DrillQuestionContent
import dev.miyado.shogisupplement.ui.drill.DrillUiState
import dev.miyado.shogisupplement.ui.report.ReportScreen
import dev.miyado.shogisupplement.ui.theme.ShogiTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1000dp-h900dp-mdpi", application = android.app.Application::class)
class ReportResizeStateTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun blunderScrollSurvivesRepeatedPaneChanges() {
        val widthClass = mutableStateOf(WindowWidthClass.COMPACT)
        val reports = (0 until 40).map { sampleBlunder().copy(id = it.toLong(), ply = it.toLong() + 1) }
        composeRule.setContent {
            ShogiTheme {
                CompositionLocalProvider(LocalWindowWidthClass provides widthClass.value) {
                    Surface {
                        ReportScreen(
                            game = sampleGame(), reports = reports, onBack = {},
                            initialBodyModeList = true,
                        )
                    }
                }
            }
        }
        val list = composeRule.onNode(hasScrollToIndexAction())
        list.performScrollToIndex(15)
        val before = list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue(before > 0f)
        repeat(2) {
            composeRule.runOnIdle { widthClass.value = WindowWidthClass.EXPANDED }
            composeRule.waitForIdle()
            assertEquals(before, list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value(), 0.01f)
            composeRule.runOnIdle { widthClass.value = WindowWidthClass.COMPACT }
            composeRule.waitForIdle()
            assertEquals(before, list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value(), 0.01f)
        }
    }

    @Test
    fun questionUsesNewStateAndCallbacksAfterPaneChanges() {
        val widthClass = mutableStateOf(WindowWidthClass.COMPACT)
        val enabled = mutableStateOf(false)
        var submitted = 0
        composeRule.setContent {
            val value = if (enabled.value) 2 else 1
            ShogiTheme {
                CompositionLocalProvider(LocalWindowWidthClass provides widthClass.value) {
                    Surface {
                        DrillQuestionContent(
                            state = DrillUiState.Question(
                                blunder = sampleBlunder(), sfenCurrent = SFEN,
                                moves = if (enabled.value) listOf("2f6f") else emptyList(),
                            ),
                            onSquareTapped = {}, onHandPieceTapped = {},
                            onPromoteDecision = {}, onSurrender = {}, onUndoMove = {},
                            onResetMoves = {}, onSubmitAnswer = { submitted = value },
                        )
                    }
                }
            }
        }
        composeRule.onNodeWithText("答える").assertIsNotEnabled()
        composeRule.runOnIdle {
            widthClass.value = WindowWidthClass.EXPANDED
            enabled.value = true
        }
        composeRule.onNodeWithText("答える").performClick()
        composeRule.runOnIdle { assertEquals(2, submitted) }
        composeRule.runOnIdle { widthClass.value = WindowWidthClass.COMPACT }
        composeRule.onNodeWithText("答える").performClick()
        composeRule.runOnIdle { assertEquals(2, submitted) }
    }

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
