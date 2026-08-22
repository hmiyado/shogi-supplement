package dev.miyado.shogisupplement.ui

import androidx.compose.material3.Surface
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import dev.miyado.shogisupplement.ui.strength.EstimatedStrengthDetailScreen
import dev.miyado.shogisupplement.ui.strength.StrengthDetailBestRank
import dev.miyado.shogisupplement.ui.strength.StrengthDetailData
import dev.miyado.shogisupplement.ui.strength.StrengthDetailService
import dev.miyado.shogisupplement.ui.strength.StrengthDetailServiceRule
import dev.miyado.shogisupplement.ui.strength.StrengthTrendPoint
import dev.miyado.shogisupplement.ui.theme.ShogiTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 推定棋力詳細画面の VRT: 現在の推定棋力／対局ごとの推移／対局サービスの3カード。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    qualifiers = "w400dp-h1200dp-xxhdpi",
    application = android.app.Application::class,
)
class EstimatedStrengthDetailScreenScreenshotTest {

    @OptIn(ExperimentalRoborazziApi::class)
    private val roborazziOptions = RoborazziOptions(
        recordOptions = RoborazziOptions.RecordOptions(resizeScale = 0.5),
        compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f),
    )

    private fun sampleData(): StrengthDetailData = StrengthDetailData(
        deviation = 58,
        rangeLow = 54,
        rangeHigh = 62,
        bestRank = StrengthDetailBestRank(label = "将棋ウォーズ 初段", ruleLabel = "3分切れ負け"),
        trend = listOf(
            StrengthTrendPoint(1, "8/02", 46, 11, "16%(6/38)", "34%(13/38)"),
            StrengthTrendPoint(2, "8/04", 49, 11, "13%(5/38)", "39%(15/38)"),
            StrengthTrendPoint(3, "8/06", 47, 11, "15%(5/34)", "35%(12/34)"),
            StrengthTrendPoint(4, "8/08", 52, 11, "10%(4/39)", "44%(17/39)"),
            StrengthTrendPoint(5, "8/10", 51, 11, "12%(4/34)", "41%(14/34)"),
            StrengthTrendPoint(6, "8/12", 55, 11, "9%(3/35)", "46%(16/35)"),
            StrengthTrendPoint(7, "8/15", 56, 11, "9%(3/34)", "47%(16/34)"),
            StrengthTrendPoint(8, "8/17", 58, 11, "8%(3/38)", "47%(18/38)"),
        ),
        services = listOf(
            StrengthDetailService(
                serviceId = "shogi_wars",
                label = "将棋ウォーズ",
                accountName = "sample",
                rules = listOf(
                    StrengthDetailServiceRule("10分切れ負け", "2級"),
                    StrengthDetailServiceRule("3分切れ負け", "初段"),
                    StrengthDetailServiceRule("10秒将棋", null),
                ),
            ),
            StrengthDetailService(
                serviceId = "lishogi",
                label = "lishogi",
                accountName = "sample_shogi",
                ratingText = "1542",
            ),
        ),
    )

    @Test
    fun strength_detail_default() {
        captureRoboImage(
            filePath = "src/test/snapshots/strength_detail_default.png",
            roborazziOptions = roborazziOptions,
        ) {
            ShogiTheme {
                Surface {
                    EstimatedStrengthDetailScreen(
                        data = sampleData(),
                        onBack = {},
                        onEditAccounts = {},
                    )
                }
            }
        }
    }

    @Test
    fun strength_detail_dark() {
        captureRoboImage(
            filePath = "src/test/snapshots/strength_detail_dark.png",
            roborazziOptions = roborazziOptions,
        ) {
            ShogiTheme(themeMode = "dark") {
                Surface {
                    EstimatedStrengthDetailScreen(
                        data = sampleData(),
                        onBack = {},
                        onEditAccounts = {},
                    )
                }
            }
        }
    }

    /** 対局サービスに何も入力していない状態（空メッセージ表示）。 */
    @Test
    fun strength_detail_no_services() {
        captureRoboImage(
            filePath = "src/test/snapshots/strength_detail_no_services.png",
            roborazziOptions = roborazziOptions,
        ) {
            ShogiTheme {
                Surface {
                    EstimatedStrengthDetailScreen(
                        data = sampleData().copy(bestRank = null, services = emptyList()),
                        onBack = {},
                        onEditAccounts = {},
                    )
                }
            }
        }
    }
}
