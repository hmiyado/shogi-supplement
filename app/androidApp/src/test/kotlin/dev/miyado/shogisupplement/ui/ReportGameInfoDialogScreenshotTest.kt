package dev.miyado.shogisupplement.ui

import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
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

/**
 * レポート画面の対局情報ダイアログ（Info アイコン→「この棋譜について」）の VRT。
 * AlertDialog は別ウィンドウに描画されるため、compose 単体キャプチャではなく
 * captureScreenRoboImage（画面全体撮影）を使う（AccountDeleteDialogScreenshotTest と同じ規約）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    qualifiers = "w400dp-h800dp-xxhdpi",
)
class ReportGameInfoDialogScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @OptIn(ExperimentalRoborazziApi::class)
    private val roborazziOptions = RoborazziOptions(
        recordOptions = RoborazziOptions.RecordOptions(resizeScale = 0.5),
        compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f),
    )

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
        sourcePlace = "将棋ウォーズ",
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

    /** 対局情報ダイアログ表示状態（ファイル名・先手/後手名・閉じるのみ）。 */
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
            roborazziOptions = roborazziOptions,
        )
    }
}
