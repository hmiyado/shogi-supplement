package dev.miyado.shogisupplement.ui

import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import dev.miyado.shogisupplement.db.GameAnalysisStatus
import dev.miyado.shogisupplement.db.GameRecord
import dev.miyado.shogisupplement.ui.report.ReportScreen
import dev.miyado.shogisupplement.ui.theme.ShogiTheme
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    qualifiers = "w400dp-h800dp-xxhdpi",
    application = android.app.Application::class,
)
class PendingAnalysisScreenScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun pendingGame() = GameRecord(
        id = 1,
        fileName = "2026-08-17 対局.kif",
        contentHash = "pending",
        moveCount = 4,
        senteName = "miyado",
        goteName = "相手",
        analyzedAt = 1_780_000_000,
        rating = 0,
        coefVersion = "",
        sourcePlace = "wars",
        movesUsi = listOf("7g7f", "3c3d", "2g2f", "8c8d"),
        analysisStatus = GameAnalysisStatus.PENDING,
    )

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun pendingAnalysis() {
        captureRoboImage(
            filePath = "src/test/snapshots/pending_analysis.png",
            roborazziOptions = RoborazziOptions(
                recordOptions = RoborazziOptions.RecordOptions(resizeScale = 0.5),
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f),
            ),
        ) {
            ShogiTheme {
                Surface {
                    ReportScreen(
                        game = pendingGame(),
                        reports = emptyList(),
                        analysisPending = true,
                        onBack = {},
                        onAnalyze = {},
                    )
                }
            }
        }
    }

    @Test
    fun pendingAnalysisInteraction() {
        var analyzed = false
        composeRule.setContent {
            ShogiTheme {
                ReportScreen(
                    game = pendingGame(),
                    reports = emptyList(),
                    analysisPending = true,
                    onBack = {},
                    onAnalyze = { analyzed = true },
                )
            }
        }

        composeRule.onNodeWithContentDescription("1手進む").assertIsEnabled()
        composeRule.onNodeWithTag("eval_graph_card").assertIsNotEnabled()
        composeRule.onNodeWithTag("pending_analysis_button").performClick()
        assertTrue(analyzed)
    }
}
