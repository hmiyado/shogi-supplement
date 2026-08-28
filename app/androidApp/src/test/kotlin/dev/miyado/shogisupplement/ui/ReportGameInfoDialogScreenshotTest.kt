package dev.miyado.shogisupplement.ui

import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.miyado.shogisupplement.db.BlunderRecord
import dev.miyado.shogisupplement.db.GameRecord
import dev.miyado.shogisupplement.ui.report.ReportScreen
import dev.miyado.shogisupplement.ui.theme.ShogiTheme
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
class ReportGameInfoDialogScreenshotTest {

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
        sourcePlace = "wars",
    )

    private fun sampleBlunder() = BlunderRecord(
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
        note = "あなたの棋力帯(偏差値47-59): 約3局に1回",
        problemType = "手筋 (両取り・素抜き) の問題",
        priority = 2.9978349024480666,
        bestPv = "2f6f 2d2e",
        punishPv = "2d2e 2f2e",
        cpBefore = -350L,
    )

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun report_viewer_game_info_dialog() {
        composeRule.setContent {
            ShogiTheme {
                Surface {
                    ReportScreen(
                        game = sampleGame(),
                        reports = listOf(sampleBlunder()),
                        flip = false,
                        strengthDisplayText = "52 ±27",
                        onBack = {},
                        initialShowGameInfoDialog = true,
                    )
                }
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage(
            filePath = "src/test/snapshots/report_viewer_game_info_dialog.png",
            roborazziOptions = screenshotRoborazziOptions,
        )
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun report_viewer_game_info_dialog_quest_rating() {
        composeRule.setContent {
            ShogiTheme {
                Surface {
                    ReportScreen(
                        game = sampleGame().copy(
                            senteName = "相手A",
                            goteName = "player1",
                            sourcePlace = "shogi_quest",
                            senteRating = 464L,
                            goteRating = 800L,
                        ),
                        reports = listOf(sampleBlunder()),
                        flip = false,
                        strengthDisplayText = "52 ±27",
                        onBack = {},
                        initialShowGameInfoDialog = true,
                    )
                }
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage(
            filePath = "src/test/snapshots/report_viewer_game_info_dialog_quest_rating.png",
            roborazziOptions = screenshotRoborazziOptions,
        )
    }
}
