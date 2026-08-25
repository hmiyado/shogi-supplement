package dev.miyado.shogisupplement.ui

import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import dev.miyado.shogisupplement.ui.common.LocalBoardBaseHeight
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import dev.miyado.shogisupplement.db.BlunderRecord
import dev.miyado.shogisupplement.db.GameRecord
import dev.miyado.shogisupplement.db.PositionEvalRow
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.report.ReportScreen
import dev.miyado.shogisupplement.ui.theme.ShogiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 縦に詰まった端末でレポートのサマリーが潰れないことを確かめる。
 *
 * 実機はsafe areaを引くと本文が650dp程度になる。この高さでは、Columnが最後の子である
 * 「悪手一覧を見る」を押し潰して26dpまで縮めていた。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h650dp-xxhdpi", application = android.app.Application::class)
class ReportSummaryCompactHeightTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun game() = GameRecord(
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

    private fun blunder() = BlunderRecord(
        id = 1L,
        gameId = 1L,
        ply = 41L,
        side = "sente",
        moveUsi = "B*3d",
        bestUsi = "2f6f",
        lossWp = 0.225,
        sfenBefore = "ln2g3l/2ks1s3/1pppppnr1/p7p/5gpp1/P1P4RP/1PSPPSP2/1KGG5/LN5NL b BPbp 41",
        category = "駒損（タクティクス）",
        diffMaterial = -11L,
        punishChecks = 0L,
        tookMovedPiece = false,
        missedMateIn = null,
        verdict = "○ 出題対象",
        note = "note",
        problemType = "手筋",
        priority = 2.99,
        bestPv = "2f6f 8c8d",
        punishPv = "2d2e 2f2e",
        cpBefore = -350L,
    )

    private var rootHeight by mutableStateOf<Dp?>(null)

    /** iOSのルートと同じく、safe areaを引いた高さを基準として与える。 */
    private fun setContentWithRootHeight(initial: Dp?) {
        rootHeight = initial
        composeRule.setContent {
            ShogiTheme {
                Surface {
                    CompositionLocalProvider(LocalBoardBaseHeight provides rootHeight) {
                        ReportScreen(
                        game = game(),
                        reports = listOf(blunder()),
                        flip = false,
                        strengthDisplayText = "1682 ±120",
                        matchRateDisplayText = "68.2%",
                        blunderRateDisplayText = "4.1%",
                        positionEvals = (0..40).map {
                            PositionEvalRow(ply = it, scoreCp = it * 30 - 200, mateIn = null, bestUsi = "7g7f")
                        },
                            onBack = {},
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun boardCellWidth(): Float =
        composeRule.onNodeWithTag("board_sq_5_5").fetchSemanticsNode().boundsInRoot.width

    /**
     * safe areaを引いた高さを基準として与えたら、盤はその高さから組む。
     *
     * ウィンドウ全体を基準にしていたときは、iOSだけ盤がsafe areaのぶん大きくなっていた。
     */
    @Test
    fun ルートの基準高さを与えると盤はその高さから決まる() {
        setContentWithRootHeight(650.dp)
        val full = boardCellWidth()

        rootHeight = 650.dp - 93.dp
        composeRule.waitForIdle()
        val withSafeArea = boardCellWidth()

        val expectedRatio = (650f - 93f) / 650f
        val ratio = withSafeArea / full
        assert(ratio in (expectedRatio - 0.05f)..(expectedRatio + 0.05f)) {
            "safe areaを引いた基準が盤に効いていない: 比 $ratio 期待 $expectedRatio"
        }
    }

    @Test
    fun 縦に詰まった端末でも悪手一覧ボタンが潰れない() {
        setContentWithRootHeight(null)

        val bounds = composeRule.onNodeWithText(AppStrings.VIEW_BLUNDER_LIST).getUnclippedBoundsInRoot()
        val height = bounds.bottom - bounds.top
        assert(height >= 40.dp) { "悪手一覧ボタンが潰れている: $height" }
    }
}
