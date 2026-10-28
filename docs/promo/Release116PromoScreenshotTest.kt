package dev.miyado.shogisupplement.ui

import dev.miyado.shogisupplement.ui.theme.ShogiTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import dev.miyado.shogisupplement.blunder.PositionEvalDisplay
import dev.miyado.shogisupplement.board.ShogiSquare
import dev.miyado.shogisupplement.db.BlunderRecord
import dev.miyado.shogisupplement.db.GameRecord
import dev.miyado.shogisupplement.db.PositionEvalRow
import dev.miyado.shogisupplement.ui.report.MoveListSheet
import dev.miyado.shogisupplement.ui.report.ReportScreen
import dev.miyado.shogisupplement.ui.report.StudyEvalState
import dev.miyado.shogisupplement.ui.report.StudyState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 棋譜ビューア型レポート画面の状態別 VRT。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    qualifiers = "w400dp-h800dp-xxhdpi",
    application = android.app.Application::class,
)
class Release116PromoScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleGame() = GameRecord(
        id = 1L,
        fileName = "序盤の検討.kif",
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

    @Test
    fun report_viewer_study_branch() {
        composeRule.setContent {
            ShogiTheme {
                Surface {
                    ReportScreen(
                        game = sampleGame(),
                        reports = emptyList(),
                        flip = false,
                        strengthDisplayText = "52 ±27",
                        onBack = {},
                        studyState = dev.miyado.shogisupplement.ui.report.StudyState(
                            baseSfen = dev.miyado.shogisupplement.board.ShogiBoard().toSfen(),
                            moves = listOf("7g7f", "3c3d"),
                            displayLine = listOf("7g7f", "3c3d"),
                            origin = dev.miyado.shogisupplement.ui.report.StudyOrigin(
                                label = "開始局面",
                                userCp = null,
                            ),
                            originIsBestPv = true,
                            originPlyIndex = 0,
                            originSelectedIdx = 0,
                            originAbsolutePly = 0,
                            flip = false,
                            branchFlags = listOf(false, true),
                            moveOptions = listOf(emptyList(), listOf(
                                dev.miyado.shogisupplement.ui.report.StudyBranchOption("3c3d", dev.miyado.shogisupplement.ui.report.StudyEvalState.None, true, 1),
                                dev.miyado.shogisupplement.ui.report.StudyBranchOption("8c8d", dev.miyado.shogisupplement.ui.report.StudyEvalState.None, false, 2),
                            )),
                            evalState = dev.miyado.shogisupplement.ui.report.StudyEvalState.None,
                        ),
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage(
            filePath = "../../docs/promo/release-1.16-study.png",
            roborazziOptions = screenshotRoborazziOptions,
        )
    }
}
