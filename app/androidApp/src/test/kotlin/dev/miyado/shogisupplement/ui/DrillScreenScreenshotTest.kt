package dev.miyado.shogisupplement.ui

import dev.miyado.shogisupplement.ui.theme.ShogiTheme
import androidx.compose.material3.Surface
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import dev.miyado.shogisupplement.db.BlunderRecord
import dev.miyado.shogisupplement.drill.DrillJudge
import dev.miyado.shogisupplement.ui.drill.DrillQuestionContent
import dev.miyado.shogisupplement.ui.drill.DrillResultContent
import dev.miyado.shogisupplement.ui.drill.DrillUiState
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
class DrillScreenScreenshotTest {

    @OptIn(ExperimentalRoborazziApi::class)
    private val roborazziOptions = RoborazziOptions(
        recordOptions = RoborazziOptions.RecordOptions(resizeScale = 0.5),
        compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f),
    )

    @Test
    fun drillQuestion() {
        captureRoboImage(
            filePath = "src/test/snapshots/drill_question.png",
            roborazziOptions = roborazziOptions,
        ) {
            ShogiTheme {
                Surface {
                    DrillQuestionContent(
                        state = DrillUiState.Question(
                            blunder = vrtBlunderRecord(),
                            sfenCurrent = "ln2g3l/2ks1s3/1pppppnr1/p7p/5gpp1/P1P4RP/1PSPPSP2/1KGG5/LN5NL b BPbp 41",
                            attemptCount = 2,
                            totalCandidates = 5,
                        ),
                        onSquareTapped = {},
                        onHandPieceTapped = {},
                        onPromoteDecision = {},
                        onSurrender = {},
                    )
                }
            }
        }
    }

    @Test
    fun drillQuestion_flipped() {
        captureRoboImage(
            filePath = "src/test/snapshots/drill_question_flipped.png",
            roborazziOptions = roborazziOptions,
        ) {
            ShogiTheme {
                Surface {
                    DrillQuestionContent(
                        state = DrillUiState.Question(
                            blunder = vrtBlunderRecord(),
                            sfenCurrent = "ln2g3l/2ks1s3/1pppppnr1/p7p/5gpp1/P1P4RP/1PSPPSP2/1KGG5/LN5NL b BPbp 41",
                            attemptCount = 2,
                            totalCandidates = 5,
                            flip = true,
                        ),
                        onSquareTapped = {},
                        onHandPieceTapped = {},
                        onPromoteDecision = {},
                        onSurrender = {},
                    )
                }
            }
        }
    }

    @Test
    fun drillResult_correct() {
        captureRoboImage(
            filePath = "src/test/snapshots/drill_result_correct.png",
            roborazziOptions = roborazziOptions,
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
                        blunder = vrtBlunderRecord(),
                        onNext = {},
                        onBack = {},
                    )
                }
            }
        }
    }

    @Test
    fun drillResult_incorrect() {
        captureRoboImage(
            filePath = "src/test/snapshots/drill_result_incorrect.png",
            roborazziOptions = roborazziOptions,
        ) {
            ShogiTheme {
                Surface {
                    DrillResultContent(
                        result = DrillJudge.DrillResult(
                            isCorrect = false,
                            lossWp = 0.225,
                            userMoveUsi = "B*3d",
                            bestMoveUsi = "2f6f",
                            reason = DrillJudge.Reason.MATCH_ACTUAL_BLUNDER,
                        ),
                        blunder = vrtBlunderRecord(),
                        onNext = {},
                        onBack = {},
                    )
                }
            }
        }
    }

    @Test
    fun drillResult_withEval() {
        captureRoboImage(
            filePath = "src/test/snapshots/drill_result_with_eval.png",
            roborazziOptions = roborazziOptions,
        ) {
            ShogiTheme {
                Surface {
                    DrillResultContent(
                        result = DrillJudge.DrillResult(
                            isCorrect = false,
                            lossWp = 0.225,
                            userMoveUsi = "B*3d",
                            bestMoveUsi = "2f6f",
                            reason = DrillJudge.Reason.MATCH_ACTUAL_BLUNDER,
                        ),
                        blunder = vrtBlunderRecord().copy(cpBefore = -350L, cpAfter = 200L),
                        onNext = {},
                        onBack = {},
                    )
                }
            }
        }
    }

    @Test
    fun drillResult_withEval_ply1() {
        captureRoboImage(
            filePath = "src/test/snapshots/drill_result_with_eval_ply1.png",
            roborazziOptions = roborazziOptions,
        ) {
            ShogiTheme {
                Surface {
                    DrillResultContent(
                        result = DrillJudge.DrillResult(
                            isCorrect = false,
                            lossWp = 0.225,
                            userMoveUsi = "B*3d",
                            bestMoveUsi = "2f6f",
                            reason = DrillJudge.Reason.MATCH_ACTUAL_BLUNDER,
                        ),
                        blunder = vrtBlunderRecord().copy(cpBefore = -350L, cpAfter = 200L),
                        initialPlyIndex = 1,
                        onNext = {},
                        onBack = {},
                    )
                }
            }
        }
    }

    @Test
    fun drillResult_extend_indicator() {
        captureRoboImage(
            filePath = "src/test/snapshots/drill_result_extend_indicator.png",
            roborazziOptions = roborazziOptions,
        ) {
            ShogiTheme {
                Surface {
                    DrillResultContent(
                        result = DrillJudge.DrillResult(
                            isCorrect = false,
                            lossWp = 0.225,
                            userMoveUsi = "B*3d",
                            bestMoveUsi = "2f6f",
                            reason = DrillJudge.Reason.MATCH_ACTUAL_BLUNDER,
                        ),
                        blunder = vrtBlunderRecord(),
                        initialActiveLineIdx = 1,
                        initialPlyIndex = 2,
                        onNext = {},
                        onBack = {},
                    )
                }
            }
        }
    }
}

private fun vrtBlunderRecord() = BlunderRecord(
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
)
