package dev.miyado.shogisupplement.ui

import androidx.compose.material3.Surface
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import dev.miyado.shogisupplement.db.GameAnalysisStatus
import dev.miyado.shogisupplement.db.GameRecord
import dev.miyado.shogisupplement.ui.report.PendingAnalysisScreen
import dev.miyado.shogisupplement.ui.theme.ShogiTheme
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
    application = android.app.Application::class,
)
class PendingAnalysisScreenScreenshotTest {

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
                    PendingAnalysisScreen(
                        game = GameRecord(
                            id = 1,
                            fileName = "2026-08-17 対局.kif",
                            contentHash = "pending",
                            moveCount = 83,
                            senteName = "miyado",
                            goteName = "相手",
                            analyzedAt = 1_780_000_000,
                            rating = 0,
                            coefVersion = "",
                            sourcePlace = "wars",
                            analysisStatus = GameAnalysisStatus.PENDING,
                        ),
                        onBack = {},
                        onAnalyze = {},
                    )
                }
            }
        }
    }
}
