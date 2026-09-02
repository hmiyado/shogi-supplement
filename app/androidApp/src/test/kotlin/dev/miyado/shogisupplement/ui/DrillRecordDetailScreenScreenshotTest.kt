package dev.miyado.shogisupplement.ui

import androidx.compose.material3.Surface
import com.github.takahirom.roborazzi.captureRoboImage
import dev.miyado.shogisupplement.ui.drillrecord.DrillRecordDetailData
import dev.miyado.shogisupplement.ui.drillrecord.DrillRecordDetailScreen
import dev.miyado.shogisupplement.ui.drillrecord.WeekStreakRow
import dev.miyado.shogisupplement.ui.theme.ShogiTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 学習の記録の詳細画面の VRT: 概要／30日の密度グリッド／7日間連続の達成の3カード。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    qualifiers = "w400dp-h1200dp-xxhdpi",
    application = android.app.Application::class,
)
class DrillRecordDetailScreenScreenshotTest {

    // 濃さ4段階（0 / 1-2 / 3-5 / 6以上）と休んだ日が混ざる30日。末尾が今日。
    private val dailyCounts = listOf(
        1, 3, 0, 2, 8, 4, 0, 0, 1, 3,
        7, 5, 2, 1, 0, 4, 9, 6, 3, 2,
        0, 1, 4, 5, 8, 2, 0, 3, 1, 6,
    )

    private fun sampleData() = DrillRecordDetailData(
        activeDaysInWindow = 24,
        windowDays = 30,
        totalAttempts = 214,
        correctAttempts = 137,
        dailyCounts = dailyCounts,
        weekStreaks = listOf(
            WeekStreakRow(ordinal = 2, startLabel = "8/12", endLabel = "8/18"),
            WeekStreakRow(ordinal = 1, startLabel = "7/29", endLabel = "8/4"),
        ),
    )

    @Test
    fun drill_record_detail() {
        captureRoboImage(
            filePath = "src/test/snapshots/drill_record_detail.png",
            roborazziOptions = screenshotRoborazziOptions,
        ) {
            ShogiTheme {
                Surface {
                    DrillRecordDetailScreen(data = sampleData(), onBack = {})
                }
            }
        }
    }

    @Test
    fun drill_record_detail_no_streak() {
        captureRoboImage(
            filePath = "src/test/snapshots/drill_record_detail_no_streak.png",
            roborazziOptions = screenshotRoborazziOptions,
        ) {
            ShogiTheme {
                Surface {
                    DrillRecordDetailScreen(
                        data = sampleData().copy(weekStreaks = emptyList()),
                        onBack = {},
                    )
                }
            }
        }
    }

    @Test
    fun drill_record_detail_dark() {
        captureRoboImage(
            filePath = "src/test/snapshots/drill_record_detail_dark.png",
            roborazziOptions = screenshotRoborazziOptions,
        ) {
            ShogiTheme(themeMode = "dark") {
                Surface {
                    DrillRecordDetailScreen(data = sampleData(), onBack = {})
                }
            }
        }
    }
}
