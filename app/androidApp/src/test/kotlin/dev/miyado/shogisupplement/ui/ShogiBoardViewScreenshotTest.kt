package dev.miyado.shogisupplement.ui

import dev.miyado.shogisupplement.ui.theme.ShogiTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.github.takahirom.roborazzi.captureRoboImage
import dev.miyado.shogisupplement.db.BlunderRecord
import dev.miyado.shogisupplement.ui.common.ShogiBoardView
import dev.miyado.shogisupplement.ui.report.BlunderCard
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
class ShogiBoardViewScreenshotTest {

    @Test
    fun shogiBoard_initialPosition() {
        captureRoboImage(
            filePath = "src/test/snapshots/shogiboard_initial.png",
            roborazziOptions = screenshotRoborazziOptions,
        ) {
            TestWrapper {
                ShogiBoardView(
                    sfen = "lnsgkgsnl/1r5b1/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL b - 1",
                )
            }
        }
    }

    @Test
    fun shogiBoard_midgamePosition_ply40() {
        captureRoboImage(
            filePath = "src/test/snapshots/shogiboard_midgame_ply40.png",
            roborazziOptions = screenshotRoborazziOptions,
        ) {
            TestWrapper {
                ShogiBoardView(
                    sfen = "ln2g3l/2ks1s3/1pppppnr1/p7p/5gpp1/P1P4RP/1PSPPSP2/1KGG5/LN5NL b BPbp 41",
                )
            }
        }
    }

    @Test
    fun shogiBoard_flipped() {
        captureRoboImage(
            filePath = "src/test/snapshots/shogiboard_flipped.png",
            roborazziOptions = screenshotRoborazziOptions,
        ) {
            TestWrapper {
                ShogiBoardView(
                    sfen = "ln2g3l/2ks1s3/1pppppnr1/p7p/5gpp1/P1P4RP/1PSPPSP2/1KGG5/LN5NL b BPbp 41",
                    flip = true,
                )
            }
        }
    }

    @Test
    fun blunderCard_noBoard() {
        val sampleBlunder = BlunderRecord(
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
        )
        captureRoboImage(
            filePath = "src/test/snapshots/blunder_card_no_board.png",
            roborazziOptions = screenshotRoborazziOptions,
        ) {
            TestWrapper {
                BlunderCard(report = sampleBlunder)
            }
        }
    }

    @Test
    fun shogiBoard_blackHandMax() {
        captureRoboImage(
            filePath = "src/test/snapshots/shogiboard_black_hand_max.png",
            roborazziOptions = screenshotRoborazziOptions,
        ) {
            TestWrapper {
                ShogiBoardView(sfen = DebugPositions.BLACK_HAND_MAX)
            }
        }
    }

    @Test
    fun shogiBoard_whiteHandMax() {
        captureRoboImage(
            filePath = "src/test/snapshots/shogiboard_white_hand_max.png",
            roborazziOptions = screenshotRoborazziOptions,
        ) {
            TestWrapper {
                ShogiBoardView(sfen = DebugPositions.WHITE_HAND_MAX)
            }
        }
    }

    @Test
    fun shogiBoard_legacySfenFallback() {
        captureRoboImage(
            filePath = "src/test/snapshots/shogiboard_legacy_fallback.png",
            roborazziOptions = screenshotRoborazziOptions,
        ) {
            TestWrapper {
                ShogiBoardView(sfen = "startpos moves 2g2f 3c3d")
            }
        }
    }

}

@Composable
private fun TestWrapper(content: @Composable () -> Unit) {
    ShogiTheme {
        Surface {
            content()
        }
    }
}
