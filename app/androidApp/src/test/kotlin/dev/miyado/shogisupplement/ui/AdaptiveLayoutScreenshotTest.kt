package dev.miyado.shogisupplement.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import dev.miyado.shogisupplement.R
import dev.miyado.shogisupplement.db.BlunderRecord
import dev.miyado.shogisupplement.db.GameRecord
import dev.miyado.shogisupplement.db.PositionEvalRow
import dev.miyado.shogisupplement.drill.DrillJudge
import dev.miyado.shogisupplement.ui.drill.DrillQuestionContent
import dev.miyado.shogisupplement.ui.drill.DrillResultContent
import dev.miyado.shogisupplement.ui.drill.DrillUiState
import dev.miyado.shogisupplement.ui.home.HomeScreen
import dev.miyado.shogisupplement.ui.home.StrengthCardData
import dev.miyado.shogisupplement.ui.home.TodaysDrillHint
import dev.miyado.shogisupplement.ui.report.ReportScreen
import dev.miyado.shogisupplement.ui.theme.ShogiTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 広い画面のレイアウト。expanded（1024dp = iPad横）で盤とデータが2ペインに割れること、
 * medium（834dp = iPad縦）では1カラムのまま本文が最大幅で止まることを固定する。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class AdaptiveLayoutScreenshotTest {

    @Composable
    private fun testTitleIcon() {
        Image(
            painter = painterResource(id = R.drawable.ic_app_title_icon),
            contentDescription = null,
            modifier = Modifier.height(30.dp).width(24.dp),
        )
    }

    private fun sampleGames() = listOf(
        GameRecord(
            id = 1L,
            fileName = "miyado_game1.kif",
            contentHash = "hash1",
            moveCount = 74L,
            senteName = "miyado",
            goteName = "相手",
            analyzedAt = 1_780_000_000L,
            rating = 1750L,
            coefVersion = "hao_v1",
            uploadedAt = 1_780_000_100L,
        ),
        GameRecord(
            id = 2L,
            fileName = "miyado_game2.kif",
            contentHash = "hash2",
            moveCount = 50L,
            senteName = null,
            goteName = null,
            analyzedAt = 1_779_000_000L,
            rating = 1750L,
            coefVersion = "hao_v1",
            uploadedAt = null,
        ),
    )

    private fun reportGame() = sampleGames()[0].copy(
        movesUsi = listOf("7g7f", "3c3d", "2g2f", "8c8d"),
        userSide = "sente",
    )

    private fun sampleBlunder() = BlunderRecord(
        id = 1L,
        gameId = 1L,
        ply = 3L,
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

    @Composable
    private fun ReportUnderTest() {
        ReportScreen(
            game = reportGame(),
            reports = listOf(sampleBlunder()),
            flip = false,
            strengthDisplayText = "52 ±27",
            matchRateDisplayText = "62%(31/50)",
            blunderRateDisplayText = "12%(3/25)",
            positionEvals = listOf(
                PositionEvalRow(ply = 0, scoreCp = 50, mateIn = null, bestUsi = "7g7f"),
                PositionEvalRow(ply = 1, scoreCp = -30, mateIn = null, bestUsi = "8c8d"),
                PositionEvalRow(ply = 2, scoreCp = 180, mateIn = null, bestUsi = "2g2f"),
                PositionEvalRow(ply = 3, scoreCp = -620, mateIn = null, bestUsi = "8b3b"),
                PositionEvalRow(ply = 4, scoreCp = null, mateIn = -7, bestUsi = "2f2e"),
            ),
            initialPlyIndex = 2,
            onBack = {},
        )
    }

    @Test
    @Config(qualifiers = "w1024dp-h768dp-xxhdpi")
    fun home_expanded() {
        captureRoboImage(
            filePath = "src/test/snapshots/adaptive_home_expanded.png",
            roborazziOptions = screenshotRoborazziOptions,
        ) {
            ShogiTheme {
                Surface {
                    HomeScreen(
                        pastGames = sampleGames(),
                        isLoggedIn = true,
                        strengthCard = StrengthCardData(
                            displayText = "51 ±25",
                            detailText = "直近5局から算出",
                        ),
                        todaysDrillHint = TodaysDrillHint(ply = 41L),
                        onOpenKif = {},
                        onGameClick = {},
                        onStartDrill = {},
                        onViewAllGames = {},
                        titleIcon = { testTitleIcon() },
                    )
                }
            }
        }
    }

    @Test
    @Config(qualifiers = "w1024dp-h768dp-xxhdpi")
    fun report_expanded() {
        captureRoboImage(
            filePath = "src/test/snapshots/adaptive_report_expanded.png",
            roborazziOptions = screenshotRoborazziOptions,
        ) {
            ShogiTheme { Surface { ReportUnderTest() } }
        }
    }

    /** デスクトップ幅では、ペインが600dpを超えてもデータ側は最大幅で止まる。 */
    @Test
    @Config(qualifiers = "w1440dp-h900dp-xxhdpi")
    fun report_desktop() {
        captureRoboImage(
            filePath = "src/test/snapshots/adaptive_report_desktop.png",
            roborazziOptions = screenshotRoborazziOptions,
        ) {
            ShogiTheme { Surface { ReportUnderTest() } }
        }
    }

    /** 横向きのiPhone（852x393pt）もexpandedに入る。低い高さでも盤とナビが収まること。 */
    @Test
    @Config(qualifiers = "w852dp-h393dp-xxhdpi")
    fun report_phoneLandscape() {
        captureRoboImage(
            filePath = "src/test/snapshots/adaptive_report_phone_landscape.png",
            roborazziOptions = screenshotRoborazziOptions,
        ) {
            ShogiTheme { Surface { ReportUnderTest() } }
        }
    }

    @Test
    @Config(qualifiers = "w834dp-h1112dp-xxhdpi")
    fun report_medium() {
        captureRoboImage(
            filePath = "src/test/snapshots/adaptive_report_medium.png",
            roborazziOptions = screenshotRoborazziOptions,
        ) {
            ShogiTheme { Surface { ReportUnderTest() } }
        }
    }

    @Test
    @Config(qualifiers = "w1024dp-h768dp-xxhdpi")
    fun drillQuestion_expanded() {
        captureRoboImage(
            filePath = "src/test/snapshots/adaptive_drill_question_expanded.png",
            roborazziOptions = screenshotRoborazziOptions,
        ) {
            ShogiTheme {
                Surface {
                    DrillQuestionContent(
                        state = DrillUiState.Question(
                            blunder = sampleBlunder(),
                            sfenCurrent = sampleBlunder().sfenBefore,
                            attemptCount = 2,
                            totalCandidates = 5,
                        ),
                        onSquareTapped = {},
                        onHandPieceTapped = {},
                        onPromoteDecision = {},
                        onSurrender = {},
                        onUndoMove = {},
                        onResetMoves = {},
                        onSubmitAnswer = {},
                    )
                }
            }
        }
    }

    @Test
    @Config(qualifiers = "w1024dp-h768dp-xxhdpi")
    fun drillResult_expanded() {
        captureRoboImage(
            filePath = "src/test/snapshots/adaptive_drill_result_expanded.png",
            roborazziOptions = screenshotRoborazziOptions,
        ) {
            ShogiTheme {
                Surface {
                    DrillResultContent(
                        result = DrillJudge.DrillResult(
                            isCorrect = true,
                            lossWp = 0.0,
                            userMoveUsi = "2f6f",
                            bestMoveUsi = "2f6f",
                            reason = DrillJudge.Reason.MATCH_BEST,
                        ),
                        blunder = sampleBlunder(),
                        onNext = {},
                        onBack = {},
                    )
                }
            }
        }
    }
}
