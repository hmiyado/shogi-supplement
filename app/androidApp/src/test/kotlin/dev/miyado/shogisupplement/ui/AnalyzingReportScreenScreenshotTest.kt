package dev.miyado.shogisupplement.ui

import androidx.compose.material3.Surface
import com.github.takahirom.roborazzi.captureRoboImage
import dev.miyado.shogisupplement.blunder.Score
import dev.miyado.shogisupplement.engine.PvInfo
import dev.miyado.shogisupplement.pipeline.ProgressiveReportState
import dev.miyado.shogisupplement.ui.report.AnalyzingReportScreen
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
class AnalyzingReportScreenScreenshotTest {

    private val sampleMoves = listOf(
        "7g7f", "3c3d", "2g2f", "8c8d", "2f2e", "8d8e", "2e2d", "2c2d", "2h2d", "4a3b",
    )

    private fun pv(cp: Int): List<PvInfo> =
        listOf(PvInfo(multipv = 1, score = Score.Cp(cp), pv = emptyList(), nodes = 0L))

    private fun progressiveState(confirmedThrough: Int): ProgressiveReportState {
        val cps = listOf(40, -20, 100, 400, -50, 120, -80, 60, -30, 10, 20)
        var state = ProgressiveReportState.initial(sampleMoves)
        for (ply in 0 until confirmedThrough) {
            state = state.withPosition(ply, pv(cps[ply]))
        }
        return state
    }

    @Test
    fun analyzing_report_30pct() {
        captureRoboImage(
            filePath = "src/test/snapshots/analyzing_report_30pct.png",
            roborazziOptions = screenshotRoborazziOptions,
        ) {
            ShogiTheme {
                Surface {
                    AnalyzingReportScreen(
                        titleHint = "miyado_game1.kif",
                        moves = sampleMoves,
                        userSide = "sente",
                        progressive = progressiveState(confirmedThrough = 3),
                        onBack = {},
                    )
                }
            }
        }
    }

    @Test
    fun analyzing_report_80pct() {
        captureRoboImage(
            filePath = "src/test/snapshots/analyzing_report_80pct.png",
            roborazziOptions = screenshotRoborazziOptions,
        ) {
            ShogiTheme {
                Surface {
                    AnalyzingReportScreen(
                        titleHint = "miyado_game1.kif",
                        moves = sampleMoves,
                        userSide = "sente",
                        progressive = progressiveState(confirmedThrough = 9),
                        onBack = {},
                    )
                }
            }
        }
    }
}
