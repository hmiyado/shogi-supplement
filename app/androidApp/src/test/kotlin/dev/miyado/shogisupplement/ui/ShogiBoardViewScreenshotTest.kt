package dev.miyado.shogisupplement.ui

import dev.miyado.shogisupplement.ui.theme.ShogiTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import dev.miyado.shogisupplement.db.BlunderRecord
import dev.miyado.shogisupplement.ui.common.ShogiBoardView
import dev.miyado.shogisupplement.ui.report.BlunderCard
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * ShogiBoardView の VRT（スクリーンショットテスト）。
 *
 * Roborazzi + Robolectric で JVM 上でレンダリングし、
 * src/test/snapshots/ にゴールデン画像を保存する。
 *
 * ## ゴールデン更新
 *   ./gradlew :androidApp:recordRoborazziDebug
 *
 * ## 照合（CI）
 *   ./gradlew :androidApp:verifyRoborazziDebug
 *
 * ## 通常テスト（照合なし）
 *   ./gradlew :androidApp:testDebugUnitTest
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    qualifiers = "w400dp-h800dp-xxhdpi",
    application = android.app.Application::class,
)
class ShogiBoardViewScreenshotTest {

    @OptIn(ExperimentalRoborazziApi::class)
    private val roborazziOptions = RoborazziOptions(
        recordOptions = RoborazziOptions.RecordOptions(resizeScale = 0.5),
        compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f),
    )

    // ─── 局面図 単体テスト ─────────────────────────────────────────────────

    @Test
    fun shogiBoard_initialPosition() {
        captureRoboImage(
            filePath = "src/test/snapshots/shogiboard_initial.png",
            roborazziOptions = roborazziOptions,
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
        // miyado_game1.kif 41手目直前の局面（仕様書指定 SFEN）
        captureRoboImage(
            filePath = "src/test/snapshots/shogiboard_midgame_ply40.png",
            roborazziOptions = roborazziOptions,
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
        // 後手視点（flip=true）: 盤が180度反転し後手が下になる
        captureRoboImage(
            filePath = "src/test/snapshots/shogiboard_flipped.png",
            roborazziOptions = roborazziOptions,
        ) {
            TestWrapper {
                ShogiBoardView(
                    sfen = "ln2g3l/2ks1s3/1pppppnr1/p7p/5gpp1/P1P4RP/1PSPPSP2/1KGG5/LN5NL b BPbp 41",
                    flip = true,
                )
            }
        }
    }

    // ─── 悪手カード（ミニ盤なし）テスト ─────────────────────────────────────

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
            roborazziOptions = roborazziOptions,
        ) {
            TestWrapper {
                BlunderCard(report = sampleBlunder)
            }
        }
    }

    // ─── 持駒最大局面（「×N」が見切れないこと）────────────────────────────

    @Test
    fun shogiBoard_blackHandMax() {
        // 先手持駒最大（2R2B4G4S4N4L18P）: FlowRow 折返しで全持駒が見切れず表示される
        captureRoboImage(
            filePath = "src/test/snapshots/shogiboard_black_hand_max.png",
            roborazziOptions = roborazziOptions,
        ) {
            TestWrapper {
                ShogiBoardView(sfen = DebugPositions.BLACK_HAND_MAX)
            }
        }
    }

    @Test
    fun shogiBoard_whiteHandMax() {
        // 後手持駒最大（2r2b4g4s4n4l18p）: 上段の持駒行も折返しで見切れない
        captureRoboImage(
            filePath = "src/test/snapshots/shogiboard_white_hand_max.png",
            roborazziOptions = roborazziOptions,
        ) {
            TestWrapper {
                ShogiBoardView(sfen = DebugPositions.WHITE_HAND_MAX)
            }
        }
    }

    // ─── レガシー SFEN フォールバックのスナップショット ──────────────────

    @Test
    fun shogiBoard_legacySfenFallback() {
        // 旧形式（startpos moves...）が渡された場合も crash しないことを確認
        // DB 読み込み時に convertLegacySfen で変換済みなので、
        // ここでは parseが空盤またはフォールバックを返すだけで安全なことを確認
        captureRoboImage(
            filePath = "src/test/snapshots/shogiboard_legacy_fallback.png",
            roborazziOptions = roborazziOptions,
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
