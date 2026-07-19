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

/**
 * ドリル画面の VRT（スクリーンショットテスト）。
 *
 * ゴールデン更新・照合の手順は app/docs/vrt.md 参照。
 */
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
        // 後手視点（flip=true）: 出題盤が180度反転する
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
        // 結果画面のナビラベルに統合した形勢サフィックス（cpBefore あり）。
        // 開始局面ラベル「開始局面」に「（−350）」が連結され1行に収まることを確認する。
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
        // 1手送った状態（initialPlyIndex=1）。ナビラベルが「1手目 ▲３四角（−350）」
        // のように手表記＋形勢サフィックスで1行に収まること、かつ ply=0（drillResult_withEval）
        // と比べてナビ行より下（正誤バナー以下）のY座標が不変であること（No-jitter）を
        // ピクセル比較で確認する対（手送り前後比較用の golden）。
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
        // 「最善」タブ（インデックス1）でライン末尾（bestPv=2手の末尾）に到達した状態。
        // ▶ボタンが「▶+」（primary色）に切り替わり、延長トリガーであることを示す
        // （ReportScreen の「最善の変化」タブと同じ規約）。ナビ行の高さ・ボタンサイズは
        // 他の drill_result 系 golden と不変であること（No-jitter）を目視確認する対。
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
