package dev.miyado.shogisupplement.ui

import androidx.compose.material3.Surface
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
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

/**
 * 解析中レポート画面（[AnalyzingReportScreen]）の VRT（スクリーンショットテスト）。
 * 解析中の2状態（30%・80%）を撮る
 * （完了直後・定常は ReportViewerScreenshotTest 側。完成レポート表示を再利用するため）。
 *
 * - analyzing_report_30pct: 進捗バナー・盤スクリム・グラフの反映区間実線＋未反映区間
 *   ハッチング・サマリーの「—」固定表示・悪手一覧ボタン無効を確認
 * - analyzing_report_80pct: 反映区間が広がり、悪手一覧の朱ドット＋卵黄の反映先端ドットを
 *   グラフ上で確認（ply=3で意図的にスイング悪手を発生させている）
 *
 * ゴールデン更新: ./gradlew :androidApp:recordRoborazziDebug
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    qualifiers = "w400dp-h800dp-xxhdpi",
    application = android.app.Application::class,
)
class AnalyzingReportScreenScreenshotTest {

    @OptIn(ExperimentalRoborazziApi::class)
    private val roborazziOptions = RoborazziOptions(
        recordOptions = RoborazziOptions.RecordOptions(resizeScale = 0.5),
        compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f),
    )

    private val sampleMoves = listOf(
        "7g7f", "3c3d", "2g2f", "8c8d", "2f2e", "8d8e", "2e2d", "2c2d", "2h2d", "4a3b",
    )

    private fun pv(cp: Int): List<PvInfo> =
        listOf(PvInfo(multipv = 1, score = Score.Cp(cp), pv = emptyList(), nodes = 0L))

    /**
     * moves.size+1局面ぶんの評価値からconfirmedThrough件だけ順に反映したアキュムレータを作る。
     * cps[2]=100 → cps[3]=400 の並びはBlunderJudgeTestの「スイング」ケースと同じ数値
     * （損失500cp・悪手判定）を流用し、80%状態のply=3に朱ドットが立つようにしている。
     */
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
            roborazziOptions = roborazziOptions,
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
            roborazziOptions = roborazziOptions,
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
