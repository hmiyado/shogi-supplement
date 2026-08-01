package dev.miyado.shogisupplement.ui

import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.db.GameRecord
import dev.miyado.shogisupplement.ui.report.ReportScreen
import dev.miyado.shogisupplement.ui.report.StudyOrigin
import dev.miyado.shogisupplement.ui.report.StudyState
import dev.miyado.shogisupplement.ui.theme.ShogiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 検討モードの出入りで罫線（HorizontalDivider・testTag="report_divider"）のY座標が
 * 動かないことを検証する（DESIGN.md No-jitter原則。単一ナビ行構成が守るべき不変条件）。
 *
 * VRT（スクリーンショット比較）ではなく、Semanticsツリーの実測座標を直接比較する
 * ことで、目視によるピクセル差の見落としを避ける。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    qualifiers = "w400dp-h800dp-xxhdpi",
)
class ReportScreenStudyLayoutTest {

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

    private fun dividerTop(): Float =
        composeRule.onNodeWithTag("report_divider").fetchSemanticsNode().boundsInRoot.top

    @Test
    fun studyModeDoesNotShiftDividerY() {
        // setContent は1回しか呼べない（AndroidComposeTestRule の制約）ため、
        // studyState を可変状態にして同じコンポジション内でトグルする。
        var studyState by mutableStateOf<StudyState?>(null)
        composeRule.setContent {
            ShogiTheme {
                Surface {
                    ReportScreen(
                        game = sampleGame(),
                        reports = emptyList(),
                        flip = false,
                        onBack = {},
                        studyState = studyState,
                    )
                }
            }
        }
        composeRule.waitForIdle()
        val normalTop = dividerTop()

        studyState = StudyState(
            baseSfen = ShogiBoard().toSfen(),
            moves = emptyList(),
            origin = StudyOrigin(label = "開始局面", userCp = null),
            originIsBestPv = false,
            originPlyIndex = 0,
            originSelectedIdx = null,
            originAbsolutePly = 0,
            flip = false,
        )
        composeRule.waitForIdle()
        val studyTop = dividerTop()

        assertEquals(
            "検討モードの出入りで罫線のY座標が動かないこと（No-jitter原則）",
            normalTop,
            studyTop,
            0.5f,
        )
    }
}
